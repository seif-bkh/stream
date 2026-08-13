package com.seif.stream.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seif.stream.ui.capture.CaptureScreen
import com.seif.stream.ui.capture.CaptureViewModel
import com.seif.stream.ui.log.LogScreen
import com.seif.stream.ui.log.LogViewModel
import com.seif.stream.ui.settings.SettingsScreen
import com.seif.stream.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

private const val CAPTURE_PAGE = 0
private const val LOG_PAGE = 1

@Composable
fun StreamApp(
    captureViewModel: CaptureViewModel,
    logViewModel: LogViewModel,
    settingsViewModel: SettingsViewModel,
    quickCaptureRequest: Int,
) {
    val captureState by captureViewModel.state.collectAsStateWithLifecycle()
    val entries by logViewModel.entries.collectAsStateWithLifecycle()
    val pagerState = key(captureViewModel.uiSessionKey) {
        rememberPagerState(initialPage = CAPTURE_PAGE, pageCount = { 2 })
    }
    val scope = rememberCoroutineScope()
    var settingsVisible by key(captureViewModel.uiSessionKey) {
        rememberSaveable { mutableStateOf(false) }
    }

    LaunchedEffect(quickCaptureRequest) {
        if (quickCaptureRequest > 0 && captureViewModel.startFresh()) {
            settingsVisible = false
            pagerState.scrollToPage(CAPTURE_PAGE)
        }
    }

    BackHandler(enabled = settingsVisible) {
        settingsVisible = false
    }
    BackHandler(enabled = !settingsVisible && pagerState.currentPage == LOG_PAGE) {
        scope.launch { pagerState.animateScrollToPage(CAPTURE_PAGE) }
    }

    if (settingsVisible) {
        SettingsScreen(
            viewModel = settingsViewModel,
            onBack = { settingsVisible = false },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { page -> page },
        ) { page ->
            when (page) {
                CAPTURE_PAGE -> CaptureScreen(
                    state = captureState,
                    onTextChanged = captureViewModel::onTextChanged,
                    shouldFocus = pagerState.currentPage == CAPTURE_PAGE,
                )

                LOG_PAGE -> LogScreen(
                    entries = entries,
                    onFreshCapture = {
                        scope.launch {
                            if (captureViewModel.startFresh()) {
                                pagerState.scrollToPage(CAPTURE_PAGE)
                            }
                        }
                    },
                    onOpenSettings = {
                        // Include even a just-typed entry in an immediately requested export.
                        captureViewModel.flushOnStop()
                        settingsVisible = true
                    },
                )
            }
        }
    }
}
