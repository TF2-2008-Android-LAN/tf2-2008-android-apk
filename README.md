# TF2 2008 Android: aplicación e instalador

Repositorio del código del APK de TF2 2008 Android. Los recursos pesados del juego están en un repositorio separado:
https://github.com/TF2-2008-Android-LAN/tf2-2008-android-resources

## Funciones
- Prepara Source Engine y TF2 con confirmación de Android.
- Descarga archivos desde GitHub Releases y reanuda descargas interrumpidas.
- Aplica voces, música, controles e idioma sin botones extra.
- Prepara automáticamente materiales gráficos del HUD Android al pulsar JUGAR.
- Respalda archivos originales y conserva modificaciones personales.

## Código fuente
Carpeta android-app. Código de QA versión 1.0.27.

Se requieren JDK 17, Android Platform 36 y Build Tools 34. La secuencia
android-app/build_pc2_qa.ps1 usa rutas de QA de PC2.

Para generar el APK, proporcionar localmente estos dos archivos en android-app/assets:
Source_Engine_1.17.25.apk y TF2_2008_Game_1.00.apk. No se suben a Git:
sus SHA256 esperados están indicados en BundledApkInstaller.java.

La clave de firma del actualizador antiguo NO está en este repositorio.
Los builds con firma de QA no se instalan encima de la versión estable.

## Estado
Parche de HUD de 40 VTF + 59 VMT y seis pares VTF/VMT para controles táctiles comprobado en Samsung A34 jugando 2Fort
con Heavy. Los seis iconos táctiles de menú, disparo, disparo alternativo, recarga,
salto y agacharse ya están incluidos en el parche v2 (111 archivos).
Probados en Samsung A34 en Heavy, Demoman, Engineer y Spy. La subida de archivos grandes y los
ensayos completos de la versión pública siguen pendientes.
