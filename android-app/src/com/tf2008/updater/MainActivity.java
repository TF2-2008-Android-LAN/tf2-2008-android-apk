package com.tf2008.updater;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import android.text.InputFilter;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public class MainActivity extends Activity {
 static final int PICK_TREE=40,PICK_ZIP=41,REQUEST_ALL_FILES=42,MAX_OVERLAY=24000000;
 static final String GAME="com.valvesoftware.source.tf_port",ENGINE="com.valvesoftware.source",SERVER_DEFAULT="https://github.com/TF2-2008-Android-LAN/tf2-2008-android-resources/releases/latest/download/";
 final ExecutorService workers=Executors.newSingleThreadExecutor();
 SharedPreferences prefs;
 Uri tree;
 String lang="es",pending="",serverAddress=SERVER_DEFAULT;
 byte[] remoteM,remoteS,remoteZ;
 boolean busy=false,awaitingStorageGrant=false,awaitingBootstrapInstallPermission=false;
 String packageInstallRequested="";
 TextView title,folderLabel,status,subtitle,advancedLabel,serverLabel,footer,languageLabel,playerNameLabel,voiceLabel,inviteInfo;
 Spinner language;
 EditText serverInput,playerNameInput;
 Button pick,update,repair,repairGame,check,importOriginal,play,advancedToggle,savePlayerName;
 LinearLayout layout,advancedBox;
 String tx(String es,String en){return lang.equals("es")?es:en;}
 void report(String s){runOnUiThread(()->status.setText(s));}
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  prefs=getSharedPreferences("tf2008_update_prefs",0);
  lang=prefs.getString("language","es");
  serverAddress=prefs.getString("server",SERVER_DEFAULT).trim();
  // Upgrade cleanup: do not keep outdated addresses from PC1's LAN QA server.
  // Never clear a valid invitation URL that friends already configured.
  if(serverAddress.matches("(?i)^http://192\\.168\\.1\\.63:(18868|18876|18877)/?$") ||
     serverAddress.matches("(?i)^https://[a-z0-9-]+\\.trycloudflare\\.com/i/[A-Za-z0-9_-]{43}/?$")){
   serverAddress=SERVER_DEFAULT;
   prefs.edit().putString("server",SERVER_DEFAULT).apply();
  }
  String f=prefs.getString("tree","");
  if(!f.isEmpty())try{tree=Uri.parse(f);}catch(Exception ignored){}
  createUI();
  labels();
  if(acceptFriendLink(getIntent()))startUpdate(false);
  else if(!isPackageInstalled(ENGINE)||!isPackageInstalled(GAME))bootstrapIfNeeded("update");
  if(getIntent()!=null&&getIntent().getBooleanExtra("checkAssets",false))
   runJob(()->GameAssetInstaller.diagnose(this));
 }
 TextView label(int size){TextView v=new TextView(this);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(0,11,0,11);return v;}
 Button button(Runnable action){Button b=new Button(this);b.setAllCaps(false);layout.addView(b);b.setOnClickListener(v->{if(!busy)action.run();});return b;}
 void createUI(){
  ScrollView scroll=new ScrollView(this);
  scroll.setFillViewport(true);
  scroll.setBackgroundColor(Color.rgb(23,26,32));
  layout=new LinearLayout(this);
  layout.setOrientation(LinearLayout.VERTICAL);
  layout.setPadding(dp(22),dp(22),dp(22),dp(18));
  scroll.addView(layout);
  setContentView(scroll);
  title=label(29);
  title.setTypeface(null,1);
  title.setVisibility(View.GONE);
  layout.addView(title);
  // Original 2008 game logo extracted from materials/logo/tf2_logo.vtf.
  // Aspect ratio 4:1, transparent PNG. Show fallback text if asset unavailable.
  ImageView originalLogo=new ImageView(this);
  try(InputStream logoStream=getAssets().open("tf2_logo_original.png")){
   originalLogo.setImageBitmap(android.graphics.BitmapFactory.decodeStream(logoStream));
   originalLogo.setContentDescription("Team Fortress 2");
   originalLogo.setAdjustViewBounds(true);
   originalLogo.setScaleType(ImageView.ScaleType.FIT_START);
   LinearLayout.LayoutParams brandSize=new LinearLayout.LayoutParams(dp(305),dp(79));
   brandSize.bottomMargin=dp(3);
   layout.addView(originalLogo,brandSize);
  }catch(IOException missingLogo){
   title.setVisibility(View.VISIBLE);
  }
  subtitle=label(14);
  subtitle.setTextColor(Color.rgb(180,187,197));
  layout.addView(subtitle);
  inviteInfo=label(13);
  inviteInfo.setTextColor(Color.rgb(177,212,237));
  layout.addView(inviteInfo);
  play=button(this::playGame);
  play.setMinHeight(dp(65));
  play.setTextSize(21);
  play.setTypeface(null,1);
  play.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(51,133,100)));
  update=button(()->startUpdate(false));
  update.setMinHeight(dp(59));
  update.setTextSize(17);
  update.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(54,87,143)));
  playerNameLabel=label(16);
  layout.addView(playerNameLabel);
  playerNameInput=new EditText(this);
  playerNameInput.setSingleLine(true);
  playerNameInput.setTextColor(Color.WHITE);
  playerNameInput.setTextSize(17);
  playerNameInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
  playerNameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
  playerNameInput.setText(prefs.getString("player_name",""));
  playerNameInput.setSelectAllOnFocus(true);
  layout.addView(playerNameInput);
  savePlayerName=button(this::saveChosenPlayerName);
  savePlayerName.setTextSize(15);
  languageLabel=label(16);
  layout.addView(languageLabel);
  language=new Spinner(this);
  ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"Español (predeterminado)","English"});
  adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  language.setAdapter(adapter);
  language.setSelection(lang.equals("es")?0:1);
  layout.addView(language);
  voiceLabel=label(14);
  voiceLabel.setTextColor(Color.rgb(177,212,237));
  layout.addView(voiceLabel);
  // The voice and menu-music packs are installed by the same ACTUALIZAR action.
  // Keeping separate install buttons only confused first-time users.
  language.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
   public void onItemSelected(android.widget.AdapterView<?> parent,View selected,int index,long id){
    String next=index==0?"es":"en";
    if(!next.equals(lang)){
     lang=next;
     prefs.edit().putString("language",next).apply();
     labels();
     if(storageReady()){
      report(tx("Aplicando el idioma...","Applying language..."));
      saveServer();
      runJob(()->{install(true,false,true);try{VoicePackInstaller.ensure(MainActivity.this,false);}catch(Exception e){throw new RuntimeException(e);}});
     }else report(tx("Idioma elegido. Se aplicará al preparar el juego.",
                    "Language selected. It will be applied during setup."));
    }
   }
  });
  status=label(14);
  status.setTextColor(Color.rgb(174,223,193));
  layout.addView(status);
  advancedToggle=button(()->{
   boolean show=advancedBox.getVisibility()!=View.VISIBLE;
   advancedBox.setVisibility(show?View.VISIBLE:View.GONE);
   advancedToggle.setText(show?tx("Ocultar opciones avanzadas","Hide advanced settings"):
                               tx("Opciones avanzadas","Advanced settings"));
  });
  advancedToggle.setTextSize(13);
  advancedBox=new LinearLayout(this);
  advancedBox.setOrientation(LinearLayout.VERTICAL);
  advancedBox.setVisibility(View.GONE);
  layout.addView(advancedBox);
  folderLabel=label(13);
  advancedBox.addView(folderLabel);
  pick=buttonIn(advancedBox,()->chooseTree(""));
  repair=buttonIn(advancedBox,this::repairConfig);
  repairGame=buttonIn(advancedBox,()->startUpdate(true));
  check=buttonIn(advancedBox,()->{saveServer();runJob(()->checkServer(true));});
  advancedLabel=label(14);
  advancedBox.addView(advancedLabel);
  importOriginal=buttonIn(advancedBox,this::chooseGameZip);
  serverLabel=label(12);
  advancedBox.addView(serverLabel);
  serverInput=new EditText(this);
  serverInput.setSingleLine(true);
  serverInput.setTextColor(Color.WHITE);
  serverInput.setTextSize(12);
  serverInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
  serverInput.setText(serverAddress);
  serverInput.setHint("https://.../i/codigo-de-invitacion/");
  advancedBox.addView(serverInput);
  footer=label(12);
  footer.setTextColor(Color.rgb(169,174,185));
  advancedBox.addView(footer);
  if(!isPackageInstalled(ENGINE)||!isPackageInstalled(GAME))
   report(tx("Preparando instalación única de TF2 2008; Android pedirá confirmar los componentes.",
             "Setting up TF2 2008. Android will request confirmation for its components."));
  else if(!storageReady() && getPackageManager().getLaunchIntentForPackage(GAME)!=null)
   report(tx("Juego detectado. Podés jugar ahora; para actualizar, autorizá la carpeta una sola vez.",
             "Game detected. You can play now; updating needs one-time folder permission."));
  else if(!storageReady())
   report(tx("Para preparar el juego, tocá Actualizar.",
             "Tap Update to set up the game."));
  else report(tx("Configuración lista. Podés jugar o actualizar.","Ready to play or update."));
 }


 // A friend opens the setup link from the same web page as the APK.
 // Never treat arbitrary URL input as a trusted update source.
 boolean acceptFriendLink(Intent intent){
  if(intent==null||!Intent.ACTION_VIEW.equals(intent.getAction()))return false;
  Uri incoming=intent.getData();
  if(incoming==null||!"tf2friends".equalsIgnoreCase(incoming.getScheme())||
     !"setup".equalsIgnoreCase(incoming.getHost()))return false;
  String base=incoming.getQueryParameter("base");
  try{
   if(base==null||base.length()>256)throw new SecurityException("Missing invite");
   Uri endpoint=Uri.parse(base);
   String host=endpoint.getHost(),p=endpoint.getPath();
   if(!"https".equalsIgnoreCase(endpoint.getScheme())||host==null||
      !(host.matches("(?i)^[a-z0-9-]+\\.trycloudflare\\.com$") ||
        host.matches("(?i)^tf2-2008-resources\\.[a-z0-9-]+\\.workers\\.dev$") ||
        host.equalsIgnoreCase("github.com"))||
      endpoint.getPort()!=-1||
      endpoint.getUserInfo()!=null||
      endpoint.getQuery()!=null||endpoint.getFragment()!=null||
      p==null||!(p.matches("^/i/[A-Za-z0-9_-]{43}/$") ||
        (host.equalsIgnoreCase("github.com") &&
         (p.matches("^/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/download/[A-Za-z0-9_.-]+/$") ||
          p.matches("^/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/latest/download/$")))))
       throw new SecurityException("Invalid invite address");
   serverAddress=endpoint.toString();
   serverInput.setText(serverAddress);
   prefs.edit().putString("server",serverAddress).apply();
   labels();
   report(tx("Enlace de tus amigos aceptado. Preparando TF2...",
             "Friend invite accepted. Preparing TF2..."));
   intent.setData(null);
   return true;
  }catch(Exception invalid){
   report(tx("El enlace de invitación no es válido. Pedí uno nuevo.",
             "Invalid invite link. Ask for a new one."));
   return false;
  }
 }
 @Override protected void onNewIntent(Intent intent){
  super.onNewIntent(intent);
  setIntent(intent);
  if(acceptFriendLink(intent)&&!busy)startUpdate(false);
 }
 boolean hasAllFilesAccess(){
  return Build.VERSION.SDK_INT>=30 && Environment.isExternalStorageManager();
 }
 boolean storageReady(){return hasAllFilesAccess()||tree!=null;}
 File ensureGameRoot() throws Exception {
  if(!hasAllFilesAccess())throw new SecurityException("Access to shared storage not granted");
  File storage=Environment.getExternalStorageDirectory().getCanonicalFile();
  File dir=new File(storage,"srceng").getCanonicalFile();
  if(!dir.getParentFile().equals(storage))throw new SecurityException("Unexpected game root");
  if(dir.exists()&&!dir.isDirectory())throw new IOException("srceng exists but is not a folder");
  if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create srceng automatically");
  return dir;
 }
 void afterStoragePermission(String action){
  saveServer();
  if(action.equals("import"))chooseGameZip();
  else if(action.equals("playerName"))runJob(()->applyPlayerNameFromPreferences());
  else if(action.equals("playWithName"))playGame();
  else if(action.equals("repairConfig"))runJob(()->install(true,false,true));
  else if(action.equals("repair"))runJob(()->install(true,true));
  else if(action.equals("update"))runJob(()->install(false));
  else report(tx("Carpeta srceng preparada.","srceng folder ready."));
 }
 void requestStorage(String action){
  // Exactly one Android permission screen; no preliminary in-app dialog.
  if(Build.VERSION.SDK_INT<30){chooseTree(action);return;}
  pending=action;
  awaitingStorageGrant=true;
  report(tx("Autorizá una vez el acceso a archivos en Android. srceng se creará sola.",
            "Enable file access once in Android. srceng is created automatically."));
  try{
   Intent intent=new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:"+getPackageName()));
   startActivityForResult(intent,REQUEST_ALL_FILES);
  }catch(android.content.ActivityNotFoundException unavailable){
   try{
    startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                           REQUEST_ALL_FILES);
   }catch(Exception error){
    awaitingStorageGrant=false;
    pending="";
    report(tx("Este Android no admite el permiso directo. Usá Cambiar carpeta en Opciones avanzadas.",
              "Direct permission unavailable. Use Change game folder in Advanced settings."));
   }
  }
 }
 void completeStoragePermission(){
  if(!awaitingStorageGrant)return;
  awaitingStorageGrant=false;
  if(!hasAllFilesAccess()){
   pending="";
   report(tx("Permiso no otorgado. Tocá Actualizar para reintentar o Cambiar carpeta en Opciones avanzadas.",
             "Permission not granted. Tap Update to retry or Change game folder in Advanced settings."));
   return;
  }
  String action=pending;pending="";
  try{
   ensureGameRoot();
   labels();
   afterStoragePermission(action);
  }catch(Exception e){report(tx("No se pudo crear srceng: ","Could not create srceng: ")+e.getMessage());}
 }
 @Override protected void onResume(){
  super.onResume();
  if(awaitingStorageGrant)completeStoragePermission();
  if(awaitingBootstrapInstallPermission){
   awaitingBootstrapInstallPermission=false;
   if(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls()){
    if(!packageInstallRequested.isEmpty())
     openBundledInstallerFromCache(packageInstallRequested);
   }else{
    report(tx("Permití instalar aplicaciones desde TF2 2008 para continuar.",
              "Allow TF2 2008 to install apps to continue."));
   }
   return;
  }
  // The system package installer returned after the user's confirmation.
  // Do not reopen automatically if the user cancelled.
  if(!busy&&!packageInstallRequested.isEmpty()){
   String completed=packageInstallRequested;
   packageInstallRequested="";
   if(isPackageInstalled(completed)){
    report(tx("Componente instalado. Continuando preparación...",
              "Component installed. Continuing setup..."));
    String action=prefs.getString("bootstrap_action","update");
    if(!bootstrapIfNeeded(action)){
     prefs.edit().remove("bootstrap_action").apply();
     if("play".equals(action))playGame();
     else startUpdate("repair".equals(action));
    }
   }else report(tx("Instalación cancelada. Tocá JUGAR para reintentar.",
                  "Install cancelled. Tap PLAY to retry."));
  }
 }
 boolean bootstrapIfNeeded(String nextAction){
  if(isPackageInstalled(ENGINE)&&isPackageInstalled(GAME))return false;
  final String missing=isPackageInstalled(ENGINE)?GAME:ENGINE;
  prefs.edit().putString("bootstrap_action",nextAction).apply();
  report(tx("Preparando TF2 2008. Android solicitará confirmar la instalación.",
            "Preparing TF2 2008. Android will ask you to approve installation."));
  runJob(()->{
   try{BundledApkInstaller.ensure(this,missing);}
   catch(Exception e){throw new RuntimeException(e.getMessage(),e);}
  });
  return true;
 }
 void openBundledInstallerFromCache(String target){
  if(isPackageInstalled(target))return;
  packageInstallRequested=target;
  if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
   awaitingBootstrapInstallPermission=true;
   report(tx("Permití instalar aplicaciones desde TF2 2008 una sola vez.",
             "Allow TF2 2008 to install apps once."));
   try{
    startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                             Uri.parse("package:"+getPackageName())));
   }catch(Exception error){
    awaitingBootstrapInstallPermission=false;
    report(tx("No se pudo abrir el permiso de instalación: ",
              "Could not open install permission: ")+error.getMessage());
   }
   return;
  }
  BundledApkInstaller.launch(this,target);
 }
 int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
 Button buttonIn(LinearLayout root,Runnable action){
  Button b=new Button(this);
  b.setAllCaps(false);
  root.addView(b);
  b.setOnClickListener(v->{if(!busy)action.run();});
  return b;
 }
 void labels(){
  title.setText("TEAM FORTRESS 2");
  subtitle.setText(tx("Versión 2008 · Android","2008 edition · Android"));
  inviteInfo.setText(serverAddress.isEmpty()?
      tx("Servidor de amigos: sin conectar. Abrí la invitación recibida.",
         "Friends server: not connected. Open the invitation link."):
      tx("Servidor de amigos: conectado por enlace HTTPS.",
         "Friends server: connected using HTTPS invitation."));
  play.setText(tx("JUGAR","PLAY"));
  update.setText(tx("ACTUALIZAR","UPDATE"));
  playerNameLabel.setText(tx("Nombre dentro del juego","In-game name"));
  playerNameInput.setHint(tx("Ej.: Jugador123 (hasta 24 caracteres)","E.g. Player123 (up to 24 characters)"));
  savePlayerName.setText(tx("Guardar nombre","Save name"));
  languageLabel.setText(tx("Idioma del juego (textos y voces)","Game language (text and voices)"));
  voiceLabel.setText(tx("El juego se prepara automáticamente: componentes, voces y 16 canciones de TF2 + Classic. Android pide confirmar instalaciones.",
                        "TF2 prepares its components, voices and 16 TF2 + Classic tracks automatically. Android confirms component installation."));
  advancedToggle.setText(advancedBox.getVisibility()==View.VISIBLE?
      tx("Ocultar opciones avanzadas","Hide advanced settings"):
      tx("Opciones avanzadas","Advanced settings"));
  folderLabel.setText(!storageReady()?tx("Carpeta pendiente de autorizar","Folder not yet authorized"):
                                tx("Acceso al juego autorizado","Game folder access granted"));
  pick.setText(tx("Cambiar carpeta del juego","Change game folder"));
  repair.setText(tx("Reparar controles e idioma","Repair controls and language"));
  repairGame.setText(tx("Reparar archivos del juego (completa)","Repair all game files (full scan)"));
  check.setText(tx("Comprobar actualizaciones","Check for updates"));
  advancedLabel.setText(tx("Instalación inicial (solo si faltan archivos)","Initial setup (only if files are missing)"));
  importOriginal.setText(tx("Importar mi copia de recursos originales","Import my original game resources"));
  serverLabel.setText(tx("Servidor de descargas (HTTPS; se configura con la invitación, no con IP local)",
                          "Download server (HTTPS; set by invitation, not local IP)"));
  footer.setText(tx("El permiso general permite acceso amplio. El actualizador solo modifica srceng. Acceso por carpeta disponible en Opciones avanzadas.",
                    "Broad access allows more than the game folder; this updater only modifies srceng. Folder-only access remains available in Advanced settings."));
 }
 void saveServer(){
  String next=serverInput.getText().toString().trim();
  serverAddress=next;
  prefs.edit().putString("server",next).apply();
  labels();
 }
 void showInviteRequired(){
  new AlertDialog.Builder(this)
   .setTitle(tx("Falta conectar el servidor de amigos","Friends server not connected"))
   .setMessage(tx("Para descargar y actualizar, abrí en este celular la página HTTPS que te compartieron y tocá «Conectar y preparar TF2». No escribas ninguna IP ni puerto.",
                  "To download or update, open your friend's HTTPS invitation page on this phone and tap 'Connect and set up TF2'. No IP address or port needed."))
   .setPositiveButton(tx("Entendido","OK"),null).show();
 }
 void saveChosenPlayerName(){
  String nick=playerNameInput.getText().toString().trim();
  if(!PlayerNameManager.valid(nick)){
   report(tx("Usá de 1 a 24 letras, números, espacios, puntos, guiones o guion bajo.",
             "Use 1–24 letters, numbers, spaces, dots, hyphens or underscores."));
   return;
  }
  // Retain the choice in this APK, even before the first storage permission.
  prefs.edit().putString("player_name",nick).apply();
  if(!storageReady()){requestStorage("playerName");return;}
  runJob(()->applyPlayerNameFromPreferences());
 }
 void applyPlayerNameFromPreferences(){
  try{
   String nick=prefs.getString("player_name","").trim();
   if(nick.isEmpty())return;
   PlayerNameManager.apply(this,nick);
   report(tx("Nombre guardado: ","Name saved: ")+nick+
          tx(". Se aplicará en cada inicio de TF2 y después de actualizar.",
             ". It will load each time TF2 starts and after updating."));
  }catch(Exception e){
   throw new RuntimeException(tx("No se pudo guardar el nombre: ","Could not save name: ")+e.getMessage(),e);
  }
 }
 void runJob(Runnable work){
  if(busy)return;busy=true;
  runOnUiThread(()->{update.setEnabled(false);repair.setEnabled(false);repairGame.setEnabled(false);check.setEnabled(false);importOriginal.setEnabled(false);play.setEnabled(false);language.setEnabled(false);savePlayerName.setEnabled(false);playerNameInput.setEnabled(false);});
  workers.execute(()->{
   try{work.run();}catch(Throwable e){android.util.Log.e("TF2_2008_Updater","Task failure",e);report(tx("Error: ","Error: ")+e.getMessage());}
   finally{busy=false;runOnUiThread(()->{update.setEnabled(true);repair.setEnabled(true);repairGame.setEnabled(true);check.setEnabled(true);importOriginal.setEnabled(true);play.setEnabled(true);language.setEnabled(true);savePlayerName.setEnabled(true);playerNameInput.setEnabled(true);});}
  });
 }
 void chooseTree(String next){
  pending=next;
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
  i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
  try { i.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
      DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:srceng")); }
  catch(Exception ignored) { /* Other file providers may not expose the standard primary root. */ }
  startActivityForResult(i,PICK_TREE);
 }
 void chooseGameZip(){
  if(!storageReady()){requestStorage("import");return;}
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);
  startActivityForResult(i,PICK_ZIP);
 }
 // Fast recovery: only the signed touch controls and language profiles.
 // This must never start the large game-asset downloads implicitly.
 void repairConfig(){
  if(!storageReady()){requestStorage("repairConfig");return;}
  new AlertDialog.Builder(this)
    .setTitle(tx("Reparar controles e idioma","Repair controls and language"))
    .setMessage(tx("Restaura los controles y los textos oficiales de esta versión. No descarga mapas ni gráficos. Se conserva una copia de lo que se cambie.",
                   "Restores base touch controls and translations. Does not download maps or graphics. Changed files are backed up."))
    .setPositiveButton(tx("Reparar","Repair"),(d,w)->runJob(()->install(true,false,true)))
    .setNegativeButton(tx("Cancelar","Cancel"),null).show();
 }
 void startUpdate(boolean force){
  // The single initial TF2 APK includes both Source and the game launcher.
  // Those are installed first, only with Android's own confirmation screens.
  if(bootstrapIfNeeded(force?"repair":"update"))return;
  saveServer();
  if(serverAddress.isEmpty()){showInviteRequired();return;}
  if(!storageReady()){
   requestStorage(force?"repair":"update");
   return;
  }
  if(hasAllFilesAccess()){
   try{ensureGameRoot();}catch(Exception e){report(tx("Error al preparar srceng: ","Error preparing srceng: ")+e.getMessage());return;}
  }
  if(force){
   new AlertDialog.Builder(this)
      .setTitle(tx("Reparación completa del juego","Full game repair"))
      .setMessage(tx("Se comprobarán todos los archivos del juego y se descargarán hasta 2,6 GB para reparar los que estén dañados. Puede ocupar varios GB adicionales temporalmente. No se borrarán tus configuraciones personales.",
                     "All game resources will be checked and up to 2.6 GB downloaded. This may temporarily require several GB of extra storage. Your personal settings will be preserved."))
      .setPositiveButton(tx("Reparar todo","Repair all"),(d,w)->runJob(()->install(true,true)))
      .setNegativeButton(tx("Cancelar","Cancel"),null).show();
  } else runJob(()->install(false));
 }
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(result!=RESULT_OK||data==null||data.getData()==null)return;
  if(req==REQUEST_ALL_FILES)return;
  Uri uri=data.getData();
  if(req==PICK_TREE){
   try{
    int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    getContentResolver().takePersistableUriPermission(uri,flags);
    tree=uri;prefs.edit().putString("tree",uri.toString()).apply();labels();
    String next=pending;pending="";
    afterStoragePermission(next);
   }catch(Exception e){report("Folder permission: "+e.getMessage());}
  } else if(req==PICK_ZIP)runJob(()->importZip(uri));
 }
 Uri root() throws Exception{
  if(tree==null)throw new IOException("Choose srceng folder");
  return DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
 }
 Uri child(Uri parent,String name,String mime,boolean create)throws Exception{
  Uri listing=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(parent));
  String[] cols={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME};
  try(Cursor c=getContentResolver().query(listing,cols,null,null,null)){
   if(c==null)throw new IOException("Folder read denied: "+parent);
   while(c.moveToNext())if(name.equals(c.getString(1)))return DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0));
  }
  if(!create)return null;
  Uri created=DocumentsContract.createDocument(getContentResolver(),parent,mime,name);
  if(created==null)throw new IOException("Cannot create "+name);
  return created;
 }
 Uri file(String rel,boolean create)throws Exception{
  if(!(rel.startsWith("tf_port/")||rel.startsWith("hl2/")||rel.startsWith("platform/"))||rel.startsWith("/")||rel.contains("..")||rel.indexOf('\\')>=0)throw new SecurityException("Invalid path "+rel);
  if(hasAllFilesAccess()){
   File base=ensureGameRoot().getCanonicalFile();
   File f=new File(base,rel).getCanonicalFile();
   if(!f.getPath().startsWith(base.getPath()+File.separator))throw new SecurityException("Outside srceng");
   if(f.isDirectory())throw new IOException("Folder blocks file "+rel);
   if(!f.exists()&&create){
    File parent=f.getParentFile();
    if(!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot create "+parent);
    if(!f.createNewFile())throw new IOException("Cannot create "+rel);
   }
   return f.exists()?Uri.fromFile(f):null;
  }
  String[] parts=rel.split("/");Uri d=root();
  for(int i=0;i<parts.length-1;i++){d=child(d,parts[i],DocumentsContract.Document.MIME_TYPE_DIR,create);if(d==null)return null;}
  return child(d,parts[parts.length-1],"application/octet-stream",create);
 }
 static byte[] read(InputStream in,int limit)throws Exception{
  try(InputStream stream=in){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n,total=0;
   while((n=stream.read(buf))!=-1){total+=n;if(total>limit)throw new IOException("File too large");b.write(buf,0,n);}return b.toByteArray();}
 }
 static byte[] readEntry(ZipInputStream in,int limit)throws Exception{
  ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n,total=0;
  while((n=in.read(buf))!=-1){total+=n;if(total>limit)throw new IOException("Entry too large");b.write(buf,0,n);}return b.toByteArray();
 }
 byte[] asset(String name)throws Exception{return read(getAssets().open(name),MAX_OVERLAY);}
 static String hash(byte[] data)throws Exception{
  byte[] h=MessageDigest.getInstance("SHA-256").digest(data);StringBuilder b=new StringBuilder();
  for(byte x:h)b.append(String.format(Locale.ROOT,"%02x",x&0xff));return b.toString();
 }
 void signed(byte[] data,byte[] sig)throws Exception{
  PublicKey pub=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(asset("public_key.der")));
  Signature s=Signature.getInstance("SHA256withRSA");s.initVerify(pub);s.update(data);
  if(!s.verify(sig))throw new SecurityException("Bad signature");
 }
 static byte[] download(String s,int limit)throws Exception{
  URL url=new URL(s);
  HttpURLConnection c=ReleaseTransport.open(url,0,4500,10000);
  try{if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());return read(c.getInputStream(),limit);}finally{c.disconnect();}
 }
 void checkServer(boolean show){
  try{
   if(serverAddress==null||serverAddress.isEmpty()){
    if(show)report(tx("Abrí primero el enlace de invitación de tus amigos.",
                      "Open your friend's invitation link first."));
    return;
   }
   byte[] m=download(ReleaseTransport.resource(serverAddress,"manifest.json").toString(),160000),
          sig=download(ReleaseTransport.resource(serverAddress,"manifest.sig").toString(),2048);
   signed(m,sig);
   JSONObject j=new JSONObject(new String(m,StandardCharsets.UTF_8));
   if(!j.getString("product").equals("TF2_2008_ANDROID"))throw new SecurityException("Wrong product");
   String name=j.getString("archive");
   if(name.contains("/")||name.contains("\\")||name.contains(".."))throw new SecurityException("Archive name");
   byte[] z=download(ReleaseTransport.resource(serverAddress,name).toString(),MAX_OVERLAY);
   if(!hash(z).equals(j.getString("archiveSha256"))||z.length!=j.getLong("archiveSize"))throw new SecurityException("Bad archive hash");
   remoteM=m;remoteS=sig;remoteZ=z;
   report(tx("Actualización verificada: ","Verified release: ")+j.getString("version"));
  }catch(Exception e){
   remoteM=remoteS=remoteZ=null;
   if(show)report(tx("Servidor inaccesible; la versión incluida funciona sin conexión.","Server unavailable; bundled version works offline.")+" ("+e.getMessage()+")");
  }
 }
 void install(boolean force){ install(force,false,false); }
 void install(boolean force,boolean repairAssets){install(force,repairAssets,false);}
 void install(boolean force,boolean repairAssets,boolean overlayOnly){
  try{
   if(!overlayOnly && (repairAssets || GameAssetInstaller.needsAnything(this))){
    report(tx("Preparando TF2 2008 por primera vez...","Preparing TF2 2008 for first use..."));
    GameAssetInstaller.ensure(this,repairAssets);
   }
   report(tx("Verificando archivos de actualización...","Verifying update..."));
   if(!force)checkServer(false);
   byte[] m=remoteM!=null?remoteM:asset("manifest.json");
   byte[] sig=remoteS!=null?remoteS:asset("manifest.sig");
   byte[] zip=remoteZ!=null?remoteZ:asset("overlay.zip");
   signed(m,sig);
   JSONObject j=new JSONObject(new String(m,StandardCharsets.UTF_8));
   if(!j.getString("product").equals("TF2_2008_ANDROID"))throw new SecurityException("Wrong product");
   if(zip.length!=j.getLong("archiveSize")||!hash(zip).equals(j.getString("archiveSha256")))throw new SecurityException("Wrong ZIP digest");
   JSONArray listed=j.getJSONArray("files");
   HashMap<String,String> hashes=new HashMap<>();
   for(int i=0;i<listed.length();i++){JSONObject q=listed.getJSONObject(i);hashes.put(q.getString("path"),q.getString("sha256"));}
   HashMap<String,byte[]> zipFiles=new HashMap<>();
   try(ZipInputStream zi=new ZipInputStream(new ByteArrayInputStream(zip))){
    ZipEntry e;while((e=zi.getNextEntry())!=null){
     if(e.isDirectory())continue;
     String path=e.getName();
     if(path.contains("..")||path.contains("\\")||!hashes.containsKey(path))throw new SecurityException("Unexpected "+path);
     byte[] bytes=readEntry(zi,3*1024*1024);
     if(!hash(bytes).equals(hashes.get(path)))throw new SecurityException("Bad file "+path);
     zipFiles.put(path,bytes);
     if(zipFiles.size()>100)throw new IOException("Too many assets");
    }
   }
   if(zipFiles.size()!=listed.length())throw new SecurityException("Incomplete ZIP");
   LinkedHashMap<String,byte[]> plan=new LinkedHashMap<>();
   for(String p:new TreeSet<>(zipFiles.keySet())){
    if(p.startsWith("common/"))plan.put(p.substring(7),zipFiles.get(p));
    else if(p.startsWith("languages/"+lang+"/"))plan.put(p.substring(("languages/"+lang+"/").length()),zipFiles.get(p));
   }
   if(plan.size()<25)throw new IOException("Incomplete profile");
   File backup=new File(getFilesDir(),"backups/"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date()));
   HashMap<String,byte[]> original=new HashMap<>();
   ArrayList<String> done=new ArrayList<>();
   int skip=0,unchanged=0;
   for(Map.Entry<String,byte[]> ent:plan.entrySet()){
    String name=ent.getKey();
    Uri target=file(name,false);
    byte[] old=target==null?null:read(getContentResolver().openInputStream(target),3*1024*1024);
    // Never overwrite a player's custom autoexec settings.
    if(name.endsWith("/autoexec.cfg")&&old!=null&&!Arrays.equals(old,ent.getValue())){skip++;continue;}
    if(old!=null&&Arrays.equals(old,ent.getValue())){unchanged++;continue;}
    original.put(name,old);
    if(old!=null){
     File dst=new File(backup,name);if(!dst.getParentFile().exists()&&!dst.getParentFile().mkdirs())throw new IOException("Backup folder");
     try(FileOutputStream out=new FileOutputStream(dst)){out.write(old);}
    }
   }
   try{
    for(Map.Entry<String,byte[]> ent:plan.entrySet()){
     String name=ent.getKey();
     if(!original.containsKey(name))continue;
     Uri u=file(name,true);
     try(OutputStream out=getContentResolver().openOutputStream(u,"rwt")){if(out==null)throw new IOException("No write access "+name);out.write(ent.getValue());}
     if(!hash(read(getContentResolver().openInputStream(u),3*1024*1024)).equals(hash(ent.getValue())))throw new IOException("Verify failed "+name);
     done.add(name);
     if(done.size()%5==0)report(tx("Aplicando: ","Applying: ")+done.size()+"/"+plan.size());
    }
   }catch(Exception error){
    for(int i=done.size()-1;i>=0;i--){
     String n=done.get(i);byte[] old=original.get(n);
     try{
      Uri target=file(n,false);
      if(target==null)continue;
      if(old==null){if("file".equals(target.getScheme()))new File(target.getPath()).delete();
       else DocumentsContract.deleteDocument(getContentResolver(),target);}
      else try(OutputStream out=getContentResolver().openOutputStream(target,"rwt")){out.write(old);}
     }catch(Exception ex){android.util.Log.e("TF2Update","Rollback issue "+n,ex);}
    }
    throw error;
   }
   prefs.edit().putString("installed_version",j.getString("version")).putString("installed_language",lang).apply();
   // Every update/repair reapplies the preferred in-game name last.
   String selectedName=prefs.getString("player_name","").trim();
   if(!selectedName.isEmpty())PlayerNameManager.apply(this,selectedName);
   // Restore faithful TF2 HUD backgrounds without unsupported PC-only shader effects.
   // Only replace unmodified original VMTs, leave custom HUD files untouched.
   if(!overlayOnly)AndroidHudCompatInstaller.ensure(this);
   // Normal updates and full repairs reinstall Spanish voices after the game files.
   // Quick "repair controls" deliberately remains fast, without large audio downloads.
   boolean spanishAudio=!overlayOnly&&VoicePackInstaller.ensure(this,repairAssets);
   // Game + voices + menu music are updated in one pass (no extra user actions).
   if(!overlayOnly)MenuMusicInstaller.ensure(this);
   if(!overlayOnly&&(!isPackageInstalled(ENGINE)||!isPackageInstalled(GAME)))
    throw new IllegalStateException("Android components must be installed before game assets");
   report(tx("Listo: ","Complete: ")+done.size()+tx(" archivos aplicados, "," files applied, ")+unchanged+tx(" ya correctos, "," already correct, ")+skip+tx(" personales conservados. Reiniciá TF2."," user settings preserved. Restart TF2.")+
       (spanishAudio?tx(" Voces en español listas."," Spanish voices ready."):"")+
       (!overlayOnly?tx(" 16 canciones TF2 + Classic instaladas."," 16 TF2 + Classic tracks installed."):""));
  }catch(Exception e){throw new RuntimeException(e.getMessage(),e);}
 }
 void importZip(Uri source){
  int count=0;long bytes=0;
  try(ZipInputStream zin=new ZipInputStream(getContentResolver().openInputStream(source))){
   ZipEntry e;while((e=zin.getNextEntry())!=null){
    if(e.isDirectory())continue;
    String path=e.getName().replace('\\','/');
    if(!path.startsWith("tf_port/")){
     if(path.startsWith("materials/")||path.startsWith("models/")||path.startsWith("maps/")||path.startsWith("scripts/")||path.startsWith("resource/")||path.startsWith("sound/")||path.startsWith("particles/")||path.startsWith("cfg/"))path="tf_port/"+path;
     else continue;
    }
    if(path.contains("..")||path.endsWith(".exe")||path.endsWith(".dll")||path.contains("/bin/"))continue;
    if(++count>35000)throw new IOException("Too many files");
    Uri dest=file(path,true);
    try(OutputStream out=getContentResolver().openOutputStream(dest,"rwt")){
     byte[] chunk=new byte[32768];int n;
     while((n=zin.read(chunk))!=-1){bytes+=n;if(bytes>6L*1024*1024*1024)throw new IOException("Archive too large");out.write(chunk,0,n);}
    }
    if(count%150==0)report(tx("Importando: ","Importing: ")+count);
   }
   report(tx("Importados ","Imported ")+count+tx(" archivos. Ahora pulsá Actualizar."," files. Press Update next."));
  }catch(Exception e){throw new RuntimeException("ZIP import after "+count+" files: "+e.getMessage(),e);}
 }
 boolean isPackageInstalled(String name){
  try{getPackageManager().getPackageInfo(name,0);return true;}
  catch(android.content.pm.PackageManager.NameNotFoundException missing){return false;}
 }
 void playGame(){
  if(bootstrapIfNeeded("play"))return;
  // Rebuild only the small missing Android HUD materials, without downloading
  // the entire game again. Android asks for file access only once.
  if(!storageReady()){requestStorage("playWithName");return;}
  runJob(()->{
   try{
    AndroidHudCompatInstaller.ensure(this);
    String name=prefs.getString("player_name","").trim();
    if(!name.isEmpty())PlayerNameManager.apply(this,name);
    runOnUiThread(()->{
     Intent intent=getPackageManager().getLaunchIntentForPackage(GAME);
     if(intent==null){
      report(tx("TF2 está instalado, pero el launcher no se abre.",
                "TF2 is installed, but the game launcher could not open."));
     }else startActivity(intent);
    });
   }catch(Exception e){throw new RuntimeException("Could not prepare TF2 HUD: "+e.getMessage(),e);}
  });
 }
 @Override protected void onDestroy(){super.onDestroy();workers.shutdownNow();}
}
