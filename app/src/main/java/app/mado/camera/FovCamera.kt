package app.mado.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Handler
import android.media.Image
import android.os.Build
import android.media.ImageReader
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import app.mado.diagnostics.CameraInfo
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Preview liviano para la prueba de FOV: UN solo stream chico (~640x480) a fps bajos, sin ImageReader,
 * sin RAW, sin captura. Una sola lente a la vez. Mismo diseño de hilos que `MeterCamera` de LUX-0:
 * todas las operaciones de cámara corren en un hilo propio y `generation` invalida arranques viejos,
 * así un stop() que llega mientras la cámara se abre nunca la deja abierta.
 */
class FovCamera(
    context: Context,
    private val onError: (String) -> Unit,
) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val thread = HandlerThread("mado-cam").also { it.start() }
    private val handler = Handler(thread.looper)
    private val exec = Executor { handler.post(it) }

    // Solo se tocan desde el hilo de cámara.
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var active: Active? = null
    private var capturing = false

    private class Active(val surface: Surface, val lens: CameraInfo, val direct: Boolean, val distortionOff: Boolean)

    /** Resultado de una toma: JPEG ya codificado y la rotación que hay que aplicar al verlo derecho. */
    sealed interface Shot {
        class Ok(val jpeg: ByteArray, val width: Int, val height: Int, val rotationDeg: Int, val ms: Long, val exposedMs: Long = 0) : Shot
        class Fail(val message: String) : Shot
    }

    @Volatile private var generation = 0
    @Volatile private var released = false

    /** Lo que quiere la UI. El vigilante cierra cualquier cámara abierta cuando esto es false (anti-huérfana). */
    @Volatile private var wanted = false

    /** Relación (lado largo / lado corto) del preview en uso: la foto sale con la misma, para que los recuadros coincidan. */
    @Volatile private var previewAspect = 4.0 / 3.0

    /**
     * Stream chico que soporte la lente (SurfaceView). Por omisión el más chico 4:3 con ancho >= 640. Con `screenAspect`
     * (lado largo / lado corto de la pantalla) elige el de proporción más parecida entre los de ~0.15 a 0.56 MP, y de ésos el más
     * grande: la imagen llena más pantalla. Un sensor 4:3 recorta el lado corto, así que el campo del lado largo no cambia.
     */
    fun choosePreviewSize(lens: CameraInfo, screenAspect: Double? = null): Size {
        val fallback = Size(640, 480)
        val streamId = lens.id
        val map = try {
            manager.getCameraCharacteristics(streamId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        } catch (e: Exception) { null } ?: return fallback
        val sizes = map.getOutputSizes(SurfaceHolder::class.java)?.takeIf { it.isNotEmpty() } ?: return fallback
        if (screenAspect != null) {
            val cands = sizes.filter { it.width >= it.height && it.width.toLong() * it.height in 150_000L..560_000L }
            if (cands.isNotEmpty()) {
                val best = cands.minBy { Math.abs(Math.log((it.width.toDouble() / it.height) / screenAspect)) }
                val a = best.width.toDouble() / best.height
                return cands.filter { Math.abs(it.width.toDouble() / it.height - a) < 0.01 }.maxBy { it.width.toLong() * it.height }
            }
        }
        return sizes.filter { it.width * 3 == it.height * 4 && it.width >= 640 }.minByOrNull { it.width }
            ?: sizes.minBy { Math.abs(it.width - 640) }
    }

    fun start(holder: SurfaceHolder, lens: CameraInfo, size: Size, distortionOff: Boolean) {
        if (released) return
        val gen = ++generation
        wanted = true
        previewAspect = size.width.toDouble() / size.height
        focusRegion = null
        Log.i("mado", "cam.start gen=$gen lens=${lens.id} size=${size.width}x${size.height}")
        val surface = holder.surface
        handler.post { startInternal(surface, lens, size, distortionOff, gen) }
    }

    fun stop() {
        generation++
        wanted = false
        Log.i("mado", "cam.stop gen=$generation")
        if (!released) handler.post { closeAll() }
    }

    /** Cierra y espera (máx. 1 s). Usar cuando la superficie está por destruirse. */
    fun stopAndWait() {
        generation++
        wanted = false
        if (released) return
        val latch = CountDownLatch(1)
        handler.post { closeAll(); latch.countDown() }
        latch.await(1, TimeUnit.SECONDS)
    }

    fun release() {
        generation++
        wanted = false
        released = true
        handler.post { closeAll(); thread.quitSafely() }
    }

    private val watchRunnable = Runnable { watch() }

    /** Cada 1.5 s: si la UI no quiere cámara y hay algo abierto, se cierra. Se detiene solo cuando no queda nada. */
    private fun watch() {
        if (released) return
        if (!wanted && (device != null || session != null)) {
            Log.w("mado", "watchdog: cámara abierta sin que la UI la quiera; cerrando")
            closeAll()
        }
        if (wanted || device != null || session != null) handler.postDelayed(watchRunnable, 1500)
    }

    private fun fail(gen: Int, msg: String) {
        if (gen != generation) return
        closeAll()
        onError(msg)
    }

    @SuppressLint("MissingPermission")
    private fun startInternal(surface: Surface, lens: CameraInfo, size: Size, distortionOff: Boolean, gen: Int) {
        closeAll()
        if (gen != generation) return
        handler.removeCallbacks(watchRunnable)
        handler.postDelayed(watchRunnable, 1500)
        try {
            // Una cámara física que no está en cameraIdList se pide como stream de su cámara lógica.
            val direct = lens.id in manager.cameraIdList
            val openId = if (direct) lens.id else lens.parentId ?: return fail(gen, "no parent camera for ${lens.id}")
            manager.openCamera(openId, exec, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    if (gen != generation) { d.close(); return }
                    device = d
                    createSession(d, surface, lens, direct, distortionOff, gen)
                }
                override fun onDisconnected(d: CameraDevice) {
                    d.close(); if (device == d) device = null
                    fail(gen, "disconnected")
                }
                override fun onError(d: CameraDevice, error: Int) {
                    d.close(); if (device == d) device = null
                    fail(gen, "error $error")
                }
            })
        } catch (e: Exception) {
            Log.w("mado", "start", e)
            fail(gen, e.message ?: "open failed")
        }
    }

    private fun createSession(d: CameraDevice, surface: Surface, lens: CameraInfo, direct: Boolean, distortionOff: Boolean, gen: Int) {
        try {
            val out = OutputConfiguration(surface)
            if (!direct) out.setPhysicalCameraId(lens.id)
            d.createCaptureSession(SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, listOf(out), exec,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        if (gen != generation) { s.close(); return }
                        session = s
                        active = Active(surface, lens, direct, distortionOff)
                        try {
                            s.setRepeatingRequest(request(d, surface, lens, distortionOff), null, handler)
                        } catch (e: Exception) {
                            Log.w("mado", "repeating", e)
                            fail(gen, e.message ?: "repeating failed")
                        }
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) = fail(gen, "configure failed")
                },
            ))
        } catch (e: Exception) {
            Log.w("mado", "createSession", e)
            fail(gen, e.message ?: "session failed")
        }
    }

    /** Zoom del flujo de preview (1 = sin zoom). Solo lo usa la vista recortada; la foto siempre sale a 1x. */
    @Volatile var zoomRatio = 1f
    @Volatile private var focusRegion: android.hardware.camera2.params.MeteringRectangle? = null

    /** Zoom máximo que acepta la lente (1 si el teléfono no tiene control de zoom). */
    fun maxZoom(lens: CameraInfo): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 1f
        return try {
            val id = if (lens.id in manager.cameraIdList) lens.id else lens.parentId ?: lens.id
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.upper ?: 1f
        } catch (e: Exception) { 1f }
    }

    fun setZoom(z: Float) {
        zoomRatio = z
        reissue()
    }

    private fun reissue() {
        if (released) return
        handler.post {
            val s = session; val d = device; val a = active
            if (s != null && d != null && a != null && !capturing) {
                try { s.setRepeatingRequest(request(d, a.surface, a.lens, a.distortionOff), null, handler) } catch (e: Exception) { Log.w("mado", "reissue", e) }
            }
        }
    }

    /**
     * Enfoca y mide la exposición en un punto de la imagen del preview, dado en fracciones (0..1) de lo que muestra el flujo
     * (ya con zoom) en la orientación de la pantalla. Se traduce a coordenadas del sensor: giro de la pantalla, recorte del
     * flujo (proporción más ancha que el sensor) y zoom.
     */
    fun focusAt(u: Float, v: Float, displayRotationDeg: Int) {
        if (released) return
        handler.post {
            val s = session; val d = device; val a = active
            if (s == null || d == null || a == null || capturing) return@post
            try {
                val c = manager.getCameraCharacteristics(d.id)
                val arr = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return@post
                val rot = ((c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90) - displayRotationDeg + 360) % 360
                var x: Float; var y: Float
                when (rot) { 90 -> { x = v; y = 1f - u }; 180 -> { x = 1f - u; y = 1f - v }; 270 -> { x = 1f - v; y = u }; else -> { x = u; y = v } }
                val sensorAspect = arr.width().toDouble() / arr.height()
                val ap = previewAspect
                if (ap > sensorAspect) y = 0.5f + (y - 0.5f) * (sensorAspect / ap).toFloat() else x = 0.5f + (x - 0.5f) * (ap / sensorAspect).toFloat()
                x = 0.5f + (x - 0.5f) / zoomRatio; y = 0.5f + (y - 0.5f) / zoomRatio
                val half = (minOf(arr.width(), arr.height()) * 0.08f).toInt()
                val cx = (arr.left + x.coerceIn(0f, 1f) * arr.width()).toInt().coerceIn(arr.left + half, arr.right - half)
                val cy = (arr.top + y.coerceIn(0f, 1f) * arr.height()).toInt().coerceIn(arr.top + half, arr.bottom - half)
                focusRegion = android.hardware.camera2.params.MeteringRectangle(cx - half, cy - half, 2 * half, 2 * half, android.hardware.camera2.params.MeteringRectangle.METERING_WEIGHT_MAX - 1)
                val trig = requestBuilder(d, a.surface, a.lens, a.distortionOff)
                trig.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                s.capture(trig.build(), null, handler)
                s.setRepeatingRequest(request(d, a.surface, a.lens, a.distortionOff), null, handler)
            } catch (e: Exception) { Log.w("mado", "focusAt", e) }
        }
    }

    /** Compensación de exposición en pasos de la cámara (0 = automática). Vale para el preview y para la foto. */
    @Volatile var exposureIndex = 0

    /** Rango (en pasos) y tamaño del paso (EV) de la compensación de exposición de la lente, o null si no la tiene. */
    fun exposureInfo(lens: CameraInfo): Pair<IntRange, Double>? = try {
        val id = if (lens.id in manager.cameraIdList) lens.id else lens.parentId ?: lens.id
        val c = manager.getCameraCharacteristics(id)
        val r = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val step = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toDouble()
        if (r == null || step == null || step <= 0.0 || (r.lower == 0 && r.upper == 0)) null else (r.lower..r.upper) to step
    } catch (e: Exception) { null }

    /** Cambia la exposición sobre la marcha (si hay una sesión de preview abierta); si no, queda para el próximo inicio. */
    fun setExposure(index: Int) {
        exposureIndex = index
        reissue()
    }

    private fun applyExposure(b: CaptureRequest.Builder, d: CameraDevice) {
        val r = manager.getCameraCharacteristics(d.id).get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: return
        b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureIndex.coerceIn(r.lower, r.upper))
    }

    private fun request(d: CameraDevice, surface: Surface, lens: CameraInfo, distortionOff: Boolean, lowFps: Boolean = true): CaptureRequest =
        requestBuilder(d, surface, lens, distortionOff, lowFps).build()

    private fun requestBuilder(d: CameraDevice, surface: Surface, lens: CameraInfo, distortionOff: Boolean, lowFps: Boolean = true): CaptureRequest.Builder {
        val b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        b.addTarget(surface)
        applyExposure(b, d)
        // Cuadros por segundo bajos: menos trabajo y menos calor (mismo criterio que LUX-0).
        val c = manager.getCameraCharacteristics(d.id)
        if (lowFps) c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.filter { it.upper >= 10 }
            ?.minWithOrNull(compareBy<Range<Int>> { it.upper }.thenBy { it.lower })
            ?.let { b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
        // Zoom 1x salvo en la vista recortada (recorte por hardware: el flujo chico muestra solo el encuadre, con detalle).
        // Se pide sobre una cámara física fija, así que no cambia de lente sola.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val zr = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            b.set(CaptureRequest.CONTROL_ZOOM_RATIO, if (zr != null) zoomRatio.coerceIn(zr.lower, zr.upper) else 1.0f)
        }
        // Enfoque continuo (como una app de cámara) y, si el usuario tocó la imagen, enfoque y exposición en ese punto.
        val afModes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList().orEmpty()
        if (CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE in afModes) b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        focusRegion?.let { rg ->
            if ((c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0) > 0) b.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(rg))
            if ((c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0) > 0) b.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(rg))
        }
        if (distortionOff) {
            val modes = c.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)?.toList().orEmpty()
            if (CaptureRequest.DISTORTION_CORRECTION_MODE_OFF in modes) {
                b.set(CaptureRequest.DISTORTION_CORRECTION_MODE, CaptureRequest.DISTORTION_CORRECTION_MODE_OFF)
            }
        }
        return b
    }

    /** Cierra sesión y dispositivo. Solo en el hilo de cámara. */
    private fun closeAll() {
        Log.i("mado", "closeAll device=${device != null} session=${session != null}")
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { device?.close() } catch (_: Exception) {}
        device = null
        active = null
        capturing = false
    }

    // ---------- Captura de una foto ----------
    // El preview sigue siendo el stream chico de siempre. Para la foto se cambia, SOLO en ese instante, a una sesión con un
    // segundo stream YUV de hasta ~12 MP, se deja ~0.9 s para que converjan exposición y foco, se hace UNA toma y se vuelve
    // a la sesión liviana. Así la cámara nunca queda en un modo pesado entre fotos.

    private val STILL_MAX_PIXELS = 12_700_000L
    // Tope de espera a que converjan exposición y foco: se dispara apenas están estables (mínimo STILL_MIN_FRAMES cuadros).
    private val STILL_SETTLE_MS = 1400L
    private val STILL_MIN_FRAMES = 4
    private val STILL_TIMEOUT_MS = 8000L

    /** `onExposed` se llama en el instante en que el sensor empieza a exponer la foto (desde ahí se puede mover el teléfono). */
    fun capture(displayRotationDeg: Int, onExposed: () -> Unit = {}, onResult: (Shot) -> Unit) {
        if (released) { onResult(Shot.Fail("released")); return }
        val gen = generation
        handler.post { captureInternal(displayRotationDeg, gen, onExposed, onResult) }
    }

    private fun chooseStillSize(c: CameraCharacteristics): Size? {
        val sizes = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.YUV_420_888) ?: return null
        val ok = sizes.filter { it.width.toLong() * it.height <= STILL_MAX_PIXELS }
        // La foto con la misma proporción que el preview, para que los recuadros caigan donde se vieron.
        return ok.filter { Math.abs(it.width.toDouble() / it.height - previewAspect) < 0.01 }.maxByOrNull { it.width.toLong() * it.height }
            ?: ok.filter { it.width * 3 == it.height * 4 }.maxByOrNull { it.width.toLong() * it.height }
            ?: ok.maxByOrNull { it.width.toLong() * it.height }
    }

    private fun captureInternal(displayRotationDeg: Int, gen: Int, onExposed: () -> Unit, cb: (Shot) -> Unit) {
        val a = active; val d = device
        if (a == null || d == null || session == null || gen != generation) return cb(Shot.Fail("camera not ready"))
        if (capturing) return cb(Shot.Fail("busy"))
        capturing = true
        val t0 = SystemClock.elapsedRealtime()
        var reader: ImageReader? = null
        var done = false
        var exposedAt = 0L
        val timeout = Runnable { }
        lateinit var finish: (Shot) -> Unit
        finish = { result ->
            if (!done) {
                done = true
                handler.removeCallbacksAndMessages(timeout)
                try { session?.close() } catch (_: Exception) {}
                session = null
                try { reader?.close() } catch (_: Exception) {}
                capturing = false
                // Vuelve al preview liviano si la cámara sigue siendo la misma y la UI la quiere.
                if (gen == generation && wanted && device === d) createSession(d, a.surface, a.lens, a.direct, a.distortionOff, gen)
                cb(result)
            }
        }
        try {
            val c = manager.getCameraCharacteristics(a.lens.id)
            val size = chooseStillSize(c) ?: return finish(Shot.Fail("no still size"))
            val sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val rotation = (sensorOrientation - displayRotationDeg + 360) % 360
            val r = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 1)
            reader = r
            r.setOnImageAvailableListener({ ir ->
                val img = try { ir.acquireLatestImage() } catch (e: Exception) { null } ?: return@setOnImageAvailableListener
                try {
                    val jpeg = yuvToJpeg(img, 95)
                    finish(Shot.Ok(jpeg, img.width, img.height, rotation, SystemClock.elapsedRealtime() - t0, if (exposedAt > 0) exposedAt - t0 else 0))
                } catch (e: Exception) {
                    Log.w("mado", "yuvToJpeg", e)
                    finish(Shot.Fail(e.message ?: "encode failed"))
                } finally { img.close() }
            }, handler)

            try { session?.close() } catch (_: Exception) {}
            session = null
            val prev = OutputConfiguration(a.surface).also { if (!a.direct) it.setPhysicalCameraId(a.lens.id) }
            val still = OutputConfiguration(r.surface).also { if (!a.direct) it.setPhysicalCameraId(a.lens.id) }
            handler.postDelayed({ finish(Shot.Fail("timeout")) }, timeout, STILL_TIMEOUT_MS)
            d.createCaptureSession(SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, listOf(prev, still), exec,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        if (done || gen != generation) { s.close(); finish(Shot.Fail("stopped")); return }
                        session = s
                        val tConfigured = SystemClock.elapsedRealtime()
                        var fired = false
                        val fire = {
                            if (!fired && !done) {
                                fired = true
                                Log.i("mado", "still: configurada en ${tConfigured - t0} ms, disparo a los ${SystemClock.elapsedRealtime() - t0} ms")
                                shoot(s, d, a, r, gen, finish) { exposedAt = SystemClock.elapsedRealtime(); onExposed() }
                            }
                        }
                        // Se dispara en cuanto la exposición y el foco automáticos están estables, en vez de esperar un tiempo fijo.
                        val ready = object : CameraCaptureSession.CaptureCallback() {
                            var frames = 0
                            override fun onCaptureCompleted(sess: CameraCaptureSession, req: CaptureRequest, res: TotalCaptureResult) {
                                if (fired) return
                                frames++
                                val ae = res.get(CaptureResult.CONTROL_AE_STATE)
                                val af = res.get(CaptureResult.CONTROL_AF_STATE)
                                val aeOk = ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED || ae == CaptureResult.CONTROL_AE_STATE_LOCKED ||
                                    ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                                val afOk = af == null || (af != CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN && af != CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN)
                                if (frames >= STILL_MIN_FRAMES && aeOk && afOk) fire()
                            }
                        }
                        try {
                            s.setRepeatingRequest(request(d, a.surface, a.lens, a.distortionOff, lowFps = false), ready, handler)
                            handler.postDelayed({ fire() }, timeout, STILL_SETTLE_MS)       // tope si nunca converge
                        } catch (e: Exception) { finish(Shot.Fail(e.message ?: "repeating failed")) }
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) = finish(Shot.Fail("configure failed"))
                },
            ))
        } catch (e: Exception) {
            Log.w("mado", "capture", e)
            finish(Shot.Fail(e.message ?: "capture failed"))
        }
    }

    private fun shoot(s: CameraCaptureSession, d: CameraDevice, a: Active, r: ImageReader, gen: Int, finish: (Shot) -> Unit, onStarted: () -> Unit = {}) {
        if (gen != generation || session !== s) return finish(Shot.Fail("stopped"))
        try {
            val b = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            b.addTarget(r.surface)
            applyExposure(b, d)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) b.set(CaptureRequest.CONTROL_ZOOM_RATIO, 1.0f) // Android 11+; antes no existe y el zoom por defecto ya es 1x
            if (a.distortionOff) {
                val modes = manager.getCameraCharacteristics(d.id).get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)?.toList().orEmpty()
                if (CaptureRequest.DISTORTION_CORRECTION_MODE_OFF in modes) b.set(CaptureRequest.DISTORTION_CORRECTION_MODE, CaptureRequest.DISTORTION_CORRECTION_MODE_OFF)
            }
            s.capture(b.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureStarted(sess: CameraCaptureSession, req: CaptureRequest, timestamp: Long, frameNumber: Long) = onStarted()
                override fun onCaptureFailed(sess: CameraCaptureSession, req: CaptureRequest, f: CaptureFailure) = finish(Shot.Fail("capture failed (${f.reason})"))
            }, handler)
        } catch (e: Exception) { finish(Shot.Fail(e.message ?: "shoot failed")) }
    }

    /** YUV_420_888 → NV21 → JPEG. Respeta rowStride y pixelStride de cada plano. */
    private fun yuvToJpeg(img: Image, quality: Int): ByteArray {
        val w = img.width; val h = img.height
        val nv21 = ByteArray(w * h * 3 / 2)
        val y = img.planes[0]
        for (row in 0 until h) { y.buffer.position(row * y.rowStride); y.buffer.get(nv21, row * w, w) }
        val u = img.planes[1]; val v = img.planes[2]
        var o = w * h
        for (row in 0 until h / 2) for (col in 0 until w / 2) {
            nv21[o++] = v.buffer.get(row * v.rowStride + col * v.pixelStride)
            nv21[o++] = u.buffer.get(row * u.rowStride + col * u.pixelStride)
        }
        return ByteArrayOutputStream(w * h / 4).also { YuvImage(nv21, ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), quality, it) }.toByteArray()
    }
}
