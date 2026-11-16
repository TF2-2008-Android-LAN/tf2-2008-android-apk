$ErrorActionPreference='Stop'
$project=Split-Path -Parent $MyInvocation.MyCommand.Path
$toolRoot='C:\TF2AndroidLAN\BuildToolsPortable'
$javaBin=Get-ChildItem "$toolRoot\jdk17" -Filter javac.exe -File -Recurse | Select-Object -First 1 -ExpandProperty DirectoryName
if(-not $javaBin){throw "JDK 17 missing"}
$env:JAVA_HOME=Split-Path -Parent $javaBin
$env:PATH="$javaBin;$env:PATH"
$bt="$toolRoot\buildtools\android-14"
$androidJar="$toolRoot\platform\android-36\android.jar"
$build=Join-Path $project 'build'
$classes=Join-Path $build 'classes'
$dex=Join-Path $build 'dex'
$flatDir=Join-Path $build 'res_compiled'
New-Item -ItemType Directory -Force $classes,$dex,$flatDir,(Join-Path $project 'outputs') | Out-Null
function CheckNative([string]$name){
 if($LASTEXITCODE -ne 0){throw "$name failed with code $LASTEXITCODE"}
}
Write-Output 'STEP 1/7 COMPILE_JAVA'
$java=Get-ChildItem (Join-Path $project 'src\com\tf2008\updater') -Filter '*.java' -File|ForEach-Object {$_.FullName}
& "$javaBin\javac.exe" -encoding UTF-8 -Xlint:-options -source 8 -target 8 -cp $androidJar -d $classes $java
CheckNative 'javac'
Write-Output 'STEP 2/7 DEX'
$classFiles=Get-ChildItem $classes -Filter '*.class' -Recurse -File|ForEach-Object {$_.FullName}
& "$bt\d8.bat" --lib $androidJar --min-api 26 --output $dex $classFiles
CheckNative 'd8'
Write-Output 'STEP 3/7 RESOURCES'
& "$bt\aapt2.exe" compile --dir (Join-Path $project 'res') -o $flatDir
CheckNative 'aapt2 compile'
$flat=Get-ChildItem $flatDir -Filter '*.flat' -File|ForEach-Object {$_.FullName}
& "$bt\aapt2.exe" link --manifest (Join-Path $project 'AndroidManifest.xml') -I $androidJar --min-sdk-version 26 --target-sdk-version 36 -R $flat -o (Join-Path $build 'manifest_only.apk')
CheckNative 'aapt2 link'
Write-Output 'STEP 4/7 ASSEMBLE_ANDROID_COMPATIBLE_APK'
$py='C:\TF2AndroidLAN\BuildToolsPortable\python312\python.exe'
if(-not (Test-Path $py)){throw 'Missing Python ZIP packager'}
& $py (Join-Path $project 'assemble_correct_zip.py') $project
CheckNative 'Python zipfile APK assembly'
$unaligned=Join-Path $build 'unaligned.apk'
Write-Output 'STEP 5/7 ZIPALIGN'
$aligned=Join-Path $build 'aligned.apk'
& "$bt\zipalign.exe" -f -p 4 $unaligned $aligned
CheckNative 'zipalign'
& "$bt\zipalign.exe" -c -p 4 $aligned
CheckNative 'zipalign check'
Write-Output 'STEP 6/7 SIGN_QA_ONLY'
$keystore=Join-Path $build 'qa_signing_do_not_release.jks'
if(-not (Test-Path $keystore)){
 & "$javaBin\keytool.exe" -genkeypair -keystore $keystore -alias qa_bootstrap -keyalg RSA -keysize 3072 -validity 365 -storepass changeit -keypass changeit -dname 'CN=TF2 2008 Isolated QA, O=Local Test Only' -noprompt
 CheckNative 'keytool'
}
$out=Join-Path $project 'outputs\TF2_2008_Instalador_GitHub_HUD_v1.0.28_ISOLATED_QA.apk'
& "$bt\apksigner.bat" sign --ks $keystore --ks-key-alias qa_bootstrap --ks-pass pass:changeit --out $out $aligned
CheckNative 'apksigner sign'
& "$bt\apksigner.bat" verify --verbose $out
CheckNative 'apksigner verify'
Write-Output 'STEP 7/7 VERIFY_APK_CONTENT'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z=[IO.Compression.ZipFile]::OpenRead($out)
try{
 foreach($n in @('assets/Source_Engine_1.17.25.apk','assets/TF2_2008_Game_1.00.apk','classes.dex')){
  $entry=$z.GetEntry($n)
  if(-not $entry){throw "Missing $n"}
  Write-Output "ENTRY=$n LENGTH=$($entry.Length) COMPRESSED=$($entry.CompressedLength)"
 }
}finally{$z.Dispose()}
$stat=Get-Item $out
Write-Output "FINAL_QA_APK=$out SIZE=$($stat.Length) SHA256=$((Get-FileHash $out -Algorithm SHA256).Hash.ToLower())"
Write-Output 'QA build only: PC1 release signing key is missing on PC2. Do not install over v1.0.19.'
