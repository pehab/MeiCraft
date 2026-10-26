package de.meiapps.meicraft;

import java.nio.ByteBuffer;
import java.util.*;
import com.google.zxing.common.BitMatrix;

public final class QrCameraFrameTests {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        for(boolean front:new boolean[]{false,true})for(int sensor:new int[]{0,90,180,270})for(int display:new int[]{0,90,180,270}){
            int rotation=QrCameraFrame.relativeRotation(sensor,display,front);
            check(rotation==(sensor+(front?-display:display)+360)%360,"relative rotation follows camera/display contract");
            int[] fit=QrCameraFrame.fit(564,492,1920,1080,rotation);
            double aspect=rotation%180==0?1920.0/1080:1080.0/1920;
            check(fit[0]<=564&&fit[1]<=492,"entire camera image fits the preview");
            check(Math.abs((double)fit[0]/fit[1]-aspect)<.01,"preview preserves circles and square QR modules");
        }
        check(QrCameraFrame.relativeRotation(90,90,false)==180,"phone left landscape uses landscape buffer");
        check(QrCameraFrame.relativeRotation(90,270,false)==0,"phone right landscape uses landscape buffer");
        check(QrCameraFrame.relativeRotation(0,0,false)==0,"landscape-native tablet supported");
        List<String> frames=WorldTransfer.frames(WorldTransfer.pack("Kameratest",new World(7654321,true)));
        for(int turn=0;turn<4;turn++)for(int stride:new int[]{1,2})for(String text:frames){
            BitMatrix matrix=QrCodec.encode(text);int n=matrix.getWidth(),code=n*4,width=code+160,height=code+80,row=width*stride+31,base=13;
            ByteBuffer buffer=ByteBuffer.allocateDirect(base+(height-1)*row+(width-1)*stride+1);buffer.position(base);
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                int mx=(x-80)/4,my=(y-40)/4;boolean inside=x>=80&&x<80+code&&y>=40&&y<40+code;
                if(inside)for(int i=0;i<turn;i++){int old=mx;mx=my;my=n-1-old;}
                buffer.put(base+y*row+x*stride,(byte)(inside&&matrix.get(mx,my)?20:235));
            }
            byte[] pixels=QrCameraFrame.luma(buffer,width,height,row,stride);
            check(buffer.position()==base,"extracting a camera plane preserves buffer position");
            check(text.equals(QrCodec.decode(pixels,width,height)),"padded direct YUV camera plane decodes in every orientation");
        }
        boolean rejected=false;try{QrCameraFrame.luma(ByteBuffer.allocate(1),1920,1080,1920,1);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"truncated camera plane rejected before reading");
        System.out.println("PASS: "+checks+" camera geometry and padded YUV assertions");
    }
}
