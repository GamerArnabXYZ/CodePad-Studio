package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CodeTab
import com.example.engine.ExportFormat
import com.example.engine.SyntaxHighlightVisualTransformation
import com.example.ui.components.EditorSettingsSheet
import com.example.ui.components.ExportProjectSheet
import com.example.ui.components.ExportResultDialog
import com.example.ui.components.IntegratedConsolePanel
import com.example.ui.components.NativeDomPreviewPanel
import com.example.ui.components.RenameProjectDialog
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalEditorPalette
import com.example.ui.theme.uiSp
import com.example.ui.theme.StatusSavedGreenBgDark
import com.example.ui.theme.StatusSavedGreenBgLight
import com.example.ui.theme.StatusSavedGreenDark
import com.example.ui.theme.StatusSavedGreenLight
import com.example.ui.theme.StatusUnsavedOrangeBgDark
import com.example.ui.theme.StatusUnsavedOrangeBgLight
import com.example.ui.theme.StatusUnsavedOrangeDark
import com.example.ui.theme.StatusUnsavedOrangeLight
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CodePadStudioScreen(
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val editorPalette = LocalEditorPalette.current
    var pendingSafFormat by remember { mutableStateOf(ExportFormat.BUNDLED_HTML) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToUserUri(context, uri, pendingSafFormat)
        }
    }

    var textFieldValue by remember(uiState.activeTab) {
        mutableStateOf(
            TextFieldValue(
                text = uiState.activeCode,
                selection = TextRange(0)
            )
        )
    }

    LaunchedEffect(uiState.activeCode, uiState.activeTab) {
        if (textFieldValue.text != uiState.activeCode) {
            val safeSelection = textFieldValue.selection.start.coerceIn(0, uiState.activeCode.length)
            textFieldValue = TextFieldValue(
                text = uiState.activeCode,
                selection = TextRange(safeSelection)
            )
        }
    }

    val visualTransformation = remember(uiState.activeTab, editorPalette) {
        SyntaxHighlightVisualTransformation(uiState.activeTab, editorPalette)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Balanced Top App Bar (Two-line Title + Saved/Unsaved Status on left, 4 clean action buttons on right)
        IdeTopAppBar(
            projectName = uiState.projectName,
            isSaved = uiState.isSaved,
            isDarkTheme = uiState.isDarkTheme,
            onEditProjectName = { viewModel.setRenameDialogOpen(true) },
            onFormatCode = { viewModel.formatActiveTabCode() },
            onToggleTheme = { viewModel.toggleTheme() },
            onOpenExport = { viewModel.setExportSheetOpen(true) },
            onOpenSettings = { viewModel.setSettingsSheetOpen(true) }
        )

        // 2. Native Material 3 PrimaryTabRow (index.html, style.css, script.js)
        PrimaryTabRow(
            selectedTabIndex = uiState.activeTab.index,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            CodeTab.entries.forEach { tab ->
                val selected = tab == uiState.activeTab
                Tab(
                    selected = selected,
                    onClick = { viewModel.selectTab(tab) },
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("editor_tab_${tab.extension}"),
                    text = {
                        Text(
                            text = tab.fileName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 3. Synchronized Code Editor with Exact Line-Height Gutter
        SynchronizedCodeEditorWithGutter(
            textFieldValue = textFieldValue,
            onValueChange = { updatedValue ->
                val textChanged = updatedValue.text != textFieldValue.text
                textFieldValue = updatedValue
                if (textChanged) {
                    viewModel.onCodeChanged(updatedValue.text, updatedValue.selection.start)
                } else {
                    viewModel.onCursorPositionChanged(updatedValue.text, updatedValue.selection.start)
                }
            },
            visualTransformation = visualTransformation,
            fontSizeSp = uiState.editorFontSize,
            currentLine = uiState.currentLine,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        // 4. Collapsible Live Preview or Console Panel
        AnimatedVisibility(
            visible = uiState.isPreviewVisible,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                NativeDomPreviewPanel(
                    domNodes = uiState.domNodes,
                    bodyStyle = uiState.bodyStyle,
                    onDomButtonClick = { fn -> viewModel.onNativeDomButtonClicked(fn) },
                    onRefreshRunJs = { viewModel.rebuildNativePreview(runTopLevelJs = true) },
                    onClose = { viewModel.togglePreview() },
                    maxBodyHeight = uiState.previewSectionHeightDp.dp,
                    onResizeDeltaDp = { deltaDp -> viewModel.adjustPreviewSectionHeight(deltaDp) }
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.isConsoleVisible,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                IntegratedConsolePanel(
                    logs = uiState.consoleLogs,
                    activeFilter = uiState.consoleFilter,
                    onSelectFilter = { viewModel.setConsoleFilter(it) },
                    onSimulateError = { viewModel.triggerSimulatedStackTraceError() },
                    onClearConsole = { viewModel.clearConsole() },
                    onClose = { viewModel.toggleConsole() }
                )
            }
        }

        // 5. Full-Width Quick Symbol Bar (dedicated 38.dp row so symbols never collide with Preview/Console buttons)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        QuickSymbolBar(
            onInsertSymbol = { sym ->
                val currentText = textFieldValue.text
                val start = textFieldValue.selection.start.coerceIn(0, currentText.length)
                val end = textFieldValue.selection.end.coerceIn(0, currentText.length)
                val newText = currentText.replaceRange(start, end, sym)
                val newCursor = start + sym.length
                textFieldValue = TextFieldValue(
                    text = newText,
                    selection = TextRange(newCursor)
                )
                viewModel.onCodeChanged(newText, newCursor)
            }
        )

        // 6. Unified Bottom Status & Panel Bar (Ln X, Col Y + Language Tag on left, Preview & Console toggles on right)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        IdeBottomStatusAndToolsBar(
            currentLine = uiState.currentLine,
            currentColumn = uiState.currentColumn,
            activeTab = uiState.activeTab,
            fontSize = uiState.editorFontSize,
            isPreviewVisible = uiState.isPreviewVisible,
            isConsoleVisible = uiState.isConsoleVisible,
            errorCount = uiState.errorCount,
            onTogglePreview = { viewModel.togglePreview() },
            onToggleConsole = { viewModel.toggleConsole() }
        )
    }

    // Dialogs & Modal Bottom Sheets
    if (uiState.isRenameDialogOpen) {
        RenameProjectDialog(
            currentName = uiState.projectName,
            onConfirm = { viewModel.updateProjectName(it) },
            onDismiss = { viewModel.setRenameDialogOpen(false) }
        )
    }

    if (uiState.isSettingsSheetOpen) {
        EditorSettingsSheet(
            currentFontSize = uiState.editorFontSize,
            autoWriteFontSize = uiState.autoWriteFontSize,
            uiFontScale = uiState.uiFontScale,
            isDarkTheme = uiState.isDarkTheme,
            onFontSizeChange = { viewModel.updateEditorFontSize(it) },
            onAutoWriteFontSizeChange = { viewModel.updateAutoWriteFontSize(it) },
            onUiFontScaleChange = { viewModel.updateUiFontScale(it) },
            onApplyFontPreset = { ed, aw, sc -> viewModel.applyFontSizePreset(ed, aw, sc) },
            onThemeChange = { viewModel.setDarkTheme(it) },
            onSelectPreset = { viewModel.loadPreset(it) },
            onDismiss = { viewModel.setSettingsSheetOpen(false) }
        )
    }

    if (uiState.isExportSheetOpen) {
        ExportProjectSheet(
            projectName = uiState.projectName,
            onExportAppStorage = { format ->
                viewModel.exportToAppStorage(context, format)
            },
            onExportDeviceFilePicker = { format ->
                pendingSafFormat = format
                val safeName = uiState.projectName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                createDocumentLauncher.launch("$safeName.${format.extension}")
            },
            onDismiss = { viewModel.setExportSheetOpen(false) }
        )
    }

    uiState.latestExportResult?.let { exportResult ->
        ExportResultDialog(
            result = exportResult,
            onDismiss = { viewModel.dismissExportResult() }
        )
    }
}

/**
 * Balanced Top Bar:
 * Left Column: Project Name + Edit icon on top line, Saved/Unsaved status pill on second line.
 * This prevents the Project Name and Status Badge from fighting for horizontal space on 360dp screens!
 */
@Composable
private fun IdeTopAppBar(
    projectName: String,
    isSaved: Boolean,
    isDarkTheme: Boolean,
    onEditProjectName: () -> Unit,
    onFormatCode: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val statusTextColor by animateColorAsState(
        targetValue = when {
            isSaved && isDarkTheme -> StatusSavedGreenDark
            isSaved && !isDarkTheme -> StatusSavedGreenLight
            !isSaved && isDarkTheme -> StatusUnsavedOrangeDark
            else -> StatusUnsavedOrangeLight
        },
        animationSpec = tween(220),
        label = "statusTextColor"
    )
    val statusBgColor by animateColorAsState(
        targetValue = when {
            isSaved && isDarkTheme -> StatusSavedGreenBgDark
            isSaved && !isDarkTheme -> StatusSavedGreenBgLight
            !isSaved && isDarkTheme -> StatusUnsavedOrangeBgDark
            else -> StatusUnsavedOrangeBgLight
        },
        animationSpec = tween(220),
        label = "statusBgColor"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Two-line Project Title + Saved/Unsaved Status Badge
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEditProjectName)
                    .padding(vertical = 2.dp, horizontal = 2.dp)
                    .testTag("edit_project_name_button"),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = projectName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Rename Project",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Saved (Green) / Unsaved (Orange) Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(statusBgColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("save_status_badge")
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusTextColor)
                    )
                    Text(
                        text = if (isSaved) "Saved" else "Unsaved",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.uiSp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusTextColor
                    )
                }
            }

            // Right: 4 Action Buttons with clean 42.dp size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onFormatCode,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("format_code_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = "Format Code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("toggle_theme_button")
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = "Toggle Light or Dark Theme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onOpenExport,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("export_project_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = "Export Project",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("editor_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Editor Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Custom Code Editor with Synchronized Line Number Gutter.
 * Uses identical PlatformTextStyle(includeFontPadding = false) and fixed row height
 * so every line number aligns pixel-perfectly with its code line!
 */
@Composable
private fun SynchronizedCodeEditorWithGutter(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    visualTransformation: SyntaxHighlightVisualTransformation,
    fontSizeSp: Float,
    currentLine: Int,
    modifier: Modifier = Modifier
) {
    val palette = LocalEditorPalette.current
    val density = LocalDensity.current
    val sharedVerticalScroll = rememberScrollState()
    val horizontalCodeScroll = rememberScrollState()

    LaunchedEffect(visualTransformation) {
        sharedVerticalScroll.scrollTo(0)
        horizontalCodeScroll.scrollTo(0)
    }

    val lineCount = remember(textFieldValue.text) {
        max(1, textFieldValue.text.count { it == '\n' } + 1)
    }

    val lineHeightSp = (fontSizeSp * 1.5f)
    val lineHeightPx = with(density) { lineHeightSp.sp.toPx() }
    val verticalPaddingPx = with(density) { 12.dp.toPx() }

    val sharedCodeTextStyle = remember(fontSizeSp, palette.plainText) {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = fontSizeSp.sp,
            lineHeight = lineHeightSp.sp,
            color = palette.plainText,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None
            )
        )
    }

    val gutterAnnotatedNumbers = remember(lineCount, currentLine, palette.gutterText, palette.gutterActiveText) {
        buildAnnotatedString {
            for (lineIdx in 1..lineCount) {
                val isCurrent = lineIdx == currentLine
                withStyle(
                    SpanStyle(
                        color = if (isCurrent) palette.gutterActiveText else palette.gutterText,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                ) {
                    append(lineIdx.toString())
                }
                if (lineIdx < lineCount) {
                    append('\n')
                }
            }
        }
    }

    Row(
        modifier = modifier
            .background(palette.editorBackground)
    ) {
        // Left: Single-node Line Numbers Gutter for O(1) layout even on 1,000+ line files
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .background(palette.gutterBackground)
                .verticalScroll(sharedVerticalScroll)
                .drawBehind {
                    val activeIdx = (currentLine - 1).coerceAtLeast(0)
                    val topY = verticalPaddingPx + (activeIdx * lineHeightPx)
                    drawRect(
                        color = palette.activeLineBackground,
                        topLeft = Offset(0f, topY),
                        size = Size(size.width, lineHeightPx)
                    )
                }
                .padding(vertical = 12.dp, horizontal = 8.dp)
                .testTag("editor_line_gutter"),
            contentAlignment = Alignment.TopEnd
        ) {
            Text(
                text = gutterAnnotatedNumbers,
                style = sharedCodeTextStyle.copy(
                    textAlign = TextAlign.End
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 1.dp Divider between Gutter and Code
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(palette.divider)
        )

        // Right: Syntax-Highlighted Code Editor Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(palette.editorBackground)
                .verticalScroll(sharedVerticalScroll)
                .horizontalScroll(horizontalCodeScroll)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = onValueChange,
                textStyle = sharedCodeTextStyle,
                cursorBrush = SolidColor(palette.cursor),
                visualTransformation = visualTransformation,
                modifier = Modifier
                    .defaultMinSize(minWidth = 320.dp, minHeight = 260.dp)
                    .fillMaxSize()
                    .testTag("code_editor_input")
            )
        }
    }
}

/**
 * Dedicated Full-Width Quick Symbol Bar (38.dp tall) so symbols are easy to tap and never cramped.
 */
@Composable
private fun QuickSymbolBar(
    onInsertSymbol: (String) -> Unit
) {
    val symbols = listOf("  ", "<", ">", "/", "=", "\"", "'", "{", "}", "(", ")", ";", ":", ".", "#")

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            symbols.forEach { sym ->
                val label = if (sym == "  ") "Tab" else sym
                Surface(
                    onClick = { onInsertSymbol(sym) },
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = label,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.uiSp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Unified Bottom Status Bar & Preview/Console Toggle Bar (42.dp tall).
 * Left: Ln X, Col Y • Active Language Tag • Font Size
 * Right: Compact Preview & Console toggle pills
 */
@Composable
private fun IdeBottomStatusAndToolsBar(
    currentLine: Int,
    currentColumn: Int,
    activeTab: CodeTab,
    fontSize: Float,
    isPreviewVisible: Boolean,
    isConsoleVisible: Boolean,
    errorCount: Int,
    onTogglePreview: () -> Unit,
    onToggleConsole: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .testTag("ide_bottom_status_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Line/Column indicator + Language badge + Font size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Ln $currentLine, Col $currentColumn",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.uiSp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("status_line_indicator")
                )

                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.testTag("active_language_tag")
                ) {
                    Text(
                        text = activeTab.languageTag,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.uiSp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "${fontSize.roundToInt()}px",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.uiSp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Right: Compact Preview & Console Toggle Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompactPanelTogglePill(
                    label = "Preview",
                    icon = Icons.Outlined.Visibility,
                    selected = isPreviewVisible,
                    badgeCount = 0,
                    onClick = onTogglePreview,
                    testTag = "toggle_preview_button"
                )

                CompactPanelTogglePill(
                    label = "Console",
                    icon = Icons.Outlined.Terminal,
                    selected = isConsoleVisible,
                    badgeCount = errorCount,
                    onClick = onToggleConsole,
                    testTag = "toggle_console_button"
                )
            }
        }
    }
}

@Composable
private fun CompactPanelTogglePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .height(30.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 8.5.uiSp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }
    }
}
