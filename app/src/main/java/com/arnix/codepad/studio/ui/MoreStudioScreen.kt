package com.arnix.codepad.studio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arnix.codepad.studio.data.ProjectPreset
import com.arnix.codepad.studio.data.ProjectTemplates
import com.arnix.codepad.studio.engine.ExportFormat
import com.arnix.codepad.studio.engine.TypingSoundEngine
import com.arnix.codepad.studio.engine.TypingSoundProfile
import com.arnix.codepad.studio.ui.components.ExportProjectSheet
import com.arnix.codepad.studio.ui.components.ExportResultDialog
import com.arnix.codepad.studio.ui.components.RenameProjectDialog
import com.arnix.codepad.studio.ui.theme.JetBrainsMonoFontFamily
import com.arnix.codepad.studio.ui.theme.uiSp
import com.arnix.codepad.studio.viewmodel.AutoDurationTarget
import com.arnix.codepad.studio.viewmodel.EndCtaStyle
import com.arnix.codepad.studio.viewmodel.ShortsBackdropPreset
import com.arnix.codepad.studio.viewmodel.StudioModule
import com.arnix.codepad.studio.viewmodel.StudioUiState
import com.arnix.codepad.studio.viewmodel.StudioViewModel
import com.arnix.codepad.studio.viewmodel.ViralHookStyle
import com.arnix.codepad.studio.viewmodel.WatermarkPosition
import com.arnix.codepad.studio.viewmodel.WatermarkStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreStudioScreen(
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var pendingSafFormat by remember { mutableStateOf(ExportFormat.BUNDLED_HTML) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToUserUri(context, uri, pendingSafFormat)
        }
    }

    val onOpenSafExport: (ExportFormat) -> Unit = { format ->
        pendingSafFormat = format
        val safeName = uiState.projectName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        createDocumentLauncher.launch("$safeName.${format.extension}")
    }

    val categories = listOf(
        "All Sections",
        "Theme & UI",
        "Editor Font",
        "Project & Export",
        "Starter Templates",
        "Typing Sounds",
        "Speed & Duration",
        "Watermark",
        "Viral Hook",
        "End CTA",
        "Video Backdrop",
        "Recording Flow",
        "About App",
        "About Developer"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("more_screen_container")
    ) {
        // Top App Bar for More Hub
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Studio Hub & Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Har setting alag-alag section mein organized hai",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Quick Theme Toggle Button
                    IconButton(
                        onClick = { viewModel.toggleTheme() },
                        modifier = Modifier.testTag("more_quick_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (uiState.isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEachIndexed { index, title ->
                        FilterChip(
                            selected = selectedCategoryIndex == index,
                            onClick = { selectedCategoryIndex = index },
                            label = { Text(title) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("more_chip_$index")
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Main Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick Navigation Hub Banner
            QuickNavBannerCard(
                projectName = uiState.projectName,
                activeTabName = uiState.activeTab.fileName,
                onOpenEditor = { viewModel.selectModule(StudioModule.CODEPAD_IDE) },
                onOpenRec = { viewModel.selectModule(StudioModule.AUTO_WRITE_STUDIO) }
            )

            // Section 1: Display & Theme Settings
            if (selectedCategoryIndex == 0 || selectedCategoryIndex == 1) {
                SectionCard(
                    title = "Theme & Appearance",
                    subtitle = "Dark mode aur UI visual scales",
                    icon = Icons.Filled.FormatPaint
                ) {
                    DisplayAndThemeSectionContent(
                        isDarkTheme = uiState.isDarkTheme,
                        uiFontScale = uiState.uiFontScale,
                        onToggleTheme = { viewModel.toggleTheme() },
                        onUiFontScaleChange = { viewModel.updateUiFontScale(it) }
                    )
                }
            }

            // Section 2: Editor Font & Typography
            if (selectedCategoryIndex == 0 || selectedCategoryIndex == 2) {
                SectionCard(
                    title = "Editor Typography & Font",
                    subtitle = "Code text size presets aur interactive slider",
                    icon = Icons.Filled.TextFields
                ) {
                    EditorTypographySectionContent(
                        fontSize = uiState.editorFontSize,
                        onFontSizePreset = { ed, aw, sc -> viewModel.applyFontSizePreset(ed, aw, sc) },
                        onFontSizeChange = { viewModel.updateEditorFontSize(it) }
                    )
                }
            }

            // Section 3: Project Actions & Export
            if (selectedCategoryIndex == 0 || selectedCategoryIndex == 3) {
                SectionCard(
                    title = "Project Management & Export",
                    subtitle = "Rename project, format code, aur files export karein",
                    icon = Icons.Filled.FileDownload
                ) {
                    ProjectActionsSectionContent(
                        onRename = { viewModel.setRenameDialogOpen(true) },
                        onFormat = { viewModel.formatActiveTabCode() },
                        onSplitHtml = { viewModel.splitEmbeddedHtmlIntoTabs() },
                        onExportHtml = { onOpenSafExport(ExportFormat.BUNDLED_HTML) },
                        onExportZip = { onOpenSafExport(ExportFormat.ZIP_ARCHIVE) }
                    )
                }
            }

            // Section 4: Starter Templates
            if (selectedCategoryIndex == 0 || selectedCategoryIndex == 4) {
                SectionCard(
                    title = "Starter Project Templates",
                    subtitle = "HTML, CSS & JS presets 1-tap me load karein",
                    icon = Icons.Filled.FolderSpecial
                ) {
                    StarterTemplatesSectionContent(
                        onLoadPreset = { viewModel.loadPreset(it) }
                    )
                }
            }

            // Section 5: Typing Sound Effects (Mechanical ASMR)
            if (selectedCategoryIndex == 0 || selectedCategoryIndex == 5) {
                SectionCard(
                    title = "ASMR Typing Sound Effects",
                    subtitle = "Realistic mechanical keyboard clicks test & toggle karein",
                    icon = Icons.Filled.VolumeUp
                ) {
                    TypingSoundSectionContent(
                        currentProfile = uiState.typingSoundProfile,
                        onSelectProfile = { profile ->
                            viewModel.selectTypingSoundProfile(profile)
                            TypingSoundEngine.playKeyTick(profile, 'a')
                        },
                        onTestSound = {
                            TypingSoundEngine.playKeyTick(uiState.typingSoundProfile, 'a')
                        }
                    )
                }
            }

            // Section 6: Target Duration & Speed
            if (selectedCategoryIndex == 0 || selectedCategoryIndex == 6) {
                SectionCard(
                    title = "Target Duration & Turbo Speed",
                    subtitle = "Video length targets (15s, 30s, 60s) aur 2× turbo",
                    icon = Icons.Filled.Speed
                ) {
                    SpeedAndDurationSectionContent(
                        currentTarget = uiState.autoDurationTarget,
                        isTurboMode = uiState.isTurboMode,
                        onSelectTarget = { viewModel.selectAutoDurationTarget(it) },
                        onToggleTurbo = { viewModel.toggleTurboMode() }
                    )
                }
            }

            // Section 7: Creator Watermark & Handle Branding
            if (selectedCategoryIndex == 0 || selectedCategoryIndex == 7) {
                SectionCard(
                    title = "Creator Watermark & Branding",
                    subtitle = "Apna social handle, position aur badge style set karein",
                    icon = Icons.Filled.BrandingWatermark
                ) {
                    WatermarkSectionContent(
                        creatorHandle = uiState.creatorHandle,
                        currentPosition = uiState.watermarkPosition,
                        currentStyle = uiState.watermarkStyle,
                        onUpdateHandle = { viewModel.updateCreatorHandle(it) },
                        onSelectPosition = { viewModel.selectWatermarkPosition(it) },
                        onSelectStyle = { viewModel.selectWatermarkStyle(it) }
                    )
                }
            }

            // Section 8: Zero-Editing Viral Hook Header Banner
            if (selectedCategoryIndex == 0 || selectedCategoryIndex == 8) {
                SectionCard(
                    title = "Viral Hook Header Banner",
                    subtitle = "Video hook text aur badge style customize karein",
                    icon = Icons.Filled.NotificationAdd
                ) {
                    ViralHookSectionContent(
                        showBanner = uiState.showViralHookBanner,
                        hookBadge = uiState.viralHookBadge,
                        hookTitle = uiState.viralHookTitle,
                        hookStyle = uiState.viralHookStyle,
                        onToggleBanner = { viewModel.toggleShowViralHookBanner() },
                        onUpdateBadge = { viewModel.updateViralHookBadge(it) },
                        onUpdateTitle = { viewModel.updateViralHookTitle(it) },
                        onSelectStyle = { viewModel.selectViralHookStyle(it) }
                    )
                }
            }

            // Section 9: Recording End CTA Overlay
            if (selectedCategoryIndex == 0 || selectedCategoryIndex == 9) {
                SectionCard(
                    title = "Recording End CTA Overlay",
                    subtitle = "Like, Comment & Subscribe animated overlay cards",
                    icon = Icons.Filled.NotificationAdd
                ) {
                    EndCtaSectionContent(
                        showEndCta = uiState.showEndCtaAnimation,
                        ctaMessage = uiState.endCtaMessage,
                        ctaStyle = uiState.endCtaStyle,
                        onToggleEndCta = { viewModel.toggleShowEndCtaAnimation() },
                        onUpdateMessage = { viewModel.updateEndCtaMessage(it) },
                        onSelectStyle = { viewModel.selectEndCtaStyle(it) }
                    )
                }
            }

            // Section 10: Shorts Backdrop Gradient Themes
            if (selectedCategoryIndex == 0 || selectedCategoryIndex == 10) {
                SectionCard(
                    title = "Shorts Backdrop Color Themes",
                    subtitle = "Cinematic vertical gradients for TikTok, Reels & YT Shorts",
                    icon = Icons.Filled.ColorLens
                ) {
                    BackdropSectionContent(
                        selectedIndex = uiState.backdropPresetIndex,
                        onSelectBackdrop = { viewModel.selectBackdropPreset(it) }
                    )
                }
            }

            // Section 11: Recording Flow & Sequence Controls
            if (selectedCategoryIndex == 0 || selectedCategoryIndex == 11) {
                SectionCard(
                    title = "Recording Flow & Automation",
                    subtitle = "3s countdown, auto-chain tabs aur auto-reveal preview",
                    icon = Icons.Filled.Timer
                ) {
                    RecordingFlowSectionContent(
                        autoWriteFontSize = uiState.autoWriteFontSize,
                        useCountdown = uiState.useRecordingCountdown,
                        isAutoChainTabs = uiState.isAutoChainTabs,
                        autoRevealPreview = uiState.autoRevealPreviewOnFinish,
                        onUpdateAutoWriteFontSize = { viewModel.updateAutoWriteFontSize(it) },
                        onToggleCountdown = { viewModel.toggleUseRecordingCountdown() },
                        onToggleAutoChain = { viewModel.toggleAutoChainTabs() },
                        onToggleAutoReveal = { viewModel.toggleAutoRevealPreview() }
                    )
                }
            }

            // Section 12: About CodePad Studio (Simple Hinglish App About)
            if (selectedCategoryIndex == 0 || selectedCategoryIndex == 12) {
                AppAboutSection()
            }

            // Section 13: About Developer (About Me)
            if (selectedCategoryIndex == 0 || selectedCategoryIndex == 13) {
                AboutDeveloperSection()
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }

    // Dialogs managed from More Hub
    if (uiState.isRenameDialogOpen) {
        RenameProjectDialog(
            currentName = uiState.projectName,
            onConfirm = { newName -> viewModel.updateProjectName(newName) },
            onDismiss = { viewModel.setRenameDialogOpen(false) }
        )
    }

    if (uiState.isExportSheetOpen) {
        ExportProjectSheet(
            projectName = uiState.projectName,
            onExportAppStorage = { format ->
                viewModel.exportToAppStorage(context, format)
                viewModel.setExportSheetOpen(false)
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
 * Reusable Dedicated Section Card Container for Cleaner UI Division
 */
@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            content()
        }
    }
}

/**
 * Quick Navigation Banner between Editor and Rec modules
 */
@Composable
private fun QuickNavBannerCard(
    projectName: String,
    activeTabName: String,
    onOpenEditor: () -> Unit,
    onOpenRec: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Active Project: $projectName",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Current Tab: $activeTabName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenEditor,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("more_nav_to_editor")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = "Open Editor",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Editor")
                }

                Button(
                    onClick = onOpenRec,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("more_nav_to_rec")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Videocam,
                        contentDescription = "Open Rec",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rec")
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SEPARATE DEDICATED SECTION CONTENTS
// -------------------------------------------------------------

@Composable
private fun DisplayAndThemeSectionContent(
    isDarkTheme: Boolean,
    uiFontScale: Float,
    onToggleTheme: () -> Unit,
    onUiFontScaleChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Dark Mode",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isDarkTheme) "Dark Studio theme enabled" else "Light theme enabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isDarkTheme,
                onCheckedChange = { onToggleTheme() },
                modifier = Modifier.testTag("more_dark_theme_switch")
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "App UI & Labels Scale",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${(uiFontScale * 100).roundToInt()}%",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = uiFontScale,
                onValueChange = onUiFontScaleChange,
                valueRange = 0.75f..1.30f,
                steps = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("more_ui_font_scale_slider")
            )
        }
    }
}

@Composable
private fun EditorTypographySectionContent(
    fontSize: Float,
    onFontSizePreset: (Float, Float, Float) -> Unit,
    onFontSizeChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Quick Presets",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = fontSize <= 10.5f,
                onClick = { onFontSizePreset(10f, 9.5f, 0.82f) },
                label = { Text("XS (10px)") },
                modifier = Modifier.testTag("more_font_preset_xs")
            )
            FilterChip(
                selected = fontSize in 11f..12f,
                onClick = { onFontSizePreset(11.5f, 11f, 0.92f) },
                label = { Text("Compact (11.5px)") },
                modifier = Modifier.testTag("more_font_preset_compact")
            )
            FilterChip(
                selected = fontSize in 12.5f..14f,
                onClick = { onFontSizePreset(13f, 12.5f, 1.0f) },
                label = { Text("Medium (13px)") },
                modifier = Modifier.testTag("more_font_preset_medium")
            )
            FilterChip(
                selected = fontSize >= 14.5f,
                onClick = { onFontSizePreset(15f, 14f, 1.12f) },
                label = { Text("Large (15px)") },
                modifier = Modifier.testTag("more_font_preset_large")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Editor Code Font Size",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${fontSize.roundToInt()} px",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = fontSize,
            onValueChange = onFontSizeChange,
            valueRange = 8f..22f,
            steps = 13,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("more_editor_font_slider")
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "<h1 class=\"title\">Hello CodePad!</h1>\nconst msg = \"Live Editor Preview\";",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = fontSize.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ProjectActionsSectionContent(
    onRename: () -> Unit,
    onFormat: () -> Unit,
    onSplitHtml: () -> Unit,
    onExportHtml: () -> Unit,
    onExportZip: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Code & Project Operations",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRename,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("more_rename_project_btn")
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rename")
            }

            OutlinedButton(
                onClick = onFormat,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("more_format_code_btn")
            ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Format")
            }

            OutlinedButton(
                onClick = onSplitHtml,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("more_split_html_btn")
            ) {
                Text("Split HTML")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Text(
            text = "Export Project Files",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onExportHtml,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("more_export_html_btn")
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Single .html")
            }

            OutlinedButton(
                onClick = onExportZip,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("more_export_zip_btn")
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Full .zip")
            }
        }
    }
}

@Composable
private fun StarterTemplatesSectionContent(
    onLoadPreset: (ProjectPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProjectTemplates.PRESETS.forEach { preset ->
            Surface(
                onClick = { onLoadPreset(preset) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = preset.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Load",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingSoundSectionContent(
    currentProfile: TypingSoundProfile,
    onSelectProfile: (TypingSoundProfile) -> Unit,
    onTestSound: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select Sound Profile",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedButton(
                onClick = onTestSound,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("more_test_sound_btn")
            ) {
                Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Test Sound", fontSize = 11.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TypingSoundProfile.entries.forEach { profile ->
                FilterChip(
                    selected = currentProfile == profile,
                    onClick = { onSelectProfile(profile) },
                    label = { Text(profile.label) },
                    modifier = Modifier.testTag("more_sound_chip_${profile.name}")
                )
            }
        }

        Text(
            text = "Tip: 'Test Sound' par click karke dekhein. Auto-Write chalne par har character par audio play hoga.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SpeedAndDurationSectionContent(
    currentTarget: AutoDurationTarget,
    isTurboMode: Boolean,
    onSelectTarget: (AutoDurationTarget) -> Unit,
    onToggleTurbo: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Video Target Duration",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AutoDurationTarget.entries.forEach { target ->
                FilterChip(
                    selected = currentTarget == target,
                    onClick = { onSelectTarget(target) },
                    label = { Text(target.fullLabel) },
                    modifier = Modifier.testTag("more_target_chip_${target.name}")
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Turbo High-Speed Mode (2×)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Instant fast auto-write for quick shorts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isTurboMode,
                onCheckedChange = { onToggleTurbo() },
                modifier = Modifier.testTag("more_turbo_mode_switch")
            )
        }
    }
}

@Composable
private fun WatermarkSectionContent(
    creatorHandle: String,
    currentPosition: WatermarkPosition,
    currentStyle: WatermarkStyle,
    onUpdateHandle: (String) -> Unit,
    onSelectPosition: (WatermarkPosition) -> Unit,
    onSelectStyle: (WatermarkStyle) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = creatorHandle,
            onValueChange = onUpdateHandle,
            label = { Text("Creator Handle (@username)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("more_creator_handle_input")
        )

        Text(
            text = "Watermark Position",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WatermarkPosition.entries.forEach { pos ->
                FilterChip(
                    selected = currentPosition == pos,
                    onClick = { onSelectPosition(pos) },
                    label = { Text(pos.label) }
                )
            }
        }

        Text(
            text = "Watermark Badge Style",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WatermarkStyle.entries.forEach { style ->
                FilterChip(
                    selected = currentStyle == style,
                    onClick = { onSelectStyle(style) },
                    label = { Text(style.label) }
                )
            }
        }
    }
}

@Composable
private fun ViralHookSectionContent(
    showBanner: Boolean,
    hookBadge: String,
    hookTitle: String,
    hookStyle: ViralHookStyle,
    onToggleBanner: () -> Unit,
    onUpdateBadge: (String) -> Unit,
    onUpdateTitle: (String) -> Unit,
    onSelectStyle: (ViralHookStyle) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Enable Viral Hook Banner",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Headline card at top of shorts screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = showBanner,
                onCheckedChange = { onToggleBanner() },
                modifier = Modifier.testTag("more_viral_hook_switch")
            )
        }

        if (showBanner) {
            OutlinedTextField(
                value = hookBadge,
                onValueChange = onUpdateBadge,
                label = { Text("Badge Label (e.g. DAY 14 • JAVASCRIPT)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = hookTitle,
                onValueChange = onUpdateTitle,
                label = { Text("Main Headline Text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Banner Visual Style",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ViralHookStyle.entries.forEach { style ->
                    FilterChip(
                        selected = hookStyle == style,
                        onClick = { onSelectStyle(style) },
                        label = { Text(style.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EndCtaSectionContent(
    showEndCta: Boolean,
    ctaMessage: String,
    ctaStyle: EndCtaStyle,
    onToggleEndCta: () -> Unit,
    onUpdateMessage: (String) -> Unit,
    onSelectStyle: (EndCtaStyle) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "End CTA Animation Overlay",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Subscribe & Like card when code finishes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = showEndCta,
                onCheckedChange = { onToggleEndCta() },
                modifier = Modifier.testTag("more_end_cta_switch")
            )
        }

        if (showEndCta) {
            OutlinedTextField(
                value = ctaMessage,
                onValueChange = onUpdateMessage,
                label = { Text("Call to Action Text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Animation Style",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EndCtaStyle.entries.forEach { style ->
                    FilterChip(
                        selected = ctaStyle == style,
                        onClick = { onSelectStyle(style) },
                        label = { Text(style.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BackdropSectionContent(
    selectedIndex: Int,
    onSelectBackdrop: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShortsBackdropPreset.entries.forEach { preset ->
                FilterChip(
                    selected = selectedIndex == preset.index,
                    onClick = { onSelectBackdrop(preset.index) },
                    label = { Text(preset.label) }
                )
            }
        }
    }
}

@Composable
private fun RecordingFlowSectionContent(
    autoWriteFontSize: Float,
    useCountdown: Boolean,
    isAutoChainTabs: Boolean,
    autoRevealPreview: Boolean,
    onUpdateAutoWriteFontSize: (Float) -> Unit,
    onToggleCountdown: () -> Unit,
    onToggleAutoChain: () -> Unit,
    onToggleAutoReveal: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Auto-Write Font Size",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${autoWriteFontSize.roundToInt()} px",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = autoWriteFontSize,
            onValueChange = onUpdateAutoWriteFontSize,
            valueRange = 8f..22f,
            steps = 13,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "3-Second Recording Countdown",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = useCountdown,
                onCheckedChange = { onToggleCountdown() }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Auto Chain Tabs (HTML ➔ CSS ➔ JS)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = isAutoChainTabs,
                onCheckedChange = { onToggleAutoChain() }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Reveal Live Preview on Finish",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = autoRevealPreview,
                onCheckedChange = { onToggleAutoReveal() }
            )
        }
    }
}

/**
 * About CodePad Studio Section (Simple Hinglish App About)
 */
@Composable
private fun AppAboutSection() {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("more_about_app_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "About CodePad Studio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Point 1: Ye Kya Hai
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "📱 Ye App Kya Hai?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "CodePad Studio ek complete Mobile Web IDE (HTML, CSS, JS) aur Coding Shorts / Reels Creator Studio hai. Isme aap bina PC ke apne smartphone par live code likh sakte hain, realtime DOM render aur JS console output dekh sakte hain, aur YouTube Shorts / Instagram Reels ke liye ultra-smooth auto-typing coding animations record kar sakte hain.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }

            // Point 2: Kyun Banaya Gaya
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "💡 Kyun Banaya Gaya?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Har developer ya student ke paas laptop ya PC hamesha accessible nahi hota. Sath hi, social media par viral coding reels banane ke liye PC screen recorder aur video editing software ka setup bohot tedious hota hai. CodePad Studio aapko direct phone se code run karne aur cinematic auto-typing coding videos record karne ki poori aazadi deta hai.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }

            // Point 3: Kaise Use Karein
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "🚀 Kaise Use Karein?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "1. Editor Screen: index.html, style.css aur script.js tabs mein code likhein ya paste karein. Niche diye gaye Live Preview aur JS Console buttons se output test karein.\n" +
                        "2. Rec Screen: Auto-Write studio mein jakar 'Record' dabayein. Code automatically smoothly type hoga. Recording ke dauran screen par kahi bhi 3 baar tap (triple tap) karne par auto-write turant stop ho jayega.\n" +
                        "3. More Screen: Har tarah ki audio typing sounds, watermark branding, viral hook banners, CTA overlays, font sizes aur export formats alag-alag sections se customize karein.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/**
 * About Developer (About Me) Section
 */
@Composable
private fun AboutDeveloperSection() {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("more_about_me_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "About Developer (About Me)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "A",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column {
                    Text(
                        text = "Arnix (Code With Arnix)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Developer & Tech Content Creator",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "codewitharnix@gmail.com",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = "Building modern mobile-first tools to empower developers, learners, and content creators. CodePad Studio is designed to make coding and video creation fast, smooth, and accessible on Android.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "App Version: 1.0.0",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Built with Jetpack Compose & M3",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
