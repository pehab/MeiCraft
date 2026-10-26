package de.meiapps.meicraft;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.hardware.camera2.params.*;
import android.media.*;
import android.os.*;
import android.util.Size;
import android.view.*;
import java.util.*;

/** Foreground-only Camera2 reader; preview rotation belongs to Android's SurfaceView. */
final class QrCamera implements AutoCloseable,SurfaceHolder.Callback {
    interface Listener {void frame(String text);void problem(String message);}
    final Activity activity;final QrPreview preview;final Listener listener;
    private final HandlerThread thread=new HandlerThread("meiCraft QR camera");
    private final Handler cameraHandler;
    private CameraDevice device;private CameraCaptureSession session;private ImageReader reader;
    private CaptureRequest.Builder request;
    private int focusMode=CaptureRequest.CONTROL_AF_MODE_OFF,generation;
    private boolean disableRotateCrop,opening;
    private volatile boolean closed;private long lastFrame;
    volatile int imageCount;
    QrCamera(Activity activity,QrPreview preview,Listener listener){
        this.activity=activity;this.preview=preview;this.listener=listener;thread.start();cameraHandler=new Handler(thread.getLooper());
        preview.getHolder().addCallback(this);if(preview.getHolder().getSurface().isValid())cameraHandler.post(this::start);
    }
    @Override public void surfaceCreated(SurfaceHolder holder){cameraHandler.post(this::start);}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){cameraHandler.post(this::release);}
    private void error(String message){if(!closed)activity.runOnUiThread(()->{if(!closed)listener.problem(message);});}
    private static boolean contains(int[] modes,int value){if(modes!=null)for(int mode:modes)if(mode==value)return true;return false;}
    private void start(){
        if(closed||opening||!preview.getHolder().getSurface().isValid())return;
        if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){error("Kamerazugriff fehlt. Bitte Kamera erlauben.");return;}
        opening=true;final int ticket=++generation;
        try{
            CameraManager manager=(CameraManager)activity.getSystemService(Activity.CAMERA_SERVICE);
            String selected=null;CameraCharacteristics characteristics=null;
            for(String id:manager.getCameraIdList()){
                CameraCharacteristics candidate=manager.getCameraCharacteristics(id);
                if(selected==null){selected=id;characteristics=candidate;}
                Integer lens=candidate.get(CameraCharacteristics.LENS_FACING);
                if(lens!=null&&lens==CameraCharacteristics.LENS_FACING_BACK){selected=id;characteristics=candidate;break;}
            }
            if(selected==null||characteristics==null){error("Dieses Gerät hat keine nutzbare Kamera.");return;}
            int[] focusModes=characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            focusMode=CaptureRequest.CONTROL_AF_MODE_OFF;
            for(int mode:new int[]{CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,CaptureRequest.CONTROL_AF_MODE_AUTO})if(contains(focusModes,mode)){focusMode=mode;break;}
            disableRotateCrop=Build.VERSION.SDK_INT>=31&&contains(characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES),CaptureRequest.SCALER_ROTATE_AND_CROP_NONE);
            StreamConfigurationMap config=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes=config==null?null:config.getOutputSizes(ImageFormat.YUV_420_888);
            Size[] previewSizes=config==null?null:config.getOutputSizes(SurfaceHolder.class);
            Size size=null;
            if(sizes!=null&&previewSizes!=null)for(Size s:sizes)if(Arrays.asList(previewSizes).contains(s)&&Math.max(s.getWidth(),s.getHeight())<=1920&&Math.min(s.getWidth(),s.getHeight())<=1080&&(size==null||(long)s.getWidth()*s.getHeight()>(long)size.getWidth()*size.getHeight()))size=s;
            if(size==null){error("Keine passende Kameraauflösung gefunden.");return;}
            Integer sensor=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION),lens=characteristics.get(CameraCharacteristics.LENS_FACING);
            Size chosen=size;String cameraId=selected;
            reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.YUV_420_888,2);
            reader.setOnImageAvailableListener(this::image,cameraHandler);
            CameraDevice.StateCallback stateCallback=new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice camera){if(closed||ticket!=generation){camera.close();return;}device=camera;configure(ticket);}
                @Override public void onDisconnected(CameraDevice camera){camera.close();error("Kamera getrennt. Bitte Empfang erneut öffnen.");}
                @Override public void onError(CameraDevice camera,int code){camera.close();error("Kamera nicht verfügbar. Bitte andere Kamera-Apps schließen und erneut versuchen.");}
            };
            activity.runOnUiThread(()->{
                if(closed)return;preview.configure(chosen.getWidth(),chosen.getHeight(),sensor==null?0:sensor,lens!=null&&lens==CameraCharacteristics.LENS_FACING_FRONT);
                preview.getHolder().setFixedSize(chosen.getWidth(),chosen.getHeight());
                cameraHandler.post(()->{if(closed||ticket!=generation)return;try{manager.openCamera(cameraId,stateCallback,cameraHandler);}catch(CameraAccessException|RuntimeException e){error("Kamera konnte nicht geöffnet werden. Bitte Empfang erneut öffnen.");}});
            });
        }catch(CameraAccessException|RuntimeException e){error("Kamera konnte nicht geöffnet werden. Bitte Empfang erneut öffnen.");}
    }
    @SuppressWarnings("deprecation") // API 26/27 use the session overload predating SessionConfiguration.
    private void configure(int ticket){
        if(closed||device==null||reader==null)return;
        try{
            List<Surface> surfaces=Arrays.asList(preview.getHolder().getSurface(),reader.getSurface());
            CameraCaptureSession.StateCallback callback=new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession configured){
                    if(closed||ticket!=generation){configured.close();return;}session=configured;
                    try{
                        request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        for(Surface surface:surfaces)request.addTarget(surface);
                        request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                        request.set(CaptureRequest.CONTROL_AF_MODE,focusMode);
                        if(Build.VERSION.SDK_INT>=31&&disableRotateCrop)request.set(CaptureRequest.SCALER_ROTATE_AND_CROP,CaptureRequest.SCALER_ROTATE_AND_CROP_NONE);
                        session.setRepeatingRequest(request.build(),null,cameraHandler);
                        if(focusMode==CaptureRequest.CONTROL_AF_MODE_AUTO)focus();
                    }catch(CameraAccessException|RuntimeException e){error("Kameravorschau konnte nicht gestartet werden.");}
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed){error("Kameravorschau nicht verfügbar.");}
            };
            if(Build.VERSION.SDK_INT>=28){List<OutputConfiguration> outputs=new ArrayList<>();for(Surface surface:surfaces)outputs.add(new OutputConfiguration(surface));device.createCaptureSession(new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,outputs,command->cameraHandler.post(command),callback));}
            else device.createCaptureSession(surfaces,callback,cameraHandler);
        }catch(CameraAccessException|RuntimeException e){error("Kamera konnte nicht gestartet werden.");}
    }
    /** Keep continuous autofocus; tapping requests a fresh focus cycle on autofocus-capable lenses. */
    void refocus(){if(!closed)cameraHandler.post(this::focus);}
    private void focus(){
        if(closed||request==null||session==null||focusMode==CaptureRequest.CONTROL_AF_MODE_OFF)return;
        try{
            request.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);session.capture(request.build(),null,cameraHandler);
            request.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_START);session.capture(request.build(),null,cameraHandler);
            request.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            if(focusMode!=CaptureRequest.CONTROL_AF_MODE_AUTO){int ticket=generation;cameraHandler.postDelayed(()->{if(!closed&&ticket==generation&&request!=null&&session!=null)try{request.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);session.capture(request.build(),null,cameraHandler);request.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE);}catch(CameraAccessException|RuntimeException ignored){/* Session closed. */}},1500);}
        }catch(CameraAccessException|RuntimeException e){error("Bitte den Abstand zum Code etwas verändern und erneut antippen.");}
    }
    private void image(ImageReader source){
        byte[] luma;int width,height;
        try(Image image=source.acquireLatestImage()){
            if(image==null||closed)return;imageCount++;long now=SystemClock.elapsedRealtime();if(now-lastFrame<160)return;lastFrame=now;
            Image.Plane plane=image.getPlanes()[0];width=image.getWidth();height=image.getHeight();
            luma=QrCameraFrame.luma(plane.getBuffer(),width,height,plane.getRowStride(),plane.getPixelStride());
        }catch(IllegalStateException|IllegalArgumentException|IndexOutOfBoundsException ignored){return;}
        // Release the camera-owned image before the expensive decode, so preview buffers can continue.
        String text=QrCodec.decode(luma,width,height);if(text!=null&&!closed)listener.frame(text);
    }
    private void release(){generation++;opening=false;request=null;if(session!=null){session.close();session=null;}if(device!=null){device.close();device=null;}if(reader!=null){reader.close();reader=null;}}
    @Override public void close(){if(closed)return;closed=true;preview.getHolder().removeCallback(this);cameraHandler.post(()->{release();thread.quitSafely();});}
    boolean closed(){return closed;}
}
