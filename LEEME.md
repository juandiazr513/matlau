# MatLau: versión independiente

Esta carpeta es la app completa. No depende de Claude ni de ningún servidor propio.

## Qué contiene
- `index.html`: la app.
- `manifest.webmanifest` y `sw.js`: la hacen instalable y le permiten funcionar sin internet.
- `fonts/`: tipografías incluidas (no se descargan de internet).
- `icons/`: íconos para Android, iPhone y el navegador.

## Diferencias con la versión en Claude
- El progreso se guarda **en el dispositivo**, no en una cuenta.
- Para respaldarlo o pasarlo a otro dispositivo: Panel del adulto › Ajustes › **Copia de seguridad**.
- No recolecta datos personales ni tiene publicidad.

## Probarla en tu computador
No funciona bien abriendo el archivo con doble clic (el modo sin internet necesita un servidor).
En esta carpeta, ejecuta:

    python3 -m http.server 8080

y abre http://localhost:8080

## Siguiente paso (paso 2)
Publicar la carpeta en internet (GitHub Pages, Netlify o Firebase Hosting) para obtener una dirección https.
Esa dirección es la que se usa en pwabuilder.com para generar la app de Android.

## Al actualizar la app
Sube los archivos nuevos y cambia `VERSION` en `sw.js` (por ejemplo, `matlau-v2`), para que los dispositivos descarguen la versión nueva.
