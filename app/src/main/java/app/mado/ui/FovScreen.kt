package app.mado.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
import app.mado.data.PhotoAnnotator
import app.mado.data.PhotoSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.mado.R
import kotlinx.coroutines.async
import app.mado.optics.OrientationMath
import app.mado.data.LocationHelper
import app.mado.data.BarInfo
import app.mado.camera.OrientationTracker
import app.mado.camera.FovCamera
import app.mado.camera.ThermalGuard
import app.mado.diagnostics.CameraInfo
import app.mado.diagnostics.DiagnosticsReport
import app.mado.diagnostics.FovResults
import app.mado.optics.Fov
import app.mado.optics.defaultLensIndex
import app.mado.optics.Guide
import app.mado.optics.Framing
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pausa automática sin tocar la pantalla (igual que LUX-0). */
private const val IDLE_MS = 90_000L

/** Tope de funcionamiento continuo: pasado esto la cámara se apaga aunque haya toques. */
private const val MAX_RUN_MS = 5 * 60_000L

internal enum class Reason { USER, IDLE, MAX_RUN }

/** Estado de la última foto, para el botón y el aviso. */
internal sealed interface ShotUi {
    data object Idle : ShotUi
    /** Entre el toque del obturador y el instante en que el sensor expone la foto: hay que mantener quieto el teléfono. */
    data object Working : ShotUi
    /** La foto ya se tomó (se puede mover el teléfono); falta procesarla y guardarla. */
    data object Exposed : ShotUi
    data class Saved(val name: String, val movedDeg: Int = 0) : ShotUi
    data class Error(val message: String) : ShotUi
}

/** Lentes traseras que se pueden fijar una a una (excluye la cámara lógica que las agrupa). */
fun backLenses(report: DiagnosticsReport): List<CameraInfo> =
    report.cameras.filter { it.facing == "back" && !it.isLogical && it.error == null && it.activeSizeMm != null }
        .distinctBy { it.id }

@Composable
fun FovScreen(
    report: DiagnosticsReport,
    results: FovResults,
    onShare: (String) -> Unit,
    /** En horizontal el encabezado va dentro del panel derecho para que el visor use todo el alto. */
    header: (@Composable () -> Unit)? = null,
    /** Si no es null, la pantalla funciona como modo marco (recuadro de otra cámara) en vez de la prueba de FOV. */
    framing: FramingState? = null,
    /** Abre el menú ☰ (solo el visor: ahí el encabezado no existe, la imagen ocupa toda la pantalla). */
    onMenu: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val p = LocalPalette.current
    val lenses = remember(report) { backLenses(report) }
    if (lenses.isEmpty()) {
        BlackScreen { Text(stringResource(R.string.fov_no_lenses), style = mono(12, p.accent)) }
        return
    }

    // Arranca en la lente calibrada de este teléfono; si no hay, en la principal (no en la ultra gran angular).
    var lensIdx by rememberSaveable {
        val profile = framing?.db?.phoneProfiles?.firstOrNull { it.buildModel == Build.MODEL }
        // En el visor se vuelve a la lente del teléfono usada la última vez (si sigue existiendo).
        val lastLens = if (framing != null) ctx.getSharedPreferences("mado", Context.MODE_PRIVATE).getString("lens_id", null) else null
        val savedIdx = lenses.indexOfFirst { it.id == lastLens }
        if (savedIdx >= 0) mutableIntStateOf(savedIdx) else
        mutableIntStateOf(
            defaultLensIndex(
                lenses.map { it.equiv35Mm.firstOrNull() ?: 0.0 },
                lenses.map { l -> profile?.lenses?.any { it.camera2Id == l.id && it.measuredHFovDeg != null } == true },
            ),
        )
    }
    var distortionOff by rememberSaveable { mutableStateOf(false) }
    var reason by remember { mutableStateOf<Reason?>(Reason.USER) } // arranca SIEMPRE detenido
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var holder by remember { mutableStateOf<SurfaceHolder?>(null) }
    // Sube cada vez que Android crea una superficie nueva (p. ej. al girar el teléfono): obliga a reabrir la cámara.
    var surfaceGen by remember { mutableIntStateOf(0) }
    var hasPermission by remember {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    val guard = remember { ThermalGuard(ctx) }
    val camera = remember { FovCamera(ctx) { msg -> error = msg } }
    var lastTouch by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    DisposableEffect(owner) {
        guard.start()
        val obs = LifecycleEventObserver { _, e ->
            // Al salir de la app la cámara se apaga y no vuelve sola.
            if (e == Lifecycle.Event.ON_PAUSE) { android.util.Log.i("mado", "ON_PAUSE"); camera.stop(); reason = Reason.USER }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            guard.stop()
            camera.release()
        }
    }

    val lens = lenses[lensIdx.coerceIn(0, lenses.lastIndex)]
    if (framing != null) LaunchedEffect(lens.id) { ctx.getSharedPreferences("mado", Context.MODE_PRIVATE).edit().putString("lens_id", lens.id).apply() }
    // Exposición (compensación EV): se ajusta desde el panel del visor y vale para el preview y la foto.
    var ev by rememberSaveable { mutableIntStateOf(0) }
    val evInfo = remember(lens.id) { camera.exposureInfo(lens) }
    LaunchedEffect(ev, evInfo) { camera.setExposure(evInfo?.first?.let { ev.coerceIn(it.first, it.last) } ?: 0) }
    // El visor usa una proporción parecida a la de la pantalla (la imagen la llena); la calibración queda en 4:3.
    // Tamaño real de la ventana (no la configuración de Android, que puede quedar desfasada al girar).
    val win = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val screenAspect = maxOf(win.width, win.height).toDouble() / maxOf(1, minOf(win.width, win.height))
    val size = remember(lens, framing != null, screenAspect) { camera.choosePreviewSize(lens, if (framing != null) screenAspect else null) }
    val running = hasPermission && holder != null && reason == null && !guard.hot

    // Al girar el teléfono con la cámara andando, Android no vuelve a orientar el flujo ya abierto: hay que reabrir la sesión
    // para que el preview salga derecho (si no, queda de lado hasta detener e iniciar).
    val displayRotation = LocalView.current.display?.rotation ?: 0
    // Captura rápida (stream de foto en la sesión del preview): activada por omisión; se puede apagar desde el panel.
    var fastOn by remember { mutableStateOf(ctx.getSharedPreferences("mado", Context.MODE_PRIVATE).getBoolean("fast_capture", true)) }
    LaunchedEffect(running, lens.id, distortionOff, retry, surfaceGen, displayRotation, fastOn) {
        android.util.Log.i("mado", "effect running=$running perm=$hasPermission holder=${holder != null} reason=$reason err=$error hot=${guard.hot} lens=${lens.id} surfaceGen=$surfaceGen retry=$retry rot=$displayRotation fast=$fastOn dist=$distortionOff")
        if (running) {
            error = null
            holder!!.setFixedSize(size.width, size.height)
            camera.fastCapture = fastOn
            camera.start(holder!!, lens, size, distortionOff)
        } else {
            camera.stop()
        }
    }
    // Vigilancia de inactividad y de tiempo máximo continuo.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val startedAt = SystemClock.elapsedRealtime()
        lastTouch = startedAt
        while (true) {
            kotlinx.coroutines.delay(1000)
            val now = SystemClock.elapsedRealtime()
            if (now - startedAt > MAX_RUN_MS) { reason = Reason.MAX_RUN; break }
            if (now - lastTouch > IDLE_MS) { reason = Reason.IDLE; break }
        }
    }
    // Un error o calor deja la cámara apagada hasta que el usuario toque iniciar.
    LaunchedEffect(error) { if (error != null) { android.util.Log.w("mado", "error=$error"); reason = Reason.USER } }

    val landscape = win.width > win.height
    val theory = remember(lens, size) {
        val m = lens.activeSizeMm!!
        val w = Fov.croppedWidthMm(m.first, m.second, size.width.toDouble() / size.height)
        Fov.fovDeg(w, lens.focalLengthsMm.first().toDouble())
    }

    // Distancia a la pared: la usa la regla teórica del visor y el cálculo. Cambia a la sugerida al cambiar de lente.
    var distCm by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(lens.id) { distCm = suggestedDistanceCm(theory).toString() }

    // Modo marco: FOV del teléfono = medido (perfil calibrado) o, si no hay, el teórico (precisión no verificada).
    val profileDeg = remember(framing, lens.id) {
        framing?.db?.phoneProfiles?.firstOrNull { it.buildModel == Build.MODEL }
            ?.lenses?.firstOrNull { it.camera2Id == lens.id }?.measuredHFovDeg
    }
    val phoneDeg = profileDeg ?: theory
    val target = framing?.target()
    // En fracciones del ancho y del alto de la pantalla; horizontal en el mundo también con el teléfono en vertical.
    val frame = target?.let { Framing.toScreen(Framing.frame(it, Math.toRadians(phoneDeg), size.width.toDouble() / size.height), size.width.toDouble() / size.height, portrait = !landscape) }
    // Un recuadro por focal del kit (la actual resaltada). El preview se achica según el más ancho (padding).
    val frameDraws = framing?.options().orEmpty().map {
        val hDeg = Math.toDegrees(it.target.hFovRad)
        val vDeg = Math.toDegrees(2.0 * kotlin.math.atan(kotlin.math.tan(it.target.hFovRad / 2.0) / it.target.aspect))
        // La actual lleva su medida en la etiqueta: focal y campo horizontal × vertical.
        val detail = if (it.current) String.format(Locale.ROOT, "%s mm · %.1f° × %.1f°", it.label, hDeg, vDeg) else it.label
        // Siempre horizontales en el mundo: con el teléfono en vertical se reexpresan en los ejes de la pantalla.
        val phoneAspect = size.width.toDouble() / size.height
        FrameDraw(it.label, Framing.toScreen(Framing.frame(it.target, Math.toRadians(phoneDeg), phoneAspect), phoneAspect, portrait = !landscape), it.current, detail)
    }
    // Solo el recuadro actual puede achicar la imagen (padding); los otros del kit que no caben salen del visor.
    val imageScale = Framing.imageScale(frameDraws.firstOrNull { it.current }?.frame)

    // Vista recortada (doble toque en el visor): el encuadre actual llena el visor y se ve «como saldría».
    var cropView by rememberSaveable { mutableStateOf(false) }
    val cropOn = framing != null && cropView && frame != null
    val imgAspect = if (landscape) size.width.toFloat() / size.height else size.height.toFloat() / size.width
    // Fracción del ancho y del alto de la pantalla que ocupa el encuadre actual.
    val cropFw = frameDraws.firstOrNull { it.current }?.frame?.widthFrac?.toFloat() ?: 1f
    val cropFh = frameDraws.firstOrNull { it.current }?.frame?.heightFrac?.toFloat() ?: 1f
    // Recorte por hardware: en la vista recortada el flujo (chico) muestra solo el encuadre, con mucho más detalle que ampliarlo.
    val maxZoom = remember(lens.id) { camera.maxZoom(lens) }
    val camZoom = if (cropOn && cropFw <= 1f && cropFh <= 1f) (1f / maxOf(cropFw, cropFh)).coerceIn(1f, maxOf(1f, maxZoom)) else 1f
    LaunchedEffect(camZoom) { camera.setZoom(camZoom) }
    val surfFracX = if (cropOn) 1f / (cropFw * camZoom) else imageScale
    val surfFracY = if (cropOn) 1f / (cropFh * camZoom) else imageScale
    // Toque en la imagen: enfoca y mide la exposición ahí (fracciones del recuadro del visor → fracciones del flujo).
    val onFocus: (Float, Float) -> Unit = { bu, bv ->
        val su = 0.5f + (bu - 0.5f) / surfFracX; val sv = 0.5f + (bv - 0.5f) / surfFracY
        if (su in 0f..1f && sv in 0f..1f) camera.focusAt(su, sv, displayRotation * 90)
    }
    val cropDraws = if (cropOn) frameDraws.filter { it.current }.map { it.copy(frame = Framing.Frame(1.0, 1.0)) } else frameDraws

    val preview: @Composable (Modifier) -> Unit = { mod ->
        Preview(
            modifier = mod,
            aspect = if (cropOn) imgAspect * cropFw / cropFh else imgAspect,
            running = running,
            onHolder = { holder = it; if (it != null) surfaceGen++ },
            onDestroyed = { camera.stopAndWait() },
            surfaceFracX = surfFracX,
            surfaceFracY = surfFracY,
            corner = if (framing != null) 0.dp else 14.dp,
            overlay = {
                if (framing != null) Box(Modifier.fillMaxSize()) {
                    FrameOverlay(cropDraws, if (cropOn) 1f else imageScale, showTags = !cropOn, guides = framing.guides, delivery = framing.delivery)
                    if (cropOn) {
                        val hDeg = target?.let { Math.toDegrees(it.hFovRad) }
                        val vDeg = target?.let { Math.toDegrees(2.0 * kotlin.math.atan(kotlin.math.tan(it.hFovRad / 2.0) / it.aspect)) }
                        CropInfo(framing, hDeg, vDeg, Modifier.align(Alignment.BottomStart), dDeg = target?.let { Math.toDegrees(Framing.diagonalFovRad(it.hFovRad, it.aspect)) })
                    }
                }
                else Overlay(theory, distCm.toDoubleOrNull(), landscape)
            },
        )
    }

    // Captura (solo modo marco): una toma por toque.
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var shot by remember { mutableStateOf<ShotUi>(ShotUi.Idle) }
    // Pista de una sola vez: el doble toque (vista recortada) no se descubre solo, así que se avisa la primera vez que corre el visor.
    var showHint by remember { mutableStateOf(false) }
    if (framing != null) LaunchedEffect(running) {
        val pr = ctx.getSharedPreferences("mado", Context.MODE_PRIVATE)
        if (running && !pr.getBoolean("hint_dtap", false)) {
            showHint = true
            kotlinx.coroutines.delay(6000L)
            showHint = false
            pr.edit().putBoolean("hint_dtap", true).apply()
        }
    }
    var flashTick by remember { mutableIntStateOf(0) }           // sube en el instante de la toma: destello blanco

    // Orientación de la cámara (rumbo, elevación, nivel): el sensor solo escucha mientras el visor corre en modo marco.
    val tracker = remember { OrientationTracker(ctx) }
    DisposableEffect(running, framing != null) {
        if (running && framing != null) tracker.start()
        onDispose { tracker.stop() }
    }
    val compass = androidx.compose.ui.res.stringArrayResource(R.array.compass_points)
    val elevWord = stringResource(R.string.orient_elev)
    val levelWord = stringResource(R.string.orient_level)
    fun headingText(pose: OrientationMath.Pose?): String? = pose?.let {
        String.format(Locale.ROOT, "%d° %s", it.headingDeg.roundToInt() % 360, compass[OrientationMath.compassIndex(it.headingDeg)])
    }
    fun attitudeText(pose: OrientationMath.Pose?): String? = pose?.let {
        String.format(Locale.ROOT, "%s %+d° · %s %d°", elevWord, it.elevationDeg.roundToInt(), levelWord, abs(it.levelDeg).roundToInt())
    }
    fun orientationText(pose: OrientationMath.Pose?): String? =
        pose?.let { headingText(it) + "  |  " + attitudeText(it) }

    // Ubicación en las fotos: opcional, la activa el usuario, una lectura al disparar.
    val prefs = remember { ctx.getSharedPreferences("mado", Context.MODE_PRIVATE) }
    // La selección (cámara, modo, lentes, focal, kit) y las guías de composición se recuerdan entre usos.
    if (framing != null) {
        val snap = framing.snapshot()
        LaunchedEffect(snap) { prefs.edit().putString("framing", snap).apply() }
        LaunchedEffect(Unit) {
            framing.guides = prefs.getString("guides", "").orEmpty().split(',').mapNotNull { n -> Guide.values().firstOrNull { it.name == n } }.toSet()
        }
        LaunchedEffect(framing.guides) { prefs.edit().putString("guides", framing.guides.joinToString(",") { it.name }).apply() }
    }
    var gpsOn by remember { mutableStateOf(prefs.getBoolean("gps", false) && LocationHelper.hasPermission(ctx)) }
    var gpsDenied by remember { mutableStateOf(false) }
    val gpsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        gpsOn = granted; gpsDenied = !granted; prefs.edit().putBoolean("gps", granted).apply()
    }
    val onGps: (Boolean) -> Unit = { want ->
        if (!want) { gpsOn = false; prefs.edit().putBoolean("gps", false).apply() }
        else if (LocationHelper.hasPermission(ctx)) { gpsOn = true; gpsDenied = false; prefs.edit().putBoolean("gps", true).apply() }
        else gpsLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Textos de las copias con datos: se leen en composición (siguen el idioma vigente).
    val phoneLensShort = stringResource(R.string.frm_phone_lens_short)
    val verifiedText = stringResource(R.string.bar_verified)
    val unverifiedText = stringResource(R.string.bar_unverified)
    val shotWordText = stringResource(R.string.frm_shot_word)
    val takeShot: () -> Unit = takeShot@{
        if (framing == null || !running || shot is ShotUi.Working || shot is ShotUi.Exposed) return@takeShot
        shot = ShotUi.Working
        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)       // «clic» al tocar el obturador
        val rotation = (view.display?.rotation ?: 0) * 90
        val pose = tracker.latest                                       // lectura al disparar
        val locJob = if (gpsOn) scope.async { LocationHelper.current(ctx) } else null
        val meta = JSONObject().apply {
            put("app", "Mado")
            put("time", Instant.now().toString())
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            evInfo?.let { (_, st) -> put("exposure_compensation_ev", ev * st) }
            put("phone_lens", JSONObject().put("camera2_id", lens.id).put("hfov_deg_used", phoneDeg).put("hfov_deg_theory", theory)
                .put("calibrated", profileDeg != null).put("distortion_correction", if (distortionOff) "off" else "default"))
            put("target", JSONObject().put("label", framing.label()).put("kit_focals", org.json.JSONArray(frameDraws.map { it.label })).put("hfov_deg", target?.let { Math.toDegrees(it.hFovRad) })
                .put("aspect", target?.aspect).put("focal_mm", framing.focal.toDoubleOrNull()).put("squeeze", framing.squeeze)
                .put("frame_width_pct", frame?.let { it.widthFrac * 100 }).put("frame_height_pct", frame?.let { it.heightFrac * 100 })
                .put("padding", frame?.padding))
            pose?.let { put("orientation", JSONObject().put("heading_deg_magnetic", it.headingDeg).put("elevation_deg", it.elevationDeg).put("level_deg", it.levelDeg)) }
        }
        val guidesNow = framing.guides                              // las guías elegidas van también en las fotos
        meta.put("guides", org.json.JSONArray(guidesNow.map { it.name }))
        val annotFrames = frameDraws.map { app.mado.data.AnnotFrame(it.label, it.frame, it.current, it.detail) }
        val anglesText = target?.let {
            val v = Math.toDegrees(2.0 * kotlin.math.atan(kotlin.math.tan(it.hFovRad / 2.0) / it.aspect))
            val d = Math.toDegrees(Framing.diagonalFovRad(it.hFovRad, it.aspect))
            String.format(Locale.ROOT, "FOV H %.1f°  V %.1f°  D %.1f°", Math.toDegrees(it.hFovRad), v, d)
        }.orEmpty()
        // Aspecto de la imagen resultante: el de entrega si se eligió uno, si no el del sensor/modo (como «… | 4.6K 3:2 Open Gate | 3:2»).
        val deliveryNow = framing.delivery
        val aspectText = (deliveryNow ?: target?.aspect)?.let { app.mado.optics.aspectLabel(it) }.orEmpty()
        val deliveryLabel = deliveryNow?.let { app.mado.optics.aspectLabel(it) }.orEmpty()
        val sceneNow = framing.scene.trim(); val shotNow = framing.shotNo
        val sceneTagText = sceneNow.takeIf { it.isNotEmpty() }?.let { "$it · $shotWordText $shotNow" }
        meta.put("scene", sceneNow); meta.put("shot_no", shotNow); meta.put("delivery_aspect", deliveryLabel)
        val camText = framing.cameraName(); val modeText = listOf(framing.modeName(), aspectText).filter { it.isNotEmpty() }.joinToString("  ·  "); val lensSetText = framing.subLabel(); val bigText = framing.bigLabel()
        val phoneLine = String.format(Locale.ROOT, "%s %s · %.1f° · %s", phoneLensShort, lens.id, phoneDeg, if (profileDeg != null) verifiedText else unverifiedText)
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(java.util.Date())
        var poseExp: OrientationMath.Pose? = null                   // orientación en el instante real de la toma
        var movedDeg = 0
        camera.capture(rotation, onExposed = {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                // La foto ya se tomó: destello y clic, y desde aquí se puede mover el teléfono.
                shot = ShotUi.Exposed
                flashTick++
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.LONG_PRESS)
                poseExp = tracker.latest
                val p0 = pose; val p1 = poseExp
                if (p0 != null && p1 != null) {
                    val dh = ((p1.headingDeg - p0.headingDeg + 540.0) % 360.0) - 180.0
                    val de = p1.elevationDeg - p0.elevationDeg
                    val dl = p1.levelDeg - p0.levelDeg
                    movedDeg = kotlin.math.sqrt(dh * dh + de * de + dl * dl).roundToInt()
                }
            }
        }) { result ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                when (result) {
                    is FovCamera.Shot.Fail -> shot = ShotUi.Error(result.message)
                    is FovCamera.Shot.Ok -> scope.launch {
                        val posePhoto = poseExp ?: pose                  // la del instante de la toma; si no hubo, la del toque
                        val headText = headingText(posePhoto); val attText = attitudeText(posePhoto)
                        val loc = try { locJob?.await() } catch (e: Exception) { null }
                        meta.put("still", JSONObject().put("width", result.width).put("height", result.height).put("rotation_deg", result.rotationDeg).put("capture_ms", result.ms).put("delay_to_exposure_ms", result.exposedMs).put("moved_deg", movedDeg))
                        loc?.let { meta.put("location", JSONObject().put("lat", it.latitude).put("lon", it.longitude).put("accuracy_m", it.accuracy.toDouble())) }
                        val info = BarInfo(camText, modeText, phoneLine, stamp, headText, attText, loc?.let { LocationHelper.format(it) }, lensSetText, anglesText, bigText, sceneTagText, app.mado.optics.sceneColorArgb(sceneNow))
                        shot = try {
                            val now = java.util.Date()
                            val saved = withContext(Dispatchers.IO) {
                                // Dos archivos con la barra de datos: la foto con los recuadros del kit y el plano recortado al encuadre.
                                // (No se guarda la foto sin gráficos: es la misma imagen que la primera.)
                                val overview = PhotoAnnotator.overview(result.jpeg, result.rotationDeg, annotFrames, info, guidesNow, deliveryNow, deliveryLabel)
                                val marco = PhotoSaver.save(ctx, overview, 0, meta, now, suffix = "_marco", location = loc, headingDeg = posePhoto?.headingDeg)
                                val plano = PhotoAnnotator.plano(result.jpeg, result.rotationDeg, annotFrames, info, guidesNow, deliveryNow, deliveryLabel)
                                PhotoSaver.save(ctx, plano, 0, meta, now, suffix = "_plano", location = loc, headingDeg = posePhoto?.headingDeg)
                                marco
                            }
                            if (sceneNow.isNotEmpty()) framing.shotNo = (shotNow + 1).coerceAtMost(999)   // siguiente plano de la escena
                            ShotUi.Saved(saved.name, if (movedDeg >= 3) movedDeg else 0)
                        } catch (e: Exception) { ShotUi.Error(e.message ?: "save failed") }
                    }
                }
            }
        }
    }
    val onToggle: () -> Unit = {
        when {
            !hasPermission -> permLauncher.launch(Manifest.permission.CAMERA)
            running -> reason = Reason.USER
            else -> { reason = null; error = null; retry++ }
        }
        Unit
    }
    val controls: @Composable () -> Unit = {
        if (framing != null) FramingControls(
            framing = framing, target = target, frame = frame, phoneDeg = phoneDeg, verified = profileDeg != null,
            lenses = lenses, lensIdx = lensIdx, onLens = { lensIdx = it }, lens = lens, size = size,
            running = running, hasPermission = hasPermission, reason = reason, error = error, guard = guard,
            onToggle = onToggle, shot = shot, onShot = takeShot,
            orientation = orientationText(tracker.pose), gpsOn = gpsOn, onGps = onGps, gpsDenied = gpsDenied,
            ev = ev, evInfo = evInfo, onEv = { ev = it },
            fastOn = fastOn, onFast = { fastOn = it; ctx.getSharedPreferences("mado", Context.MODE_PRIVATE).edit().putBoolean("fast_capture", it).apply() },
        ) else Controls(
            report = report, lenses = lenses, lensIdx = lensIdx, onLens = { lensIdx = it },
            distortionOff = distortionOff, onDistortion = { distortionOff = it },
            lens = lens, size = size, theory = theory, distCm = distCm, onDist = { distCm = it },
            running = running, hasPermission = hasPermission,
            reason = reason, error = error, guard = guard,
            onToggle = onToggle,
            results = results, onShare = onShare,
        )
    }

    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        // Cualquier toque cuenta como actividad (reinicia el reloj de inactividad).
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            lastTouch = SystemClock.elapsedRealtime()
        }
    }) {
        if (framing != null) {
            ViewerLayout(
                framing = framing, landscape = landscape, preview = preview, controls = controls,
                running = running, working = shot is ShotUi.Working || shot is ShotUi.Exposed, hasPermission = hasPermission,
                onToggle = onToggle, onShot = takeShot, onMenu = onMenu, onDoubleTap = { cropView = !cropView }, shot = shot, flashTick = flashTick, hint = showHint, cropped = cropOn,
                evLabel = evInfo?.takeIf { ev != 0 }?.let { evText(ev, it.second) }, onFocus = onFocus,
            )
        } else if (landscape) {
            // El preview ocupa el alto; los controles van en un panel desplazable a la derecha.
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                preview(Modifier.fillMaxHeight())
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { header?.invoke(); controls() }
            }
        } else {
            // Preview fijo arriba y a todo el ancho: si saliera de pantalla al hacer scroll, Android destruye la
            // superficie y la cámara se detiene.
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                preview(Modifier.fillMaxWidth())
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { controls() }
            }
        }
    }
}

/**
 * Visor a pantalla completa: la imagen ocupa todo el ancho (vertical) o todo el alto (horizontal) y las teclas flotan encima,
 * abajo o al costado. Las opciones (cámara, modo, lentes, ubicación…) salen de un panel que se abre y se cierra, así que no le
 * quitan espacio a la imagen salvo mientras se usan; tocar la imagen lo cierra.
 */
@Composable
private fun ViewerLayout(
    framing: FramingState, landscape: Boolean,
    preview: @Composable (Modifier) -> Unit, controls: @Composable () -> Unit,
    running: Boolean, working: Boolean, hasPermission: Boolean,
    onToggle: () -> Unit, onShot: () -> Unit, onMenu: () -> Unit, onDoubleTap: () -> Unit, evLabel: String? = null, onFocus: (Float, Float) -> Unit = { _, _ -> },
    shot: ShotUi = ShotUi.Idle, flashTick: Int = 0, hint: Boolean = false, cropped: Boolean = false,
) {
    val p = LocalPalette.current
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    val safe = androidx.compose.foundation.layout.WindowInsets.safeDrawing
    // Rectángulo del visor (donde se dibuja la imagen) y última marca de enfoque, ambos en px del contenedor.
    var previewRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var focusMark by remember { mutableStateOf<Offset?>(null) }
    androidx.compose.runtime.LaunchedEffect(focusMark) { if (focusMark != null) { kotlinx.coroutines.delay(1200); focusMark = null } }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screenH = maxHeight
        val screenW = maxWidth
        preview(
            Modifier.align(Alignment.Center).then(if (landscape) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
                .androidxOnPlaced { previewRect = it },
        )

        // Un toque sobre la imagen cierra el panel; doble toque alterna la vista recortada al encuadre.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pt ->
                        if (optionsOpen) optionsOpen = false
                        else if (running && previewRect.width > 0f && previewRect.contains(pt)) {
                            focusMark = pt
                            onFocus((pt.x - previewRect.left) / previewRect.width, (pt.y - previewRect.top) / previewRect.height)
                        }
                    },
                    onDoubleTap = { onDoubleTap() },
                )
            },
        )

        // Marca del último toque de enfoque.
        focusMark?.let { m ->
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White, 34.dp.toPx(), m, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawCircle(Accent, 4.dp.toPx(), m)
            }
        }

        // Cámara y modo (arriba a la izquierda): tocarla abre la configuración.
        ViewerChip(
            framing, { optionsOpen = !optionsOpen },
            evLabel = evLabel,
            modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(safe.only(WindowInsetsSides.Top + WindowInsetsSides.Start)).padding(8.dp),
        )

        // Destello blanco en el instante en que se toma la foto (avisa que ya se puede mover el teléfono).
        val flash = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(flashTick) {
            if (flashTick > 0) { flash.snapTo(0.75f); flash.animateTo(0f, androidx.compose.animation.core.tween(260)) }
        }
        if (flash.value > 0.01f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))

        // Aviso del estado de la toma: mantener quieto → tomada → guardada (o error / «se movió»).
        var bannerOn by remember { mutableStateOf(false) }
        LaunchedEffect(shot) {
            bannerOn = shot !is ShotUi.Idle
            if (shot is ShotUi.Saved || shot is ShotUi.Error) {
                kotlinx.coroutines.delay(if (shot is ShotUi.Saved && shot.movedDeg > 0) 5000L else 2200L)
                bannerOn = false
            }
        }
        if (bannerOn) {
            val moved = (shot as? ShotUi.Saved)?.movedDeg ?: 0
            val text = when (shot) {
                is ShotUi.Working -> stringResource(R.string.frm_hold_still)
                is ShotUi.Exposed -> stringResource(R.string.frm_shot_taken)
                is ShotUi.Saved -> if (moved > 0) stringResource(R.string.frm_moved, moved) else stringResource(R.string.frm_shot_saved_short)
                is ShotUi.Error -> stringResource(R.string.frm_capture_error, shot.message)
                else -> ""
            }
            val warn = shot is ShotUi.Working || shot is ShotUi.Error || moved > 0
            Box(
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(safe.only(WindowInsetsSides.Bottom))
                    .padding(bottom = if (landscape) 16.dp else 96.dp, start = 16.dp, end = if (landscape) 96.dp else 16.dp)
                    .clip(RoundedCornerShape(12.dp)).background(if (warn) Accent else Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) { Text(text, style = mono(13, Color.White, true)) }
        }

        // Mientras está la vista recortada, un aviso fijo (y tocable) explica por qué la imagen se ve así y cómo volver.
        if (cropped) Box(
            Modifier.align(Alignment.TopCenter).windowInsetsPadding(safe.only(WindowInsetsSides.Top)).padding(top = 68.dp)
                .clip(RoundedCornerShape(12.dp)).background(Accent.copy(alpha = 0.92f)).clickable { onDoubleTap() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) { Text(stringResource(R.string.frm_crop_on), style = mono(11, Color.White, true)) }

        // Pista de una sola vez (primer uso): doble toque = vista recortada.
        if (hint && !bannerOn) Box(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(safe.only(WindowInsetsSides.Bottom))
                .padding(bottom = if (landscape) 16.dp else 96.dp, start = 16.dp, end = if (landscape) 96.dp else 16.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 14.dp, vertical = 9.dp),
        ) { Text(stringResource(R.string.frm_double_tap), style = mono(12, Color.White)) }

        // Cámara apagada: un solo botón grande en el centro (no se confunde con las flechas de focal).
        if (!running) Box(Modifier.align(Alignment.Center).padding(horizontal = 24.dp)) {
            WideKey(
                if (hasPermission) stringResource(R.string.fov_start) else stringResource(R.string.fov_grant),
                accent = true, modifier = Modifier.widthIn(min = 220.dp), onClick = onToggle,
            )
        }

        val optionsPanel: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.verticalScroll(rememberScrollState()).padding(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text(framing.cameraName(), style = mono(14, p.text, true), modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.action_close), style = mono(13, p.accent, true),
                        modifier = Modifier.clickable { optionsOpen = false }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Text(stringResource(R.string.frm_double_tap), style = mono(10, p.text.copy(alpha = 0.6f)), modifier = Modifier.padding(bottom = 8.dp))
                controls()
            }
        }

        if (landscape) {
            ViewerBar(
                framing, running, working, onToggle, onShot, vertical = true, onMenu = onMenu,
                modifier = Modifier.align(Alignment.CenterEnd)
                    .windowInsetsPadding(safe.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)).padding(6.dp),
            )
            if (optionsOpen) optionsPanel(
                Modifier.align(Alignment.CenterEnd)
                    .windowInsetsPadding(safe.only(WindowInsetsSides.End + WindowInsetsSides.Vertical))
                    .padding(end = 72.dp, top = 6.dp, bottom = 6.dp)
                    .width(minOf(screenW * 0.5f, 380.dp)).fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp)).background(p.body),
            )
        } else {
            // Arriba a la derecha: detener (si corre) y menú ☰.
            Row(
                Modifier.align(Alignment.TopEnd).windowInsetsPadding(safe.only(WindowInsetsSides.Top + WindowInsetsSides.End)).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (running) BarKey("■", onToggle)
                BarKey("☰", onMenu)
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .windowInsetsPadding(safe.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                if (optionsOpen) optionsPanel(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).heightIn(max = screenH * 0.55f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(p.body),
                )
                ViewerBar(framing, running, working, onToggle, onShot, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun Preview(
    modifier: Modifier,
    aspect: Float,
    running: Boolean,
    onHolder: (SurfaceHolder?) -> Unit,
    onDestroyed: () -> Unit,
    surfaceFracX: Float = 1f,
    surfaceFracY: Float = 1f,
    corner: androidx.compose.ui.unit.Dp = 14.dp,
    overlay: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    Box(modifier.aspectRatio(aspect).clip(RoundedCornerShape(corner)).background(p.screen)) {
        AndroidView(
            factory = { c ->
                SurfaceView(c).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) { android.util.Log.i("mado", "surfaceCreated"); onHolder(h) }
                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
                        override fun surfaceDestroyed(h: SurfaceHolder) { android.util.Log.i("mado", "surfaceDestroyed"); onHolder(null); onDestroyed() }
                    })
                }
            },
            // La superficie se dimensiona como fracción del visor (puede ser mayor: el visor la recorta).
            modifier = Modifier.align(Alignment.Center).layout { m, c ->
                val w = (c.maxWidth * surfaceFracX).roundToInt().coerceAtLeast(1)
                val h = (c.maxHeight * surfaceFracY).roundToInt().coerceAtLeast(1)
                val pl = m.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                layout(c.maxWidth, c.maxHeight) { pl.place((c.maxWidth - w) / 2, (c.maxHeight - h) / 2) }
            },
        )
        overlay()
        if (!running) Box(Modifier.fillMaxSize().background(p.screen.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Text("■", style = mono(28, p.screenText))
        }
    }
}

/** Distancia sugerida a la pared: el cuadro completo cabe en ~90 cm (cinta de 1 m), máximo 100 cm. */
private fun suggestedDistanceCm(theoryDeg: Double): Int {
    val d = 45.0 / kotlin.math.tan(Math.toRadians(theoryDeg / 2.0))
    return (minOf(100.0, d) / 5.0).roundToInt().coerceAtLeast(2) * 5
}

private val RULER_STEPS = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0)

/**
 * Mira central y regla teórica: marcas naranjas cada N cm de pared (según el FOV teórico y la distancia
 * indicada) a lo largo del lado largo de la imagen. Si el FOV real coincide con el teórico, las marcas
 * caen sobre las marcas de la cinta.
 */
@Composable
private fun Overlay(theoryDeg: Double, distCm: Double?, longHorizontal: Boolean) {
    val p = LocalPalette.current
    val line = if (p.red) p.screenText else Color.White
    val tick = if (p.red) p.screenText else Accent
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height / 2)
        val s = 1.dp.toPx()
        drawLine(line, Offset(c.x, 0f), Offset(c.x, size.height), s)
        drawLine(line, Offset(0f, c.y), Offset(size.width, c.y), s)
        drawCircle(line, size.minDimension * 0.12f, c, style = Stroke(1.5.dp.toPx()))
        if (distCm == null || distCm <= 0) return@Canvas

        // Ancho de pared visible a lo largo del lado largo (cm) y su largo en píxeles.
        val wallCm = 2.0 * distCm * kotlin.math.tan(Math.toRadians(theoryDeg / 2.0))
        val longPx = if (longHorizontal) size.width else size.height
        val step = RULER_STEPS.lastOrNull { it <= wallCm / 8.0 } ?: RULER_STEPS.first()
        val pxPerCm = longPx / wallCm.toFloat()
        val label = TextStyle(fontSize = 15.sp, color = tick, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        val edgeStyle = label.copy(fontSize = 17.sp, color = Color.White)
        val pad = 3.dp.toPx()
        fun tag(text: String, style: TextStyle, at: Offset) {
            val l = measurer.measure(text, style)
            drawRoundRect(
                Color.Black.copy(alpha = 0.65f), Offset(at.x - pad, at.y - pad),
                Size(l.size.width + 2 * pad, l.size.height + 2 * pad), CornerRadius(4.dp.toPx()),
            )
            drawText(l, topLeft = at)
        }
        var k = 0
        while (k * step * pxPerCm <= longPx / 2) {
            listOf(k, -k).distinct().forEach { i ->
                val off = (i * step * pxPerCm).toFloat()
                val major = i % 5 == 0
                val t = (if (major) 16.dp else 10.dp).toPx()
                val txt = "${abs((i * step).roundToInt())}"
                if (longHorizontal) {
                    val x = c.x + off
                    drawLine(tick, Offset(x, c.y - t), Offset(x, c.y + t), 3.dp.toPx())
                    if (i != 0) tag(txt, label, Offset(x - 8.dp.toPx(), c.y + t + 4.dp.toPx()))
                } else {
                    val y = c.y + off
                    drawLine(tick, Offset(c.x - t, y), Offset(c.x + t, y), 3.dp.toPx())
                    if (i != 0) tag(txt, label, Offset(c.x + t + 6.dp.toPx(), y - 10.dp.toPx()))
                }
            }
            k++
        }
        // Borde de la imagen: el número que debería leerse en la cinta (mitad del ancho teórico).
        val edge = "${(wallCm / 2).roundToInt()} cm"
        if (longHorizontal) tag(edge, edgeStyle, Offset(size.width - 78.dp.toPx(), 8.dp.toPx()))
        else tag(edge, edgeStyle, Offset(8.dp.toPx(), 8.dp.toPx()))
    }
}

@Composable
private fun Controls(
    report: DiagnosticsReport,
    lenses: List<CameraInfo>,
    lensIdx: Int,
    onLens: (Int) -> Unit,
    distortionOff: Boolean,
    onDistortion: (Boolean) -> Unit,
    lens: CameraInfo,
    size: android.util.Size,
    theory: Double,
    distCm: String,
    onDist: (String) -> Unit,
    running: Boolean,
    hasPermission: Boolean,
    reason: Reason?,
    error: String?,
    guard: ThermalGuard,
    onToggle: () -> Unit,
    results: FovResults,
    onShare: (String) -> Unit,
) {
    val p = LocalPalette.current
    var widthCm by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf<Int?>(null) }
    val w = widthCm.toDoubleOrNull()
    val d = distCm.toDoubleOrNull()
    // `w` es el número que la cinta marca en el borde del visor (0 de la cinta en el centro de la cruz): ancho = 2 × w.
    val measured = if (w != null && d != null && w > 0 && d > 0) Fov.measuredFovDeg(2 * w, d) else null
    val errPct = measured?.let { (it / theory - 1.0) * 100.0 }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BlackScreen {
            Text(stringResource(R.string.cal_intro), style = mono(10, p.screenDim), modifier = Modifier.padding(bottom = 6.dp))
            Row {
                val (status, accent) = when {
                    error != null -> stringResource(R.string.fov_state_error) to true
                    guard.hot -> stringResource(R.string.fov_state_hot) to true
                    running -> stringResource(R.string.fov_state_live) to false
                    else -> stringResource(R.string.fov_state_stopped) to false
                }
                Text(
                    "id ${lens.id} · ${size.width}×${size.height}", style = mono(10, p.screenDim),
                )
                Spacer(Modifier.weight(1f))
                Text(status, style = mono(10, p.accent))
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                Text(fmt(theory, 1), style = mono(40, p.screenText, true))
                Text(" °", style = mono(16, p.screenDim))
            }
            Text(stringResource(R.string.fov_theory_caption), style = mono(10, p.screenDim))
            d?.takeIf { it > 0 }?.let {
                val wall = 2.0 * it * kotlin.math.tan(Math.toRadians(theory / 2.0))
                Text(
                    stringResource(R.string.fov_ruler_caption, fmt(wall, 0), fmt(it, 0)),
                    style = mono(11, p.accent, true), modifier = Modifier.padding(top = 2.dp),
                )
            }
            val note = when {
                error != null -> stringResource(R.string.fov_error_cam, error)
                guard.hot -> stringResource(R.string.fov_hot_msg)
                reason == Reason.IDLE -> stringResource(R.string.fov_idle_msg)
                reason == Reason.MAX_RUN -> stringResource(R.string.fov_max_msg)
                guard.warm -> stringResource(R.string.fov_warm_msg)
                else -> null
            }
            Text(
                note ?: stringResource(R.string.fov_hint, d?.takeIf { it > 0 }?.let { fmt(2.0 * it * kotlin.math.tan(Math.toRadians(theory / 2.0)) / 2.0, 0) } ?: "?"),
                style = mono(10, if (note != null) p.accent else p.screenDim),
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                stringResource(R.string.fov_thermal, ThermalGuard.name(guard.status)),
                style = mono(9, p.screenDim), modifier = Modifier.padding(top = 4.dp),
            )
        }

        WideKey(
            when {
                !hasPermission -> stringResource(R.string.fov_grant)
                running -> stringResource(R.string.fov_stop)
                else -> stringResource(R.string.fov_start)
            },
            accent = !running, modifier = Modifier.fillMaxWidth(), onClick = onToggle,
        )

        Label(stringResource(R.string.fov_lens_label))
        Seg(
            lenses.map { "${it.id} · ${fmt(it.equiv35Mm.first(), 0)}mm" }, lensIdx, onLens,
        )
        Label(stringResource(R.string.fov_distortion_label))
        Seg(listOf(stringResource(R.string.fov_dist_auto), stringResource(R.string.fov_dist_off)), if (distortionOff) 1 else 0) {
            onDistortion(it == 1)
        }

        Label(stringResource(R.string.fov_distance_label, suggestedDistanceCm(theory)))
        NumField(distCm, onDist)
        Label(stringResource(R.string.fov_width_label))
        NumField(widthCm) { widthCm = it }

        BlackScreen {
            Row {
                Text(stringResource(R.string.fov_measured), style = mono(10, p.screenDim))
                Spacer(Modifier.weight(1f))
                Text(measured?.let { "${fmt(it, 1)} °" } ?: "—", style = mono(14, p.screenText, true))
            }
            Row(Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.fov_error), style = mono(10, p.screenDim))
                Spacer(Modifier.weight(1f))
                val big = errPct != null && abs(errPct) > 3.0
                Text(
                    errPct?.let { "%+.1f %%".format(Locale.ROOT, it) } ?: "—",
                    style = mono(14, if (big) p.accent else p.screenText, true),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WideKey(stringResource(R.string.action_share), false, Modifier.weight(1f)) { onShare(results.json()) }
            WideKey(
                saved?.let { stringResource(R.string.fov_saved, it) } ?: stringResource(R.string.fov_save),
                measured != null, Modifier.weight(1f),
            ) {
                if (measured == null) return@WideKey
                saved = results.append(JSONObject().apply {
                    put("time", Instant.now().toString())
                    put("device", "${report.device["manufacturer"]} ${report.device["model"]}")
                    put("lens_id", lens.id)
                    put("parent_id", lens.parentId ?: JSONObject.NULL)
                    put("focal_mm", lens.focalLengthsMm.first().toDouble())
                    put("stream", "${size.width}x${size.height}")
                    put("distortion_correction", if (distortionOff) "off" else "default")
                    put("edge_reading_cm", w)
                    put("visible_width_cm", w?.let { it * 2 })
                    put("distance_cm", d)
                    put("measured_hfov_deg", measured)
                    put("theory_hfov_deg", theory)
                    put("error_pct", errPct)
                    put("thermal", ThermalGuard.name(guard.status))
                })
            }
        }
        Spacer(Modifier.padding(bottom = 4.dp))
    }
}

@Composable
internal fun Label(text: String) {
    val p = LocalPalette.current
    Text(text, style = mono(11, p.text), modifier = Modifier.padding(top = 2.dp))
}

@Composable
internal fun NumField(value: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    BasicTextField(
        value, { onChange(it.filter { ch -> ch.isDigit() || ch == '.' }.take(7)) },
        textStyle = mono(16, p.keyText, true), cursorBrush = SolidColor(p.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.width(140.dp).clip(RoundedCornerShape(7.dp)).background(p.keyBg).padding(10.dp),
    )
}

/** Selector segmentado estilo aparato (de LUX-0). */
@Composable
internal fun Seg(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label, style = mono(12, if (on) p.keyOnText else p.keyText, on),
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(if (on) p.keyOn else p.keyBg)
                    .clickable { onSelect(i) }.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

private fun fmt(v: Double, digits: Int) = String.format(Locale.ROOT, "%.${digits}f", v)

/** Informa el rectángulo del elemento (en px, relativo a su contenedor) cada vez que se coloca. */
private fun Modifier.androidxOnPlaced(onRect: (androidx.compose.ui.geometry.Rect) -> Unit): Modifier =
    this.onGloballyPositioned { c ->
        val p = c.positionInParent()
        onRect(androidx.compose.ui.geometry.Rect(p, androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat())))
    }
