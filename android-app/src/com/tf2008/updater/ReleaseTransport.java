package com.tf2008.updater;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

/**
 * Public GitHub Release assets use flat filenames and HTTPS redirects.
 * No token or GitHub login is embedded in the Android application.
 * Existing SHA-256 / signed-manifest checks remain the final integrity gate.
 */
final class ReleaseTransport {
 private ReleaseTransport(){}
 static boolean isGitHubRelease(URL url){
  String p=url.getPath();
  return "https".equalsIgnoreCase(url.getProtocol())&&"github.com".equalsIgnoreCase(url.getHost())
    &&url.getPort()==-1&&url.getUserInfo()==null
    &&(p.matches("^/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/download/[A-Za-z0-9_.-]+/?$") ||
       p.matches("^/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/latest/download/?$"));
 }
 static URL resource(String base,String route)throws Exception{
  if(base==null||base.trim().isEmpty())throw new IOException("Missing download origin");
  if(!route.matches("^[A-Za-z0-9_./-]{1,240}$")||route.contains("..")||
     route.startsWith("/")||route.contains("//"))
   throw new SecurityException("Unsafe resource name");
  if(!base.endsWith("/"))base+="/";
  URL root=new URL(base);
  if(root.getQuery()!=null||root.getRef()!=null||root.getUserInfo()!=null)
   throw new SecurityException("Unexpected origin parameters");
  if(isGitHubRelease(root)){
   String basename=route.substring(route.lastIndexOf('/')+1);
   if(!basename.matches("^[A-Za-z0-9_.-]+$"))throw new SecurityException("Unsafe asset basename");
   return new URL(root,basename);
  }
  // Legacy private server format, maintained during migration.
  if(!"https".equalsIgnoreCase(root.getProtocol())||
     !("127.0.0.1".equals(root.getHost())||
       root.getHost().toLowerCase(Locale.ROOT).matches("^[a-z0-9-]+\\.trycloudflare\\.com$")||
       root.getHost().toLowerCase(Locale.ROOT).matches("^[a-z0-9-]+\\.[a-z0-9-]+\\.workers\\.dev$")))
   throw new SecurityException("Unsupported resource server");
  return new URL(root,route);
 }
 private static boolean githubAssetHost(String host){
  return "github.com".equals(host)||"release-assets.githubusercontent.com".equals(host)||
    "objects.githubusercontent.com".equals(host)||
    "github-releases.githubusercontent.com".equals(host);
 }
 static boolean safeRedirect(URL from,URL to,boolean githubOrigin){
  if(!"https".equalsIgnoreCase(to.getProtocol())||to.getPort()!=-1||
     to.getUserInfo()!=null||to.getRef()!=null)return false;
  String toHost=to.getHost().toLowerCase(Locale.ROOT);
  if(githubOrigin)return githubAssetHost(toHost);
  return from.getHost().equalsIgnoreCase(to.getHost())&&to.getPort()==from.getPort();
 }
 static HttpURLConnection open(URL resource,long offset,int connectTimeout,int readTimeout)
   throws IOException {
  if(offset<0)throw new IOException("Negative partial offset");
  String host=resource.getHost().toLowerCase(Locale.ROOT);
  boolean ghOrigin=githubAssetHost(host);
  if(!"https".equalsIgnoreCase(resource.getProtocol())||
     resource.getUserInfo()!=null||resource.getPort()!=-1||
     !(ghOrigin || host.matches("^[a-z0-9-]+\\.trycloudflare\\.com$") ||
       host.matches("^[a-z0-9-]+\\.[a-z0-9-]+\\.workers\\.dev$")))
   throw new IOException("Download must use a trusted HTTPS endpoint");
  URL current=resource;
  for(int i=0;i<7;i++){
   HttpURLConnection connection=(HttpURLConnection)current.openConnection();
   connection.setConnectTimeout(connectTimeout);
   connection.setReadTimeout(readTimeout);
   connection.setInstanceFollowRedirects(false);
   connection.setRequestProperty("Accept-Encoding","identity");
   connection.setRequestProperty("User-Agent","TF2-2008-Android-Updater/1.0");
   if(offset>0)connection.setRequestProperty("Range","bytes="+offset+"-");
   int status;
   try{status=connection.getResponseCode();}
   catch(IOException ex){connection.disconnect();throw ex;}
   if(status==301||status==302||status==303||status==307||status==308){
    String location=connection.getHeaderField("Location");
    if(location==null||location.length()>4096){
     connection.disconnect();
     throw new IOException("Invalid HTTPS download redirect");
    }
    URL next;
    try{next=new URL(current,location);}
    catch(MalformedURLException e){connection.disconnect();throw new IOException("Bad redirect",e);}
    if(!safeRedirect(current,next,ghOrigin)){
     connection.disconnect();
     throw new SecurityException("Download tried to redirect to an untrusted server");
    }
    connection.disconnect();
    current=next;
    continue;
   }
   return connection;
  }
  throw new IOException("Too many download redirects");
 }
}
