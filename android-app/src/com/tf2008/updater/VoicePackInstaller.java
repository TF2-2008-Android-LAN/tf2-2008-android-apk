package com.tf2008.updater;

import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.DocumentsContract;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Official Spanish TF2 voice audio adapted from the user's local Steam copy.
 * This adds the 2008 localized WAVs, preserving unmatched Pyro / sound effects.
 * It never deletes non-voice settings and backs up the original voice WAVs.
 */
final class VoicePackInstaller {
 private VoicePackInstaller(){}
 private static final String TAG="TF2VoiceES";
 private static final String ROUTE="private_assets/tf2008-voices-es-2008.zip";
 private static final String HASH="f82c6aaccf74580b137e7611bdf27cb910ee0e73580d2a381b974bccf3be237a";
 private static final long PACK_BYTES=183332403L;
 private static final int FILE_COUNT=1609, BUFFER=65536;
 private static final String BACKUP="tf_port/tf2008_original_english_voices_backup.zip";
 private static final String BACKUP_TEMP="tf_port/tf2008_original_english_voices_backup.partial";
 private static final String CACHE_FILE="voice_spanish_2008";
 private static final long RESERVE_BYTES=480L*1024*1024;
 private static final class Entry {
  final String path,sha;
  final long size;
  Entry(String p,String h,long n){path=p;sha=h;size=n;}
 }
 private static List<Entry> manifest(MainActivity a)throws Exception{
  byte[] bytes=a.asset("voice_es_manifest.json");
  JSONObject m=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
  if(!"TF2_2008_AUDIO_ES".equals(m.getString("product"))||
     m.getLong("archiveSize")!=PACK_BYTES||
     !HASH.equals(m.getString("archiveSha256"))||
     m.getInt("count")!=FILE_COUNT)
    throw new SecurityException("Spanish audio manifest does not match this APK");
  JSONArray files=m.getJSONArray("files");
  if(files.length()!=FILE_COUNT)throw new SecurityException("Bad voice manifest count");
  ArrayList<Entry> result=new ArrayList<>();
  HashSet<String> seen=new HashSet<>();
  for(int i=0;i<files.length();i++){
   JSONObject q=files.getJSONObject(i);
   String p=q.getString("path"),sha=q.getString("sha256");
   long size=q.getLong("size");
   if(!p.matches("^tf_port/sound/(vo|misc)/[a-z0-9_./-]+\\.wav$")||
      p.contains("..")||p.length()>170||!sha.matches("^[a-f0-9]{64}$")||
      size<44||size>8L*1024*1024||!seen.add(p))
    throw new SecurityException("Invalid localized voice: "+p);
   result.add(new Entry(p,sha,size));
  }
  return result;
 }
 static boolean ensure(MainActivity a,boolean force)throws Exception {
  if("es".equals(a.lang)){installSpanish(a,force);return true;}
  restoreEnglish(a);
  return false;
 }
 private static String hex(byte[] b){
  char[] alpha="0123456789abcdef".toCharArray();
  char[] out=new char[b.length*2];int i=0;
  for(byte x:b){int n=x&255;out[i++]=alpha[n>>>4];out[i++]=alpha[n&15];}
  return new String(out);
 }
 private static String digest(File p)throws Exception{
  MessageDigest md=MessageDigest.getInstance("SHA-256");
  try(InputStream in=new BufferedInputStream(new FileInputStream(p))){
   byte[] buf=new byte[BUFFER];int n;
   while((n=in.read(buf))!=-1)md.update(buf,0,n);
  }
  return hex(md.digest());
 }
 private static String digestUri(MainActivity a,Uri uri)throws Exception{
  MessageDigest md=MessageDigest.getInstance("SHA-256");
  try(InputStream in=a.getContentResolver().openInputStream(uri)){
   if(in==null)throw new IOException("Voice file cannot be opened");
   byte[] buf=new byte[BUFFER];int n;
   while((n=in.read(buf))!=-1)md.update(buf,0,n);
  }
  return hex(md.digest());
 }
 private static void checkFree(long needed)throws Exception{
  StatFs fs=new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
  if(fs.getAvailableBytes()<needed+RESERVE_BYTES)
   throw new IOException("Not enough space to install/backup Spanish audio; free 700 MB or more");
 }
 private static boolean quickSpanishCheck(MainActivity a,List<Entry> list)throws Exception{
  if(!HASH.equals(a.prefs.getString("voice_es_version",""))||
     a.prefs.getBoolean("voice_es_installing",false))return false;
  int[] samples={0,list.size()/5,list.size()*2/5,list.size()*3/5,list.size()*4/5,list.size()-1};
  for(int idx:samples){
   Entry e=list.get(idx);
   Uri u=a.file(e.path,false);
   if(u==null||!e.sha.equals(digestUri(a,u)))return false;
  }
  return true;
 }
 private static File download(MainActivity a)throws Exception{
  File cache=new File(a.getExternalCacheDir()!=null?a.getExternalCacheDir():a.getCacheDir(),"tf2008");
  if(!cache.exists()&&!cache.mkdirs())throw new IOException("Cannot create audio cache");
  File partial=new File(cache,CACHE_FILE+".part");
  File ready=new File(cache,CACHE_FILE+".verified.zip");
  if(ready.exists()){
   if(ready.length()==PACK_BYTES&&HASH.equals(digest(ready)))return ready;
   if(!ready.delete())throw new IOException("Cannot remove invalid audio ZIP");
  }
  if(partial.exists()&&partial.length()>PACK_BYTES&&!partial.delete())
   throw new IOException("Cannot reset invalid partial audio download");
  long start=partial.exists()?partial.length():0L;
  long needed=PACK_BYTES-start;
  if(needed>0)checkFree(needed);
  String base=a.serverAddress;
  if(base==null||base.trim().isEmpty())
   throw new IOException(a.tx("Falta el enlace de invitación para descargar las voces en español.",
                              "Open the invitation link before downloading Spanish voices."));
  if(!base.endsWith("/"))base+="/";
  URL url=ReleaseTransport.resource(base,ROUTE);
  HttpURLConnection c=ReleaseTransport.open(url,start,10000,45000);
  try{
   int status=c.getResponseCode();
   if(start>0&&status==200){
    if(!partial.delete())throw new IOException("Cannot reset partial audio download");
    start=0;
   }else if(start>0&&status==206){
    String range=c.getHeaderField("Content-Range");
    if(range==null||!range.startsWith("bytes "+start+"-"))throw new IOException("Wrong audio download resume range");
   }else if(status!=200)throw new IOException("Spanish audio unavailable from this server (HTTP "+status+")");
   long done=start,previous=0;
   try(InputStream in=new BufferedInputStream(c.getInputStream());
       OutputStream out=new BufferedOutputStream(new FileOutputStream(partial,start>0))){
    byte[] b=new byte[BUFFER];int n;
    while((n=in.read(b))!=-1){
     out.write(b,0,n);done+=n;
     if(done>PACK_BYTES)throw new IOException("Audio download exceeds expected size");
     long now=android.os.SystemClock.elapsedRealtime();
     if(now-previous>1000){
      previous=now;
      a.report(a.tx("Descargando voces oficiales en español: ","Downloading official Spanish voices: ")+(done*100/PACK_BYTES)+"%");
     }
    }
   }
  }finally{c.disconnect();}
  if(partial.length()!=PACK_BYTES)throw new IOException("Audio download incomplete; it can resume");
  a.report(a.tx("Verificando voces en español...","Verifying Spanish voice pack..."));
  if(!HASH.equals(digest(partial))){
   if(!partial.delete())android.util.Log.w(TAG,"Invalid ZIP not deleted");
   throw new SecurityException("Voice ZIP does not match expected checksum");
  }
  if(!partial.renameTo(ready))throw new IOException("Cannot finalize voice ZIP");
  return ready;
 }
 private static void delete(MainActivity a,Uri uri)throws Exception{
  if(uri==null)return;
  if("file".equals(uri.getScheme())){
   File f=new File(uri.getPath());
   if(f.exists()&&!f.delete())throw new IOException("Cannot delete staging file");
  }else if(!DocumentsContract.deleteDocument(a.getContentResolver(),uri))
   throw new IOException("Cannot delete staging document");
 }
 private static Uri backupUri(MainActivity a)throws Exception{return a.file(BACKUP,false);}
 private static void validateBackup(MainActivity a,List<Entry> entries)throws Exception{
  Uri u=backupUri(a);
  if(u==null)throw new IOException("Original voice backup missing");
  HashSet<String> pending=new HashSet<>();
  for(Entry e:entries)pending.add(e.path);
  try(InputStream input=a.getContentResolver().openInputStream(u);
      ZipInputStream zi=new ZipInputStream(new BufferedInputStream(input))){
   ZipEntry e;int count=0;byte[] buf=new byte[BUFFER];
   while((e=zi.getNextEntry())!=null){
    if(e.isDirectory()||!pending.remove(e.getName()))throw new SecurityException("Unexpected backup entry");
    int n;long size=0;
    while((n=zi.read(buf))!=-1){size+=n;if(size>8L*1024*1024)throw new SecurityException("Backup entry too large");}
    if(size==0)throw new IOException("Backup entry empty");
    count++;zi.closeEntry();
   }
   if(count!=FILE_COUNT||!pending.isEmpty())throw new IOException("Incomplete original voice backup");
  }
 }
 private static void prepareBackup(MainActivity a,List<Entry> entries)throws Exception{
  if(backupUri(a)!=null){validateBackup(a,entries);return;}
  // Never accidentally preserve already-localized audio as an English backup.
  for(int i:new int[]{0,entries.size()/2,entries.size()-1}){
   Entry e=entries.get(i);Uri uri=a.file(e.path,false);
   if(uri==null)throw new IOException("The original TF2 voice files are missing");
   if(e.sha.equals(digestUri(a,uri)))
    throw new IOException("Spanish voices exist without an original backup; repair base game first");
  }
  Uri temp=a.file(BACKUP_TEMP,false);
  if(temp!=null)delete(a,temp); // A previous interrupted backup; no original voices have been changed yet.
  checkFree(260L*1024*1024);
  Uri outUri=a.file(BACKUP_TEMP,true);
  try(OutputStream raw=a.getContentResolver().openOutputStream(outUri,"rwt");
      ZipOutputStream output=new ZipOutputStream(new BufferedOutputStream(raw))){
   byte[] buf=new byte[BUFFER];
   for(int i=0;i<entries.size();i++){
    Entry e=entries.get(i);
    Uri inputUri=a.file(e.path,false);
    if(inputUri==null)throw new IOException("Missing original voice "+e.path);
    output.putNextEntry(new ZipEntry(e.path));
    try(InputStream input=a.getContentResolver().openInputStream(inputUri)){
     if(input==null)throw new IOException("Could not open "+e.path);
     int n;long total=0;
     while((n=input.read(buf))!=-1){output.write(buf,0,n);total+=n;if(total>8L*1024*1024)throw new IOException("Bad original voice size");}
    }
    output.closeEntry();
    if(i%120==0)a.report(a.tx("Guardando las voces originales: ","Backing up original voices: ")+(i+1)+"/"+entries.size());
   }
  }
  if("file".equals(outUri.getScheme())){
   File src=new File(outUri.getPath());
   File dst=new File(src.getParentFile(),BACKUP.substring(BACKUP.lastIndexOf('/')+1));
   if(dst.exists()||!src.renameTo(dst))throw new IOException("Cannot finalize voice backup");
  }else{
   Uri renamed=DocumentsContract.renameDocument(a.getContentResolver(),outUri,BACKUP.substring(BACKUP.lastIndexOf('/')+1));
   if(renamed==null)throw new IOException("Cannot finalize SAF voice backup");
  }
  a.report(a.tx("Comprobando la copia de las voces originales...","Validating original voice backup..."));
  validateBackup(a,entries);
 }
 private static void writeTarget(MainActivity a,String path,InputStream input,long expected,String sha)throws Exception{
  Uri oldTmp=a.file(path+".tf2tmp",false);
  if(oldTmp!=null)delete(a,oldTmp);
  Uri temp=a.file(path+".tf2tmp",true);
  MessageDigest md=MessageDigest.getInstance("SHA-256");
  long total=0;byte[] buf=new byte[BUFFER];
  try(OutputStream out=a.getContentResolver().openOutputStream(temp,"rwt")){
   if(out==null)throw new IOException("Cannot write localized voice");
   int n;
   while((n=input.read(buf))!=-1){
    total+=n;
    if(total>8L*1024*1024)throw new IOException("Voice entry size limit");
    out.write(buf,0,n);md.update(buf,0,n);
   }
  }
  if((expected>=0&&total!=expected)||(sha!=null&&!sha.equals(hex(md.digest())))){
   delete(a,temp);
   throw new SecurityException("Audio entry hash/size mismatch "+path);
  }
  if("file".equals(temp.getScheme())){
   File src=new File(temp.getPath());
   File target=new File(src.getParentFile(),path.substring(path.lastIndexOf('/')+1));
   if(!src.renameTo(target))throw new IOException("Cannot atomically replace "+path);
  }else{
   Uri target=a.file(path,true);
   try(InputStream tmpInput=a.getContentResolver().openInputStream(temp);
       OutputStream dst=a.getContentResolver().openOutputStream(target,"rwt")){
    if(tmpInput==null||dst==null)throw new IOException("No folder access to "+path);
    int n;while((n=tmpInput.read(buf))!=-1)dst.write(buf,0,n);
   }
   delete(a,temp);
   if(sha!=null&&!sha.equals(digestUri(a,target)))throw new IOException("Voice verification failed "+path);
  }
 }
 private static void invalidateGameAudioCache(MainActivity a)throws Exception{
  Uri cache=a.file("tf_port/sound/sound.cache",false);
  if(cache!=null)delete(a,cache); // Source regenerates this file when the audio catalog changes.
 }
 static void installSpanish(MainActivity a,boolean force)throws Exception{
  List<Entry> entries=manifest(a);
  if(!force&&quickSpanishCheck(a,entries)){
   a.report(a.tx("Voces en español listas.","Spanish voices are ready."));
   return;
  }
  checkFree(PACK_BYTES+300L*1024*1024);
  File zip=download(a);
  try(ZipFile input=new ZipFile(zip)){
   if(input.size()!=FILE_COUNT)throw new SecurityException("Unexpected voice archive entries");
   HashSet<String> expected=new HashSet<>();
   for(Entry e:entries){
    expected.add(e.path);
    ZipEntry item=input.getEntry(e.path);
    if(item==null||item.getSize()!=e.size)throw new SecurityException("Missing audio "+e.path);
   }
   Enumeration<? extends ZipEntry> iter=input.entries();
   while(iter.hasMoreElements()){
    ZipEntry e=iter.nextElement();
    if(!expected.remove(e.getName()))throw new SecurityException("Unexpected audio path "+e.getName());
   }
   if(!expected.isEmpty())throw new SecurityException("Audio archive incomplete");
   prepareBackup(a,entries);
   if(!a.prefs.edit().putBoolean("voice_es_installing",true).commit())
    throw new IOException("Cannot checkpoint voice installation");
   int changed=0;
   for(int i=0;i<entries.size();i++){
    Entry e=entries.get(i);
    Uri existing=a.file(e.path,false);
    if(existing!=null&&e.sha.equals(digestUri(a,existing)))continue;
    try(InputStream wav=input.getInputStream(input.getEntry(e.path))){
     writeTarget(a,e.path,wav,e.size,e.sha);
    }
    changed++;
    if(i%60==0)a.report(a.tx("Aplicando voces en español: ","Installing Spanish voices: ")+(i+1)+"/"+entries.size());
   }
   invalidateGameAudioCache(a);
   if(!a.prefs.edit().putString("voice_es_version",HASH).putBoolean("voice_es_installing",false).commit())
    throw new IOException("Could not mark Spanish voices as installed");
   a.report(a.tx("Voces en español instaladas: ","Spanish voice clips installed: ")+changed);
  }
  if(zip.exists()&&!zip.delete())android.util.Log.w(TAG,"Could not remove verified voice ZIP");
 }
 static void restoreEnglish(MainActivity a)throws Exception{
  String version=a.prefs.getString("voice_es_version","");
  if(!HASH.equals(version)&&!a.prefs.getBoolean("voice_es_installing",false))return;
  List<Entry> entries=manifest(a);
  Uri source=backupUri(a);
  if(source==null)throw new IOException("Original voice backup not found; run full game repair to restore English");
  validateBackup(a,entries);
  HashSet<String> expected=new HashSet<>();
  for(Entry e:entries)expected.add(e.path);
  a.prefs.edit().putBoolean("voice_es_installing",true).commit();
  int restored=0;
  try(InputStream raw=a.getContentResolver().openInputStream(source);
      ZipInputStream in=new ZipInputStream(new BufferedInputStream(raw))){
   ZipEntry ze;
   while((ze=in.getNextEntry())!=null){
    if(!expected.remove(ze.getName()))throw new SecurityException("Bad backup voice entry");
    writeTarget(a,ze.getName(),in,ze.getSize(),null);
    in.closeEntry();restored++;
    if(restored%100==0)a.report(a.tx("Restaurando voces originales: ","Restoring original voices: ")+restored+"/"+FILE_COUNT);
   }
  }
  if(!expected.isEmpty())throw new SecurityException("Original voices incomplete");
  invalidateGameAudioCache(a);
  if(!a.prefs.edit().remove("voice_es_version").putBoolean("voice_es_installing",false).commit())
   throw new IOException("Could not save voice restoration state");
  a.report(a.tx("Voces originales en inglés restauradas.","Original English voices restored."));
 }
}
