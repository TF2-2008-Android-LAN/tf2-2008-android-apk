package com.tf2008.updater;

import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/** Applies the saved player name to Source 2008 at every game start.
 * valve.rc executes autoexec.cfg; autoexec executes the dedicated filename.
 * All other user settings remain untouched.
 */
final class PlayerNameManager {
 private PlayerNameManager(){}
 private static final String AUTOEXEC="tf_port/cfg/autoexec.cfg";
 private static final String NAME_FILE="tf_port/cfg/tf2008_playername.cfg";
 private static final String START="// TF2008_UPDATER_PLAYER_NAME_BEGIN";
 private static final String END="// TF2008_UPDATER_PLAYER_NAME_END";
 private static final Pattern SAFE=Pattern.compile("[\\p{L}\\p{N} ._-]{1,24}");
 static boolean valid(String name){
  return name!=null && name.equals(name.trim()) && SAFE.matcher(name).matches()
       && !name.contains("  ");
 }
 private static byte[] get(MainActivity a,String name)throws Exception{
  Uri u=a.file(name,false);
  if(u==null)return null;
  try(InputStream in=a.getContentResolver().openInputStream(u)){
   if(in==null)throw new IOException("Cannot read "+name);
   return MainActivity.read(in,64*1024);
  }
 }
 private static void backup(MainActivity a,String file,byte[] old)throws Exception{
  if(old==null)return;
  File root=new File(a.getFilesDir(),"player_name_backups");
  if(!root.exists()&&!root.mkdirs())throw new IOException("Cannot back up settings");
  String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.ROOT).format(new Date());
  File out=new File(root,stamp+"_"+file.substring(file.lastIndexOf('/')+1)+".bak");
  if(out.exists())throw new IOException("Backup conflict");
  try(FileOutputStream f=new FileOutputStream(out)){f.write(old);f.getFD().sync();}
 }
 private static void put(MainActivity a,String name,byte[] bytes)throws Exception{
  Uri uri=a.file(name,true);
  if(uri==null)throw new IOException("Cannot create "+name);
  if("file".equals(uri.getScheme())){
   File dst=new File(uri.getPath()).getCanonicalFile();
   File temp=new File(dst.getParentFile(),dst.getName()+".tf2008tmp");
   if(temp.exists())throw new IOException("Pending backup detected");
   try{
    try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}
    if(!temp.renameTo(dst))throw new IOException("Cannot replace "+name);
   }finally{if(temp.exists()&&!temp.delete())android.util.Log.w("TF2PlayerName","Temp cleanup failed");}
  }else{
   try(OutputStream out=a.getContentResolver().openOutputStream(uri,"rwt")){
    if(out==null)throw new IOException("Cannot write "+name);
    out.write(bytes);
   }
  }
  byte[] check=get(a,name);
  if(!Arrays.equals(check,bytes))throw new IOException("Could not verify "+name);
 }
 private static void writeIfChanged(MainActivity a,String name,byte[] old,byte[] next)throws Exception{
  if(Arrays.equals(old,next))return;
  backup(a,name,old);
  put(a,name,next);
 }
 static synchronized void apply(MainActivity a,String nick)throws Exception{
  if(!valid(nick))throw new SecurityException("Invalid player nickname");
  if(!a.storageReady())throw new SecurityException("Storage permission required");
  // Two writes, but any interrupted attempt is safe to retry at next launch/update.
  // Source only reads the dedicated file when the managed autoexec command exists.
  String command="// Player name saved by TF2 2008 updater.\nname \""+nick+"\"\n";
  byte[] oldDedicated=get(a,NAME_FILE);
  byte[] nextDedicated=command.getBytes(StandardCharsets.UTF_8);
  writeIfChanged(a,NAME_FILE,oldDedicated,nextDedicated);

  byte[] original=get(a,AUTOEXEC);
  String content=original==null?"":new String(original,StandardCharsets.UTF_8);
  String newline=content.contains("\r\n")?"\r\n":"\n";
  String block=START+newline+"exec tf2008_playername.cfg"+newline+END;
  int begin=content.indexOf(START),end=content.indexOf(END);
  if((begin>=0)!=(end>=0))throw new IOException("Incomplete managed nickname block");
  if(begin>=0){
   if(end<begin || content.indexOf(START,begin+START.length())>=0 ||
      content.indexOf(END,end+END.length())>=0)throw new IOException("Ambiguous nickname block");
   content=content.substring(0,begin)+block+content.substring(end+END.length());
  }else{
   if(!content.isEmpty() && !content.endsWith("\n"))content+=newline;
   content+=block+newline;
  }
  byte[] updated=content.getBytes(StandardCharsets.UTF_8);
  writeIfChanged(a,AUTOEXEC,original,updated);
 }
}
