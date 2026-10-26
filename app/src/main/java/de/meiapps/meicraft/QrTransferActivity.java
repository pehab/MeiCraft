package de.meiapps.meicraft;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.common.BitMatrix;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Self-contained, screen-to-camera world exchange. No radios or server required. */
public final class QrTransferActivity extends Activity {
    static final int REQUEST=601,CAMERA_REQUEST=602;
    static final String OUTGOING="qr-outgoing.bin";
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    WorldTransfer.Collector collector=new WorldTransfer.Collector();
    WorldTransfer incoming;
    QrCamera camera;
    TextureView preview;
    ImageView image;
    TextView status,detail;
    ProgressBar progress;
    Button pause,speed,permission,importButton;
    EditText name;
    List<String> frames=Collections.emptyList();
    boolean sending,permissionAsked,importing,hold;
    volatile boolean resumed,finishedScan;
    int frameIndex,renderTicket;long delay=700;
    private LinearLayout controls;
    private final Runnable next=new Runnable(){public void run(){if(resumed&&sending&&!hold&&!frames.isEmpty())showFrame((frameIndex+1)%frames.size());}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);sending=getIntent().getBooleanExtra("send",false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(sending){WindowManager.LayoutParams params=getWindow().getAttributes();params.screenBrightness=.9f;getWindow().setAttributes(params);}
        LinearLayout root=new LinearLayout(this);root.setPadding(dp(12),dp(12),dp(12),dp(12));root.setBackgroundColor(0xff153d42);root.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout visual=new FrameLayout(this);root.addView(visual,new LinearLayout.LayoutParams(0,-1,1));
        if(sending){image=new ImageView(this);image.setBackgroundColor(Color.WHITE);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("Welt als QR-Code");visual.addView(image,new FrameLayout.LayoutParams(-1,-1));}
        else{preview=new TextureView(this);preview.setContentDescription("Kameravorschau zum Empfangen einer Welt");visual.addView(preview,new FrameLayout.LayoutParams(-1,-1));}
        ScrollView scroll=new ScrollView(this);controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(dp(14),0,0,0);scroll.addView(controls);root.addView(scroll,new LinearLayout.LayoutParams(dp(240),-1));
        label(sending?"Welt per QR teilen":"Welt per QR empfangen",21);
        detail=label(sending?"Auf dem anderen Gerät im Menü ‚Welt per QR empfangen‘ öffnen. Kamera auf den gesamten Code richten.":"Kamera auf die wechselnden Codes des anderen Geräts richten. Fehlende Bilder kommen in der nächsten Runde wieder.",14);
        status=label(sending?"Codes werden vorbereitet …":"Noch keine Bilder empfangen",16);status.setContentDescription("QR-Übertragungsstatus");
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);controls.addView(progress,new LinearLayout.LayoutParams(-1,dp(16)));
        if(sending){
            pause=button("Pause",()->{hold=!hold;pause.setText(hold?"Weiter":"Pause");handler.removeCallbacks(next);if(!hold)handler.postDelayed(next,delay);});
            speed=button("Langsamer",()->{delay=delay==700?1200:700;speed.setText(delay==700?"Langsamer":"Normales Tempo");});
            LinearLayout manual=new LinearLayout(this);controls.addView(manual);
            Button previous=new Button(this);previous.setText("←");previous.setContentDescription("Vorheriges QR-Bild");manual.addView(previous,new LinearLayout.LayoutParams(0,-2,1));previous.setOnClickListener(v->manual(-1));
            Button advance=new Button(this);advance.setText("→");advance.setContentDescription("Nächstes QR-Bild");manual.addView(advance,new LinearLayout.LayoutParams(0,-2,1));advance.setOnClickListener(v->manual(1));
        }else{
            permission=button("Kamera erlauben",()->{permissionAsked=false;startCamera();});
            name=new EditText(this);name.setSingleLine(true);name.setTextColor(Color.WHITE);name.setHintTextColor(0xffcccccc);name.setHint("Name der Weltkopie");name.setContentDescription("Name der Weltkopie");name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(48)});name.setVisibility(View.GONE);controls.addView(name,new LinearLayout.LayoutParams(-1,-2));
            importButton=button("Als neue Welt importieren",this::importWorld);importButton.setVisibility(View.GONE);
            button("Empfang zurücksetzen",this::reset);
        }
        button("Zurück",this::finish);setContentView(root);
        if(sending)prepare();
    }
    int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    TextView label(String text,int size){TextView view=new TextView(this);view.setText(text);view.setTextColor(Color.WHITE);view.setTextSize(size);view.setPadding(0,0,0,dp(10));controls.addView(view,new LinearLayout.LayoutParams(-1,-2));return view;}
    Button button(String text,Runnable action){Button view=new Button(this);view.setText(text);controls.addView(view,new LinearLayout.LayoutParams(-1,-2));view.setOnClickListener(v->action.run());return view;}
    private void prepare(){
        worker.execute(()->{
            try{
                File file=new File(getCacheDir(),OUTGOING);byte[] packet;
                try(InputStream in=new FileInputStream(file);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(bytes.size()+count>WorldTransfer.MAX_PACKET)throw new IOException("Welt zu groß");bytes.write(buffer,0,count);}packet=bytes.toByteArray();
                }
                // Cache lives only for this exchange; it is not a public share endpoint.
                file.delete();WorldTransfer transfer=WorldTransfer.unpack(packet);List<String> encoded=WorldTransfer.frames(packet);
                handler.post(()->{if(isFinishing()||isDestroyed())return;frames=encoded;detail.setText(transfer.name+" · "+(transfer.world.story?"Story":"Kreativ")+"\n\nAm anderen Gerät alle "+frames.size()+" Bilder sammeln. Die Folge wiederholt sich automatisch. Bei Problemen ‚Langsamer‘ wählen.");progress.setMax(frames.size());showFrame(0);});
            }catch(IOException|RuntimeException e){handler.post(()->status.setText("Welt konnte nicht vorbereitet werden. Bitte zurückgehen und erneut teilen."));}
        });
    }
    private void manual(int direction){if(frames.isEmpty())return;hold=true;pause.setText("Weiter");showFrame((frameIndex+direction+frames.size())%frames.size());}
    void showFrame(int index){
        handler.removeCallbacks(next);if(frames.isEmpty()||worker.isShutdown())return;
        frameIndex=index;int ticket=++renderTicket;String text=frames.get(index);
        worker.execute(()->{
            try{BitMatrix matrix=QrCodec.encode(text);int w=matrix.getWidth(),h=matrix.getHeight();int[] pixels=new int[w*h];
                for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
                Bitmap bitmap=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
                handler.post(()->{if(isFinishing()||isDestroyed()||ticket!=renderTicket)return;
                    BitmapDrawable drawable=new BitmapDrawable(getResources(),bitmap);drawable.setFilterBitmap(false);drawable.setAntiAlias(false);image.setImageDrawable(drawable);
                    status.setText("Bild "+(index+1)+" / "+frames.size()+" · wiederholt sich");progress.setProgress(index+1);
                    if(resumed&&!hold)handler.postDelayed(next,delay);
                });
            }catch(IOException e){handler.post(()->status.setText("QR-Code konnte nicht erzeugt werden."));}
        });
    }
    private void startCamera(){
        if(sending||!resumed||finishedScan||camera!=null)return;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            permission.setVisibility(View.VISIBLE);status.setText("Zum Scannen wird die Kamera benötigt. Bilder werden nur auf diesem Gerät verarbeitet.");
            if(!permissionAsked){permissionAsked=true;requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQUEST);}return;
        }
        permission.setVisibility(View.GONE);camera=new QrCamera(this,preview,new QrCamera.Listener(){
            public void frame(String text){acceptFrame(text);}
            public void problem(String message){status.setText(message);}
        });
    }
    private void stopCamera(){if(camera!=null){camera.close();camera=null;}}
    /** Called by the camera only; also exercises the exact receiving path in device tests. */
    void acceptFrame(String text){
        if(worker.isShutdown()||!resumed)return;try{worker.execute(()->{
            if(finishedScan)return;
            try{
                if(!collector.add(text))return;int received=collector.received(),total=collector.total();
                if(collector.complete()){
                    WorldTransfer transfer=WorldTransfer.unpack(collector.packet());finishedScan=true;incoming=transfer;
                    handler.post(()->{if(isFinishing()||isDestroyed())return;stopCamera();progress.setMax(total);progress.setProgress(total);status.setText("Alle "+total+" Bilder geprüft · Welt vollständig");
                        detail.setText(transfer.name+" · "+(transfer.world.story?"Story":"Kreativ")+"\n\nEs entsteht eine eigene Weltkopie. Eure vorhandenen Welten werden nicht überschrieben.");
                        name.setText(copyName(transfer.name));name.setVisibility(View.VISIBLE);importButton.setVisibility(View.VISIBLE);
                    });
                }else handler.post(()->{if(!finishedScan){progress.setMax(total);progress.setProgress(received);status.setText("Empfangen: "+received+" / "+total+" Bilder");}});
            }catch(IOException e){handler.post(()->{if(!finishedScan)status.setText(e.getMessage());});}
        });}catch(RejectedExecutionException ignored){/* Activity closed while a camera callback was finishing. */}
    }
    private static String copyName(String original){return original.substring(0,Math.min(40,original.length()))+" (Kopie)";}
    private void reset(){
        if(importing)return;stopCamera();
        worker.execute(()->{collector=new WorldTransfer.Collector();incoming=null;finishedScan=false;handler.post(()->{
            name.setVisibility(View.GONE);importButton.setVisibility(View.GONE);progress.setProgress(0);status.setText("Noch keine Bilder empfangen");startCamera();
        });});
    }
    private void importWorld(){
        if(incoming==null||importing)return;
        final String chosen=name.getText().toString();
        try{SavedWorld.create(chosen,incoming.world.story?1:0);}catch(IllegalArgumentException e){name.setError(e.getMessage());return;}
        importing=true;importButton.setEnabled(false);WorldTransfer transfer=incoming;
        worker.execute(()->{
            try{WorldLibrary library=new WorldLibrary(getFilesDir());SavedWorld imported=library.importCopy(chosen,transfer.worldBytes);
                handler.post(()->{setResult(RESULT_OK,new Intent().putExtra("worldId",imported.id));finish();});
            }catch(IOException e){handler.post(()->{importing=false;importButton.setEnabled(true);status.setText("Import fehlgeschlagen. Deine vorhandenen Welten bleiben erhalten.");});}
        });
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==CAMERA_REQUEST)startCamera();}
    @Override protected void onResume(){super.onResume();resumed=true;if(sending){if(!frames.isEmpty())showFrame(frameIndex);}else startCamera();}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(next);stopCamera();super.onPause();}
    @Override protected void onDestroy(){stopCamera();handler.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
}
