package de.meiapps.meicraft;

import java.nio.ByteBuffer;

/** Camera geometry and Y-plane extraction, independent of Android and QR decoding. */
final class QrCameraFrame {
    private QrCameraFrame(){}
    static int relativeRotation(int sensor,int display,boolean front){
        return Math.floorMod(sensor-(front?display:-display),360);
    }
    static int[] fit(int viewWidth,int viewHeight,int bufferWidth,int bufferHeight,int rotation){
        if(rotation%180!=0){int swap=bufferWidth;bufferWidth=bufferHeight;bufferHeight=swap;}
        if(viewWidth<1||viewHeight<1||bufferWidth<1||bufferHeight<1)throw new IllegalArgumentException("Invalid preview size");
        double scale=Math.min((double)viewWidth/bufferWidth,(double)viewHeight/bufferHeight);
        return new int[]{Math.max(1,(int)Math.round(bufferWidth*scale)),Math.max(1,(int)Math.round(bufferHeight*scale))};
    }
    static byte[] luma(ByteBuffer source,int width,int height,int rowStride,int pixelStride){
        if(width<1||height<1||width>2048||height>2048||pixelStride<1||rowStride<(long)(width-1)*pixelStride+1)throw new IllegalArgumentException("Invalid camera plane");
        int base=source.position();long last=(long)base+(long)(height-1)*rowStride+(long)(width-1)*pixelStride;
        if(last>=source.limit())throw new IllegalArgumentException("Incomplete camera plane");
        ByteBuffer plane=source.duplicate();byte[] result=new byte[width*height];
        for(int y=0;y<height;y++){
            if(pixelStride==1){plane.position(base+y*rowStride);plane.get(result,y*width,width);}
            else for(int x=0;x<width;x++)result[y*width+x]=plane.get(base+y*rowStride+x*pixelStride);
        }
        return result;
    }
}
