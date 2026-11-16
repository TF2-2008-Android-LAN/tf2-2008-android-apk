package com.tf2008.updater;

import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Applies a small, reversible shader-compatibility overlay using Valve's
 *  existing color textures and HUD alpha masks. Never replaces custom HUD files.
 *  Does not modify models or gameplay logic. */
final class AndroidHudCompatInstaller {
 private AndroidHudCompatInstaller(){}
 static final String ARCHIVE="android_hud_compat_v1.zip";
 static final String INVENTORY="android_hud_compat_v1_manifest.json";
 static final String EXPECTED_SHA="ad1b72120fefa0e3d1e525219d4e2146bab13b17678b32f19000701d831fc3e8";
 static final long EXPECTED_SIZE=108814L;
 private static final int MAX_ENTRY=1024*1024;
 private static final class FileItem {
  final String path,sha,baseSha;
  final byte[] data;
  FileItem(String path,String sha,String baseSha,byte[] data) {
   this.path=path;this.sha=sha;this.baseSha=baseSha;this.data=data;
  }
 }
 private static final class Backup {
  final String path;
  final byte[] original;
  Backup(String path,byte[] original){this.path=path;this.original=original;}
 }
 static void ensure(MainActivity activity) throws Exception {
  byte[] bytes=activity.asset(ARCHIVE);
  if(bytes.length!=EXPECTED_SIZE||!EXPECTED_SHA.equals(MainActivity.hash(bytes)))
   throw new SecurityException("Android HUD bundle checksum failed");
  JSONObject inventory=new JSONObject(new String(activity.asset(INVENTORY),StandardCharsets.UTF_8));
  if(!"TF2_2008_ANDROID_HUD_COMPAT".equals(inventory.getString("product"))||
     inventory.getInt("version")!=1||inventory.getInt("count")!=99)
   throw new SecurityException("Invalid HUD inventory");
  JSONArray json=inventory.getJSONArray("files");
  HashMap<String,JSONObject> expected=new HashMap<>();
  for(int i=0;i<json.length();i++){
   JSONObject row=json.getJSONObject(i);
   String path=row.getString("path");
   if(!safePath(path)||expected.put(path,row)!=null)
    throw new SecurityException("Invalid or duplicated HUD resource");
  }
  HashMap<String,FileItem> content=new HashMap<>();
  try(ZipInputStream zin=new ZipInputStream(new ByteArrayInputStream(bytes))) {
   ZipEntry entry;
   while((entry=zin.getNextEntry())!=null) {
    if(entry.isDirectory())continue;
    String path=entry.getName();
    JSONObject meta=expected.get(path);
    if(meta==null||content.containsKey(path))
     throw new SecurityException("Unexpected Android HUD entry: "+path);
    byte[] raw=MainActivity.readEntry(zin,MAX_ENTRY);
    if(raw.length!=meta.getInt("bytes")||!MainActivity.hash(raw).equals(meta.getString("sha256")))
     throw new SecurityException("Invalid HUD file: "+path);
    content.put(path,new FileItem(path,meta.getString("sha256"),
       meta.optString("original_sha256",""),raw));
   }
  }
  if(content.size()!=99)throw new SecurityException("Incomplete HUD bundle");

  ArrayList<FileItem> updates=new ArrayList<>();
  int kept=0,custom=0;
  for(String path:new TreeSet<>(content.keySet())){
   FileItem item=content.get(path);
   Uri existing=activity.file(path,false);
   byte[] old=null;
   if(existing!=null){
    InputStream in=activity.getContentResolver().openInputStream(existing);
    if(in==null)throw new IOException("Cannot read HUD entry "+path);
    old=MainActivity.read(in,MAX_ENTRY);
   }
   if(old!=null){
    String oldSha=MainActivity.hash(old);
    if(oldSha.equals(item.sha)){kept++;continue;}
    // Preserve users' custom HUD mods. Only replace unmodified stock material
    // files whose SHA256 was captured from the original TF2 archive.
    if(item.baseSha.isEmpty()||!oldSha.equals(item.baseSha)){
     custom++;
     continue;
    }
   }
   updates.add(item);
  }
  ArrayList<Backup> done=new ArrayList<>();
  File backupRoot=new File(activity.getFilesDir(),"tf2008_hud_stock_backups");
  try {
   for(FileItem item:updates){
    Uri existing=activity.file(item.path,false);
    byte[] old=null;
    if(existing!=null){
     InputStream in=activity.getContentResolver().openInputStream(existing);
     if(in==null)throw new IOException("Cannot open HUD for backup");
     old=MainActivity.read(in,MAX_ENTRY);
     File dest=new File(backupRoot,item.path);
     if(!dest.getParentFile().isDirectory()&&!dest.getParentFile().mkdirs())
      throw new IOException("Cannot create HUD rollback directory");
     if(!dest.exists()) {
      try(OutputStream fout=new FileOutputStream(dest)){fout.write(old);}
     }
    }
    Uri target=activity.file(item.path,true);
    if(target==null)throw new IOException("Cannot create HUD material "+item.path);
    done.add(new Backup(item.path,old));
    try(OutputStream out=activity.getContentResolver().openOutputStream(target,"rwt")){
     if(out==null)throw new IOException("Cannot write HUD file");
     out.write(item.data);
    }
    InputStream checked=activity.getContentResolver().openInputStream(target);
    if(checked==null||!MainActivity.hash(MainActivity.read(checked,MAX_ENTRY)).equals(item.sha))
     throw new IOException("HUD verification failed "+item.path);
   }
  }catch(Exception issue) {
   for(int i=done.size()-1;i>=0;i--){
    Backup b=done.get(i);
    try {
     Uri dest=activity.file(b.path,false);
     if(dest==null)continue;
     if(b.original==null){
      if("file".equals(dest.getScheme()))new File(dest.getPath()).delete();
      else DocumentsContract.deleteDocument(activity.getContentResolver(),dest);
     }else {
      try(OutputStream out=activity.getContentResolver().openOutputStream(dest,"rwt")){
       if(out!=null)out.write(b.original);
      }
     }
    }catch(Exception rollback){
     android.util.Log.e("TF2008Hud","HUD rollback failed: "+b.path,rollback);
    }
   }
   throw issue;
  }
  activity.report(activity.tx("HUD Android preparado: ","Android HUD prepared: ")+
      updates.size()+activity.tx(" materiales corregidos, "," fixed materials, ")+
      kept+activity.tx(" correctos, "," already correct, ")+
      custom+activity.tx(" personales conservados."," custom resources preserved."));
  android.util.Log.i("TF2008Hud","HUD_COMPAT_QA_PASS updated="+updates.size()+
    " alreadyCorrect="+kept+" customSkipped="+custom);
 }
 private static boolean safePath(String path){
  if(path==null||path.length()>180||path.contains("..")||path.indexOf('\\')>=0||path.startsWith("/"))return false;
  if(!path.startsWith("tf_port/materials/hud/"))return false;
  if(!(path.endsWith(".vmt")||path.endsWith(".vtf")))return false;
  return path.matches("^[a-zA-Z0-9_./-]+$");
 }
}
