package com.seif.stream.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seif.stream.R
import com.seif.stream.data.Entry
import com.seif.stream.ui.theme.Accent
import com.seif.stream.ui.theme.Ink
import com.seif.stream.ui.theme.Muted
import com.seif.stream.ui.theme.Paper
import com.seif.stream.util.formatEntryTime
import com.seif.stream.util.parseIsoDate
import java.time.LocalDate

private enum class SearchMode {
    Content,
    Date,
}

@Composable
fun LogScreen(
    entries: List<Entry>,
    onFreshCapture: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchMode by rememberSaveable { mutableStateOf(SearchMode.Content) }
    var contentQuery by rememberSaveable { mutableStateOf("") }
    var dateQuery by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val today = LocalDate.now()
    val listItems = remember(entries, contentQuery, searchMode, today) {
        buildLogItems(
            entries = entries,
            contentQuery = if (searchMode == SearchMode.Content) contentQuery else "",
            today = today,
        )
    }
    val jumpDate = if (searchMode == SearchMode.Date) parseIsoDate(dateQuery) else null
    val jumpIndex = jumpDate?.let { dividerIndexForDate(listItems, it) } ?: -1

    LaunchedEffect(jumpDate, jumpIndex) {
        if (jumpDate != null && jumpIndex >= 0) {
            listState.scrollToItem(jumpIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            LogHeader(onOpenSettings = onOpenSettings)
            SearchControls(
                mode = searchMode,
                onModeChange = { searchMode = it },
                contentQuery = contentQuery,
                onContentQueryChange = { contentQuery = it },
                dateQuery = dateQuery,
                onDateQueryChange = { next ->
                    dateQuery = next
                        .filter { character -> character.isDigit() || character == '-' }
                        .take(10)
                },
                dateHasNoMatch = dateQuery.length == 10 && jumpDate != null && jumpIndex < 0,
            )

            if (listItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (entries.isEmpty()) "NO ENTRIES YET" else "NO MATCHES",
                        color = Muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 22.dp,
                        top = 12.dp,
                        end = 22.dp,
                        bottom = 104.dp,
                    ),
                ) {
                    items(
                        items = listItems,
                        key = { item ->
                            when (item) {
                                is LogListItem.DayDivider -> "day-${item.date}"
                                is LogListItem.EntryRow -> "entry-${item.entry.timestamp}"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is LogListItem.DayDivider -> DayDivider(item.label)
                            is LogListItem.EntryRow -> EntryRow(item.entry)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onFreshCapture,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 20.dp)
                .size(56.dp),
            shape = CircleShape,
            containerColor = Accent,
            contentColor = Paper,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
            ),
        ) {
            Text(
                text = "+",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Light,
                fontSize = 31.sp,
            )
        }
    }
}

@Composable
private fun LogHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(start = 22.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "STREAM / LOG",
            color = Ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "Settings",
                tint = Ink,
            )
        }
    }
}

@Composable
private fun SearchControls(
    mode: SearchMode,
    onModeChange: (SearchMode) -> Unit,
    contentQuery: String,
    onContentQueryChange: (String) -> Unit,
    dateQuery: String,
    onDateQueryChange: (String) -> Unit,
    dateHasNoMatch: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            SearchMode.entries.forEach { option ->
                val active = option == mode
                Text(
                    text = option.name.uppercase(),
                    color = if (active) Accent else Muted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier
                        .clickable { onModeChange(option) }
                        .padding(vertical = 8.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = if (
                    (mode == SearchMode.Content && contentQuery.isNotEmpty()) ||
                    (mode == SearchMode.Date && dateQuery.isNotEmpty())
                ) Accent else Muted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(11.dp))
            val value = if (mode == SearchMode.Content) contentQuery else dateQuery
            val placeholder = if (mode == SearchMode.Content) "Search entry text" else "YYYY-MM-DD"
            BasicTextField(
                value = value,
                onValueChange = if (mode == SearchMode.Content) onContentQueryChange else onDateQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (mode == SearchMode.Date) KeyboardType.Ascii else KeyboardType.Text,
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = Muted.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        if (dateHasNoMatch) {
            Text(
                text = "no entries on this date",
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 29.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun DayDivider(label: String) {
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
private fun EntryRow(entry: Entry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = formatEntryTime(entry.timestamp),
            color = Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(55.dp),
        )
        Text(
            text = entry.text,
            color = Ink,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
