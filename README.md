# AI Video Studio Android

MVP de una app Android para crear Shorts verticales 9:16 a partir de un guion, imágenes y videos MP4.

## Incluye
- Guion en español.
- Voz TTS local del teléfono.
- Importación múltiple de imágenes y MP4.
- Montaje secuencial.
- Conversión a 1080x1920.
- Exportación MP4 a `Películas/AI Video Studio`.

## Compilar en GitHub Actions
1. Sube esta carpeta a un repositorio de GitHub.
2. Usa el workflow incluido en `.github/workflows/build.yml`.
3. El APK aparecerá como artifact `AI-Video-Studio-debug`.

## Nota
Esta primera versión no genera imágenes/video con un modelo IA local. Esa parte se añadirá después; primero dejamos funcionando el editor/exportador Android sin depender de NVIDIA.
