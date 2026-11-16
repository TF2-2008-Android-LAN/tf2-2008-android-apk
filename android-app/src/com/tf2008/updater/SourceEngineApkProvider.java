package com.tf2008.updater;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Gives Android's system installer read-only, temporary access to two
 * private, verified, embedded APKs. Never exposes arbitrary filesystem paths.
 */
public final class SourceEngineApkProvider extends ContentProvider {
 static final String AUTHORITY="com.tf2008.updater.sourceapk";
 static final String SOURCE_NAME="Source_Engine_1.17.25.apk";
 static final String GAME_NAME="TF2_2008_Game_1.00.apk";
 static boolean nameAllowed(String name){
  return SOURCE_NAME.equals(name)||GAME_NAME.equals(name);
 }
 static Uri contentUri(String name){
  if(!nameAllowed(name))throw new IllegalArgumentException("Unknown bundled APK");
  return Uri.parse("content://"+AUTHORITY+"/"+name);
 }
 static Uri contentUri(){return contentUri(SOURCE_NAME);}
 static File file(Context context,String name){
  if(!nameAllowed(name))throw new IllegalArgumentException("Unknown bundled APK");
  File cache=context.getExternalCacheDir();
  if(cache==null)cache=context.getCacheDir();
  return new File(new File(cache,"tf2008_bootstrap_verified"),name);
 }
 static File apk(Context context){return file(context,SOURCE_NAME);}
 private String name(Uri uri){
  if(uri==null||!AUTHORITY.equals(uri.getAuthority()))return null;
  String path=uri.getPath();
  if(path==null||path.length()<2)return null;
  String basename=path.substring(1);
  return nameAllowed(basename)?basename:null;
 }
 private File ready(Uri uri)throws FileNotFoundException{
  String n=name(uri);
  if(n==null)throw new FileNotFoundException("Unknown installer file");
  File f=file(getContext(),n);
  if(!f.isFile())throw new FileNotFoundException("Installer file not yet verified");
  return f;
 }
 @Override public boolean onCreate(){return true;}
 @Override public String getType(Uri uri){
  return name(uri)!=null?"application/vnd.android.package-archive":null;
 }
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
  if(!"r".equals(mode))throw new FileNotFoundException("Installer files are read-only");
  return ParcelFileDescriptor.open(ready(uri),ParcelFileDescriptor.MODE_READ_ONLY);
 }
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
  String n=name(uri);
  if(n==null)return null;
  File f=file(getContext(),n);
  String[] columns=projection!=null?projection:new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
  MatrixCursor result=new MatrixCursor(columns);
  Object[] values=new Object[columns.length];
  for(int i=0;i<columns.length;i++){
   if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]=n;
   else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=f.exists()?f.length():0L;
  }
  result.addRow(values);
  return result;
 }
 @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
 @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
 @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
