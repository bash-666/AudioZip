# AudioZip

Aplicación Android para comprimir audios por lotes: mp3, ogg, opus y wav de entrada y salida, conservando el nombre original de cada archivo.

- Cola por lotes con progreso por archivo
- Salida a una carpeta elegida (los archivos conservan su nombre, solo cambia la extensión)
- Sin internet: todo se procesa en el teléfono con ffmpeg-kit
- Material 3, modo oscuro, español

## Compilar

Opción 1 — GitHub Actions (recomendado si no tienes Android Studio):
el workflow `.github/workflows/build.yml` compila el APK en cada push.
Descárgalo desde la pestaña **Actions** del repo (artefacto `compresor-audio-debug-apk`).

Opción 2 — Android Studio:
1. Si compilas en local, copia `gradle-wrapper.jar` de un proyecto con Gradle 9.6.0 a `gradle/wrapper/` (el jar no se incluye en el repo).
2. Abre el proyecto y pulsa Run.

## Licencia

GPL-3.0.
