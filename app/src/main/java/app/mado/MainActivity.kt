package app.mado

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mado.diagnostics.CameraDiagnostics
import app.mado.data.CameraRepository
import app.mado.data.FileExport
import app.mado.diagnostics.FovResults
import app.mado.ui.Claro
import app.mado.ui.DiagnosticsScreen
import app.mado.ui.FovScreen
import app.mado.ui.FramingState
import app.mado.ui.Header
import app.mado.ui.LocalPalette
import app.mado.ui.NocheRoja
import app.mado.ui.Oscuro

private val THEMES = listOf("auto", "light", "dark", "red")

class MainActivity : ComponentActivity() {
    /** Aplica el idioma elegido en la app antes de crear la interfaz. */
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LocaleHelper.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("mado", MODE_PRIVATE)
        setContent {
            var theme by remember { mutableStateOf(prefs.getString("theme", "auto") ?: "auto") }
            // 0 = visor (pantalla principal), 1 = calibración de lentes, 2 = diagnóstico de cámaras.
            var screen by rememberSaveable { mutableIntStateOf(0) }
            var menuOpen by remember { mutableStateOf(false) }
            var langDialog by remember { mutableStateOf(false) }
            var themeDialog by remember { mutableStateOf(false) }
            val lang = remember { LocaleHelper.saved(this@MainActivity) }
            val systemDark = isSystemInDarkTheme()
            androidx.compose.runtime.SideEffect {
                // En el visor (negro) los iconos de las barras del sistema van claros; en el resto siguen el tema.
                if (screen == 0) enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
                else enableEdgeToEdge()
            }
            val palette = when (theme) {
                "light" -> Claro
                "dark" -> Oscuro
                "red" -> NocheRoja
                else -> if (systemDark) Oscuro else Claro
            }
            // Solo lee CameraCharacteristics: no abre la cámara ni crea sesiones de captura.
            val report = remember {
                CameraDiagnostics.collect(applicationContext).also {
                    // Copia local para recuperarla por adb (run-as) en builds debug.
                    runCatching { filesDir.resolve("diagnostics.json").writeText(it.toJson()) }
                }
            }
            val results = remember { FovResults(applicationContext) }
            val appVersion = remember { runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty() }
            val framing = remember { FramingState(CameraRepository.load(applicationContext)).also { it.restore(prefs.getString("framing", null)) } }
            androidx.activity.compose.BackHandler(enabled = screen != 0) { screen = 0 }

            val title = stringResource(when (screen) { 0 -> R.string.screen_viewer; 1 -> R.string.screen_calibration; else -> R.string.mode_diagnostics })
            // Orientación según el tamaño real de la ventana: la configuración que reporta Android puede quedar desfasada tras girar
            // (en algunos teléfonos, p. ej. el Xperia, el diseño se quedaba en vertical con el teléfono en horizontal).
            val win = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
            val landscape = win.width > win.height
            val chrome: @Composable () -> Unit = {
                Header(title, onMenu = { menuOpen = true }, onBack = if (screen != 0) ({ screen = 0 }) else null)
            }
            // En horizontal, el visor y la calibración meten el encabezado en su panel derecho: la imagen usa todo el alto.
            val chromeInPanel = landscape && screen == 1

            CompositionLocalProvider(LocalPalette provides palette) {
                // El visor (pantalla 0) ocupa toda la pantalla, sin encabezado ni márgenes: sus teclas flotan sobre la imagen.
                val full = screen == 0
                Column(
                    (if (full) Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
                    else Modifier.fillMaxSize().background(palette.body).safeDrawingPadding().padding(horizontal = 12.dp)),
                ) {
                    if (!chromeInPanel && !full) chrome()
                    if (menuOpen) {
                        val items = buildList<Pair<String, () -> Unit>> {
                            if (screen != 0) add(stringResource(R.string.screen_viewer) to { screen = 0 })
                            if (screen != 1) add(stringResource(R.string.menu_calibration) to { screen = 1 })
                            if (screen != 2) add(stringResource(R.string.menu_diagnostics) to { screen = 2 })
                            add(stringResource(R.string.lang_title) to { langDialog = true })
                            add(stringResource(R.string.menu_theme) to { themeDialog = true })
                        }
                        app.mado.ui.MenuDialog(stringResource(R.string.menu_title), items, { menuOpen = false }, stringResource(R.string.action_close), appVersion)
                    }
                    if (langDialog) {
                        val names = listOf(stringResource(R.string.lang_auto), "Español", "English", "日本語")
                        app.mado.ui.PickerDialog(
                            title = stringResource(R.string.lang_title), items = names, selected = LocaleHelper.OPTIONS.indexOf(lang),
                            onPick = { i -> if (LocaleHelper.OPTIONS[i] != lang) { LocaleHelper.save(this@MainActivity, LocaleHelper.OPTIONS[i]); recreate() } },
                            onDismiss = { langDialog = false },
                        )
                    }
                    if (themeDialog) {
                        val names = listOf(R.string.theme_auto, R.string.theme_light, R.string.theme_dark, R.string.theme_red).map { stringResource(it) }
                        app.mado.ui.PickerDialog(
                            title = stringResource(R.string.menu_theme), items = names, selected = THEMES.indexOf(theme),
                            onPick = { i -> theme = THEMES[i]; prefs.edit().putString("theme", theme).apply() },
                            onDismiss = { themeDialog = false },
                        )
                    }
                    when (screen) {
                        0 -> FovScreen(report, results, onShare = ::share, framing = framing, onMenu = { menuOpen = true })
                        1 -> FovScreen(report, results, onShare = ::share, header = if (chromeInPanel) chrome else null)
                        else -> DiagnosticsScreen(report, onCopy = { copy(report.toJson()) }, onShare = { shareDiagnostics(report.toJson()) }, onSave = { saveDiagnostics(report.toJson()) })
                    }
                }
            }
        }
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("mado-diagnostics", text))
    }

    /** Guarda el JSON en Descargas/Mado y avisa dónde quedó. */
    private fun saveDiagnostics(json: String): FileExport.Saved? = try {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ROOT).format(java.util.Date())
        val name = FileExport.safeName("mado-diagnostico-${Build.MANUFACTURER}-${Build.MODEL}-$stamp") + ".json"
        FileExport.saveJson(this, name, json).also { Toast.makeText(this, getString(R.string.toast_saved_in, it.displayPath), Toast.LENGTH_LONG).show() }
    } catch (e: Exception) {
        Toast.makeText(this, getString(R.string.toast_save_failed, e.message ?: ""), Toast.LENGTH_LONG).show(); null
    }

    /** Guarda el archivo y lo comparte como adjunto; si no se pudo guardar, comparte el texto. */
    private fun shareDiagnostics(json: String) {
        val saved = saveDiagnostics(json) ?: return share(json)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, saved.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.action_share)))
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.action_share)))
    }
}
