package de.meiapps.meicraft;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Local, bounded diagnostics: no request URLs, payloads, user IDs or credentials. */
public final class OnlineDiagnostics {
    private final long started=System.currentTimeMillis();
    private long requests,failures,documents,writes,deletes,receivedBytes;
    private final Map<String,Long> operations=new LinkedHashMap<>();
    private final ArrayDeque<String> errors=new ArrayDeque<>();
    public static String clean(String text,String... secrets){
        String value=text==null?"":text;
        for(String secret:secrets)if(secret!=null&&!secret.isEmpty())value=value.replace(secret,"[entfernt]");
        value=value.replaceAll("(?i)(bearer\\s+)[^\\s\\\"']+","$1[entfernt]")
            .replaceAll("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+","[Token entfernt]")
            .replaceAll("(?i)(key|token|refresh_token|id_token|access_token)=([^&\\s\\\"']+)","$1=[entfernt]")
            .replaceAll("[a-fA-F0-9]{32}","[Kennung entfernt]")
            .replaceAll("[\\r\\n\\t]+"," ").trim();
        return value.length()>1200?value.substring(0,1200)+" …":value;
    }
    public synchronized void request(String operation){requests++;operations.put(operation,operations.getOrDefault(operation,0L)+1);}
    public synchronized void received(long bytes){receivedBytes+=Math.max(0,bytes);}
    public synchronized void read(long count){documents+=Math.max(0,count);}
    public synchronized void committed(long writeCount,long deleteCount){writes+=writeCount;deletes+=deleteCount;}
    public synchronized String failed(String detail){
        failures++;String entry=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.ROOT).format(new java.util.Date())+" · "+clean(detail);
        if(errors.size()==12)errors.removeFirst();errors.addLast(entry);return entry;
    }
    public synchronized String report(){
        StringBuilder out=new StringBuilder("meiCraft Online-Diagnose\n");
        out.append("Laufzeit seit App-Start: ").append((System.currentTimeMillis()-started)/60000).append(" min\n")
           .append("HTTP-Anfragen: ").append(requests).append(" · Fehler: ").append(failures)
           .append("\nEmpfangene Dokumente: ").append(documents).append("\nBestätigte Schreibvorgänge: ").append(writes)
           .append(" · Löschvorgänge: ").append(deletes).append("\nEmpfangene Daten: ").append(receivedBytes/1024).append(" KiB\n")
           .append("Dies sind lokale Zähler, keine Firebase-Abrechnung. Regel- und Indexlesevorgänge sowie Mindestkosten leerer Abfragen sind nicht enthalten.\n\nAnfragen nach Vorgang:\n");
        for(Map.Entry<String,Long> entry:operations.entrySet())out.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        out.append("\nLetzte Fehler:\n");if(errors.isEmpty())out.append("Keine Fehler in dieser App-Sitzung.\n");else for(String error:errors)out.append(error).append('\n');
        return out.toString();
    }
}
