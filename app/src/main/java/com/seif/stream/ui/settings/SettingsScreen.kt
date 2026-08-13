package com.seif.stream.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seif.stream.R
import com.seif.stream.ui.theme.Accent
import com.seif.stream.ui.theme.Ink
import com.seif.stream.ui.theme.Muted
import com.seif.stream.ui.theme.Paper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val exportFileFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val payload = pendingExport
        pendingExport = null
        if (uri == null || payload == null) {
            viewModel.operationCancelled()
        } else {
            scope.launch {
                val success = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)
                            ?.use { writer -> writer.write(payload) }
                            ?: error("Unable to open export destination")
                    }
                }.isSuccess
                viewModel.exportFinished(success)
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            viewModel.operationCancelled()
        } else {
            viewModel.importStarted()
            scope.launch {
                val raw = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                            ?.use { reader -> reader.readText() }
                            ?: error("Unable to open import source")
                    }
                }.getOrNull()

                if (raw == null) {
                    viewModel.importReadFailed()
                } else {
                    viewModel.import(raw)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(start = 10.dp, end = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back to log",
                    tint = Ink,
                )
            }
            Text(
                text = "STREAM / SETTINGS",
                color = Ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.4.sp,
            )
        }

        Spacer(Modifier.height(34.dp))

        SettingsAction(
            label = "EXPORT",
            description = "Write every entry to a Stream JSON file",
            enabled = !state.busy,
            onClick = {
                scope.launch {
                    val payload = viewModel.prepareExport() ?: return@launch
                    pendingExport = payload
                    createDocument.launch(
                        "stream-export-${LocalDateTime.now().format(exportFileFormatter)}.json",
                    )
                }
            },
        )

        SettingsAction(
            label = "IMPORT",
            description = "Merge a previous Stream JSON export",
            enabled = !state.busy,
            onClick = {
                openDocument.launch(arrayOf("application/json", "text/plain"))
            },
        )

        state.status?.let { status ->
            Row(
                modifier = Modifier.padding(start = 22.dp, top = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Accent, androidx.compose.foundation.shape.CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = status,
                    color = Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun SettingsAction(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        Text(
            text = label,
            color = if (enabled) Ink else Muted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 1.2.sp,
        )
        Text(
            text = description,
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}
