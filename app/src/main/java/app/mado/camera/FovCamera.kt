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
        class Ok(val jpeg: ByteArray, val width: Int, val height: Int, val rotationDeg: Int, val ms: Long) : Shot
        class Fail(val message: String) : Shot
    }

    @Volatile private var generation = 0
    @Volatile private var released = false

    /** Lo que quiere la UI. El vigilante cierra cualquier cámara abierta cuando esto es false (anti-huérfana). */
    @Volatile private var wanted = false

    /** Stream más chico 4:3 con ancho >= 640 que soporte la lente (SurfaceView). */
    fun choosePreviewSize(lens: CameraInfo): Size {
        val fallback = Size(640, 480)
        val streamId = lens.id
        val map = try {
            manager.getCameraCharacteristics(streamId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        } catch (e: Exception) { null } ?: return fallback
        val sizes = map.getOutputSizes(SurfaceHolder::class.java)?.takeIf { it.isNotEmpty() } ?: return fallback
        return sizes.filter { it.width * 3 == it.height * 4 && it.width >= 640 }.minByOrNull { it.width }
            ?: sizes.minBy { Math.abs(it.width - 640) }
    }

    fun start(holder: SurfaceHolder, lens: CameraInfo, size: Size, distortionOff: Boolean) {
        if (released) return
        val gen = ++generation
        wanted = true
        Log.i("mado", "cam.start gen=$gen lens=${lens.id}")
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

    private fun request(d: CameraDevice, surface: Surface, lens: CameraInfo, distortionOff: Boolean): CaptureRequest {
        val b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        b.addTarget(surface)
        // Cuadros por segundo bajos: menos trabajo y menos calor (mismo criterio que LUX-0).
        val c = manager.getCameraCharacteristics(d.id)
        c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.filter { it.upper >= 10 }
            ?.minWithOrNull(compareBy<Range<Int>> { it.upper }.thenBy { it.lower })
            ?.let { b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
        // Sin zoom: evita que Android cambie de lente sola y descuadre el cálculo.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) b.set(CaptureRequest.CONTROL_ZOOM_RATIO, 1.0f) // Android 11+; antes no existe y el zoom por defecto ya es 1x
        if (distortionOff) {
            val modes = c.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)?.toList().orEmpty()
            if (CaptureRequest.DISTORTION_CORRECTION_MODE_OFF in modes) {
                b.set(CaptureRequest.DISTORTION_CORRECTION_MODE, CaptureRequest.DISTORTION_CORRECTION_MODE_OFF)
            }
        }
        return b.build()
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
    private val STILL_SETTLE_MS = 900L
    private val STILL_TIMEOUT_MS = 8000L

    fun capture(displayRotationDeg: Int, onResult: (Shot) -> Unit) {
        if (released) { onResult(Shot.Fail("released")); return }
        val gen = generation
        handler.post { captureInternal(displayRotationDeg, gen, onResult) }
    }

    private fun chooseStillSize(c: CameraCharacteristics): Size? {
        val sizes = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.YUV_420_888) ?: return null
        val ok = sizes.filter { it.width.toLong() * it.height <= STILL_MAX_PIXELS }
        return ok.filter { it.width * 3 == it.height * 4 }.maxByOrNull { it.width.toLong() * it.height }
            ?: ok.maxByOrNull { it.width.toLong() * it.height }
    }

    private fun captureInternal(displayRotationDeg: Int, gen: Int, cb: (Shot) -> Unit) {
        val a = active; val d = device
        if (a == null || d == null || session == null || gen != generation) return cb(Shot.Fail("camera not ready"))
        if (capturing) return cb(Shot.Fail("busy"))
        capturing = true
        val t0 = SystemClock.elapsedRealtime()
        var reader: ImageReader? = null
        var done = false
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
                    finish(Shot.Ok(jpeg, img.width, img.height, rotation, SystemClock.elapsedRealtime() - t0))
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
                        try {
                            s.setRepeatingRequest(request(d, a.surface, a.lens, a.distortionOff), null, handler)
                            handler.postDelayed({ shoot(s, d, a, r, gen, finish) }, STILL_SETTLE_MS)
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

    private fun shoot(s: CameraCaptureSession, d: CameraDevice, a: Active, r: ImageReader, gen: Int, finish: (Shot) -> Unit) {
        if (gen != generation || session !== s) return finish(Shot.Fail("stopped"))
        try {
            val b = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            b.addTarget(r.surface)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) b.set(CaptureRequest.CONTROL_ZOOM_RATIO, 1.0f) // Android 11+; antes no existe y el zoom por defecto ya es 1x
            if (a.distortionOff) {
                val modes = manager.getCameraCharacteristics(d.id).get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)?.toList().orEmpty()
                if (CaptureRequest.DISTORTION_CORRECTION_MODE_OFF in modes) b.set(CaptureRequest.DISTORTION_CORRECTION_MODE, CaptureRequest.DISTORTION_CORRECTION_MODE_OFF)
            }
            s.capture(b.build(), object : CameraCaptureSession.CaptureCallback() {
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
