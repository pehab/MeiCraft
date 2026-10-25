package de.meiapps.meicraft;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** A grant-only, read-only content URI for exactly one private update file. */
public final class UpdateProvider extends ContentProvider {
 @Override public boolean onCreate(){return true;}
 private File file(Uri uri)throws FileNotFoundException{
  if(!"content".equals(uri.getScheme())||!(getContext().getPackageName()+".updates").equals(uri.getAuthority())||!"/update.apk".equals(uri.getPath())||uri.getQuery()!=null||uri.getFragment()!=null)throw new FileNotFoundException();
  File file=new File(getContext().getCacheDir(),"update.apk");if(!file.isFile())throw new FileNotFoundException();return file;
 }
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
 @Override public String getType(Uri uri){try{file(uri);}catch(FileNotFoundException e){return null;}return "application/vnd.android.package-archive";}
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
  File f;try{f=file(uri);}catch(FileNotFoundException e){return null;}
  String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
  MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]="meiCraft-Update.apk";else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=f.length();}cursor.addRow(row);return cursor;
 }
 @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
 @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
 @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
}
