package de.meiapps.meicraft;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Firebase user-token REST client. No service account or administrator credentials. */
final class FirebaseRest {
    static final class Failure extends IOException {
        private static final long serialVersionUID=1L;
        final int status;
        Failure(int status){super(status==403?"Kein Zugriff. Bitte Firestore-Regeln und Weltmitgliedschaft prüfen.":status==429?"Firebase-Limit erreicht. Bitte später erneut versuchen.":status==401?"Anmeldung abgelaufen. Bitte erneut verbinden.":"Firebase-Anfrage fehlgeschlagen ("+status+").");this.status=status;}
    }
    final String project,key,root;
    final SharedPreferences credentials;
    String uid,token="";long expires;
    FirebaseRest(Context context)throws IOException,JSONException{
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
        authenticate();try{return new JSONObject(request(root+"/"+path,"GET",null,true,false));}catch(Failure e){if(e.status==404)return null;throw e;}
    }
    JSONObject commit(JSONArray writes)throws IOException,JSONException{
        authenticate();return new JSONObject(request(root+":commit","POST",new JSONObject().put("writes",writes).toString(),true,false));
    }
    JSONArray query(String parent,JSONObject query)throws IOException,JSONException{
        authenticate();return new JSONArray(request(root+"/"+parent+":runQuery","POST",new JSONObject().put("structuredQuery",query).toString(),true,false));
    }
    JSONObject list(String path,String page)throws IOException,JSONException{
        authenticate();return new JSONObject(request(root+"/"+path+"?pageSize=100"+(page.isEmpty()?"":"&pageToken="+URLEncoder.encode(page,"UTF-8")),"GET",null,true,false));
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
    private String request(String url,String method,String body,boolean authorized,boolean form)throws IOException{
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        try{
            connection.setConnectTimeout(8000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);connection.setRequestMethod(method);
            if(authorized)connection.setRequestProperty("Authorization","Bearer "+token);
            if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type",form?"application/x-www-form-urlencoded":"application/json; charset=UTF-8");byte[] bytes=body.getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream output=connection.getOutputStream()){output.write(bytes);}}
            int status=connection.getResponseCode();if(status<200||status>=300)throw new Failure(status);
            try(InputStream input=connection.getInputStream()){return new String(read(input),StandardCharsets.UTF_8);}
        }finally{connection.disconnect();}
    }
    static byte[] read(InputStream input)throws IOException{ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(output.size()+n>4*1024*1024)throw new IOException("Serverantwort zu groß.");output.write(buffer,0,n);}return output.toByteArray();}
}
