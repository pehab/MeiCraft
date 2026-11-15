package de.meiapps.meicraft;

import java.io.*;
import java.util.*;

public final class RealtimeTests {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static List<String> parse(String input)throws Exception{List<String> events=new ArrayList<>();try{RealtimeStream.read(new StringReader(input),(type,data)->events.add(type+"="+data));}catch(EOFException expected){}return events;}
    public static void main(String[] args)throws Exception{
        check(parse(": comment\r\nevent: put\r\ndata: one\r\ndata: two\r\n\r\nevent: keep-alive\ndata: null\n\n").equals(Arrays.asList("put=one\ntwo","keep-alive=null")),"CRLF and multiline SSE frames");
        check(parse("event: put\ndata: lost").isEmpty(),"Dropped partial frame cannot mutate local state");
        check(parse("data: first\n\ndata: second\n\n").equals(Arrays.asList("message=first","message=second")),"Event type resets for each frame");
        check(parse("event: patch\rdata: {}\r\r").equals(Collections.singletonList("patch={}")),"CR-only frame delimiters");
        boolean bounded=false;try{parse("data: "+"x".repeat(262145));}catch(IOException expected){bounded=true;}check(bounded,"Untrusted stream lines are bounded");
        bounded=false;try{parse("data: "+"x".repeat(200000)+"\ndata: "+"x".repeat(200000)+"\n\n");}catch(IOException expected){bounded=true;}check(bounded,"Multiline event accumulation is bounded");
        OnlinePose a=new OnlinePose(),b=new OnlinePose();long t=1000000000L;a.receive(t,0,2,0,(float)Math.toRadians(179));b.receive(t,0,2,0,(float)Math.toRadians(179));a.receive(t+100000000L,1,3,0,(float)Math.toRadians(-179));b.receive(t+100000000L,1,3,0,(float)Math.toRadians(-179));
        for(int i=0;i<100;i++)a.at(t+i*1000000L);float[] pa=a.at(t+150000000L),pb=b.at(t+150000000L);
        check(Math.abs(pa[0]-.5)<1e-6&&Math.abs(pa[1]-2.5)<1e-6,"Sample-based interpolation uses a 100 ms buffer");check(Arrays.equals(pa,pb),"Render frequency does not change movement");
        check(Math.abs(Math.abs(pa[3])-Math.PI)<.0001,"Yaw interpolates across the shortest wrap arc");
        check(a.at(t+900000000L)[0]==1,"No unbounded prediction beyond last sample");a.receive(t+50000000L,7,7,7,0);check(a.at(t+900000000L)[0]==1,"Out-of-order sample is ignored");a.receive(t+1000000000L,40,3,0,0);check(a.at(t+1000000000L)[0]==40,"Teleports snap instead of crossing unloaded terrain");
        OnlinePose slow=new OnlinePose();slow.receive(t,0,0,0,0);slow.receive(t+2000000000L,1,0,0,0);check(slow.at(t+2000000000L)[0]==0&&Math.abs(slow.at(t+2050000000L)[0]-.5)<1e-6,"Slow fallback samples retain a short smooth transition");
        check(OnlinePose.send(t,0,0,0,0,0,0,0,0,0),"First pose always publishes");
        check(!OnlinePose.send(t+99000000L,t,1,0,0,0,0,0,0,0),"Movement is capped at ten updates per second");
        check(OnlinePose.send(t+100000000L,t,1,0,0,0,0,0,0,0),"Ten Hz movement can publish");
        check(!OnlinePose.send(t+100000000L,t,.001f,0,0,0,0,0,0,0),"Sub-threshold jitter is suppressed");
        check(!OnlinePose.send(t+100000000L,t,0,0,0,(float)(Math.PI*2),0,0,0,0),"Yaw wrapping alone does not trigger a write");
        check(OnlinePose.send(t+2000000000L,t,0,0,0,0,0,0,0,0),"Idle presence refreshes before the eight second TTL");
        check(!OnlineDiagnostics.clean("https://example.test/x?auth=private&key=secret").contains("private"),"RTDB auth query is redacted");
        int moving=0,idle=0;long movingLast=0,idleLast=0;float movingX=0;
        for(int i=0;i<72000;i++){long now=t+i*100000000L;float next=i*.03f;if(OnlinePose.send(now,movingLast,next,0,0,0,movingX,0,0,0)){moving++;movingLast=now;movingX=next;}if(OnlinePose.send(now,idleLast,0,0,0,0,0,0,0,0)){idle++;idleLast=now;}}
        check(moving==72000&&idle==3600,"Two-hour send simulation caps movement at 10 Hz and suppresses 95 percent of idle writes");
        System.out.println("PASS: "+checks+" realtime framing, interpolation, privacy and send-budget assertions");
    }
}
