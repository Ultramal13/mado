<p align="center">
  <img src="docs/banner.svg" alt="Mado — visor de director para Android" width="100%">
</p>

<p align="center"><b>Español</b> · <a href="README.en.md">English</a> · 日本語: en la app</p>

# Mado 窓

**Visor de director para Android.** Simula cámaras y lentes sobre la cámara de tu teléfono para componer el encuadre durante *location scouting*, sin cargar equipo. *Mado* (窓) significa ventana.

Elige una cámara, un modo de grabación y una focal, y Mado dibuja sobre el visor el recuadro que esa cámara vería. Si el campo de la cámara es más ancho que el del teléfono, muestra el padding. Compañero de [LUX-0](https://github.com/Ultramal13/lux-0), el medidor de luz.

> **Estado: en desarrollo, uso de campo.** Funciona y está en pruebas con un Galaxy S26 Ultra. Falta validar la precisión en más lentes y teléfonos. Todavía no hay versión publicada.
>
> Interfaz en **español, inglés y japonés** (la revisión del japonés está pendiente).

---

## Capturas

| Marco (vertical) | Foto con los recuadros del kit | Plano recortado (storyboard) | Diagnóstico de cámaras |
|---|---|---|---|
| <img src="docs/capturas/marco-vertical.png" width="200"> | <img src="docs/capturas/foto-con-encuadre.jpg" width="250"> | <img src="docs/capturas/plano-storyboard.jpg" width="190"> | <img src="docs/capturas/diagnostico.png" width="200"> |

| Marco (horizontal, el visor ocupa todo el alto) | Prueba de FOV con regla teórica |
|---|---|
| <img src="docs/capturas/marco-horizontal.png" width="480"> | <img src="docs/capturas/fov-regla.png" width="480"> |

*Las capturas son del emulador de Android (escena virtual); la app también corre en un teléfono real.*

---

## Qué hace hoy

- **Visor (pantalla de inicio):** dibuja sobre el visor los recuadros de **todas las focales de tu kit a la vez** (por ejemplo 24, 35, 50 y 85), con la actual en naranja y sin oscurecer lo de afuera. Sensor, modo de grabación y anamórfico (el *squeeze* suma al aspecto) salen de la cámara elegida. Si el campo de la focal más ancha supera al del teléfono, la imagen del teléfono se achica y ese recuadro llena el visor.
- **Visor a pantalla completa:** la imagen ocupa todo el ancho (vertical) o todo el alto (horizontal). Arriba a la izquierda, la cámara y el modo elegidos: **tocarlos abre la configuración** (cámara, modo, lentes, kit, ubicación). Abajo, **◀ focal ▶** y el obturador. La cámara usa una proporción parecida a la de tu pantalla, así que la imagen la llena casi toda. **Doble toque en la imagen** alterna la vista recortada: el encuadre actual llena el visor, «como saldría», con cámara, modo, focal y FOV H/V encima. Los recuadros son siempre horizontales, también con el teléfono en vertical. **Un toque** enfoca y mide la exposición en ese punto; en el panel hay una **compensación de exposición (EV)**. Un botón grande en el centro inicia la cámara; **■** la detiene y **☰** abre el menú. Pensado para pantallas chicas y de baja resolución.
- **Guías de composición** dentro del encuadre actual: tercios, **proporción áurea**, cruz central, cuadrícula y zonas seguras (se eligen en el panel y se recuerdan) y **también salen en las fotos guardadas**, dentro del marco elegido. El menú ☰ muestra el logo, el nombre y la versión de la app.
- **Una sola lente del teléfono a la vez**, fija, para que Android no cambie de lente sola y descuadre el cálculo.
- **Captura de foto:** cada toma guarda **dos archivos** a resolución completa, con los datos también en el EXIF: la foto **con los recuadros del kit** (la focal actual en naranja, con su medida) y el **plano recortado exactamente al encuadre**, listo para storyboard. Los dos llevan una barra blanca con el logo; a la izquierda el **nombre de la cámara en grande**, el modo de grabación y la lente del teléfono; al centro la **brújula** (rumbo, elevación y nivel) y, si lo activas, la **ubicación**; a la derecha el juego de lentes, el campo de visión **FOV H/V** (horizontal y vertical), la fecha y la **focal en grande**. La ubicación es opcional, pide permiso y se lee una sola vez al disparar.
- **Calibrar lentes** (menú ☰): mide el campo real de cada lente contra el calculado (regla teórica en el visor) y guarda las mediciones.
- **Diagnóstico** (menú ☰): lista las cámaras que expone Camera2 (focal, sensor, FOV) y exporta un JSON que se guarda en *Descargas/Mado*.
- **Perfil calibrado por lente:** la lente principal del S26 Ultra está medida a mano (FOV real ≈ 71.8° frente a 74.0° teórico). Una lente sin perfil muestra «precisión no verificada».
- **Protección térmica:** sin RAW ni resolución completa de forma continua; el preview es un flujo chico; la cámara se apaga sola por inactividad, al salir de la app o si el teléfono se calienta.
- **Temas** claro, oscuro y noche roja, con el mismo lenguaje visual que LUX-0.

---

## Cámaras

Los datos están en [`app/src/main/assets/camaras.json`](app/src/main/assets/camaras.json); **cada modo lleva su fuente**, y `derivado: true` marca los valores que calculamos porque el fabricante no los publica.

| Cámara | Datos |
|---|---|
| Canon EOS R5 · **principal** | sensor oficial; modos de video derivados |
| Insta360 Luna Ultra · **principal** | solo focales equivalentes oficiales (20 y 60 mm) |
| Sony FX3 · **principal** | sensor oficial; video derivado |
| Sony A7 IV | sensor oficial; video derivado |
| Sony Venice 2 (8K y 6K) | tabla oficial de Sony |
| ARRI Alexa Mini LF · Alexa 35 · Alexa Mini · Alexa (Classic y XT) | hojas técnicas oficiales de ARRI |
| RED V-RAPTOR (VV y S35) · KOMODO · KOMODO-X | guías oficiales de RED, tablas de formatos |
| Sony BURANO | tabla «Sensor Modes» de pro.sony |
| Sony FX6 | **estimado**: Sony publica «full-frame 35 mm, 10.2 MP» pero no los mm; se usa el ancho de la FX3 y queda marcado en la app |

La investigación con sus fuentes está en [`datos-camaras/`](datos-camaras/). Hay también cámara personalizada (sensor + focal) y juegos de lentes esféricos y anamórficos.

---

## Descargar

La última versión de prueba está en **[Releases](https://github.com/Ultramal13/mado/releases/latest)**: baja el archivo `.apk` en un teléfono Android 10 o superior y ábrelo (Android pedirá permitir apps de origen desconocido para el navegador o el gestor de archivos). Para medir el campo de visión de tus lentes sigue [`docs/medicion-fov.md`](docs/medicion-fov.md).

El idioma de la app se elige desde el menú **☰** → idioma: automático, español, inglés o japonés.

## Compilar

Requiere JDK 21, Gradle 9.8+ y el SDK de Android (plataforma 37.2). Aún no hay *wrapper* de Gradle en el repo.

```
echo "sdk.dir=/ruta/al/sdk" > local.properties
gradle :app:assembleDebug
```

Mínimo Android 10 (API 29).

## Hoja de ruta

1. **Etapa 0 (hecha):** diagnóstico Camera2 y prueba de precisión del FOV.
2. **Etapa 1 (en curso):** selección de cámara, lente y aspecto; recuadro con padding; cámara personalizada; captura con metadatos; catálogo de cámaras. Falta la descarga remota del catálogo.
3. **Etapa 2:** selector de focal con flechas y focales vecinas a la vez, guías de composición, setups favoritos, galería, proyectos y *shotlist* en PDF.
4. **Etapa 3:** pruebas de campo en Japón y decisión de comercialización.

## Más información

- Decisiones, reglas y estado: [`CLAUDE.md`](CLAUDE.md)
- Ideas de referencia de otras apps de visor: [`docs/ideas-visor-referencia.md`](docs/ideas-visor-referencia.md)
- Ícono y marca: [`docs/branding/`](docs/branding/) y [`tools/make_icon.py`](tools/make_icon.py)
