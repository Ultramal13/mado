# Mado — visor de director para Android

## Qué es
App Android (nombre provisorio: **Mado**, 窓 = ventana) que simula cámaras y lentes sobre la cámara del teléfono para componer el encuadre durante location scouting, sin cargar equipo. Referencia funcional: apps de visor de director existentes para iPhone, que tienen versión Android antigua con menos funciones; las alternativas gratuitas probadas por el usuario final eran básicas, con anuncios y con cámaras desactualizadas.

## Contexto y alcance
- Uso inicial: **herramienta de campo**, no producto de pago. Se decidirá comercialización según uso real.
- Usuario final de prueba: contacto audiovisual en Japón. Prepara un listado de herramientas que necesitaría en la app (pendiente; define prioridades de Etapa 2).
- Interfaz en **español, inglés y japonés** desde el inicio.
- Sin anuncios.
- Distribución inicial: APK directo o pruebas cerradas de Google Play.

## Stack propuesto
- Kotlin nativo + CameraX / Camera2 (mejor control de cámara que multiplataforma).
- Datos de cámaras y lentes en **JSON remoto** (agregar una cámara no requiere publicar actualización).
- Textos solo en recursos (`values/`, `values-es/`, `values-ja/`), nada hardcodeado.
- PDF de shotlist con fuente japonesa incrustada (ej. Noto Sans JP); probar antes de lanzar.

## Lógica central
- FOV horizontal = `2 * atan(ancho_activo_mm / (2 * focal_mm))`
- Factor de recuadro = `tan(fov_objetivo/2) / tan(fov_telefono/2)`
- Si el FOV objetivo es más ancho que el del teléfono: mostrar padding.
- Anamórficos: relación de aspecto resultante = relación del sensor × squeeze.
- El FOV depende del **área activa del sensor por modo de grabación**, no solo de la cámara.
- Fijar una sola lente del teléfono a la vez (evitar cambio automático Android que descuadra el cálculo).

## Dispositivos de desarrollo
- Samsung Galaxy S26 Ultra (disponible).
- Segundo teléfono: por definir (el usuario lo informará).
- Segundo teléfono de prueba: Sony Xperia de un amigo (el dueño dice «Z1 III»; el diagnóstico muestra modelo **SO-51B** = Xperia 1 III de NTT Docomo), **Android 13 (API 33)**, parche de seguridad jul 2023. LUX-0 le mostró «no compatible camera: this phone does not allow color control; only lux works»: no es un problema de versión de Android sino de que el Xperia no entrega a apps de terceros el control de color/matrices que LUX-0 exige. Mado no usa eso. Pendiente: leer su JSON de diagnóstico (modelo real, lentes accesibles, focales, zoom) y verificar acceso a todas las lentes vía Camera2 y si el tele tiene zoom continuo o dos focales fijas. Instrucciones y APK en `dist/`. JSON completo recibido (`datos-etapa0/xperia_so51b_camera2.json`): las tres lentes traseras son cámaras independientes que Mado puede abrir directo — id 4 ultra gran angular (2.67 mm, 93.2°, eq. 17 mm), id 2 principal (5.11 mm, 70.8°, eq. 25 mm, nivel FULL) e id 3 tele (10.22 mm, 27.1°, eq. 75 mm, foco mínimo ~50 cm); la cámara 0 las agrupa, el id 5 trasero (2.14 mm, sensor 2.4×1.8 mm) parece de apoyo y el 1 es la frontal. El tele reporta una sola focal (no las dos del 70/105 mm): la de 105 mm no se ve por Camera2. Primeras mediciones (2026-10-28, hechas por el dueño con la pantalla de calibración, 1 por lente, corrección de distorsión por defecto): principal id 2 = 67.0° (teórico 70.8°, −5.4 %), tele id 3 = 25.9° (27.1°, −4.4 %), ultra gran angular id 4 = 99.3° (93.2°, +6.5 %). Datos en `datos-etapa0/mediciones_fov_xperia_so51b.json` y perfil `SO-51B` en `camaras.json`; falta repetirlas y compararlas con la cámara nativa. El id 5 (`5 · 32mm`) no se ha medido.

## Calibración (enfoque híbrido)
1. Perfil calibrado por modelo y **por lente** (medido a mano) para los equipos propios.
2. Modelo sin perfil: modo estimado con datos de Camera2 y aviso de "precisión no verificada".
3. Más adelante: asistente de calibración (objeto de tamaño conocido a distancia conocida).

## Cámaras principales (decidido 2026-10-21)
El usuario define como principales: **Canon EOS R5, Insta360 Luna Ultra y Sony FX3** (en ese orden; `principal_orden` en `assets/camaras.json`). Van primero y la app arranca en la R5; el resto del catálogo queda bajo «más cámaras…». Otros modelos que pida el usuario se agregan después, siempre con fuente. Estado de los datos: R5 y FX3 con sensor oficial y modos de video derivados; Luna Ultra solo con focales equivalentes oficiales (20 y 60 mm), sin mm de sensor: la app supone que son horizontales y deja elegir el aspecto.

## Cámaras iniciales (datos por completar desde fichas oficiales, con fuente)
- Sony A7 (modelo exacto por confirmar)
- Sony FX3 (confirmar que no sea FX30)
- Sony Venice / Venice 2 (por confirmar)
- ARRI Alexa (Mini / Mini LF / 35, por confirmar)
- Canon EOS R5 (modos de video 8K/4K, con o sin crop)
- Insta360 Luna Ultra (lente fija; focales 20 y 60 mm equivalentes reportadas por prensa, verificar con ficha oficial; decidir si se muestran focales de zoom digital)

Esquema de datos: `camaras_esquema.json` (valores numéricos en null a propósito; no inventar datos de memoria).

## Referencia: funciones de una app de visor de director para iOS (investigadas)
Previews de miles de cámaras y lentes, framelines personalizadas, datos de foco mínimo y apertura, esféricas y anamórficas, adaptadores Speedbooster, foto y video, gestor de proyectos con escenas, shotlists en PDF (con metadatos GPS, hora, ángulo), control manual, corrección de color y LUTs en el visor, soporte multicámara del teléfono, Sun Tracking (v6.0.1, julio 2026). Pago único de USD 19.99 en iOS. No se revisó la lista completa de cámaras soportadas.

## Calor y batería (regla dura)
En la primera prueba de LUX-0 (v0.1) capturar RAW a resolución completa cada segundo calentó el S26 Ultra por casi 1 hora. En Mado:
- **Prohibido** capturar RAW o resolución completa de forma periódica o continua. La foto a resolución completa solo ocurre cuando el usuario toca el obturador.
- El preview del visor usa un flujo chico (YUV ~640x480 / preview normal), fps bajos y una sola lente fija.
- Protección térmica obligatoria antes de abrir cualquier cámara: escuchar `PowerManager` thermal status; en estado MODERATE o peor, cerrar la cámara y mostrar aviso hasta que baje. Más pausa automática tras ~90 s sin tocar la pantalla y cámara apagada en pausa/segundo plano.
- Toda operación de cámara en un único hilo, para no dejar cámaras abiertas huérfanas.
- La pantalla de diagnóstico (Etapa 0 inicial) solo lee `CameraCharacteristics`: no abre cámara ni crea sesiones de captura. No cambiar eso sin añadir antes la protección térmica.
- Referencia de implementación: `../LUX-0` (`camera/MeterCamera.kt`, `ui/MeterViewModel.kt`).

## Interfaz: mismo lenguaje visual que LUX-0
Aparato de mano: pantalla negra con monoespaciado, textos cortos en minúscula, escalas de puntos, un solo acento naranja (`#FF5A1F`) para acción principal y alertas, teclas redondeadas, encabezado `mado` + modo + botón ◐ de tema. Temas: claro, oscuro, noche roja (para sets a oscuras) y auto. Paletas en `ui/Theme.kt` (portadas de `../LUX-0/app/src/main/java/cl/lux0/ui/Theme.kt`; spec en `../LUX-0/docs/2026-10-19-lux0-diseno.md` §6). Textos siempre en recursos es/en/ja (LUX-0 usa un helper `tr()` hardcodeado; Mado no).

## Etapas
- **Etapa 0 (1-2 sem)**: prueba de precisión. App mínima que lee sensor y focal por Camera2 y compara con mediciones reales del FOV de cada lente. Si falla, replantear antes de seguir.
- **Etapa 1 (3-5 sem), MVP**: selección de cámara, lente y aspecto; recuadro sobre el preview con padding; cámara personalizada (sensor + crop); JSON remoto con 20-30 cámaras actuales; captura de foto con metadata; textos en es/en/ja.
- **Etapa 2**: proyectos y escenas, notas, shotlist PDF, y herramientas según el listado del contacto en Japón (candidatas: kits de lentes, anamórficos, video, LUTs, Sun Tracking).
- **Etapa 3**: pruebas en campo en Japón, revisión de terminología audiovisual en japonés, decisión de comercialización.

## Estado de la Etapa 0 (2026-10-21)
- Hecho: app `app.mado` (Kotlin + Compose) con diagnóstico Camera2 y prueba de FOV (desde v0.2.2 la app abre en el visor; calibración y diagnóstico están en el menú ☰) (preview 640x480, una lente, protección térmica, vigilante anti-cámara-huérfana). Datos y capturas en `datos-etapa0/`.
- S26 Ultra: las 4 lentes traseras transmiten (ids 2, 5, 6, 7; la 0 es la lógica). 5/6/7 se piden como streams físicos de la 0.
- Medición manual, lente 5 (principal), 2 montajes: FOV real ≈ 71.8° vs 74.0° teórico (−3 %). Candidato a perfil calibrado (factor ~0.96 sobre tan(FOV/2), focal efectiva ~6.77 mm). Ver `datos-etapa0/mediciones_fov_manuales.json`.
- Pendiente (más adelante): repetir con corrección de distorsión apagada, medir lentes 2, 6 y 7, y decidir si el FOV se calibra por lente. Mientras tanto, lentes sin medir = "precisión no verificada".
- Los `Log.i("mado", ...)` de depuración en `FovScreen.kt` y `FovCamera.kt` se pueden quitar al cerrar la etapa.

## Logo (decidido 2026-10-21)
Opción J: anillo de cinco rayas (el cero en Morse, como LUX-0) con un recuadro naranja 3:2 en el centro. LUX-0 es la luz (punto), Mado es la ventana (recuadro). Fondo negro `#0B0B0B`, rayas `#F0F0F0`, recuadro `#FF5A1F`; variantes claro y noche roja. Origen del concepto: descomposición del kanji 窓 (宀 techo = marco, 八 = cono de visión, 心 = punto naranja); el recuadro pasó de 2.39:1 a 3:2 porque plano parecía un signo menos.
- Generador: `tools/make_icon.py` (vectores Android adaptativos + monocromo, y maestros SVG/PNG en `docs/branding/`).
- Pendiente: que alguien que lea japonés revise la lectura del kanji antes de usarlo en comunicación.

## Pendientes
- Rediseño del visor (hecho en v0.2.3-beta, 2026-10-22): imagen a pantalla completa, teclas flotantes y panel de opciones plegable (▲); en horizontal las teclas van en columna al costado. Falta probarlo en un teléfono real de pantalla chica (el Xperia) y afinar según uso.
- Confirmar disponibilidad del nombre Mado (Play Store, App Store, marcas, dominio, redes) y que suene bien en japonés.
- Recibir listado de herramientas del contacto en Japón.
- Modelos exactos de cámaras que usa el contacto y modos de grabación habituales; lentes que maneja.
- Definir segundo teléfono de desarrollo.
- Evitar copiar nombre, logo o diseño de otras apps de visor. Registrar fuente de cada dato de cámara.

## Primer paso sugerido en Claude Code
Crear el proyecto Android (Kotlin, CameraX), agregar pantalla de diagnóstico que liste las cámaras expuestas por Camera2 en el S26 Ultra (id, focal, tamaño de sensor, FOV calculado) y guardar el resultado como base de la Etapa 0.
