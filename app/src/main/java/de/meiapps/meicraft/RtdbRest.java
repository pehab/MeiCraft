package de.meiapps.meicraft;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Existing anonymous Firebase ID token, REST mutations and one persistent EventSource. */
final class RtdbRest implements AutoCloseable {
    final FirebaseRest f;final String endpoint;volatile HttpURLConnection stream;volatile boolean closed;volatile long serverOffset;
    RtdbRest(FirebaseRest f)throws Exception{
        this.f=f;JSONObject config;
        try(InputStream in=f.context.getAssets().open("realtime-database.json")){config=new JSONObject(new String(FirebaseRest.read(in),StandardCharsets.UTF_8));}
        endpoint=config.getString("url");URL url=new URL(endpoint);
        if(!config.getString("project").equals(f.project)||!url.getProtocol().equals("https")||!url.getHost().equals(f.project+"-default-rtdb.europe-west1.firebasedatabase.app")||!url.getPath().isEmpty()||url.getPort()!=-1||url.getQuery()!=null||url.getRef()!=null)throw new IOException("RTDB-Konfiguration passt nicht zum Firebase-Projekt.");
    }
    HttpURLConnection open(String path,String accept)throws Exception{
        int queryAt=path.indexOf('?');String query=queryAt<0?"":"&"+path.substring(queryAt+1),resource=queryAt<0?path:path.substring(0,queryAt);
        String token=f.idToken();URL url=new URL(endpoint+"/"+resource+".json?auth="+URLEncoder.encode(token,"UTF-8")+query);
        for(int redirect=0;redirect<4;redirect++){
            if(closed)throw new IOException("RTDB beendet.");HttpURLConnection c=f.connections.open(url);
            c.setConnectTimeout(5000);c.setReadTimeout(accept==null?5000:65000);c.setInstanceFollowRedirects(false);if(accept!=null)c.setRequestProperty("Accept",accept);
            if(accept==null)return c;
            stream=c;if(closed){c.disconnect();throw new IOException("RTDB beendet.");}
            int status=c.getResponseCode();if(status==307){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("RTDB-Weiterleitung fehlt.");URL next=new URL(url,location);
                if(!next.getProtocol().equals("https")||next.getUserInfo()!=null||next.getPort()!=-1||!next.getHost().endsWith(".firebasedatabase.app")&&!next.getHost().endsWith(".firebaseio.com"))throw new IOException("Ungültige RTDB-Weiterleitung.");url=next;continue;}
            check(c,status,token);long date=c.getHeaderFieldDate("Date",0);if(date>0)serverOffset=date-System.currentTimeMillis();return c;
        }throw new IOException("Zu viele RTDB-Weiterleitungen.");
    }
    void check(HttpURLConnection c,int status,String token)throws IOException{
        if(status>=200&&status<300)return;
        try(InputStream in=c.getErrorStream()){if(in!=null)FirebaseRest.read(in);}finally{c.disconnect();}
        if(status==401)f.invalidateToken(token);throw f.failure(status,"RTDB","Livekanal", "{}");
    }
    Object request(String method,String path,Object value)throws Exception{return request(method,path,value,false);}
    Object request(String method,String path,Object value,boolean create)throws Exception{return exchange(method,path,value,create?"null_etag":null,false).value;}
    static final class Version {final Object value;final String etag;Version(Object value,String etag){this.value=value;this.etag=etag;}}
    Version version(String path)throws Exception{return exchange("GET",path,null,null,true);}
    void compare(String path,Object value,String etag)throws Exception{exchange("PUT",path,value,etag,false);}
    private Version exchange(String method,String path,Object value,String etag,boolean version)throws Exception{
        HttpURLConnection c;try{c=open(path,null);}catch(FirebaseRest.Failure e){throw e;}catch(IOException e){throw new IOException("RTDB-Verbindung unterbrochen. Erneuter Versuch folgt.",e);}boolean success=false;FirebaseRest.DIAGNOSTICS.request("RTDB · "+method);
        try{
            c.setRequestMethod(method);if(etag!=null)c.setRequestProperty("if-match",etag);if(version)c.setRequestProperty("X-Firebase-ETag","true");
            if(value!=null){byte[] body=value.toString().getBytes(StandardCharsets.UTF_8);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");c.setFixedLengthStreamingMode(body.length);FirebaseRest.DIAGNOSTICS.sent(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}}
            check(c,c.getResponseCode(),URLDecoder.decode(c.getURL().getQuery().substring(5).split("&",2)[0],"UTF-8"));Object result;
            try(InputStream in=c.getInputStream()){byte[] body=FirebaseRest.read(in);FirebaseRest.DIAGNOSTICS.received(body.length);result=body.length==0?JSONObject.NULL:new JSONTokener(new String(body,StandardCharsets.UTF_8)).nextValue();}
            long date=c.getHeaderFieldDate("Date",0);if(date>0)serverOffset=date-System.currentTimeMillis();
            success=true;if(!method.equals("GET"))FirebaseRest.DIAGNOSTICS.realtimeWrite();return new Version(result,c.getHeaderField("ETag"));
        }catch(FirebaseRest.Failure e){throw e;}catch(IOException e){throw new IOException("RTDB-Verbindung unterbrochen. Erneuter Versuch folgt.",e);}finally{if(!success)c.disconnect();} // Consumed successful responses retain the HTTP keep-alive pool.
    }
    void listen(String path,RealtimeStream.Events events)throws Exception{
        FirebaseRest.DIAGNOSTICS.request("RTDB · Live-Stream");HttpURLConnection c=open(path,"text/event-stream");
        try(Reader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){RealtimeStream.read(reader,events);}
        finally{if(stream==c)stream=null;c.disconnect();}
    }
    void interrupt(){HttpURLConnection c=stream;if(c!=null)c.disconnect();}
    @Override public void close(){closed=true;interrupt();}
}
