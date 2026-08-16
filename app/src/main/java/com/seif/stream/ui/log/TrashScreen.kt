package com.seif.stream.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seif.stream.R
import com.seif.stream.data.Entry
import com.seif.stream.ui.theme.Accent
import com.seif.stream.ui.theme.Ink
import com.seif.stream.ui.theme.Muted
import com.seif.stream.ui.theme.Paper
import com.seif.stream.util.formatEntryTime
import java.time.LocalDate

@Composable
fun TrashScreen(
    entries: List<Entry>,
    onBack: () -> Unit,
    onRestore: (Entry) -> Unit,
    onDeletePermanently: (Entry) -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingPermanentDelete by remember { mutableStateOf<Entry?>(null) }
    var pendingEmptyTrash by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val listItems = remember(entries, today) {
        buildLogItems(entries = entries, contentQuery = "", today = today)
    }

    pendingPermanentDelete?.let { entry ->
        EntryConfirmationDialog(
            title = "Delete permanently?",
            message = "This entry cannot be recovered after permanent deletion.",
            confirmLabel = "Delete permanently",
            onConfirm = {
                pendingPermanentDelete = null
                onDeletePermanently(entry)
            },
            onDismiss = { pendingPermanentDelete = null },
        )
    }

    if (pendingEmptyTrash) {
        EntryConfirmationDialog(
            title = "Empty trash?",
            message = "Every entry in Trash will be permanently deleted. This cannot be undone.",
            confirmLabel = "Empty trash",
            onConfirm = {
                pendingEmptyTrash = false
                onEmptyTrash()
            },
            onDismiss = { pendingEmptyTrash = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TrashHeader(
            hasEntries = entries.isNotEmpty(),
            onBack = onBack,
            onEmptyTrash = { pendingEmptyTrash = true },
        )
        Text(
            text = "Restore an entry or delete it permanently.",
            color = Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 22.dp, top = 8.dp, bottom = 8.dp),
        )

        if (listItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "TRASH IS EMPTY",
                    color = Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 22.dp,
                    top = 8.dp,
                    end = 8.dp,
                    bottom = 24.dp,
                ),
            ) {
                items(
                    items = listItems,
                    key = { item ->
                        when (item) {
                            is LogListItem.DayDivider -> "trash-day-${item.date}"
                            is LogListItem.EntryRow -> "trash-entry-${item.entry.timestamp}"
                        }
                    },
                ) { item ->
                    when (item) {
                        is LogListItem.DayDivider -> TrashDayDivider(item.label)
                        is LogListItem.EntryRow -> TrashEntryRow(
                            entry = item.entry,
                            onRestore = { onRestore(item.entry) },
                            onDeletePermanently = {
                                pendingPermanentDelete = item.entry
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashHeader(
    hasEntries: Boolean,
    onBack: () -> Unit,
    onEmptyTrash: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(start = 10.dp, end = 6.dp),
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
            text = "STREAM / TRASH",
            color = Ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.4.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onEmptyTrash,
            enabled = hasEntries,
        ) {
            Text(
                text = "EMPTY TRASH",
                color = if (hasEntries) Accent else Muted.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

@Composable
private fun TrashDayDivider(label: String) {
    Text(
        text = label,
        color = Muted,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.45.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 11.dp),
    )
}

@Composable
private fun TrashEntryRow(
    entry: Entry,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = formatEntryTime(entry.timestamp),
            color = Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier
                .width(55.dp)
                .padding(top = 8.dp),
        )
        Text(
            text = entry.text,
            color = Ink,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(top = 5.dp, bottom = 5.dp),
        )
        IconButton(
            onClick = onRestore,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_restore),
                contentDescription = "Restore entry",
                tint = Ink,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(
            onClick = onDeletePermanently,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_trash),
                contentDescription = "Delete entry permanently",
                tint = Muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
