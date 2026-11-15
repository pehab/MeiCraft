package de.meiapps.meicraft;

import java.io.*;

/** Bounded EventSource framing, independent of Android and JSON. */
final class RealtimeStream {
    interface Events {void event(String type,String data)throws IOException;}
    static void read(Reader input,Events events)throws IOException{
        StringBuilder line=new StringBuilder(),data=new StringBuilder();String type="message";boolean cr=false;int value;
        while((value=input.read())!=-1){
            char c=(char)value;if(c=='\n'&&cr){cr=false;continue;}cr=c=='\r';
            if(c!='\r'&&c!='\n'){if(line.length()>=262144)throw new IOException("RTDB-Zeile zu groß.");line.append(c);continue;}
            String text=line.toString();line.setLength(0);
            if(text.isEmpty()){if(data.length()>0){events.event(type,data.substring(0,data.length()-1));data.setLength(0);}type="message";continue;}
            if(text.charAt(0)==':')continue;int colon=text.indexOf(':');String field=colon<0?text:text.substring(0,colon),body=colon<0?"":text.substring(colon+1);if(body.startsWith(" "))body=body.substring(1);
            if(field.equals("event"))type=body;
            if(field.equals("data")){if(data.length()+body.length()>262144)throw new IOException("RTDB-Ereignis zu groß.");data.append(body).append('\n');}
        }
        // An incomplete final event must not be applied after a dropped connection.
        throw new EOFException("RTDB-Verbindung unterbrochen.");
    }
}
