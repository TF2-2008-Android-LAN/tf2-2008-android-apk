$ErrorActionPreference="Stop"
Add-Type -AssemblyName System.Drawing
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$dir=Join-Path $root "source_icons"
New-Item -ItemType Directory -Force -Path $dir|Out-Null
$names=[ordered]@{
 "bot_menu"="BOTS";"bot_close"="CERRAR";"bot_title"="BOTS LOCAL"
 "bot_add"="SUMAR +1";"bot_minus"="RESTAR -1"
 "bot_q0"="CUOTA 0";"bot_q4"="CUOTA 4"
 "bot_q8"="CUOTA 8";"bot_q12"="CUOTA 12"
 "bot_q16"="CUOTA 16";"bot_all"="QUITAR TODOS"
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
  "BOT_ICON "+$entry.Key+" LABEL="+$entry.Value+" PNG="+(Get-Item $png).Length
 }finally{$format.Dispose();$b.Dispose();$border.Dispose();$textBrush.Dispose();$g.Dispose();$bitmap.Dispose()}
}
"BOT_UI_BUTTONS = "+$names.Count+" custom graphics"

