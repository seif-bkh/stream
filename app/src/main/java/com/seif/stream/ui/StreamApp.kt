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
import com.seif.stream.ui.log.TrashScreen
import com.seif.stream.ui.settings.SettingsScreen
import com.seif.stream.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

// Page order is intentional: from Capture (1), a rightward swipe reveals Log (0).
private const val LOG_PAGE = 0
private const val CAPTURE_PAGE = 1

private enum class OverlayScreen {
    Main,
    Settings,
    Trash,
}

@Composable
fun StreamApp(
    captureViewModel: CaptureViewModel,
    logViewModel: LogViewModel,
    settingsViewModel: SettingsViewModel,
    quickCaptureRequest: Int,
) {
    val captureState by captureViewModel.state.collectAsStateWithLifecycle()
    val entries by logViewModel.entries.collectAsStateWithLifecycle()
    val trashedEntries by logViewModel.trashedEntries.collectAsStateWithLifecycle()
    val pagerState = key(captureViewModel.uiSessionKey) {
        rememberPagerState(initialPage = CAPTURE_PAGE, pageCount = { 2 })
    }
    val scope = rememberCoroutineScope()
    var overlayScreen by key(captureViewModel.uiSessionKey) {
        rememberSaveable { mutableStateOf(OverlayScreen.Main) }
    }

    LaunchedEffect(quickCaptureRequest) {
        if (quickCaptureRequest > 0 && captureViewModel.startFresh()) {
            overlayScreen = OverlayScreen.Main
            pagerState.scrollToPage(CAPTURE_PAGE)
        }
    }

    BackHandler(enabled = overlayScreen != OverlayScreen.Main) {
        overlayScreen = OverlayScreen.Main
    }
    BackHandler(
        enabled = overlayScreen == OverlayScreen.Main && pagerState.currentPage == LOG_PAGE,
    ) {
        scope.launch { pagerState.animateScrollToPage(CAPTURE_PAGE) }
    }

    when (overlayScreen) {
        OverlayScreen.Settings -> SettingsScreen(
            viewModel = settingsViewModel,
            onBack = { overlayScreen = OverlayScreen.Main },
            modifier = Modifier.fillMaxSize(),
        )

        OverlayScreen.Trash -> TrashScreen(
            entries = trashedEntries,
            onBack = { overlayScreen = OverlayScreen.Main },
            onRestore = { entry ->
                scope.launch { logViewModel.restore(entry) }
            },
            onDeletePermanently = { entry ->
                scope.launch { logViewModel.deletePermanently(entry) }
            },
            onEmptyTrash = {
                scope.launch { logViewModel.emptyTrash() }
            },
            modifier = Modifier.fillMaxSize(),
        )

        OverlayScreen.Main -> HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { page -> page },
        ) { page ->
            when (page) {
                LOG_PAGE -> LogScreen(
                    entries = entries,
                    onFreshCapture = {
                        scope.launch {
                            if (captureViewModel.startFresh()) {
                                pagerState.scrollToPage(CAPTURE_PAGE)
                            }
                        }
                    },
                    onOpenEntry = { entry ->
                        scope.launch {
                            if (captureViewModel.openEntry(entry)) {
                                pagerState.scrollToPage(CAPTURE_PAGE)
                            }
                        }
                    },
                    onMoveToTrash = { entry ->
                        scope.launch {
                            if (captureViewModel.prepareEntryForTrash(entry.timestamp)) {
                                logViewModel.moveToTrash(entry)
                            }
                        }
                    },
                    onOpenTrash = {
                        overlayScreen = OverlayScreen.Trash
                    },
                    onOpenSettings = {
                        // Include even a just-typed entry in an immediately requested export.
                        captureViewModel.flushOnStop()
                        overlayScreen = OverlayScreen.Settings
                    },
                )

                CAPTURE_PAGE -> CaptureScreen(
                    state = captureState,
                    onTextChanged = captureViewModel::onTextChanged,
                    shouldFocus = pagerState.currentPage == CAPTURE_PAGE,
                )
            }
        }
    }
}
