package app.nocturne

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import app.nocturne.ui.RootScreen
import kotlinx.coroutines.launch

@UnstableApi
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    /** Bumped whenever the HOME button brings us back to the front, so the UI can close dialogs. */
    private var homeSignal by mutableIntStateOf(0)
    private var audioPermissionGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        audioPermissionGranted = hasAudioPermission()
        if (audioPermissionGranted) vm.scanLocal()
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.addTree(uri.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        audioPermissionGranted = hasAudioPermission()
        setContent {
            RootScreen(
                vm = vm,
                homeSignal = homeSignal,
                hasAudioPermission = audioPermissionGranted,
                onRequestPermissions = { requestPermissions() },
                onPickFolder = { folderPicker.launch(null) },
                onApplyWindow = { keepAwake, fullscreen, brightness -> applyWindow(keepAwake, fullscreen, brightness) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // HOME pressed while we are the home app: behave like a launcher and return to the main screen.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal++
    }

    override fun onResume() {
        super.onResume()
        audioPermissionGranted = hasAudioPermission()
        if (audioPermissionGranted) vm.scanLocalIfStale()
    }

    private fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val wanted = buildList {
            add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }

    /** Keep-awake, immersive mode and the dimmed-screen brightness (brightness < 0 = system default). */
    private fun applyWindow(keepAwake: Boolean, fullscreen: Boolean, brightness: Float) {
        if (keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        val lp = window.attributes
        lp.screenBrightness = if (brightness < 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else brightness
        window.attributes = lp
    }
}
