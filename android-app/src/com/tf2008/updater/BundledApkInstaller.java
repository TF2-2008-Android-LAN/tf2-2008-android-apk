package com.tf2008.updater;

import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * One initial downloaded updater APK includes Source Engine and the TF2 launcher.
 * Android handles their installation with user confirmation; no root is needed.
 */
final class BundledApkInstaller {
 private BundledApkInstaller(){}
 private static final int BUF=131072;
 static final String ENGINE="com.valvesoftware.source";
 static final String GAME="com.valvesoftware.source.tf_port";
 static final String SOURCE_FILE=SourceEngineApkProvider.SOURCE_NAME;
 static final String GAME_FILE=SourceEngineApkProvider.GAME_NAME;
 private static final String SOURCE_VERSION="1.17.25";
 private static final String GAME_VERSION="1.00";
 private static final long SOURCE_BYTES=79491233L;
 private static final long GAME_BYTES=16845348L;
 private static final String SOURCE_SHA="6b82a5c93753650dd2f22065a4040c722256bf9570f6f64c6dca3841db2899bb";
 private static final String GAME_SHA="6b78682f84e244c809bbc9f22a534861d79671a8a17dcc77b8028598748900b6";

 private static boolean source(String target){return ENGINE.equals(target);}
 private static String asset(String target){
  if(source(target))return SOURCE_FILE;
  if(GAME.equals(target))return GAME_FILE;
  throw new IllegalArgumentException("Only known source/game components are supported");
 }
 private static String sha(String target){return source(target)?SOURCE_SHA:GAME_SHA;}
 private static long bytes(String target){return source(target)?SOURCE_BYTES:GAME_BYTES;}
 private static String version(String target){return source(target)?SOURCE_VERSION:GAME_VERSION;}

 private static String hex(byte[] bytes){
  final char[] chars="0123456789abcdef".toCharArray();
  char[] out=new char[bytes.length*2];
  for(int i=0;i<bytes.length;i++){
   int b=bytes[i]&255;
   out[i*2]=chars[b>>4];
   out[i*2+1]=chars[b&15];
  }
  return new String(out);
 }
 private static String hash(File file)throws Exception{
  MessageDigest digest=MessageDigest.getInstance("SHA-256");
  try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
   byte[] buf=new byte[BUF];int n;
   while((n=in.read(buf))!=-1)digest.update(buf,0,n);
  }
  return hex(digest.digest());
 }
 private static void verify(MainActivity activity,File file,String target)throws Exception{
  if(!file.isFile()||file.length()!=bytes(target)||!sha(target).equals(hash(file)))
   throw new SecurityException("Bundled APK checksum failed: "+target);
  PackageInfo info=activity.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),0);
  if(info==null||!target.equals(info.packageName)||!version(target).equals(info.versionName))
   throw new SecurityException("Wrong bundled APK identity: "+target);
 }
 private static File extract(MainActivity activity,String target)throws Exception{
  String name=asset(target);
  File ready=SourceEngineApkProvider.file(activity,name);
  File dir=ready.getParentFile();
  if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Could not create private APK cache");
  if(ready.isFile()){
   try{verify(activity,ready,target);return ready;}catch(Exception invalid){
    if(!ready.delete())throw new IOException("Could not reset invalid APK cache");
   }
  }
  if(dir.getUsableSpace()<bytes(target)+20L*1024*1024)
   throw new IOException(activity.tx("Falta espacio para preparar TF2.","Not enough space to prepare TF2."));
  File temp=new File(dir,name+".partial");
  if(temp.exists()&&!temp.delete())throw new IOException("Could not clear temp APK");
  activity.report(activity.tx("Preparando componente de TF2...","Preparing TF2 component..."));
  long count=0;
  try(InputStream in=new BufferedInputStream(activity.getAssets().open(name));
      OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
   byte[] buf=new byte[BUF];int n;
   while((n=in.read(buf))!=-1){
    count+=n;
    if(count>bytes(target))throw new IOException("Bundled APK exceeds maximum size");
    out.write(buf,0,n);
   }
  }
  verify(activity,temp,target);
  if(!temp.renameTo(ready))throw new IOException("Could not finalize verified APK");
  verify(activity,ready,target);
  return ready;
 }
 static void ensure(MainActivity activity,String target)throws Exception{
  if(activity.isPackageInstalled(target))return;
  extract(activity,target);
  activity.report(activity.tx("Se abrirá el instalador de Android. Confirmá la instalación.",
                              "Android's installer will open. Confirm the installation."));
  activity.runOnUiThread(()->{
   if(!activity.isFinishing()&&!activity.isDestroyed())
    activity.openBundledInstallerFromCache(target);
  });
 }
 static void launch(MainActivity activity,String target){
  try{
   if(activity.isPackageInstalled(target))return;
   File file=SourceEngineApkProvider.file(activity,asset(target));
   verify(activity,file,target);
   Uri uri=SourceEngineApkProvider.contentUri(asset(target));
   Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE);
   intent.setData(uri);
   intent.setClipData(ClipData.newRawUri("TF2 2008",uri));
   intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   activity.packageInstallRequested=target;
   activity.startActivity(intent);
  }catch(Exception e){
   activity.report(activity.tx("No se pudo instalar TF2: ","Could not install TF2: ")+e.getMessage());
   android.util.Log.e("TF2008Bootstrap","Android APK installation could not launch",e);
  }
 }
}
