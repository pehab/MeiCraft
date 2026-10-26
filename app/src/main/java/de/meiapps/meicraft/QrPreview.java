package de.meiapps.meicraft;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;

/** SurfaceView applies the camera producer's sensor/display rotation itself. Only fit its aspect ratio. */
final class QrPreview extends SurfaceView implements DisplayManager.DisplayListener {
    private final DisplayManager displays;
    int bufferWidth,bufferHeight,sensorDegrees;boolean front;
    QrPreview(Context context){super(context);displays=(DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);}
    void configure(int width,int height,int sensor,boolean frontFacing){
        bufferWidth=width;bufferHeight=height;sensorDegrees=sensor;front=frontFacing;requestLayout();
    }
    int relativeRotation(){return QrCameraFrame.relativeRotation(sensorDegrees,getDisplay()==null?0:getDisplay().getRotation()*90,front);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);
        if(bufferWidth>0&&width>0&&height>0){int[] fitted=QrCameraFrame.fit(width,height,bufferWidth,bufferHeight,relativeRotation());setMeasuredDimension(fitted[0],fitted[1]);}
        else super.onMeasure(widthSpec,heightSpec);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();displays.registerDisplayListener(this,new Handler(Looper.getMainLooper()));}
    @Override protected void onDetachedFromWindow(){displays.unregisterDisplayListener(this);super.onDetachedFromWindow();}
    @Override public void onDisplayAdded(int id){}
    @Override public void onDisplayRemoved(int id){}
    @Override public void onDisplayChanged(int id){if(getDisplay()!=null&&getDisplay().getDisplayId()==id)requestLayout();}
}
