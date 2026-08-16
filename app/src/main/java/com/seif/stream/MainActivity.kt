package com.seif.stream

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.seif.stream.ui.StreamApp
import com.seif.stream.ui.capture.CaptureViewModel
import com.seif.stream.ui.log.LogViewModel
import com.seif.stream.ui.settings.SettingsViewModel
import com.seif.stream.ui.theme.StreamTheme

class MainActivity : ComponentActivity() {
    private val streamApplication: StreamApplication
        get() = application as StreamApplication

    private val captureViewModel: CaptureViewModel by viewModels {
        CaptureViewModel.Factory(streamApplication.repository)
    }
    private val logViewModel: LogViewModel by viewModels {
        LogViewModel.Factory(streamApplication.repository)
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(streamApplication.repository)
    }

    private var quickCaptureRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
        )

        setContent {
            StreamTheme {
                StreamApp(
                    captureViewModel = captureViewModel,
                    logViewModel = logViewModel,
                    settingsViewModel = settingsViewModel,
                    quickCaptureRequest = quickCaptureRequest,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Launcher and static-shortcut re-entry both mean "new thought". Resuming from Recents
        // does not call this and therefore keeps the current editor intact.
        quickCaptureRequest += 1
    }

    override fun onStop() {
        captureViewModel.flushOnStop()
        super.onStop()
    }
}
