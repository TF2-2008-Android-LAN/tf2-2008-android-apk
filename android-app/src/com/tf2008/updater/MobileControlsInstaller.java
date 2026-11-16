package com.tf2008.updater;

import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Installs mobile-only HUD controls: crouch toggle, 3 classic voice menus,
 * and host-only TF bot quota tools. Never silently overwrites custom bindings. */
final class MobileControlsInstaller {
 private MobileControlsInstaller() {}
 private static final String ZIP="tf2008_mobile_controls_v2.zip";
 private static final String MANIFEST="tf2008_mobile_controls_v2_manifest.json";
 private static final String ZIP_HASH="3d98bec7d40a42bb194952d922d3a7ef584a733aeba6ce4d0e45e0ef54be5166";
 private static final int ZIP_SIZE=234613;
 private static final int COUNT=108;
 private static final int MAX_FILE=1024*1024;
 private static final class Item {
  final String path, hash;
  final byte[] content;
  final Set<String> approvedOriginals;
  Item(String p,String h,byte[] b,Set<String> allow){
   path=p;hash=h;content=b;approvedOriginals=allow;
  }
 }
 private static final class Backup {
  final String path;
  final byte[] original;
  Backup(String p,byte[] b){path=p;original=b;}
 }
 static void ensure(MainActivity activity) throws Exception {
  byte[] archive=activity.asset(ZIP);
  if(archive.length!=ZIP_SIZE||!ZIP_HASH.equals(MainActivity.hash(archive)))
   throw new SecurityException("Mobile controls ZIP checksum mismatch");
  JSONObject manifest=new JSONObject(new String(activity.asset(MANIFEST),StandardCharsets.UTF_8));
  if(!"TF2_2008_MOBILE_TOUCH_CONTROLS_V2".equals(manifest.getString("product"))||
     manifest.getInt("version")!=2||manifest.getInt("total")!=COUNT)
   throw new SecurityException("Mobile controls inventory mismatch");
  JSONArray json=manifest.getJSONArray("files");
  if(json.length()!=COUNT)throw new SecurityException("Wrong number of controls");
  Map<String,JSONObject> expected=new HashMap<>();
  for(int i=0;i<json.length();i++){
   JSONObject m=json.getJSONObject(i);
   String path=m.getString("path");
   if(!safe(path)||expected.put(path,m)!=null)
    throw new SecurityException("Unsafe or repeated controls asset: "+path);
  }
  Map<String,Item> inventory=new HashMap<>();
  try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))){
   ZipEntry entry;
   while((entry=zip.getNextEntry())!=null){
    if(entry.isDirectory())continue;
    String path=entry.getName();
    JSONObject m=expected.get(path);
    if(m==null||inventory.containsKey(path))
     throw new SecurityException("Unexpected controls file "+path);
    byte[] data=MainActivity.readEntry(zip,MAX_FILE);
    String h=MainActivity.hash(data);
    if(data.length!=m.getInt("bytes")||!h.equals(m.getString("sha256")))
     throw new SecurityException("Invalid controls asset "+path);
    Set<String> approved=new HashSet<>();
    JSONArray allowed=m.getJSONArray("allowed_original_sha256");
    for(int j=0;j<allowed.length();j++)approved.add(allowed.getString(j));
    inventory.put(path,new Item(path,h,data,approved));
   }
  }
  if(inventory.size()!=COUNT)throw new SecurityException("Incomplete controls ZIP");
  List<Item> needed=new ArrayList<>();
  int correct=0,custom=0;
  for(String path:new TreeSet<>(inventory.keySet())){
   Item item=inventory.get(path);
   byte[] existing=readExisting(activity,path);
   if(existing!=null){
    String current=MainActivity.hash(existing);
    if(current.equals(item.hash)){correct++;continue;}
    if(!item.approvedOriginals.contains(current)){
     custom++;
     continue;
    }
   }
   needed.add(item);
  }
  List<Backup> writes=new ArrayList<>();
  File backupDir=new File(activity.getFilesDir(),"tf2008_mobile_controls_backup");
  try {
   for(Item item:needed){
    byte[] previous=readExisting(activity,item.path);
    if(previous!=null){
     File backup=new File(backupDir,item.path);
     if(!backup.getParentFile().isDirectory()&&!backup.getParentFile().mkdirs())
      throw new IOException("Could not make controls backup folder");
     if(!backup.exists()){
      try(FileOutputStream out=new FileOutputStream(backup)){out.write(previous);}
     }
    }
    Uri dst=activity.file(item.path,true);
    if(dst==null)throw new IOException("Cannot create controls asset "+item.path);
    writes.add(new Backup(item.path,previous));
    try(OutputStream out=activity.getContentResolver().openOutputStream(dst,"rwt")){
     if(out==null)throw new IOException("Unable to open controls asset "+item.path);
     out.write(item.content);
    }
    byte[] verified=readExisting(activity,item.path);
    if(verified==null||!MainActivity.hash(verified).equals(item.hash))
     throw new IOException("Controls asset verification failed "+item.path);
   }
  }catch(Exception error){
   for(int i=writes.size()-1;i>=0;i--){
    Backup prev=writes.get(i);
    try{
     Uri uri=activity.file(prev.path,false);
     if(uri==null)continue;
     if(prev.original==null){
      if("file".equals(uri.getScheme()))new File(uri.getPath()).delete();
      else DocumentsContract.deleteDocument(activity.getContentResolver(),uri);
     }else{
      try(OutputStream out=activity.getContentResolver().openOutputStream(uri,"rwt")){
       if(out!=null)out.write(prev.original);
      }
     }
    }catch(Exception rollback){Log.e("TF2008Controls","Rollback failed: "+prev.path,rollback);}
   }
   throw error;
  }
  Log.i("TF2008Controls","MOBILE_CONTROLS_V2_PASS updated="+needed.size()+
   " alreadyCorrect="+correct+" customSkipped="+custom);
  activity.report(activity.tx("Controles móviles: ","Mobile controls: ")+needed.size()+
   activity.tx(" actualizados, "," updated, ")+correct+
   activity.tx(" correctos, "," already correct, ")+custom+
   activity.tx(" archivos personales conservados."," custom files preserved."));
 }
 private static byte[] readExisting(MainActivity activity,String path) throws Exception {
  Uri uri=activity.file(path,false);
  if(uri==null)return null;
  try(InputStream input=activity.getContentResolver().openInputStream(uri)){
   if(input==null)throw new IOException("Cannot inspect controls asset "+path);
   return MainActivity.read(input,MAX_FILE);
  }
 }
 private static boolean safe(String path){
  if(path==null||path.length()>180||path.startsWith("/")||
     path.contains("..")||path.indexOf('\\')>=0||
     !path.matches("[a-zA-Z0-9_./-]+"))return false;
  return path.startsWith("tf_port/cfg/")||
         path.startsWith("tf_port/materials/vgui/touch/");
 }
}
