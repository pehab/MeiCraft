package de.meiapps.meicraft;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Firebase user-token REST client. No service account or administrator credentials. */
final class FirebaseRest {
    static final OnlineDiagnostics DIAGNOSTICS=new OnlineDiagnostics();
    static final class Failure extends IOException {
        private static final long serialVersionUID=1L;
        final int status;final String service,operation,serverStatus,detail;
        Failure(int status){this(status,"Firebase","Anfrage","","Keine genauere Servermeldung verfügbar.");}
        Failure(int status,String service,String operation,String serverStatus,String detail){
            super((status==403?"Zugriff verweigert":status==429?"Kontingent oder Anfragerate begrenzt":status==401?"Anmeldung abgelaufen":"Anfrage fehlgeschlagen")+" · "+service+" · HTTP "+status+(serverStatus.isEmpty()?"":" · "+serverStatus)+". Online-Diagnose öffnen.");
            this.status=status;this.service=service;this.operation=operation;this.serverStatus=serverStatus;this.detail=detail;
        }
        String diagnostic(){return service+" · "+operation+" · HTTP "+status+" · "+serverStatus+" · "+detail;}
    }
    interface ConnectionFactory {HttpURLConnection open(URL url)throws IOException;}
    final ConnectionFactory connections;
    final android.content.Context context;
    final String project,key,root;
    final SharedPreferences credentials;
    String uid,token="";long expires;
    FirebaseRest(Context context)throws IOException,JSONException{this(context,url->(HttpURLConnection)url.openConnection());}
    FirebaseRest(Context context,ConnectionFactory connections)throws IOException,JSONException{
        this.connections=connections;this.context=context.getApplicationContext();
        JSONObject config;
        try(InputStream input=context.getAssets().open("google-services.json")){config=new JSONObject(new String(read(input),StandardCharsets.UTF_8));}
        project=config.getJSONObject("project_info").getString("project_id");
        JSONObject client=null;JSONArray clients=config.getJSONArray("client");
        for(int i=0;i<clients.length();i++){JSONObject candidate=clients.getJSONObject(i);if(context.getPackageName().equals(candidate.getJSONObject("client_info").getJSONObject("android_client_info").getString("package_name")))client=candidate;}
        if(client==null)throw new IOException("Firebase-Konfiguration passt nicht zur App.");
        key=client.getJSONArray("api_key").getJSONObject(0).getString("current_key");
        root="https://firestore.googleapis.com/v1/projects/"+project+"/databases/(default)/documents";
        credentials=context.getSharedPreferences("firebase-user",Context.MODE_PRIVATE);uid=credentials.getString("uid","");
    }
    void authenticate()throws IOException,JSONException{
        if(!token.isEmpty()&&System.currentTimeMillis()<expires)return;
        String refresh=credentials.getString("refresh","");JSONObject result;
        if(refresh.isEmpty()){
            if(!uid.isEmpty())throw new IOException("Geräteanmeldung fehlt. App-Daten nicht löschen; damit gingen die bisherigen Weltberechtigungen verloren.");
            result=new JSONObject(request("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key="+key,"POST","{\"returnSecureToken\":true}",false,false));
            token=result.getString("idToken");uid=result.getString("localId");refresh=result.getString("refreshToken");
        }else{
            result=new JSONObject(request("https://securetoken.googleapis.com/v1/token?key="+key,"POST","grant_type=refresh_token&refresh_token="+URLEncoder.encode(refresh,"UTF-8"),false,true));
            String refreshedUid=result.getString("user_id");if(!uid.isEmpty()&&!uid.equals(refreshedUid))throw new IOException("Geräteidentität stimmt nicht überein.");
            token=result.getString("id_token");uid=refreshedUid;refresh=result.getString("refresh_token");
        }
        if(!credentials.edit().putString("uid",uid).putString("refresh",refresh).commit())throw new IOException("Geräteanmeldung konnte nicht gespeichert werden.");
        expires=System.currentTimeMillis()+Math.max(0,result.optLong("expiresIn",result.optLong("expires_in",3600))-120)*1000;
    }
    JSONObject get(String path)throws IOException,JSONException{
        authenticate();try{JSONObject result=new JSONObject(request(root+"/"+path,"GET",null,true,false));DIAGNOSTICS.read(1);return result;}catch(Failure e){if(e.status==404)return null;throw e;}
    }
    JSONObject commit(JSONArray writes)throws IOException,JSONException{
        authenticate();JSONObject result=new JSONObject(request(root+":commit","POST",new JSONObject().put("writes",writes).toString(),true,false));
        long removed=0;for(int i=0;i<writes.length();i++)if(writes.getJSONObject(i).has("delete"))removed++;
        DIAGNOSTICS.committed(writes.length()-removed,removed);return result;
    }
    JSONArray query(String parent,JSONObject query)throws IOException,JSONException{
        return query(parent,query,"");
    }
    JSONArray query(String parent,JSONObject query,String readTime)throws IOException,JSONException{
        authenticate();JSONObject body=new JSONObject().put("structuredQuery",query);if(!readTime.isEmpty())body.put("readTime",readTime);
        JSONArray result=new JSONArray(request(root+"/"+parent+":runQuery","POST",body.toString(),true,false));
        for(int i=0;i<result.length();i++)if(result.getJSONObject(i).has("document"))DIAGNOSTICS.read(1);return result;
    }
    JSONObject list(String path,String page)throws IOException,JSONException{
        authenticate();JSONObject result=new JSONObject(request(root+"/"+path+"?pageSize=100"+(page.isEmpty()?"":"&pageToken="+URLEncoder.encode(page,"UTF-8")),"GET",null,true,false));
        JSONArray docs=result.optJSONArray("documents");if(docs!=null)DIAGNOSTICS.read(docs.length());return result;
    }
    JSONObject write(String path,JSONObject fields,String[] mask,JSONObject condition,boolean timestamp)throws JSONException{
        JSONObject write=new JSONObject().put("update",new JSONObject().put("name",documentName(path)).put("fields",fields));
        if(mask!=null)write.put("updateMask",new JSONObject().put("fieldPaths",new JSONArray(mask)));
        if(condition!=null)write.put("currentDocument",condition);
        if(timestamp)write.put("updateTransforms",new JSONArray().put(new JSONObject().put("fieldPath","updatedAt").put("setToServerValue","REQUEST_TIME")));
        return write;
    }
    String documentName(String path){return "projects/"+project+"/databases/(default)/documents/"+path;}
    static JSONObject value(Object value)throws JSONException{
        if(value instanceof Boolean)return new JSONObject().put("booleanValue",value);
        if(value instanceof Float||value instanceof Double)return new JSONObject().put("doubleValue",value);
        if(value instanceof Number)return new JSONObject().put("integerValue",String.valueOf(value));
        return new JSONObject().put("stringValue",value);
    }
    static JSONObject fields(Object... pairs)throws JSONException{JSONObject fields=new JSONObject();for(int i=0;i<pairs.length;i+=2)fields.put((String)pairs[i],value(pairs[i+1]));return fields;}
    static String string(JSONObject fields,String name)throws JSONException{return fields.getJSONObject(name).getString("stringValue");}
    static int integer(JSONObject fields,String name)throws JSONException{return fields.getJSONObject(name).getInt("integerValue");}
    static double number(JSONObject fields,String name)throws JSONException{JSONObject v=fields.getJSONObject(name);return v.has("doubleValue")?v.getDouble("doubleValue"):v.getDouble("integerValue");}
    private String operation(String url,String method){
        if(url.contains("identitytoolkit.googleapis.com"))return "Anmeldung anlegen";
        if(url.contains("securetoken.googleapis.com"))return "Anmeldung erneuern";
        if(url.contains(":commit"))return "Änderungen speichern";
        if(url.contains("/sync/state"))return "Änderungsübersicht lesen";
        if(url.contains(":runQuery"))return "Blockänderungen lesen";
        if(url.contains("/inventory/"))return "Inventar lesen";
        if(url.contains("pageSize="))return "Dokumentliste lesen";
        return "Welt oder Mitgliedschaft lesen";
    }
    Failure failure(int status,String service,String operation,String response){
        String serverStatus="",message="",reason="";
        try{JSONObject error=new JSONObject(response).optJSONObject("error");if(error!=null){serverStatus=error.optString("status","");message=error.optString("message","");JSONArray details=error.optJSONArray("details");if(details!=null)for(int i=0;i<details.length();i++){JSONObject detail=details.optJSONObject(i);if(detail!=null){if(detail.has("reason"))reason+=" · "+detail.optString("reason");JSONObject metadata=detail.optJSONObject("metadata");if(metadata!=null)for(String key:new String[]{"quota_metric","quota_limit","quota_limit_value","service"})if(metadata.has(key))reason+=" · "+key+"="+metadata.optString(key);JSONArray violations=detail.optJSONArray("violations");if(violations!=null)for(int v=0;v<Math.min(4,violations.length());v++){JSONObject violation=violations.optJSONObject(v);if(violation!=null)reason+=" · "+violation.optString("description","");}}}}}catch(JSONException ignored){/* Never persist a raw proxy response or request body. */}
        if(message.isEmpty())message="Server lieferte keine strukturierte Fehlerbeschreibung.";
        String refresh=credentials.getString("refresh","");
        Failure failure=new Failure(status,service,operation,OnlineDiagnostics.clean(serverStatus),OnlineDiagnostics.clean(message+reason,token,key,refresh,uid));
        if(status!=404)context.getSharedPreferences("online-diagnostics",0).edit().putString("lastError",DIAGNOSTICS.failed(failure.diagnostic())).apply();return failure;
    }
    private String request(String url,String method,String body,boolean authorized,boolean form)throws IOException{
        String operation=operation(url,method),service=url.contains("firestore.googleapis.com")?"Firestore":url.contains("securetoken.googleapis.com")?"Firebase Token":"Firebase Auth";
        DIAGNOSTICS.request(operation);HttpURLConnection connection=connections.open(new URL(url));
        try{
            connection.setConnectTimeout(8000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);connection.setRequestMethod(method);
            if(authorized)connection.setRequestProperty("Authorization","Bearer "+token);
            if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type",form?"application/x-www-form-urlencoded":"application/json; charset=UTF-8");byte[] bytes=body.getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream output=connection.getOutputStream()){output.write(bytes);}}
            int status=connection.getResponseCode();
            if(status<200||status>=300){String response="";try(InputStream input=connection.getErrorStream()){if(input!=null)response=new String(read(input),StandardCharsets.UTF_8);}catch(IOException ignored){/* The status remains useful when the error body is unavailable. */}Failure error=failure(status,service,operation,response);if(status==401&&authorized){token="";expires=0;}throw error;}
            try(InputStream input=connection.getInputStream()){byte[] response=read(input);DIAGNOSTICS.received(response.length);return new String(response,StandardCharsets.UTF_8);}
        }catch(Failure e){throw e;}catch(IOException e){String detail=DIAGNOSTICS.failed(service+" · "+operation+" · Netzwerkfehler: "+e.getClass().getSimpleName());context.getSharedPreferences("online-diagnostics",0).edit().putString("lastError",detail).apply();throw e;}
        finally{connection.disconnect();}
    }
    static byte[] read(InputStream input)throws IOException{ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(output.size()+n>4*1024*1024)throw new IOException("Serverantwort zu groß.");output.write(buffer,0,n);}return output.toByteArray();}
}
