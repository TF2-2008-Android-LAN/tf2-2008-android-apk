package com.tf2008.updater;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/**
 * Private QA: 16 separate verified MP3 tracks.
 * This installer does NOT make the Source engine shuffle by itself.
 * Engine integration and signing compatibility must be validated before release.
 */
final class MenuMusicInstaller {
 private MenuMusicInstaller(){}
 private static final int BUF=131072;
 private static final String VERSION="tf2008-menu-16-v120";
 private static final String OLD_LONG_SHA="a37d33b4adc407789a13a508a49fe3352f26eebab7ccf7651961b1bf867d613c";
 private static final int[] NUM={1,2,3,4,15,16,17,18,19,20,21,22,23,24,25,26};
 private static final long[] SIZE={
 2122713L,1209054L,7820332L,2183461L,1055719L,940292L,2429945L,2498491L,
 2011965L,2644986L,1571375L,2014210L,1856272L,3515090L,4302939L,2973834L};
 private static final String[] SHA={
 "024016c7057fafdd6c219844eae83d8af7a88b7bf6f719e3df0881a0e069cb83",
 "e56371082c30d70ee244c58eb2262a5325a15554d838f1772b77e5ffde0ccd0b",
 "a4a522804b338d322754e033f8b6e81180305885c3c6697050777bb54dfbd6b8",
 "1548f0bcd6a6632b9e9ccdf2503093603c8f70fb85579f6e0fddf0d7d08b1d04",
 "3673191047ae765eb81ff15b51e718c847aa964461c9ba26d36d02ce7b5c8e6a",
 "862bb58f2bab9327795abf5df02ed2d4fff1786d905ce39f399e1647b85d58a0",
 "a61c1f1e9c5f866ce5db37cd4fea6c285cb386bd34d8c1870c4a8eaed19e5e1c",
 "9e9190247683ee0d18d132858335ba129e1809db7c3c2fac1046ae72f1d2b244",
 "a38487ecc56620a9d2fd9abbeab7c30f454d5f8f8a668b7c9986e4bf07cdd722",
 "8632e523f467bf558b15c87de2760551c0b999d9360913017f841e11cb15db96",
 "b432ad09b3bcfaa2fdd7bdcfebea5e6c6f578a2ba1939fd1fe08debdd089ab18",
 "64c1a6660ab3ce9554f68d7a01d43058ead62d80445d936cd83dc809910835c4",
 "e0e7ff8e30160ad5e5fd0857eebf7182a905f6bb7d7ba1c8e7105674c53b9f83",
 "8ace224103419f840c77e934598a4ef6c5748f0db310d7f03838bba357caae79",
 "5a7711030e46027f4566a4f3c302c2b72d7ea9ac220d9da3de5de12db0e3dbb2",
 "eb2e74630da115522374fc4a2758b0883921fc0b83e61c42e9799cc0fd96e12f"};
 private static String path(int n){return "tf_port/sound/ui/gamestartup"+n+".mp3";}
 private static String hex(byte[] data){
  char[] alphabet="0123456789abcdef".toCharArray();
  char[] out=new char[data.length*2];
  for(int i=0;i<data.length;i++){int v=data[i]&255;out[i*2]=alphabet[v>>>4];out[i*2+1]=alphabet[v&15];}
  return new String(out);
 }
 private static String sha(InputStream input)throws Exception{
  MessageDigest d=MessageDigest.getInstance("SHA-256");
  try(InputStream in=input){byte[] buf=new byte[BUF];int n;while((n=in.read(buf))!=-1)d.update(buf,0,n);}
  return hex(d.digest());
 }
 private static String sha(File file)throws Exception{return sha(new FileInputStream(file));}
 private static String sha(MainActivity a,Uri uri)throws Exception{
  InputStream in=a.getContentResolver().openInputStream(uri);
  if(in==null)throw new IOException("Cannot read track");
  return sha(in);
 }
 private static File work(MainActivity a)throws IOException{
  File dir=new File(a.getFilesDir(),"tf2008_menu_music");
  if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Private music directory unavailable");
  return dir;
 }
 private static File originalBackup(MainActivity a)throws IOException{return new File(work(a),"original_mp3s.zip");}
 private static void verifyBackup(File backup)throws Exception{
  if(!backup.isFile())throw new IOException("Original music backup missing");
  try(ZipFile z=new ZipFile(backup)){
   if(z.size()!=4)throw new IOException("Original backup has wrong track count");
   for(int n=1;n<=4;n++){
    ZipEntry e=z.getEntry(path(n));
    if(e==null||e.getSize()<1000||e.getSize()>32000000L)throw new IOException("Invalid original track "+n);
    try(InputStream in=z.getInputStream(e)){
     byte[] buf=new byte[BUF];int got;long total=0;
     while((got=in.read(buf))!=-1){total+=got;if(total>32000000L)throw new IOException("Oversized backup");}
     if(total!=e.getSize())throw new IOException("Truncated backup");
    }
   }
  }
 }
 private static void backupOriginal(MainActivity a)throws Exception{
  File backup=originalBackup(a);
  if(backup.exists()){verifyBackup(backup);return;}
  Uri[] originals=new Uri[4];
  for(int n=1;n<=4;n++){
   Uri uri=a.file(path(n),false);
   if(uri==null)throw new IOException("Original music missing: "+n);
   String hash=sha(a,uri);
   if(OLD_LONG_SHA.equals(hash))
    throw new IOException("Old 82-minute replacement detected without original backup; refusing replacement");
   originals[n-1]=uri;
  }
  File tmp=new File(work(a),"original_mp3s.v120.partial");
  if(tmp.exists()&&!tmp.delete())throw new IOException("Cannot clear pending original backup");
  try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))){
   for(int n=1;n<=4;n++){
    out.putNextEntry(new ZipEntry(path(n)));
    try(InputStream in=a.getContentResolver().openInputStream(originals[n-1])){
     if(in==null)throw new IOException("Original audio unreadable");
     byte[] b=new byte[BUF];int count;while((count=in.read(b))!=-1)out.write(b,0,count);
    }
    out.closeEntry();
   }
  }
  verifyBackup(tmp);
  if(!tmp.renameTo(backup))throw new IOException("Cannot commit original audio backup");
 }
 private static File download(MainActivity a,int index)throws Exception{
  String server=a.serverAddress;
  if(server==null||server.isEmpty())throw new IOException("No private resource server configured");
  if(!server.endsWith("/"))server+="/";
  int num=NUM[index];
  URL url=ReleaseTransport.resource(server,"private_assets/menu_music/gamestartup"+num+".mp3");
  File cache=new File(a.getExternalCacheDir()!=null?a.getExternalCacheDir():a.getCacheDir(),"tf2008_menu_music_v120");
  if(!cache.isDirectory()&&!cache.mkdirs())throw new IOException("Cannot create temporary audio cache");
  File verified=new File(cache,"track"+num+".verified.mp3");
  if(verified.isFile()&&verified.length()==SIZE[index]&&SHA[index].equals(sha(verified)))return verified;
  if(verified.exists()&&!verified.delete())throw new IOException("Cannot reset old track");
  File part=new File(cache,"track"+num+".part");
  if(part.exists()&&part.length()>SIZE[index]&&!part.delete())throw new IOException("Invalid partial file");
  long offset=part.exists()?part.length():0;
  HttpURLConnection c=ReleaseTransport.open(url,offset,12000,45000);
  try{
   int status=c.getResponseCode();
   if(offset>0&&status==200){if(!part.delete())throw new IOException("Cannot restart track");offset=0;}
   else if(offset>0&&status==206){
    String range=c.getHeaderField("Content-Range");
    if(range==null||!range.startsWith("bytes "+offset+"-"))throw new IOException("Bad HTTP resume range");
   }else if(status!=200)throw new IOException("MP3 unavailable: HTTP "+status);
   try(InputStream in=new BufferedInputStream(c.getInputStream());
       OutputStream out=new BufferedOutputStream(new FileOutputStream(part,offset>0))){
    byte[] b=new byte[BUF];int count;long received=offset;
    while((count=in.read(b))!=-1){
     received+=count;
     if(received>SIZE[index])throw new IOException("Track exceeds expected size");
     out.write(b,0,count);
    }
   }
  }finally{c.disconnect();}
  if(part.length()!=SIZE[index]||!SHA[index].equals(sha(part))){
   part.delete();throw new SecurityException("Music SHA256 mismatch: "+num);
  }
  if(!part.renameTo(verified))throw new IOException("Cannot finish verified music download");
  return verified;
 }
 private static void write(MainActivity a,String target,InputStream source,String expected)throws Exception{
  Uri uri=a.file(target,true);
  if(uri==null)throw new IOException("Cannot write "+target);
  byte[] b=new byte[BUF];int count;
  if("file".equals(uri.getScheme())){
   File dst=new File(uri.getPath());
   File tmp=new File(dst.getParentFile(),dst.getName()+".tf2v120partial");
   if(tmp.exists()&&!tmp.delete())throw new IOException("Temporary audio busy");
   try(OutputStream out=new BufferedOutputStream(new FileOutputStream(tmp))){
    while((count=source.read(b))!=-1)out.write(b,0,count);
   }
   if(expected!=null&&!expected.equals(sha(tmp))){tmp.delete();throw new IOException("Staged track checksum invalid");}
   if(!tmp.renameTo(dst)){
    try(InputStream in=new FileInputStream(tmp);OutputStream out=new FileOutputStream(dst,false)){
     while((count=in.read(b))!=-1)out.write(b,0,count);
    }
    tmp.delete();
   }
  }else{
   try(OutputStream out=a.getContentResolver().openOutputStream(uri,"rwt")){
    if(out==null)throw new IOException("Storage not writable");
    while((count=source.read(b))!=-1)out.write(b,0,count);
   }
  }
  if(expected!=null&&!expected.equals(sha(a,uri)))throw new IOException("Installed track failed checksum: "+target);
 }
 private static boolean valid(MainActivity a)throws Exception{
  if(!VERSION.equals(a.prefs.getString("menu_music_version","")))return false;
  for(int i=0;i<NUM.length;i++){
   Uri u=a.file(path(NUM[i]),false);
   if(u==null||!SHA[i].equals(sha(a,u)))return false;
  }
  return true;
 }
 static void ensure(MainActivity a)throws Exception{if(!valid(a))install(a);}
 static void install(MainActivity a)throws Exception{
  if(valid(a))return;
  backupOriginal(a);
  // Download and validate ALL tracks BEFORE changing a single game file.
  File[] tracks=new File[NUM.length];
  for(int i=0;i<NUM.length;i++){
   a.report(a.tx("Descargando música: ","Downloading music: ")+(i+1)+"/16");
   tracks[i]=download(a,i);
  }
  if(!a.prefs.edit().putBoolean("menu_music_in_progress",true).commit())throw new IOException("Checkpoint write failed");
  try{
   for(int i=0;i<NUM.length;i++){
    a.report(a.tx("Instalando música: ","Installing music: ")+(i+1)+"/16");
    try(InputStream in=new BufferedInputStream(new FileInputStream(tracks[i]))){
     write(a,path(NUM[i]),in,SHA[i]);
    }
   }
   if(!a.prefs.edit().putBoolean("menu_music_enabled",true).putBoolean("menu_music_optedout",false)
       .putBoolean("menu_music_in_progress",false).putString("menu_music_version",VERSION).commit())
    throw new IOException("Cannot save music install result");
   a.report(a.tx("16 canciones de menú instaladas.","16 individual menu tracks installed."));
  }catch(Exception e){
   try{restoreFromBackup(a,false);}catch(Exception rollback){android.util.Log.e("TF2MenuMusic","Restore after failure unsuccessful",rollback);}
   throw e;
  }
 }
 private static void restoreFromBackup(MainActivity a,boolean optout)throws Exception{
  File backup=originalBackup(a);verifyBackup(backup);
  try(ZipFile zip=new ZipFile(backup)){
   for(int n=1;n<=4;n++){
    try(InputStream in=zip.getInputStream(zip.getEntry(path(n)))){
     write(a,path(n),in,null);
    }
   }
  }
  a.prefs.edit().putBoolean("menu_music_enabled",false).putBoolean("menu_music_optedout",optout)
    .putBoolean("menu_music_in_progress",false).remove("menu_music_version").commit();
 }
 static void restore(MainActivity a)throws Exception{
  restoreFromBackup(a,true);
  a.report(a.tx("Música original restaurada.","Original menu audio restored."));
 }
}
