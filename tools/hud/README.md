# Iconos táctiles originales para HUD Android

Los seis iconos crouch, jump, menu, reload, shoot y shoot_alt
fueron dibujados específicamente para este proyecto, sin copiar
texturas de repositorios externos.

Para regenerarlos en Windows:
1. Ejecutar render_missing_touch_icons.ps1 con PowerShell.
2. Ejecutar build_touch_icons_vtf.py con Python 3.

La salida es un ZIP de 12 recursos VMT/VTF con un manifiesto SHA-256.
Las versiones ya generadas están en el parche del instalador:
android-app/assets/android_hud_compat_v2.zip

El parche también adapta 59 materiales de HUD provenientes de la
instalación original del juego, con 40 texturas compatibles creadas
localmente. Para generar esos archivos se necesita acceso autorizado
a los recursos originales del juego.
