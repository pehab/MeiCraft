package de.meiapps.meicraft;

import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Real REST/session/renderer code against deterministic offline transports; no cloud requests. */
final class RealtimeChecks {
    final SmokeInstrumentation test;RealtimeChecks(SmokeInstrumentation test){this.test=test;}
    void check(boolean ok,String message){test.check(ok,message);}
    static final class Backend implements FirebaseRest.ConnectionFactory {
        final OnlineV5Checks.Store firestore=new OnlineV5Checks.Store();final RealtimeTree data=new RealtimeTree();
        final List<String> methods=new ArrayList<>();boolean outage;int redirects;
        @Override public HttpURLConnection open(URL url)throws IOException{
            if(!url.getHost().endsWith("firebasedatabase.app"))return firestore.open(url);
            return new HttpURLConnection(url){
                final ByteArrayOutputStream body=new ByteArrayOutputStream();String response;int status=200;
                @Override public void connect(){}@Override public void disconnect(){}@Override public boolean usingProxy(){return false;}
                @Override public OutputStream getOutputStream(){return body;}
                void evaluate()throws IOException{if(response!=null)return;try{
                    if(outage)throw new IOException("fixture offline");
                    if("text/event-stream".equals(getRequestProperty("Accept"))){if(redirects++==0){status=307;response="";}else response="event: put\ndata: {\"path\":\"/\",\"data\":null}\n\n";return;}
                    methods.add(getRequestMethod());String path=url.getPath().substring(0,url.getPath().length()-5);path=path.replaceFirst("^/worlds/[^/]+/[^/]+","");if(path.isEmpty())path="/";Object value=JSONObject.NULL;
                    if(getRequestMethod().equals("GET")){value=data.root.length()==0?JSONObject.NULL:data.root;if(!path.equals("/"))for(String part:path.substring(1).split("/")){if(!(value instanceof JSONObject)){value=JSONObject.NULL;break;}value=((JSONObject)value).opt(part);if(value==null){value=JSONObject.NULL;break;}}}
                    else{if(!getRequestMethod().equals("DELETE"))value=new JSONTokener(body.toString("UTF-8")).nextValue();String payload=new JSONObject().put("path",path).put("data",value).toString();data.apply(getRequestMethod().equals("PATCH")?"patch":"put",payload);}
                    response=value.toString();
                }catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}}
                @Override public String getHeaderField(String name){if(name.equalsIgnoreCase("Location"))return "https://redirect.europe-west1.firebasedatabase.app"+url.getFile();return null;}
                @Override public int getResponseCode()throws IOException{evaluate();return status;}
                @Override public InputStream getInputStream()throws IOException{evaluate();return new ByteArrayInputStream(response.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            };
        }
    }
    void settle(OnlineSession s,GameView game)throws Exception{s.worker.schedule(()->{},100,TimeUnit.MILLISECONDS).get(10,TimeUnit.SECONDS);test.onGl(game,()->{});}
    void run(MainActivity activity,GameView game)throws Exception{
        RealtimeTree tree=new RealtimeTree();tree.apply("put","{\"path\":\"/\",\"data\":{\"players\":{\"one\":{\"x\":1,\"y\":2}}}}");
        tree.apply("patch","{\"path\":\"/players\",\"data\":{\"one/x\":3,\"two\":{\"x\":4}}}");check(tree.root.getJSONObject("players").getJSONObject("one").getInt("x")==3&&tree.root.getJSONObject("players").getJSONObject("one").getInt("y")==2,"RTDB nested patch preserves sibling fields");
        tree.apply("put","{\"path\":\"/players/one\",\"data\":null}");check(!tree.root.getJSONObject("players").has("one"),"RTDB null removes a subtree");
        tree.apply("put","{\"path\":\"/\",\"data\":null}");check(tree.root.length()==0,"Reconnect root snapshot replaces the whole local tree");
        Backend backend=new Backend();FirebaseRest f=new FirebaseRest(activity,backend);f.uid="parent";f.token="fixture-token";f.expires=System.currentTimeMillis()+3600000;
        check(!f.realtimeEligible,"Injected offline Firebase clients cannot auto-start a production RTDB connection");
        OnlineSession s=new OnlineSession(activity,f,new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId()),"Parent",new OnlineSession.Listener(){public void loaded(World w,String n){}public void problem(String t,boolean fatal){}});s.modern=new OnlineV5(s);
        OnlineV5Checks v5=new OnlineV5Checks(test);
        try{
            s.modern.start(true,"RTDB Test",2);v5.stock(backend.firestore,s);s.loading=false;s.healthy=true;
            OnlineRealtime live=new OnlineRealtime(s);s.realtime=live;live.initialize();check(live.base.equals("worlds/parent/"+s.invitation.id),"RTDB namespace is bound to the authenticated Firestore owner");
            live.event("put","{\"path\":\"/\",\"data\":null}");settle(s,game);int reads=backend.firestore.batchGets.size(),writes=backend.firestore.commits.size();
            long now=System.currentTimeMillis();for(int i=0;i<100;i++)live.event("put",new JSONObject().put("path","/players/child").put("data",new JSONObject().put("name","Child").put("sector",0).put("x",40+i*.01).put("y",30).put("z",40).put("yaw",0).put("t",now+i)).toString());
            live.tick();s.modern.heartbeat();settle(s,game);
            if(live.lastFailure!=null)throw new AssertionError("RTDB sender failed in offline transport",live.lastFailure);
            check(backend.firestore.batchGets.size()==reads&&backend.firestore.commits.size()==writes,"One hundred live positions cause zero Firestore reads or position writes (reads "+reads+" -> "+backend.firestore.batchGets.size()+", writes "+writes+" -> "+backend.firestore.commits.size()+", connected="+live.connected+", legacy="+live.legacyPeers+")");
            check(s.players.size()==1&&s.players.get(0).uid.equals("child")&&Math.abs(s.players.get(0).x-40.99f)<.001,"Live peer positions reach the renderer model independently of Firestore polling");
            s.modern.edit(10,44,10,2);v5.drain(s,game);check(live.outgoing.get()>0&&backend.firestore.commits.get(writes).getJSONArray("writes").length()==2,"Only an atomic durable block/inventory commit queues a notification");
            live.tick();check(live.outgoing.get()==0&&backend.methods.contains("PUT"),"Sender drains latest confirmed revision through authenticated RTDB REST");
            reads=backend.firestore.batchGets.size();live.event("put",new JSONObject().put("path","/signals/parent").put("data",new JSONObject().put("rev",s.modern.revision).put("t",now)).toString());settle(s,game);check(backend.firestore.batchGets.size()==reads,"Own notification never causes a Firestore echo read");
            live.event("patch","{\"path\":\"/signals\",\"data\":{\"child\":{\"rev\":1},\"sibling\":{\"rev\":1}}}");settle(s,game);check(backend.firestore.batchGets.size()==reads+1,"Simultaneous peer notifications coalesce into one consistent Firestore snapshot");
            live.event("put","{\"path\":\"/signals/child\",\"data\":{\"rev\":1}}");settle(s,game);check(backend.firestore.batchGets.size()==reads+1,"Duplicate revisions do not cause duplicate reads");
            backend.firestore.lostReply=true;s.modern.edit(11,44,10,2);settle(s,game);check(!s.inventoryUncertain&&s.world.get(11,44,10)==2&&live.outgoing.get()>0,"Lost commit reply is reconciled before broadcasting the confirmed revision");
            backend.outage=true;live.tick();settle(s,game);check(!live.connected&&s.healthy,"RTDB failure returns to a functioning authorized Firestore session");backend.outage=false;
            final int[] events={0};try{live.db.listen(live.base+"/live",(type,data)->events[0]++);}catch(EOFException expected){}check(backend.redirects==2&&events[0]==1,"REST EventSource follows Firebase 307 and decodes its initial frame");
            live.connected=true;live.event("put",new JSONObject().put("path","/players/child/t").put("data",System.currentTimeMillis()-9000).toString());live.tick();check(s.players.isEmpty(),"Crashed peers expire after eight seconds without SDK onDisconnect");
        }finally{s.close();if(s.regionCache!=null)s.regionCache.writer.awaitTermination(10,TimeUnit.SECONDS);}
        System.out.println("RTDB CHECKS: real transport, patching, pose-only budget, commit signals, lost replies, fallback, redirects and stale peers verified");
    }
}
