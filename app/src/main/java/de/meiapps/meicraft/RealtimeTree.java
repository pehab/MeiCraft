package de.meiapps.meicraft;

import org.json.*;
import java.io.IOException;

/** Exact Firebase put/patch semantics, including nested multi-location paths and null removal. */
final class RealtimeTree {
    JSONObject root=new JSONObject();
    void apply(String type,String payload)throws JSONException,IOException{
        JSONObject event=new JSONObject(payload);String path=event.getString("path");Object data=event.get("data");
        if(!path.startsWith("/")||path.length()>512)throw new IOException("Ungültiger RTDB-Pfad.");
        if(type.equals("put"))put(path,data);
        else if(type.equals("patch")){if(!(data instanceof JSONObject))throw new IOException("Ungültiger RTDB-Patch.");JSONObject patch=(JSONObject)data;java.util.Iterator<String> keys=patch.keys();while(keys.hasNext()){String key=keys.next();put((path.equals("/")?"":path)+"/"+key,patch.get(key));}}
        else throw new IOException("Ungültiges RTDB-Ereignis.");
        if(root.toString().length()>262144)throw new IOException("RTDB-Livestand zu groß.");
    }
    private void put(String path,Object value)throws JSONException,IOException{
        if(path.equals("/")){if(value==JSONObject.NULL)root=new JSONObject();else if(value instanceof JSONObject)root=(JSONObject)value;else throw new IOException("Ungültiger RTDB-Livestand.");return;}
        String[] parts=path.substring(1).split("/",-1);if(parts.length>5)throw new IOException("RTDB-Pfad zu tief.");JSONObject node=root;
        for(int i=0;i<parts.length;i++){String key=parts[i];if(key.isEmpty()||key.matches(".*[.#$\\[\\]].*"))throw new IOException("Ungültiger RTDB-Schlüssel.");if(i==parts.length-1){if(value==JSONObject.NULL)node.remove(key);else node.put(key,value);}else{JSONObject child=node.optJSONObject(key);if(child==null){child=new JSONObject();node.put(key,child);}node=child;}}
    }
}
