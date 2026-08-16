package com.seif.stream.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seif.stream.ui.theme.Accent
import com.seif.stream.ui.theme.Ink
import com.seif.stream.ui.theme.Muted
import com.seif.stream.ui.theme.Paper
import com.seif.stream.util.formatCaptureTimestamp
import kotlinx.coroutines.delay

@Composable
fun CaptureScreen(
    state: CaptureUiState,
    onTextChanged: (String) -> Unit,
    shouldFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var fieldValue by remember(state.sessionId) {
        mutableStateOf(
            TextFieldValue(
                text = state.text,
                selection = TextRange(state.text.length),
            ),
        )
    }

    LaunchedEffect(state.text) {
        if (state.text != fieldValue.text) {
            fieldValue = TextFieldValue(
                text = state.text,
                selection = TextRange(state.text.length),
            )
        }
    }

    LaunchedEffect(shouldFocus, state.sessionId) {
        if (shouldFocus) {
            // Waiting one frame makes IME requests reliable on both cold launch and pager return.
            delay(80)
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
            .imePadding()
            .padding(horizontal = 22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "STREAM",
                color = Ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.8.sp,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Accent, CircleShape),
                )
                Text(
                    text = state.saveStatus.label,
                    color = Muted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 7.dp),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = state.timestamp?.let { formatCaptureTimestamp(it) }
                ?: "TIME STAMPS ON FIRST KEYSTROKE",
            color = if (state.timestamp == null) Muted else Ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 0.55.sp,
        )

        if (state.recovered) {
            Text(
                text = "recovered unsaved text",
                color = Accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 7.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        BasicTextField(
            value = fieldValue,
            onValueChange = { next ->
                fieldValue = next
                onTextChanged(next.text)
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            cursorBrush = SolidColor(Accent),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize()) {
                    if (fieldValue.text.isEmpty()) {
                        Text(
                            text = "Start typing…",
                            color = Muted.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
