$ErrorActionPreference="Stop"
Add-Type -AssemblyName System.Drawing
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$dir=Join-Path $root "source_icons"
New-Item -ItemType Directory -Force -Path $dir|Out-Null
$names=[ordered]@{
 "voice_tab_z"="Z";"voice_tab_x"="X";"voice_tab_c"="C";"voice_close"="CERRAR"
 "voice_z_0"="1 MEDICO";"voice_z_1"="2 GRACIAS"
 "voice_z_2"="3 VAMOS";"voice_z_3"="4 AVANCEN"
 "voice_z_4"="5 IZQUIERDA";"voice_z_5"="6 DERECHA"
 "voice_z_6"="7 SI";"voice_z_7"="8 NO"
 "voice_x_0"="1 VIENEN";"voice_x_1"="2 ESPIA"
 "voice_x_2"="3 SENTRY";"voice_x_3"="4 TELE AQUI"
 "voice_x_4"="5 DISP. AQUI";"voice_x_5"="6 SENTRY AQUI"
 "voice_x_6"="7 UBER";"voice_x_7"="8 CARGA LISTA"
 "voice_c_0"="1 AYUDA";"voice_c_1"="2 GRITO"
 "voice_c_2"="3 ALEGRIA";"voice_c_3"="4 ABUCHEO"
 "voice_c_4"="5 POSITIVO";"voice_c_5"="6 NEGATIVO"
 "voice_c_6"="7 BUEN TIRO";"voice_c_7"="8 BUEN TRABAJO"
}
foreach($entry in $names.GetEnumerator()){
 $bitmap=[System.Drawing.Bitmap]::new(256,256,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
 $g=[System.Drawing.Graphics]::FromImage($bitmap)
 $b=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(217,28,33,34))
 $border=[System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(227,221,211,193),6)
 $textBrush=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255,245,238,223))
 $format=[System.Drawing.StringFormat]::new()
 $format.Alignment=[System.Drawing.StringAlignment]::Center
 $format.LineAlignment=[System.Drawing.StringAlignment]::Center
 try{
  $g.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.TextRenderingHint=[System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
  $g.Clear([System.Drawing.Color]::Transparent)
  $g.FillRectangle($b,8,8,240,240)
  $g.DrawRectangle($border,8,8,240,240)
  $size=43.0
  if($entry.Value.Length -gt 11){$size=26.0}
  elseif($entry.Value.Length -gt 8){$size=32.0}
  elseif($entry.Value.Length -gt 5){$size=37.0}
  $font=[System.Drawing.Font]::new("Segoe UI Semibold",[float]$size,[System.Drawing.FontStyle]::Bold,[System.Drawing.GraphicsUnit]::Pixel)
  try{
   $g.DrawString($entry.Value,$font,$textBrush,[System.Drawing.RectangleF]::new(12,12,232,232),$format)
  }finally{$font.Dispose()}
  $png=Join-Path $dir ($entry.Key+".png")
  $bitmap.Save($png,[System.Drawing.Imaging.ImageFormat]::Png)
  $pixels=$bitmap.LockBits([System.Drawing.Rectangle]::new(0,0,256,256),[System.Drawing.Imaging.ImageLockMode]::ReadOnly,
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  try{
   if($pixels.Stride -ne 1024){throw "Unexpected bitmap stride"}
   $bgra=New-Object byte[] (256*256*4)
   [Runtime.InteropServices.Marshal]::Copy($pixels.Scan0,$bgra,0,$bgra.Length)
   [IO.File]::WriteAllBytes((Join-Path $dir ($entry.Key+".bgra")),$bgra)
  }finally{$bitmap.UnlockBits($pixels)}
  "VOICE_ICON "+$entry.Key+" LABEL="+$entry.Value+" PNG="+(Get-Item $png).Length
 }finally{$format.Dispose();$b.Dispose();$border.Dispose();$textBrush.Dispose();$g.Dispose();$bitmap.Dispose()}
}
"VOICE_MENUS_COMPLETED: 3 categories + 24 original commands + CLOSE = "+$names.Count+" custom graphics"
