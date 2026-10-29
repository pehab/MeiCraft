package de.meiapps.meicraft;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.*;

/** A single invite QR; offline world QR sequences retain their own receiving flow. */
public final class OnlineInviteActivity extends Activity {
    static final int REQUEST=814;
    QrPreview preview;QrCamera camera;TextView status;boolean resumed,finished;
    @Override public void onCreate(Bundle state){super.onCreate(state);LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);status=new TextView(this);status.setText(R.string.online_scan_title);status.setTextSize(20);layout.addView(status);preview=new QrPreview(this);layout.addView(preview,new LinearLayout.LayoutParams(-1,0,1));preview.setOnClickListener(v->{if(camera!=null)camera.refocus();});Button back=new Button(this);back.setText(android.R.string.cancel);back.setOnClickListener(v->finish());layout.addView(back);setContentView(layout);}
    void start(){if(!resumed||camera!=null||finished)return;if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){status.setText(R.string.online_scan_permission);return;}camera=new QrCamera(this,preview,new QrCamera.Listener(){public void frame(String text){runOnUiThread(()->{if(finished)return;try{OnlineWorld.decode(text);finished=true;setResult(RESULT_OK,new Intent().putExtra("invitation",text));finish();}catch(IllegalArgumentException e){status.setText(e.getMessage());}});}public void problem(String message){runOnUiThread(()->status.setText(message));}});}
    @Override protected void onResume(){super.onResume();resumed=true;if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},1);else start();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);start();}
    @Override protected void onPause(){resumed=false;if(camera!=null){camera.close();camera=null;}super.onPause();}
}
