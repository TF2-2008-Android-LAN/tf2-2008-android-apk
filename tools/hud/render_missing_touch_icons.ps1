$ErrorActionPreference="Stop"
Add-Type -AssemblyName System.Drawing
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$out=Join-Path $root "missing_touch_icons_original"
New-Item -ItemType Directory -Path $out -Force | Out-Null
$glyphColor=[System.Drawing.Color]::FromArgb(242,225,226,223)
$accentColor=[System.Drawing.Color]::FromArgb(224,224,225,221)
function MakePen([float]$size){
 $pen=[System.Drawing.Pen]::new($glyphColor,$size)
 $pen.StartCap=[System.Drawing.Drawing2D.LineCap]::Round
 $pen.EndCap=[System.Drawing.Drawing2D.LineCap]::Round
 $pen.LineJoin=[System.Drawing.Drawing2D.LineJoin]::Round
 return $pen
}
function Lines($gr,$pen,[float[]]$pts){
 for($i=0;$i -lt $pts.Length-2;$i+=2){
  $gr.DrawLine($pen,$pts[$i],$pts[$i+1],$pts[$i+2],$pts[$i+3])
 }
}
foreach($kind in @("menu","shoot","shoot_alt","reload","jump","crouch")){
 $bitmap=[System.Drawing.Bitmap]::new(256,256,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
 $g=[System.Drawing.Graphics]::FromImage($bitmap)
 $p=MakePen(13)
 $fine=MakePen(10)
 $strong=MakePen(16)
 $brush=[System.Drawing.SolidBrush]::new($glyphColor)
 try{
  $g.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.CompositingQuality=[System.Drawing.Drawing2D.CompositingQuality]::HighQuality
  $g.Clear([System.Drawing.Color]::Transparent)
  # Draw a clean gray rim in the same style as stock controls.
  # The slot1 icon contains a gun graphic, so DO NOT copy its interior.
  $ring=[System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(220,216,216,214),12)
  try{$g.DrawEllipse($ring,9,9,238,238)}finally{$ring.Dispose()}
  switch($kind){
   "menu" {
    foreach($yy in @(90,127,164)){$g.DrawLine($strong,75,$yy,181,$yy)}
   }
   "shoot" {
    $g.DrawEllipse($fine,98,98,60,60)
    $g.FillEllipse($brush,121,121,14,14)
    $g.DrawLine($p,128,65,128,89)
    $g.DrawLine($p,128,167,128,190)
    $g.DrawLine($p,65,128,89,128)
    $g.DrawLine($p,167,128,190,128)
   }
   "shoot_alt" {
    $g.DrawEllipse($p,86,86,84,84)
    $g.DrawEllipse($fine,105,105,46,46)
    $g.FillEllipse($brush,120,120,16,16)
    $g.DrawArc($fine,71,71,114,114,245,56)
   }
   "reload" {
    $g.DrawArc($strong,73,73,110,110,-52,273)
    Lines $g $strong ([float[]]@(69,126,82,165,120,164))
    $g.DrawLine($fine,122,101,137,101)
    $g.DrawLine($fine,122,127,150,127)
    $g.DrawLine($fine,122,153,137,153)
   }
   "jump" {
    $g.FillEllipse($brush,117,66,22,22)
    Lines $g $p ([float[]]@(128,92,128,138))
    Lines $g $p ([float[]]@(128,109,93,95))
    Lines $g $p ([float[]]@(128,109,163,94))
    Lines $g $p ([float[]]@(128,137,96,169))
    Lines $g $p ([float[]]@(128,137,163,169))
    $g.DrawLine($fine,91,191,110,191)
    $g.DrawLine($fine,146,191,165,191)
   }
   "crouch" {
    $g.FillEllipse($brush,131,73,24,24)
    Lines $g $p ([float[]]@(141,105,111,138,142,152))
    Lines $g $p ([float[]]@(111,137,87,158))
    Lines $g $strong ([float[]]@(142,152,175,169,180,187))
    Lines $g $p ([float[]]@(140,152,112,174,86,180))
    $g.DrawLine($fine,70,189,189,189)
   }
  }
  $dest=Join-Path $out ($kind+".png")
  $bitmap.Save($dest,[System.Drawing.Imaging.ImageFormat]::Png)
  $fmt=$bitmap.LockBits([System.Drawing.Rectangle]::new(0,0,256,256),
    [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  try{
   if($fmt.Stride -ne 1024){throw "Unexpected pixel stride"}
   $bytes=New-Object byte[] (256*256*4)
   [System.Runtime.InteropServices.Marshal]::Copy($fmt.Scan0,$bytes,0,$bytes.Length)
   [System.IO.File]::WriteAllBytes((Join-Path $out ($kind+".bgra")),$bytes)
  }finally{$bitmap.UnlockBits($fmt)}
  "CREATED $kind PNG=$((Get-Item $dest).Length) BGRA=$($bytes.Length)"
 }finally{
  $brush.Dispose();$p.Dispose();$fine.Dispose();$strong.Dispose();$g.Dispose();$bitmap.Dispose()
 }
}
"ALL_SIX_RENDERED"
