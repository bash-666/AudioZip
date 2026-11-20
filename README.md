# AudioZip

<p align="center">
  <a href="https://github.com/bash-666/AudioZip/actions"><img src="https://img.shields.io/github/actions/workflow/status/bash-666/AudioZip/build.yml?branch=main&label=compilaci%C3%B3n" alt="Estado de compilación"></a>
  <img src="https://img.shields.io/badge/Android-9.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 9.0 o superior">
  <img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/bash-666/AudioZip" alt="Licencia GPL-3.0"></a>
</p>

<p align="center"><b>Comprime audios por lotes en tu teléfono, sin internet y conservando el nombre original de cada archivo.</b></p>

AudioZip es una app Android gratuita y de código abierto para reducir el peso de tus audios: agrega archivos o carpetas enteras, elige formato y calidad, y exporta todo con el mismo nombre (solo cambia la extensión). Ideal para audios de videojuegos, música y grabaciones.

## ✨ Características

- 📦 **Compresión por lotes** con progreso por archivo
- 🎚️ **Modo manual**: deslizador de bitrate (32–320 kbps) con estimación de tamaño y aviso de si la compresión conviene o pesaría más
- 📁 **Salida a carpeta elegida**: los archivos conservan su nombre, solo cambia la extensión
- 🔌 **Sin internet**: todo se procesa en el teléfono con ffmpeg-kit
- 🎨 **Material 3**, modo oscuro, interfaz en español
- ❓ **Ayuda integrada** que explica qué formato elegir y por qué a veces un archivo pesa más

## 🎧 Formatos y calidad

| Formato | Alta | Media | Baja |
|---|---|---|---|
| MP3 | 320 kbps | 192 kbps | 128 kbps |
| OGG (Vorbis) | q7 | q5 | q3 |
| Opus | 192 kbps | 128 kbps | 96 kbps |
| WAV | sin pérdida (pcm_s16le) | — | — |

Entrada y salida admiten **mp3, ogg, opus y wav**. WAV no comprime: es audio sin pérdida.

## 🚀 Cómo usarla

1. Toca **Agregar audios** o **Agregar carpeta**
2. Elige **formato de salida** y **calidad** (o modo **Manual** con el deslizador)
3. Toca **Comprimir todo**
4. Comparte el resultado o guárdalo en tu carpeta elegida

## 📸 Capturas

<table>
  <tr>
    <td align="center"><img src="docs/screenshot/01-inicio.png" width="200" alt="Pantalla principal"><br>Inicio</td>
    <td align="center"><img src="docs/screenshot/02-manual.png" width="200" alt="Modo manual con slider"><br>Modo manual</td>
    <td align="center"><img src="docs/screenshot/03-ayuda.png" width="200" alt="Sección de ayuda"><br>Ayuda</td>
  </tr>
</table>

## 📥 Instalación

Descarga el APK desde la pestaña **Actions** del repo: abre la compilación más reciente y baja el artefacto. Instálalo en tu teléfono (Android 9.0 o superior) permitiendo orígenes desconocidos.

## 🔨 Compilar desde el código fuente

**Opción 1 — GitHub Actions (recomendado si no tienes Android Studio):**
el workflow `.github/workflows/build.yml` compila el APK en cada push. Descárgalo desde la pestaña **Actions** del repo.

**Opción 2 — Android Studio:**

1. Si compilas en local, copia `gradle-wrapper.jar` de un proyecto con Gradle 9.6.0 a `gradle/wrapper/` (el jar no se incluye en el repo).
2. Abre el proyecto y pulsa **Run**.

## 📄 Licencia

GPL-3.0. Puedes usar, copiar, modificar y distribuir el programa, siempre que las versiones modificadas también sean GPL-3.0 y el código fuente esté disponible.
