package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ComputedNodeStyle
import com.example.engine.ConsoleLogEntry
import com.example.engine.LogLevel
import com.example.engine.NativeDomNode
import com.example.ui.theme.ConsoleErrorRed
import com.example.ui.theme.ConsoleInfoBlue
import com.example.ui.theme.ConsoleWarnAmber
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusSavedGreenLight
import com.example.ui.theme.uiSp
import kotlin.math.roundToInt

/**
 * Clean Material 3 Native DOM Live Preview Panel (Zero WebView)
 * Supports interactive 1:1 vertical finger dragging on the top handle / header bar to resize bigger or smaller.
 */
@Composable
fun NativeDomPreviewPanel(
    domNodes: List<NativeDomNode>,
    bodyStyle: ComputedNodeStyle,
    onDomButtonClick: (String) -> Unit,
    onRefreshRunJs: () -> Unit,
    onClose: (() -> Unit)? = null,
    isRecordingMode: Boolean = false,
    autoScrollPreview: Boolean = false,
    maxBodyHeight: Dp = 175.dp,
    onResizeDeltaDp: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bg = bodyStyle.backgroundColor ?: MaterialTheme.colorScheme.background
    val previewScrollState = rememberScrollState()
    val density = LocalDensity.current

    val resizeDraggableState = rememberDraggableState { deltaPx ->
        if (onResizeDeltaDp != null) {
            // Dragging UP (deltaPx < 0) increases height; dragging DOWN (deltaPx > 0) decreases height
            val deltaDp = with(density) { (-deltaPx).toDp().value }
            onResizeDeltaDp(deltaDp)
        }
    }

    androidx.compose.runtime.LaunchedEffect(autoScrollPreview) {
        if (!autoScrollPreview) return@LaunchedEffect
        while (true) {
            if (!previewScrollState.isScrollInProgress) {
                val target = previewScrollState.maxValue
                val current = previewScrollState.value
                val diff = target - current
                if (diff > 0) {
                    androidx.compose.runtime.withFrameNanos { }
                    val step = kotlin.math.max(2f, diff * 0.28f).coerceAtMost(diff.toFloat())
                    previewScrollState.dispatchRawDelta(step)
                } else {
                    kotlinx.coroutines.delay(32L)
                }
            } else {
                kotlinx.coroutines.delay(32L)
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("native_live_preview_panel"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            // Top Finger-Drag Resize Handle + Header Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .then(
                        if (onResizeDeltaDp != null) {
                            Modifier
                                .draggable(
                                    orientation = Orientation.Vertical,
                                    state = resizeDraggableState
                                )
                                .testTag("preview_resize_drag_handle")
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (onResizeDeltaDp != null) {
                    // Sleek Finger Drag Pill at the top edge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                        )
                    }
                }

                // Compact Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StatusSavedGreenLight)
                        )
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = "Live Preview",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isRecordingMode) "Live Output" else "Live Preview",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (onResizeDeltaDp != null && !isRecordingMode) {
                            Text(
                                text = "↕ ${maxBodyHeight.value.roundToInt()}dp",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 9.uiSp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isRecordingMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onRefreshRunJs,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("rerun_js_preview_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload Preview",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (onClose != null) {
                                IconButton(
                                    onClick = onClose,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("close_preview_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Preview",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Drag ↕ to Resize • 2× Tap to Hide",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.uiSp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Rendered DOM Output Area (1:1 finger-controlled height when resizable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onResizeDeltaDp != null) {
                            Modifier.height(maxBodyHeight)
                        } else {
                            Modifier.heightIn(min = 90.dp, max = maxBodyHeight)
                        }
                    )
                    .background(bg)
                    .verticalScroll(previewScrollState)
                    .padding((bodyStyle.paddingDp.coerceIn(10f, 16f)).dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (domNodes.isEmpty()) {
                    Text(
                        text = "No elements to preview yet. Add HTML in index.html.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    domNodes.forEach { node ->
                        RenderNativeDomNode(
                            node = node,
                            inheritedTextColor = bodyStyle.textColor ?: MaterialTheme.colorScheme.onSurface,
                            onDomButtonClick = onDomButtonClick
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderNativeDomNode(
    node: NativeDomNode,
    inheritedTextColor: Color,
    onDomButtonClick: (String) -> Unit
) {
    val style = node.style
    val textColor = style.textColor ?: inheritedTextColor
    val bgColor = style.backgroundColor ?: Color.Transparent
    val borderColor = style.borderColor
    val cornerRadius = style.borderRadiusDp.coerceIn(0f, 20f).dp
    val pad = style.paddingDp.coerceIn(0f, 16f).dp

    when (node.tag) {
        "button" -> {
            Button(
                onClick = {
                    node.onClickFunction?.let { fn -> onDomButtonClick(fn) }
                },
                shape = RoundedCornerShape(if (style.borderRadiusDp > 0f) cornerRadius else 8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = style.backgroundColor ?: MaterialTheme.colorScheme.primary,
                    contentColor = style.textColor ?: MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .testTag("dom_btn_${node.id.ifEmpty { node.uid }}")
            ) {
                Text(
                    text = node.text.ifEmpty { "Button" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (style.fontSizeSp ?: 11f).uiSp
                )
            }
        }

        "progress" -> {
            val fraction = ((node.progressValue ?: 50f) / node.progressMax.coerceAtLeast(1f)).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        "hr" -> {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        }

        "h1", "h2", "h3", "h4", "h5", "h6", "p", "span", "label", "strong", "b", "em", "i", "small", "code" -> {
            val defaultFontSize = when (node.tag) {
                "h1" -> 15f
                "h2" -> 13.5f
                "h3" -> 12.5f
                "h4", "h5", "h6" -> 11.5f
                "span", "small", "code" -> 10f
                else -> 11.5f
            }
            val defaultBold = node.tag.startsWith("h") ||
                node.tag == "strong" ||
                node.tag == "b" ||
                style.isBold ||
                "badge" in node.classes
            Box(
                modifier = Modifier
                    .then(
                        if (bgColor != Color.Transparent) {
                            Modifier
                                .clip(RoundedCornerShape(cornerRadius))
                                .background(bgColor)
                        } else Modifier
                    )
                    .then(
                        if (borderColor != null) {
                            Modifier.border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
                        } else Modifier
                    )
                    .then(
                        if (node.onClickFunction != null) {
                            Modifier.clickable { onDomButtonClick(node.onClickFunction) }
                        } else Modifier
                    )
                    .padding(pad)
            ) {
                Text(
                    text = node.text,
                    color = textColor,
                    fontSize = ((style.fontSizeSp ?: defaultFontSize) * 0.92f).uiSp,
                    lineHeight = (((style.fontSizeSp ?: defaultFontSize) * 0.92f) * 1.32f).uiSp,
                    fontWeight = if (defaultBold) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }

        else -> {
            val isActionRow = "action-row" in node.classes || "metrics-grid" in node.classes
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(bgColor)
                    .then(
                        if (borderColor != null) {
                            Modifier.border(
                                width = 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(cornerRadius)
                            )
                        } else Modifier
                    )
                    .then(
                        if (node.onClickFunction != null) {
                            Modifier.clickable { onDomButtonClick(node.onClickFunction) }
                        } else Modifier
                    )
                    .padding(if (pad > 0.dp) pad else 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (node.text.isNotBlank()) {
                    Text(
                        text = node.text,
                        color = textColor,
                        fontSize = ((style.fontSizeSp ?: 11.5f) * 0.92f).uiSp
                    )
                }
                if (isActionRow) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        node.children.forEach { child ->
                            RenderNativeDomNode(
                                node = child,
                                inheritedTextColor = textColor,
                                onDomButtonClick = onDomButtonClick
                            )
                        }
                    }
                } else {
                    node.children.forEach { child ->
                        RenderNativeDomNode(
                            node = child,
                            inheritedTextColor = textColor,
                            onDomButtonClick = onDomButtonClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean Material 3 Integrated Console Panel
 */
@Composable
fun IntegratedConsolePanel(
    logs: List<ConsoleLogEntry>,
    activeFilter: LogLevel?,
    onSelectFilter: (LogLevel?) -> Unit,
    onSimulateError: () -> Unit,
    onClearConsole: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredLogs = remember(logs, activeFilter) {
        if (activeFilter == null) logs else logs.filter { it.level == activeFilter }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("integrated_console_panel"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            // Compact 38.dp Header with inline filters & action icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Terminal,
                        contentDescription = "Console",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Console (${filteredLogs.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSimulateError,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("inject_test_error_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Test Stack Trace Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onClearConsole,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("clear_console_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Console",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("close_console_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Console",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Scrollable Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = activeFilter == null,
                    onClick = { onSelectFilter(null) },
                    label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = activeFilter == LogLevel.LOG,
                    onClick = { onSelectFilter(if (activeFilter == LogLevel.LOG) null else LogLevel.LOG) },
                    label = { Text("Logs", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = activeFilter == LogLevel.WARN,
                    onClick = { onSelectFilter(if (activeFilter == LogLevel.WARN) null else LogLevel.WARN) },
                    label = { Text("Warnings", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = activeFilter == LogLevel.ERROR,
                    onClick = { onSelectFilter(if (activeFilter == LogLevel.ERROR) null else LogLevel.ERROR) },
                    label = { Text("Errors", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp)
                )
            }

            // Console Entries
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 85.dp, max = 140.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (filteredLogs.isEmpty()) {
                    item {
                        Text(
                            text = "No console output for this filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }
                } else {
                    items(filteredLogs, key = { it.id }) { entry ->
                        ConsoleLogItemRow(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleLogItemRow(entry: ConsoleLogEntry) {
    var expanded by remember(entry.id) { mutableStateOf(entry.level == LogLevel.ERROR) }
    val badgeColor = when (entry.level) {
        LogLevel.LOG -> StatusSavedGreenLight
        LogLevel.INFO -> ConsoleInfoBlue
        LogLevel.WARN -> ConsoleWarnAmber
        LogLevel.ERROR -> ConsoleErrorRed
        LogLevel.SYSTEM -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (entry.level == LogLevel.ERROR) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            )
            .clickable(enabled = entry.stackTrace != null) { expanded = !expanded }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = entry.message,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.uiSp,
                    color = if (entry.level == LogLevel.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.sourceLocation,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.uiSp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.stackTrace != null) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Stack Trace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (entry.stackTrace != null) {
            AnimatedVisibility(visible = expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = entry.stackTrace,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.uiSp,
                        lineHeight = 12.5.uiSp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
