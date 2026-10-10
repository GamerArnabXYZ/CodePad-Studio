package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CodeProjectEntity
import com.example.data.CodeProjectRepository
import com.example.data.CodeTab
import com.example.data.ProjectPreset
import com.example.engine.CodeFormatter
import com.example.engine.ComputedNodeStyle
import com.example.engine.ConsoleLogEntry
import com.example.engine.ExportFormat
import com.example.engine.ExportResult
import com.example.engine.JsFunctionDef
import com.example.engine.LogLevel
import com.example.engine.NativeDomNode
import com.example.engine.NativeLiveEngine
import com.example.engine.ProjectExportEngine
import com.example.engine.TypingSoundEngine
import com.example.engine.TypingSoundProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class StudioModule {
    CODEPAD_IDE,
    AUTO_WRITE_STUDIO
}

enum class ShortsBackdropPreset(
    val index: Int,
    val label: String,
    val startColorHex: Long,
    val endColorHex: Long
) {
    CLEAN_M3(0, "Material Default", 0xFF000000, 0xFF000000),
    MIDNIGHT_SLATE(1, "Midnight Studio", 0xFF0F172A, 0xFF1E293B),
    ROYAL_INDIGO(2, "Royal Indigo", 0xFF1E1B4B, 0xFF312E81),
    EMERALD_TEAL(3, "Emerald Craft", 0xFF064E3B, 0xFF0F766E),
    SUNSET_DUSK(4, "Sunset Dusk", 0xFF4C1D95, 0xFF9D174D);

    companion object {
        fun fromIndex(index: Int): ShortsBackdropPreset =
            entries.firstOrNull { it.index == index } ?: CLEAN_M3
    }
}

enum class WatermarkPosition(val label: String) {
    FOOTER_BAR("Window Footer"),
    TOP_STAGE("Screen Top"),
    BOTTOM_STAGE("Screen Bottom"),
    CENTER_OVERLAY("Center Overlay"),
    CORNER_OVERLAY("Bottom-Right Overlay"),
    TOP_RIGHT_OVERLAY("Top-Right Overlay"),
    HIDDEN("Hidden")
}

enum class WatermarkStyle(val label: String) {
    PILL_BADGE("Modern Pill"),
    GLASS_OUTLINE("Glass Verified"),
    CREATOR_GRADIENT("Neon Gradient"),
    CYBER_TERMINAL("Terminal Tag"),
    MINIMAL_MONO("Minimal Text")
}

enum class EndCtaStyle(val label: String) {
    BOTTOM_BAR("Floating Pill Bar"),
    SUBSCRIBE_CARD("Subscribe Card"),
    TRANSPARENT_BAR("Transparent Floating Bar"),
    MINIMAL_CHIPS("Floating Action Chips"),
    SIDE_RAIL("Vertical Side Rail"),
    CYBER_NEON_BAR("Cyber Neon Bar")
}

enum class ViralHookStyle(val label: String) {
    NEON_PILL("Neon Pill Header"),
    GLASS_BANNER("Glass Hook Card"),
    TRANSPARENT_CLEAN("Transparent Floating"),
    CYBER_TERMINAL("Cyber Terminal Box"),
    GRADIENT_RIBBON("Gradient Ribbon"),
    MINIMAL_HEADER("Clean Minimal")
}

enum class AutoDurationTarget(
    val seconds: Int,
    val shortLabel: String,
    val fullLabel: String
) {
    SEC_15(15, "15s", "15s Blitz"),
    SEC_20(20, "20s", "20s Hook"),
    SEC_30(30, "30s", "30s Reel"),
    SEC_45(45, "45s", "45s Short"),
    SEC_60(60, "1m", "1m Shorts (<60s)"),
    MANUAL(0, "Manual", "Manual Speed")
}

data class TypingSequenceMetrics(
    val totalChars: Int,
    val effectiveSteps: Int,
    val weightedUnits: Float,
    val recommendedSpeedLevel: Int,
    val requiresTurboBurst: Boolean,
    val estimatedTotalSeconds: Int
)

object AutoSpeedOptimizer {

    fun countStepsAndWeight(code: String, startIndex: Int = 0): Pair<Int, Float> {
        val len = code.length
        var idx = startIndex.coerceIn(0, len)
        var steps = 0
        var weighted = 0f

        while (idx < len) {
            val ch = code[idx]
            val mult = when {
                ch == '\n' -> 1.75f
                ch == ';' || ch == '}' || ch == '>' -> 1.35f
                ch == '{' || ch == '(' || ch == ':' || ch == '=' -> 1.18f
                ch.isLetterOrDigit() -> 0.85f
                else -> 1.0f
            }
            steps++
            weighted += mult
            idx++
            if (ch == '\n') {
                while (idx < len && (code[idx] == ' ' || code[idx] == '\t')) {
                    idx++
                }
            }
        }
        return steps to weighted
    }

    fun analyze(
        htmlCode: String,
        cssCode: String,
        jsCode: String,
        activeTab: CodeTab,
        isAutoChain: Boolean,
        showEndCta: Boolean,
        target: AutoDurationTarget,
        manualSpeedLevel: Int,
        manualTurbo: Boolean
    ): TypingSequenceMetrics {
        val codes = if (isAutoChain) {
            listOf(htmlCode, cssCode, jsCode)
        } else {
            val single = when (activeTab) {
                CodeTab.INDEX_HTML -> htmlCode
                CodeTab.STYLE_CSS -> cssCode
                CodeTab.SCRIPT_JS -> jsCode
            }
            listOf(single)
        }

        var totalChars = 0
        var totalSteps = 0
        var totalWeighted = 0f
        codes.forEach { c ->
            totalChars += c.length
            val (s, w) = countStepsAndWeight(c, 0)
            totalSteps += s
            totalWeighted += w
        }

        val transitionCount = if (isAutoChain) 2 else 0
        val perTransitionMs = if (target == AutoDurationTarget.SEC_15 || target == AutoDurationTarget.SEC_20) {
            1080L
        } else {
            1400L
        }
        val transitionOverheadMs = transitionCount * perTransitionMs
        val outroOverheadMs = if (showEndCta) {
            if (target == AutoDurationTarget.SEC_15 || target == AutoDurationTarget.SEC_20) 1300L else 2000L
        } else {
            400L
        }

        if (target == AutoDurationTarget.MANUAL) {
            val level = manualSpeedLevel.coerceIn(1, 20)
            val baseDelay = max(10f, 108f - (level * 4.8f))
            val effectiveDelay = if (manualTurbo) max(6f, baseDelay / 2f) else baseDelay
            val typingMs = (totalWeighted * effectiveDelay).toLong()
            val estSec = max(1, ((typingMs + transitionOverheadMs + outroOverheadMs) / 1000f).roundToInt())
            return TypingSequenceMetrics(
                totalChars = totalChars,
                effectiveSteps = totalSteps,
                weightedUnits = totalWeighted,
                recommendedSpeedLevel = level,
                requiresTurboBurst = manualTurbo,
                estimatedTotalSeconds = estSec
            )
        }

        val targetTotalMs = target.seconds * 1000L
        val availableTypingMs = max(2500L, targetTotalMs - transitionOverheadMs - outroOverheadMs)
        val rawBaseDelayMs = availableTypingMs.toFloat() / max(1f, totalWeighted)

        // Cap max delay per unit so short snippets still type at a crisp creator pace
        val maxNaturalDelayMs = when (target) {
            AutoDurationTarget.SEC_15 -> 16f
            AutoDurationTarget.SEC_20 -> 22f
            AutoDurationTarget.SEC_30 -> 30f
            AutoDurationTarget.SEC_45 -> 38f
            AutoDurationTarget.SEC_60 -> 44f
            AutoDurationTarget.MANUAL -> 44f
        }

        val clampedBaseDelay = min(rawBaseDelayMs, maxNaturalDelayMs)
        val recommendedLevel = ((108f - clampedBaseDelay) / 4.8f).roundToInt().coerceIn(1, 20)
        val needsBurst = rawBaseDelayMs < 11f
        val actualTypingMs = (totalWeighted * clampedBaseDelay).toLong().coerceAtMost(availableTypingMs)
        val estSec = max(
            1,
            min(
                target.seconds,
                ((actualTypingMs + transitionOverheadMs + outroOverheadMs) / 1000f).roundToInt()
            )
        )

        return TypingSequenceMetrics(
            totalChars = totalChars,
            effectiveSteps = totalSteps,
            weightedUnits = totalWeighted,
            recommendedSpeedLevel = recommendedLevel,
            requiresTurboBurst = needsBurst,
            estimatedTotalSeconds = estSec
        )
    }
}

data class StudioUiState(
    val activeModule: StudioModule = StudioModule.CODEPAD_IDE,
    val projectName: String = "Portfolio Card",
    val activeTab: CodeTab = CodeTab.INDEX_HTML,
    val htmlCode: String = "",
    val cssCode: String = "",
    val jsCode: String = "",
    val editorFontSize: Float = 11.5f,
    val autoWriteFontSize: Float = 11f,
    val uiFontScale: Float = 0.92f,
    val isDarkTheme: Boolean = false,
    val isSaved: Boolean = true,
    val lastSavedTimeText: String = "Saved",
    val currentLine: Int = 1,
    val currentColumn: Int = 1,
    val isPreviewVisible: Boolean = false,
    val isConsoleVisible: Boolean = false,
    val isSettingsSheetOpen: Boolean = false,
    val isRenameDialogOpen: Boolean = false,
    val isExportSheetOpen: Boolean = false,
    val latestExportResult: ExportResult? = null,
    // Native Live Preview & Console Subsystem
    val domNodes: List<NativeDomNode> = emptyList(),
    val bodyStyle: ComputedNodeStyle = ComputedNodeStyle(),
    val consoleLogs: List<ConsoleLogEntry> = emptyList(),
    val consoleFilter: LogLevel? = null,
    // Auto Write Studio & Creator Customization
    val autoWriteTab: CodeTab = CodeTab.INDEX_HTML,
    val isAutoWritePlaying: Boolean = false,
    val typedCharCount: Int = 0,
    val speedLevel: Int = 14, // 1..20
    val isTurboMode: Boolean = false,
    val autoDurationTarget: AutoDurationTarget = AutoDurationTarget.SEC_60,
    val isFloatingPreviewOpen: Boolean = false,
    val isRecordingMode: Boolean = false,
    val creatorHandle: String = "@codewitharnix",
    val watermarkPosition: WatermarkPosition = WatermarkPosition.FOOTER_BAR,
    val watermarkStyle: WatermarkStyle = WatermarkStyle.PILL_BADGE,
    val showEndCtaAnimation: Boolean = true,
    val endCtaStyle: EndCtaStyle = EndCtaStyle.SUBSCRIBE_CARD,
    val endCtaPosition: EndCtaPosition = EndCtaPosition.CODE_BOTTOM_OVERLAY,
    val ctaDisplayTiming: CtaDisplayTiming = CtaDisplayTiming.AT_END_100,
    val ctaTransparentBg: Boolean = false,
    val ctaShowBorder: Boolean = true,
    val ctaShowHandle: Boolean = true,
    val ctaAccentIndex: Int = 1,
    val ctaSizeScale: OverlaySizeScale = OverlaySizeScale.STANDARD,
    val endCtaMessage: String = "Like, Comment & Subscribe for more!",
    val ctaShowLike: Boolean = true,
    val ctaShowComment: Boolean = true,
    val ctaShowSubscribe: Boolean = true,
    val ctaLikeLabel: String = "Like",
    val ctaCommentLabel: String = "Comment",
    val ctaSubscribeLabel: String = "SUBSCRIBE",
    val ctaSubscribedLabel: String = "SUBSCRIBED ✓",
    val isEndCtaActive: Boolean = false,
    val isAutoChainTabs: Boolean = true,
    val autoRevealPreviewOnFinish: Boolean = false,
    val backdropPresetIndex: Int = 1,
    val showViralHookBanner: Boolean = true,
    val viralHookBadge: String = "🔥 HTML • CSS • JS",
    val viralHookTitle: String = "Build a Developer Portfolio Card",
    val viralHookStyle: ViralHookStyle = ViralHookStyle.NEON_PILL,
    val viralHookPosition: ViralHookPosition = ViralHookPosition.TOP_STAGE,
    val viralHookAlignment: ViralHookAlignment = ViralHookAlignment.START,
    val viralHookTransparentBg: Boolean = false,
    val viralHookShowBorder: Boolean = true,
    val viralHookShowBadge: Boolean = true,
    val viralHookAccentIndex: Int = 0,
    val viralHookSizeScale: OverlaySizeScale = OverlaySizeScale.STANDARD,
    val typingSoundProfile: TypingSoundProfile = TypingSoundProfile.MECH_THOCK,
    val useRecordingCountdown: Boolean = true,
    val recordingCountdownSeconds: Int = 0,
    val keepPreviewOpenInRecord: Boolean = true,
    val isCreatorCustomizeOpen: Boolean = false,
    val isViralTemplatesSheetOpen: Boolean = false,
    val previewSectionHeightDp: Float = 195f,
    val autoWriteDomNodes: List<NativeDomNode> = emptyList(),
    val autoWriteBodyStyle: ComputedNodeStyle = ComputedNodeStyle(),
    val autoWriteCurrentLine: Int = 1,
    val autoWriteCurrentColumn: Int = 1,
    val sequenceMetrics: TypingSequenceMetrics = TypingSequenceMetrics(
        totalChars = 0,
        effectiveSteps = 0,
        weightedUnits = 0f,
        recommendedSpeedLevel = 14,
        requiresTurboBurst = false,
        estimatedTotalSeconds = 1
    ),
    val isInitialized: Boolean = false
) {
    val activeCode: String
        get() = when (activeTab) {
            CodeTab.INDEX_HTML -> htmlCode
            CodeTab.STYLE_CSS -> cssCode
            CodeTab.SCRIPT_JS -> jsCode
        }

    val autoWriteFullCode: String
        get() = when (autoWriteTab) {
            CodeTab.INDEX_HTML -> htmlCode
            CodeTab.STYLE_CSS -> cssCode
            CodeTab.SCRIPT_JS -> jsCode
        }

    val autoWriteVisibleCode: String
        get() {
            val full = autoWriteFullCode
            if (full.isEmpty()) return ""
            return full.substring(0, typedCharCount.coerceIn(0, full.length))
        }

    val autoWriteProgress: Float
        get() {
            val total = autoWriteFullCode.length
            if (total == 0) return 1f
            return (typedCharCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

    val overallSequenceProgress: Float
        get() {
            if (!isAutoChainTabs) return autoWriteProgress
            val totalLen = (htmlCode.length + cssCode.length + jsCode.length).coerceAtLeast(1)
            val completedBeforeTab = when (autoWriteTab) {
                CodeTab.INDEX_HTML -> 0
                CodeTab.STYLE_CSS -> htmlCode.length
                CodeTab.SCRIPT_JS -> htmlCode.length + cssCode.length
            }
            val currentTyped = typedCharCount.coerceIn(0, autoWriteFullCode.length)
            return ((completedBeforeTab + currentTyped).toFloat() / totalLen.toFloat()).coerceIn(0f, 1f)
        }

    val shouldDisplayCtaOverlay: Boolean
        get() {
            if (!showEndCtaAnimation) return false
            if (isEndCtaActive) return true
            return when (ctaDisplayTiming) {
                CtaDisplayTiming.AT_END_100 -> false
                CtaDisplayTiming.ALWAYS_VISIBLE -> true
                CtaDisplayTiming.FROM_50_PERCENT -> overallSequenceProgress >= 0.50f
                CtaDisplayTiming.LAST_20_PERCENT -> overallSequenceProgress >= 0.80f
            }
        }

    val errorCount: Int
        get() = consoleLogs.count { it.level == LogLevel.ERROR }
}

fun computeLineAndColumn(code: String, charCount: Int): Pair<Int, Int> {
    val limit = charCount.coerceIn(0, code.length)
    var line = 1
    var col = 1
    for (i in 0 until limit) {
        if (code[i] == '\n') {
            line++
            col = 1
        } else {
            col++
        }
    }
    return line to col
}

class StudioViewModel(
    private val repository: CodeProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var autoSaveJob: Job? = null
    private var typewriterJob: Job? = null
    private var countdownJob: Job? = null
    private var lastAutoPreviewHtml: String = "__UNSET__"
    private var lastAutoPreviewCss: String = "__UNSET__"
    private var lastAutoPreviewJs: String = "__UNSET__"
    private var lastAutoPreviewTimeMs: Long = 0L

    private var runtimeFunctions: Map<String, JsFunctionDef> = emptyMap()
    private val runtimeVariables = mutableMapOf<String, String>()
    private val domTextOverrides = mutableMapOf<String, String>()
    private val domColorOverrides = mutableMapOf<String, Color>()
    private val domBgOverrides = mutableMapOf<String, Color>()

    private fun clearRuntimeDomState() {
        domTextOverrides.clear()
        domColorOverrides.clear()
        domBgOverrides.clear()
        runtimeVariables.clear()
    }

    init {
        viewModelScope.launch {
            val initial = repository.getOrInitProject()
            val initTab = CodeTab.fromIndex(initial.activeTabIndex)
            val savedWatermarkPos = WatermarkPosition.entries.getOrElse(initial.watermarkPositionOrdinal) {
                WatermarkPosition.FOOTER_BAR
            }
            val savedWatermarkStyle = WatermarkStyle.entries.getOrElse(initial.watermarkStyleOrdinal) {
                WatermarkStyle.PILL_BADGE
            }
            val savedDurationTarget = AutoDurationTarget.entries.getOrElse(initial.autoDurationTargetOrdinal) {
                AutoDurationTarget.SEC_60
            }
            val savedEndCtaStyle = EndCtaStyle.entries.getOrElse(initial.endCtaStyleOrdinal) {
                EndCtaStyle.SUBSCRIBE_CARD
            }
            val savedEndCtaPosition = EndCtaPosition.entries.getOrElse(initial.endCtaPositionOrdinal) {
                EndCtaPosition.CODE_BOTTOM_OVERLAY
            }
            val savedCtaTiming = CtaDisplayTiming.entries.getOrElse(initial.ctaDisplayTimingOrdinal) {
                CtaDisplayTiming.AT_END_100
            }
            val savedCtaSizeScale = OverlaySizeScale.fromIndex(initial.ctaSizeScaleIndex)
            val savedHookStyle = ViralHookStyle.entries.getOrElse(initial.viralHookStyleOrdinal) {
                ViralHookStyle.NEON_PILL
            }
            val savedHookPosition = ViralHookPosition.entries.getOrElse(initial.viralHookPositionOrdinal) {
                ViralHookPosition.TOP_STAGE
            }
            val savedHookAlignment = ViralHookAlignment.entries.getOrElse(initial.viralHookAlignmentOrdinal) {
                ViralHookAlignment.START
            }
            val savedHookSizeScale = OverlaySizeScale.fromIndex(initial.viralHookSizeScaleIndex)
            val savedSoundProfile = TypingSoundProfile.fromIndex(initial.typingSoundProfileIndex)

            _uiState.update {
                it.copy(
                    projectName = initial.projectName,
                    htmlCode = initial.htmlCode,
                    cssCode = initial.cssCode,
                    jsCode = initial.jsCode,
                    editorFontSize = initial.editorFontSize.coerceIn(8f, 22f),
                    autoWriteFontSize = initial.autoWriteFontSize.coerceIn(8f, 22f),
                    uiFontScale = initial.uiFontScale.coerceIn(0.70f, 1.35f),
                    isDarkTheme = initial.isDarkTheme,
                    activeTab = initTab,
                    autoWriteTab = initTab,
                    creatorHandle = initial.creatorHandle.ifBlank { "@codewitharnix" },
                    watermarkPosition = savedWatermarkPos,
                    watermarkStyle = savedWatermarkStyle,
                    autoDurationTarget = savedDurationTarget,
                    speedLevel = initial.speedLevel.coerceIn(1, 20),
                    isTurboMode = initial.isTurboMode,
                    previewSectionHeightDp = initial.previewSectionHeightDp.coerceIn(85f, 460f),
                    showEndCtaAnimation = initial.showEndCtaAnimation,
                    endCtaStyle = savedEndCtaStyle,
                    endCtaPosition = savedEndCtaPosition,
                    ctaDisplayTiming = savedCtaTiming,
                    ctaTransparentBg = initial.ctaTransparentBg,
                    ctaShowBorder = initial.ctaShowBorder,
                    ctaShowHandle = initial.ctaShowHandle,
                    ctaAccentIndex = initial.ctaAccentIndex,
                    ctaSizeScale = savedCtaSizeScale,
                    endCtaMessage = initial.endCtaMessage,
                    ctaShowLike = initial.ctaShowLike,
                    ctaShowComment = initial.ctaShowComment,
                    ctaShowSubscribe = initial.ctaShowSubscribe,
                    ctaLikeLabel = initial.ctaLikeLabel.ifBlank { "Like" },
                    ctaCommentLabel = initial.ctaCommentLabel.ifBlank { "Comment" },
                    ctaSubscribeLabel = initial.ctaSubscribeLabel.ifBlank { "SUBSCRIBE" },
                    ctaSubscribedLabel = initial.ctaSubscribedLabel.ifBlank { "SUBSCRIBED ✓" },
                    isAutoChainTabs = initial.isAutoChainTabs,
                    autoRevealPreviewOnFinish = initial.autoRevealPreviewOnFinish,
                    backdropPresetIndex = initial.backdropPresetIndex,
                    showViralHookBanner = initial.showViralHookBanner,
                    viralHookBadge = initial.viralHookBadge.ifBlank { "🔥 HTML • CSS • JS" },
                    viralHookTitle = initial.viralHookTitle.ifBlank { initial.projectName },
                    viralHookStyle = savedHookStyle,
                    viralHookPosition = savedHookPosition,
                    viralHookAlignment = savedHookAlignment,
                    viralHookTransparentBg = initial.viralHookTransparentBg,
                    viralHookShowBorder = initial.viralHookShowBorder,
                    viralHookShowBadge = initial.viralHookShowBadge,
                    viralHookAccentIndex = initial.viralHookAccentIndex,
                    viralHookSizeScale = savedHookSizeScale,
                    typingSoundProfile = savedSoundProfile,
                    useRecordingCountdown = initial.useRecordingCountdown,
                    keepPreviewOpenInRecord = initial.keepPreviewOpenInRecord,
                    isSaved = true,
                    isInitialized = true
                )
            }
            syncAutoSpeedMetricsIfNeeded()
            rebuildNativePreview(runTopLevelJs = true, logSystemBoot = true)
            updateProgressiveAutoWritePreview()
        }
    }

    fun toggleTheme() {
        val nextDark = !_uiState.value.isDarkTheme
        _uiState.update { it.copy(isDarkTheme = nextDark) }
        triggerDebouncedAutoSave()
    }

    fun setDarkTheme(dark: Boolean) {
        _uiState.update { it.copy(isDarkTheme = dark) }
        triggerDebouncedAutoSave()
    }

    fun selectModule(module: StudioModule) {
        _uiState.update { state ->
            val syncTab = if (module == StudioModule.AUTO_WRITE_STUDIO) state.activeTab else state.autoWriteTab
            state.copy(
                activeModule = module,
                autoWriteTab = syncTab
            )
        }
        if (module == StudioModule.AUTO_WRITE_STUDIO && _uiState.value.typedCharCount == 0) {
            startOrResumeAutoWrite()
        } else if (module == StudioModule.CODEPAD_IDE) {
            pauseAutoWrite()
        }
    }

    fun selectTab(tab: CodeTab) {
        _uiState.update {
            it.copy(
                activeTab = tab,
                currentLine = 1,
                currentColumn = 1
            )
        }
        triggerDebouncedAutoSave()
    }

    fun onCodeChanged(newCode: String, cursorOffset: Int) {
        val (line, col) = calculateLineAndColumn(newCode, cursorOffset)
        _uiState.update { state ->
            when (state.activeTab) {
                CodeTab.INDEX_HTML -> state.copy(
                    htmlCode = newCode,
                    isSaved = false,
                    currentLine = line,
                    currentColumn = col
                )
                CodeTab.STYLE_CSS -> state.copy(
                    cssCode = newCode,
                    isSaved = false,
                    currentLine = line,
                    currentColumn = col
                )
                CodeTab.SCRIPT_JS -> state.copy(
                    jsCode = newCode,
                    isSaved = false,
                    currentLine = line,
                    currentColumn = col
                )
            }
        }
        // Instant Live Preview update on every character typed in IDE (including top-level JS)
        syncAutoSpeedMetricsIfNeeded()
        rebuildNativePreview(runTopLevelJs = true, logSystemBoot = false)
        updateProgressiveAutoWritePreview()
        triggerDebouncedAutoSave()
    }

    fun onCursorPositionChanged(code: String, cursorOffset: Int) {
        val (line, col) = calculateLineAndColumn(code, cursorOffset)
        _uiState.update {
            if (it.currentLine == line && it.currentColumn == col) it
            else it.copy(currentLine = line, currentColumn = col)
        }
    }

    private fun calculateLineAndColumn(text: String, offset: Int): Pair<Int, Int> {
        if (text.isEmpty()) return 1 to 1
        val safeOffset = offset.coerceIn(0, text.length)
        var line = 1
        var lastNewLineIdx = -1
        for (i in 0 until safeOffset) {
            if (text[i] == '\n') {
                line++
                lastNewLineIdx = i
            }
        }
        val col = safeOffset - lastNewLineIdx
        return line to col
    }

    private fun buildProjectEntity(current: StudioUiState): CodeProjectEntity {
        return CodeProjectEntity(
            id = 1,
            projectName = current.projectName,
            htmlCode = current.htmlCode,
            cssCode = current.cssCode,
            jsCode = current.jsCode,
            editorFontSize = current.editorFontSize,
            autoWriteFontSize = current.autoWriteFontSize,
            uiFontScale = current.uiFontScale,
            activeTabIndex = current.activeTab.index,
            isDarkTheme = current.isDarkTheme,
            creatorHandle = current.creatorHandle,
            watermarkPositionOrdinal = current.watermarkPosition.ordinal,
            watermarkStyleOrdinal = current.watermarkStyle.ordinal,
            autoDurationTargetOrdinal = current.autoDurationTarget.ordinal,
            speedLevel = current.speedLevel,
            isTurboMode = current.isTurboMode,
            previewSectionHeightDp = current.previewSectionHeightDp,
            showEndCtaAnimation = current.showEndCtaAnimation,
            endCtaStyleOrdinal = current.endCtaStyle.ordinal,
            endCtaPositionOrdinal = current.endCtaPosition.ordinal,
            ctaDisplayTimingOrdinal = current.ctaDisplayTiming.ordinal,
            ctaTransparentBg = current.ctaTransparentBg,
            ctaShowBorder = current.ctaShowBorder,
            ctaShowHandle = current.ctaShowHandle,
            ctaAccentIndex = current.ctaAccentIndex,
            ctaSizeScaleIndex = current.ctaSizeScale.index,
            endCtaMessage = current.endCtaMessage,
            ctaShowLike = current.ctaShowLike,
            ctaShowComment = current.ctaShowComment,
            ctaShowSubscribe = current.ctaShowSubscribe,
            ctaLikeLabel = current.ctaLikeLabel,
            ctaCommentLabel = current.ctaCommentLabel,
            ctaSubscribeLabel = current.ctaSubscribeLabel,
            ctaSubscribedLabel = current.ctaSubscribedLabel,
            isAutoChainTabs = current.isAutoChainTabs,
            autoRevealPreviewOnFinish = current.autoRevealPreviewOnFinish,
            backdropPresetIndex = current.backdropPresetIndex,
            showViralHookBanner = current.showViralHookBanner,
            viralHookBadge = current.viralHookBadge,
            viralHookTitle = current.viralHookTitle,
            viralHookStyleOrdinal = current.viralHookStyle.ordinal,
            viralHookPositionOrdinal = current.viralHookPosition.ordinal,
            viralHookAlignmentOrdinal = current.viralHookAlignment.ordinal,
            viralHookTransparentBg = current.viralHookTransparentBg,
            viralHookShowBorder = current.viralHookShowBorder,
            viralHookShowBadge = current.viralHookShowBadge,
            viralHookAccentIndex = current.viralHookAccentIndex,
            viralHookSizeScaleIndex = current.viralHookSizeScale.index,
            typingSoundProfileIndex = current.typingSoundProfile.index,
            useRecordingCountdown = current.useRecordingCountdown,
            keepPreviewOpenInRecord = current.keepPreviewOpenInRecord,
            lastSavedTimestamp = System.currentTimeMillis()
        )
    }

    private fun triggerDebouncedAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(500L)
            val entity = buildProjectEntity(_uiState.value)
            repository.saveProject(entity)
            _uiState.update {
                it.copy(
                    isSaved = true,
                    lastSavedTimeText = "Saved"
                )
            }
        }
    }

    fun flushPendingAutoSave() {
        autoSaveJob?.cancel()
        val snapshotState = _uiState.value
        if (!snapshotState.isInitialized) return
        viewModelScope.launch {
            repository.saveProject(buildProjectEntity(snapshotState))
            _uiState.update {
                it.copy(
                    isSaved = true,
                    lastSavedTimeText = "Saved"
                )
            }
        }
    }

    fun updateProjectName(newName: String) {
        val trimmed = newName.trim().ifEmpty { "Untitled Project" }
        _uiState.update {
            it.copy(
                projectName = trimmed,
                isSaved = false,
                isRenameDialogOpen = false
            )
        }
        appendConsoleLog(
            LogLevel.SYSTEM,
            "Project renamed to '$trimmed'",
            "workspace"
        )
        triggerDebouncedAutoSave()
    }

    fun updateEditorFontSize(newSize: Float) {
        val clamped = newSize.coerceIn(8f, 22f)
        _uiState.update {
            it.copy(
                editorFontSize = clamped,
                isSaved = false
            )
        }
        triggerDebouncedAutoSave()
    }

    fun updateAutoWriteFontSize(newSize: Float) {
        val clamped = newSize.coerceIn(8f, 22f)
        _uiState.update {
            it.copy(
                autoWriteFontSize = clamped,
                isSaved = false
            )
        }
        triggerDebouncedAutoSave()
    }

    fun updateUiFontScale(newScale: Float) {
        val clamped = newScale.coerceIn(0.70f, 1.35f)
        _uiState.update {
            it.copy(
                uiFontScale = clamped,
                isSaved = false
            )
        }
        triggerDebouncedAutoSave()
    }

    fun applyFontSizePreset(editorSize: Float, autoWriteSize: Float, uiScale: Float) {
        _uiState.update {
            it.copy(
                editorFontSize = editorSize.coerceIn(8f, 22f),
                autoWriteFontSize = autoWriteSize.coerceIn(8f, 22f),
                uiFontScale = uiScale.coerceIn(0.70f, 1.35f),
                isSaved = false
            )
        }
        triggerDebouncedAutoSave()
    }

    fun formatActiveTabCode() {
        val state = _uiState.value
        val formatted = CodeFormatter.format(state.activeCode, state.activeTab)
        val totalLines = max(1, formatted.count { it == '\n' } + 1)
        _uiState.update { current ->
            val safeLine = current.currentLine.coerceIn(1, totalLines)
            when (current.activeTab) {
                CodeTab.INDEX_HTML -> current.copy(htmlCode = formatted, currentLine = safeLine, isSaved = false)
                CodeTab.STYLE_CSS -> current.copy(cssCode = formatted, currentLine = safeLine, isSaved = false)
                CodeTab.SCRIPT_JS -> current.copy(jsCode = formatted, currentLine = safeLine, isSaved = false)
            }
        }
        appendConsoleLog(
            LogLevel.SYSTEM,
            "Formatted ${state.activeTab.fileName} ($totalLines lines)",
            "Formatter"
        )
        syncAutoSpeedMetricsIfNeeded()
        rebuildNativePreview(runTopLevelJs = false, logSystemBoot = false)
        updateProgressiveAutoWritePreview(force = true)
        triggerDebouncedAutoSave()
    }

    fun loadPreset(preset: ProjectPreset) {
        typewriterJob?.cancel()
        countdownJob?.cancel()
        clearRuntimeDomState()
        _uiState.update {
            it.copy(
                projectName = preset.name,
                htmlCode = preset.html,
                cssCode = preset.css,
                jsCode = preset.js,
                viralHookBadge = preset.hookBadge,
                viralHookTitle = preset.hookTitle,
                autoWriteTab = CodeTab.INDEX_HTML,
                isSaved = false,
                typedCharCount = 0,
                autoWriteCurrentLine = 1,
                autoWriteCurrentColumn = 1,
                isEndCtaActive = false,
                isViralTemplatesSheetOpen = false
            )
        }
        appendConsoleLog(
            LogLevel.SYSTEM,
            "Loaded template '${preset.name}'",
            "Templates"
        )
        syncAutoSpeedMetricsIfNeeded()
        rebuildNativePreview(runTopLevelJs = true, logSystemBoot = false)
        updateProgressiveAutoWritePreview(force = true)
        triggerDebouncedAutoSave()
        if (_uiState.value.activeModule == StudioModule.AUTO_WRITE_STUDIO) {
            startOrResumeAutoWrite()
        }
    }

    fun rebuildNativePreview(runTopLevelJs: Boolean = true, logSystemBoot: Boolean = false) {
        val state = _uiState.value
        if (runTopLevelJs) {
            clearRuntimeDomState()
        }
        val snapshot = NativeLiveEngine.evaluateProject(
            html = state.htmlCode,
            css = state.cssCode,
            js = state.jsCode,
            existingVariables = runtimeVariables,
            domTextOverrides = domTextOverrides,
            domColorOverrides = domColorOverrides,
            domBgOverrides = domBgOverrides,
            runTopLevelJs = runTopLevelJs
        )
        runtimeFunctions = snapshot.functions
        runtimeVariables.putAll(snapshot.variables)

        _uiState.update { current ->
            val updatedLogs = buildList {
                addAll(current.consoleLogs)
                if (logSystemBoot && current.consoleLogs.isEmpty()) {
                    add(
                        ConsoleLogEntry(
                            level = LogLevel.SYSTEM,
                            message = "Native preview & console ready.",
                            sourceLocation = "system"
                        )
                    )
                }
                if (runTopLevelJs) {
                    addAll(snapshot.logs)
                }
            }.takeLast(120)

            current.copy(
                domNodes = snapshot.rootNodes,
                bodyStyle = snapshot.bodyStyle,
                consoleLogs = updatedLogs
            )
        }
    }

    private fun updateProgressiveAutoWritePreview(force: Boolean = false) {
        val state = _uiState.value
        val now = System.currentTimeMillis()
        if (!force && state.isAutoWritePlaying) {
            val code = state.autoWriteFullCode
            val idx = (state.typedCharCount - 1).coerceIn(0, max(0, code.length - 1))
            val lastChar = if (code.isNotEmpty()) code[idx] else ' '
            val isStructuralChar = lastChar == '>' || lastChar == ';' || lastChar == '}' || lastChar == '\n'
            // Throttle non-structural updates to ~35 FPS (28ms) during ultra-fast burst typing on large code
            if (!isStructuralChar && (now - lastAutoPreviewTimeMs) < 28L) {
                return
            }
        }

        val visible = state.autoWriteVisibleCode

        val rawHtml = when (state.autoWriteTab) {
            CodeTab.INDEX_HTML -> visible
            CodeTab.STYLE_CSS, CodeTab.SCRIPT_JS -> state.htmlCode
        }
        val effectiveHtml = NativeLiveEngine.normalizeProgressiveHtml(rawHtml)
        val effectiveCss = when (state.autoWriteTab) {
            CodeTab.INDEX_HTML -> if (state.isAutoChainTabs) "" else state.cssCode
            CodeTab.STYLE_CSS -> visible
            CodeTab.SCRIPT_JS -> state.cssCode
        }
        val effectiveJs = when (state.autoWriteTab) {
            CodeTab.INDEX_HTML, CodeTab.STYLE_CSS -> ""
            CodeTab.SCRIPT_JS -> {
                if (state.typedCharCount >= state.jsCode.length) {
                    visible
                } else {
                    // Run all completed JS lines/statements immediately as they are typed
                    val lastNewline = visible.lastIndexOf('\n')
                    if (lastNewline >= 0) visible.substring(0, lastNewline) else ""
                }
            }
        }

        if (!force &&
            effectiveHtml == lastAutoPreviewHtml &&
            effectiveCss == lastAutoPreviewCss &&
            effectiveJs == lastAutoPreviewJs
        ) {
            return
        }

        lastAutoPreviewHtml = effectiveHtml
        lastAutoPreviewCss = effectiveCss
        lastAutoPreviewJs = effectiveJs
        lastAutoPreviewTimeMs = now

        val runJsNow = state.autoWriteTab == CodeTab.SCRIPT_JS && effectiveJs.isNotBlank()

        val progressiveSnapshot = NativeLiveEngine.evaluateProject(
            html = effectiveHtml,
            css = effectiveCss,
            js = effectiveJs,
            existingVariables = runtimeVariables,
            domTextOverrides = domTextOverrides,
            domColorOverrides = domColorOverrides,
            domBgOverrides = domBgOverrides,
            runTopLevelJs = runJsNow
        )

        if (state.autoWriteTab == CodeTab.SCRIPT_JS && progressiveSnapshot.functions.isNotEmpty()) {
            runtimeFunctions = progressiveSnapshot.functions
        }

        _uiState.update {
            it.copy(
                autoWriteDomNodes = progressiveSnapshot.rootNodes,
                autoWriteBodyStyle = progressiveSnapshot.bodyStyle
            )
        }
    }

    fun onNativeDomButtonClicked(onClickAttr: String) {
        val state = _uiState.value
        val cleanName = onClickAttr.substringBefore("(").trim()
        if (!runtimeFunctions.containsKey(cleanName) && state.jsCode.isNotBlank()) {
            val preload = NativeLiveEngine.evaluateProject(
                html = state.htmlCode,
                css = state.cssCode,
                js = state.jsCode,
                existingVariables = runtimeVariables,
                domTextOverrides = domTextOverrides,
                domColorOverrides = domColorOverrides,
                domBgOverrides = domBgOverrides,
                runTopLevelJs = runtimeVariables.isEmpty()
            )
            runtimeFunctions = preload.functions
            runtimeVariables.putAll(preload.variables)
        }

        val newLogs = NativeLiveEngine.invokeJsFunction(
            functionName = onClickAttr,
            functions = runtimeFunctions,
            variables = runtimeVariables,
            textOverrides = domTextOverrides,
            colorOverrides = domColorOverrides,
            bgOverrides = domBgOverrides
        )
        val refreshed = NativeLiveEngine.evaluateProject(
            html = state.htmlCode,
            css = state.cssCode,
            js = state.jsCode,
            existingVariables = runtimeVariables,
            domTextOverrides = domTextOverrides,
            domColorOverrides = domColorOverrides,
            domBgOverrides = domBgOverrides,
            runTopLevelJs = false
        )
        val hasError = newLogs.any { it.level == LogLevel.ERROR }
        _uiState.update { current ->
            val isIde = current.activeModule == StudioModule.CODEPAD_IDE
            current.copy(
                domNodes = refreshed.rootNodes,
                bodyStyle = refreshed.bodyStyle,
                autoWriteDomNodes = refreshed.rootNodes,
                autoWriteBodyStyle = refreshed.bodyStyle,
                consoleLogs = (current.consoleLogs + newLogs).takeLast(120),
                isConsoleVisible = if (hasError && isIde) true else current.isConsoleVisible,
                isPreviewVisible = if (hasError && isIde) false else current.isPreviewVisible
            )
        }
    }

    fun triggerSimulatedStackTraceError() {
        appendConsoleLog(
            level = LogLevel.ERROR,
            message = "Uncaught ReferenceError: undefinedVariable is not defined",
            sourceLocation = "script.js:18:5",
            stackTrace = """
                ReferenceError: undefinedVariable is not defined
                    at handleAction (script.js:18:5)
                    at HTMLButtonElement.onclick (index.html:12:9)
            """.trimIndent()
        )
        _uiState.update { it.copy(isConsoleVisible = true, isPreviewVisible = false) }
    }

    fun clearConsole() {
        _uiState.update { it.copy(consoleLogs = emptyList()) }
    }

    fun setConsoleFilter(level: LogLevel?) {
        _uiState.update { it.copy(consoleFilter = level) }
    }

    fun togglePreview() {
        _uiState.update {
            val next = !it.isPreviewVisible
            it.copy(
                isPreviewVisible = next,
                isConsoleVisible = if (next) false else it.isConsoleVisible
            )
        }
    }

    fun toggleConsole() {
        _uiState.update {
            val next = !it.isConsoleVisible
            it.copy(
                isConsoleVisible = next,
                isPreviewVisible = if (next) false else it.isPreviewVisible
            )
        }
    }

    fun setSettingsSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isSettingsSheetOpen = open) }
    }

    fun setRenameDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isRenameDialogOpen = open) }
    }

    fun setExportSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isExportSheetOpen = open) }
    }

    fun dismissExportResult() {
        _uiState.update { it.copy(latestExportResult = null) }
    }

    fun exportToAppStorage(context: Context, format: ExportFormat) {
        val appContext = context.applicationContext
        val s = _uiState.value
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ProjectExportEngine.exportToLocalAppDirectory(
                        context = appContext,
                        projectName = s.projectName,
                        htmlCode = s.htmlCode,
                        cssCode = s.cssCode,
                        jsCode = s.jsCode,
                        format = format
                    )
                }
                _uiState.update {
                    it.copy(
                        latestExportResult = res,
                        isExportSheetOpen = false
                    )
                }
                appendConsoleLog(
                    LogLevel.SYSTEM,
                    "Exported ${res.fileName} (${res.sizeBytes} bytes)",
                    "Export"
                )
            } catch (t: Throwable) {
                appendConsoleLog(
                    LogLevel.ERROR,
                    "Export failed: ${t.message}",
                    "Export",
                    t.stackTraceToString()
                )
            }
        }
    }

    fun exportToUserUri(context: Context, uri: Uri, format: ExportFormat) {
        val appContext = context.applicationContext
        val s = _uiState.value
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ProjectExportEngine.writeToDocumentUri(
                        context = appContext,
                        uri = uri,
                        projectName = s.projectName,
                        htmlCode = s.htmlCode,
                        cssCode = s.cssCode,
                        jsCode = s.jsCode,
                        format = format
                    )
                }
                _uiState.update {
                    it.copy(
                        latestExportResult = res,
                        isExportSheetOpen = false
                    )
                }
                appendConsoleLog(
                    LogLevel.SYSTEM,
                    "Saved ${res.fileName} (${res.sizeBytes} bytes)",
                    "Export"
                )
            } catch (t: Throwable) {
                appendConsoleLog(
                    LogLevel.ERROR,
                    "Save failed: ${t.message}",
                    "Export",
                    t.stackTraceToString()
                )
            }
        }
    }

    private fun appendConsoleLog(
        level: LogLevel,
        message: String,
        sourceLocation: String,
        stackTrace: String? = null
    ) {
        _uiState.update { state ->
            state.copy(
                consoleLogs = (state.consoleLogs + ConsoleLogEntry(
                    level = level,
                    message = message,
                    sourceLocation = sourceLocation,
                    stackTrace = stackTrace
                )).takeLast(120)
            )
        }
    }

    // =========================================================================
    // MODULE 2: AUTO WRITE STUDIO (CLEAN SILENT ANIMATION ENGINE - NO POPUPS)
    // =========================================================================

    fun selectAutoWriteTab(tab: CodeTab) {
        typewriterJob?.cancel()
        if (tab == CodeTab.INDEX_HTML) {
            clearRuntimeDomState()
        }
        _uiState.update {
            it.copy(
                autoWriteTab = tab,
                typedCharCount = 0,
                autoWriteCurrentLine = 1,
                autoWriteCurrentColumn = 1,
                isAutoWritePlaying = false,
                isEndCtaActive = false
            )
        }
        syncAutoSpeedMetricsIfNeeded()
        updateProgressiveAutoWritePreview(force = true)
        startOrResumeAutoWrite()
    }

    fun selectAutoDurationTarget(target: AutoDurationTarget) {
        _uiState.update { it.copy(autoDurationTarget = target) }
        syncAutoSpeedMetricsIfNeeded()
        triggerDebouncedAutoSave()
    }

    private fun syncAutoSpeedMetricsIfNeeded() {
        _uiState.update { current ->
            val metrics = AutoSpeedOptimizer.analyze(
                htmlCode = current.htmlCode,
                cssCode = current.cssCode,
                jsCode = current.jsCode,
                activeTab = current.autoWriteTab,
                isAutoChain = current.isAutoChainTabs,
                showEndCta = current.showEndCtaAnimation,
                target = current.autoDurationTarget,
                manualSpeedLevel = current.speedLevel,
                manualTurbo = current.isTurboMode
            )
            if (current.autoDurationTarget == AutoDurationTarget.MANUAL) {
                current.copy(sequenceMetrics = metrics)
            } else {
                current.copy(
                    speedLevel = metrics.recommendedSpeedLevel,
                    isTurboMode = metrics.requiresTurboBurst,
                    sequenceMetrics = metrics
                )
            }
        }
    }

    fun setSpeedLevel(level: Int) {
        val clamped = level.coerceIn(1, 20)
        _uiState.update {
            it.copy(
                speedLevel = clamped,
                autoDurationTarget = AutoDurationTarget.MANUAL
            )
        }
        syncAutoSpeedMetricsIfNeeded()
        triggerDebouncedAutoSave()
    }

    fun toggleTurboMode() {
        _uiState.update {
            it.copy(
                isTurboMode = !it.isTurboMode,
                autoDurationTarget = AutoDurationTarget.MANUAL
            )
        }
        syncAutoSpeedMetricsIfNeeded()
        triggerDebouncedAutoSave()
    }

    fun updateCreatorHandle(handle: String) {
        _uiState.update { it.copy(creatorHandle = handle.trim().ifEmpty { "@creator" }) }
        triggerDebouncedAutoSave()
    }

    fun selectWatermarkPosition(position: WatermarkPosition) {
        _uiState.update { it.copy(watermarkPosition = position) }
        triggerDebouncedAutoSave()
    }

    fun selectWatermarkStyle(style: WatermarkStyle) {
        _uiState.update { it.copy(watermarkStyle = style) }
        triggerDebouncedAutoSave()
    }

    fun toggleShowEndCtaAnimation() {
        _uiState.update {
            val next = !it.showEndCtaAnimation
            it.copy(
                showEndCtaAnimation = next,
                isEndCtaActive = if (!next) false else it.isEndCtaActive
            )
        }
        syncAutoSpeedMetricsIfNeeded()
        triggerDebouncedAutoSave()
    }

    fun selectEndCtaStyle(style: EndCtaStyle) {
        _uiState.update {
            it.copy(
                endCtaStyle = style,
                ctaTransparentBg = if (style == EndCtaStyle.TRANSPARENT_BAR || style == EndCtaStyle.MINIMAL_CHIPS) {
                    true
                } else {
                    it.ctaTransparentBg
                }
            )
        }
        triggerDebouncedAutoSave()
    }

    fun selectEndCtaPosition(position: EndCtaPosition) {
        _uiState.update { it.copy(endCtaPosition = position) }
        triggerDebouncedAutoSave()
    }

    fun selectCtaDisplayTiming(timing: CtaDisplayTiming) {
        _uiState.update { it.copy(ctaDisplayTiming = timing) }
        triggerDebouncedAutoSave()
    }

    fun toggleCtaTransparentBg() {
        _uiState.update { it.copy(ctaTransparentBg = !it.ctaTransparentBg) }
        triggerDebouncedAutoSave()
    }

    fun toggleCtaShowBorder() {
        _uiState.update { it.copy(ctaShowBorder = !it.ctaShowBorder) }
        triggerDebouncedAutoSave()
    }

    fun toggleCtaShowHandle() {
        _uiState.update { it.copy(ctaShowHandle = !it.ctaShowHandle) }
        triggerDebouncedAutoSave()
    }

    fun selectCtaAccentIndex(index: Int) {
        _uiState.update { it.copy(ctaAccentIndex = index) }
        triggerDebouncedAutoSave()
    }

    fun selectCtaSizeScale(scale: OverlaySizeScale) {
        _uiState.update { it.copy(ctaSizeScale = scale) }
        triggerDebouncedAutoSave()
    }

    fun updateEndCtaMessage(message: String) {
        _uiState.update { it.copy(endCtaMessage = message) }
        triggerDebouncedAutoSave()
    }

    fun toggleCtaLike() {
        _uiState.update { it.copy(ctaShowLike = !it.ctaShowLike) }
        triggerDebouncedAutoSave()
    }

    fun toggleCtaComment() {
        _uiState.update { it.copy(ctaShowComment = !it.ctaShowComment) }
        triggerDebouncedAutoSave()
    }

    fun toggleCtaSubscribe() {
        _uiState.update { it.copy(ctaShowSubscribe = !it.ctaShowSubscribe) }
        triggerDebouncedAutoSave()
    }

    fun updateCtaLikeLabel(label: String) {
        _uiState.update { it.copy(ctaLikeLabel = label) }
        triggerDebouncedAutoSave()
    }

    fun updateCtaCommentLabel(label: String) {
        _uiState.update { it.copy(ctaCommentLabel = label) }
        triggerDebouncedAutoSave()
    }

    fun updateCtaSubscribeLabel(label: String) {
        _uiState.update { it.copy(ctaSubscribeLabel = label) }
        triggerDebouncedAutoSave()
    }

    fun updateCtaSubscribedLabel(label: String) {
        _uiState.update { it.copy(ctaSubscribedLabel = label) }
        triggerDebouncedAutoSave()
    }

    fun applyCtaPlatformPreset(preset: CtaPlatformQuickPreset) {
        _uiState.update {
            it.copy(
                ctaLikeLabel = preset.likeLabel,
                ctaCommentLabel = preset.commentLabel,
                ctaSubscribeLabel = preset.subscribeLabel,
                ctaSubscribedLabel = preset.subscribedLabel,
                endCtaMessage = preset.message
            )
        }
        triggerDebouncedAutoSave()
    }

    fun triggerEndCtaPreview() {
        _uiState.update {
            it.copy(
                showEndCtaAnimation = true,
                isEndCtaActive = true,
                isCreatorCustomizeOpen = false
            )
        }
    }

    fun dismissEndCta() {
        _uiState.update { it.copy(isEndCtaActive = false) }
    }

    fun toggleAutoChainTabs() {
        _uiState.update { it.copy(isAutoChainTabs = !it.isAutoChainTabs) }
        syncAutoSpeedMetricsIfNeeded()
        triggerDebouncedAutoSave()
    }

    fun toggleAutoRevealPreview() {
        _uiState.update { it.copy(autoRevealPreviewOnFinish = !it.autoRevealPreviewOnFinish) }
        triggerDebouncedAutoSave()
    }

    fun selectBackdropPreset(index: Int) {
        _uiState.update { it.copy(backdropPresetIndex = index) }
        triggerDebouncedAutoSave()
    }

    fun toggleShowViralHookBanner() {
        _uiState.update { it.copy(showViralHookBanner = !it.showViralHookBanner) }
        triggerDebouncedAutoSave()
    }

    fun updateViralHookBadge(badge: String) {
        _uiState.update { it.copy(viralHookBadge = badge) }
        triggerDebouncedAutoSave()
    }

    fun updateViralHookTitle(title: String) {
        _uiState.update { it.copy(viralHookTitle = title) }
        triggerDebouncedAutoSave()
    }

    fun selectViralHookStyle(style: ViralHookStyle) {
        _uiState.update {
            it.copy(
                viralHookStyle = style,
                viralHookTransparentBg = if (style == ViralHookStyle.TRANSPARENT_CLEAN) {
                    true
                } else {
                    it.viralHookTransparentBg
                }
            )
        }
        triggerDebouncedAutoSave()
    }

    fun selectViralHookPosition(position: ViralHookPosition) {
        _uiState.update { it.copy(viralHookPosition = position) }
        triggerDebouncedAutoSave()
    }

    fun selectViralHookAlignment(alignment: ViralHookAlignment) {
        _uiState.update { it.copy(viralHookAlignment = alignment) }
        triggerDebouncedAutoSave()
    }

    fun toggleViralHookTransparentBg() {
        _uiState.update { it.copy(viralHookTransparentBg = !it.viralHookTransparentBg) }
        triggerDebouncedAutoSave()
    }

    fun toggleViralHookShowBorder() {
        _uiState.update { it.copy(viralHookShowBorder = !it.viralHookShowBorder) }
        triggerDebouncedAutoSave()
    }

    fun toggleViralHookShowBadge() {
        _uiState.update { it.copy(viralHookShowBadge = !it.viralHookShowBadge) }
        triggerDebouncedAutoSave()
    }

    fun selectViralHookAccentIndex(index: Int) {
        _uiState.update { it.copy(viralHookAccentIndex = index) }
        triggerDebouncedAutoSave()
    }

    fun selectViralHookSizeScale(scale: OverlaySizeScale) {
        _uiState.update { it.copy(viralHookSizeScale = scale) }
        triggerDebouncedAutoSave()
    }

    fun applyViralHookQuickPreset(preset: ViralHookQuickPreset) {
        _uiState.update {
            it.copy(
                viralHookBadge = preset.badge,
                viralHookTitle = preset.title,
                showViralHookBanner = true
            )
        }
        triggerDebouncedAutoSave()
    }

    fun selectTypingSoundProfile(profile: TypingSoundProfile) {
        _uiState.update { it.copy(typingSoundProfile = profile) }
        if (profile == TypingSoundProfile.OFF) {
            TypingSoundEngine.release()
        } else {
            TypingSoundEngine.playKeyTick(profile, 'a')
        }
        triggerDebouncedAutoSave()
    }

    fun cycleTypingSoundProfile() {
        val entries = TypingSoundProfile.entries
        val nextIdx = (_uiState.value.typingSoundProfile.ordinal + 1) % entries.size
        val nextProfile = entries[nextIdx]
        selectTypingSoundProfile(nextProfile)
    }

    fun toggleUseRecordingCountdown() {
        _uiState.update { it.copy(useRecordingCountdown = !it.useRecordingCountdown) }
        triggerDebouncedAutoSave()
    }

    fun toggleKeepPreviewOpenInRecord() {
        _uiState.update { it.copy(keepPreviewOpenInRecord = !it.keepPreviewOpenInRecord) }
        triggerDebouncedAutoSave()
    }

    fun setCreatorCustomizeOpen(open: Boolean) {
        _uiState.update { it.copy(isCreatorCustomizeOpen = open) }
    }

    fun setViralTemplatesSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isViralTemplatesSheetOpen = open) }
    }

    fun adjustPreviewSectionHeight(deltaDp: Float) {
        _uiState.update { current ->
            val updated = (current.previewSectionHeightDp + deltaDp).coerceIn(85f, 460f)
            current.copy(previewSectionHeightDp = updated)
        }
        triggerDebouncedAutoSave()
    }

    fun setPreviewSectionHeight(heightDp: Float) {
        _uiState.update { current ->
            current.copy(previewSectionHeightDp = heightDp.coerceIn(85f, 460f))
        }
        triggerDebouncedAutoSave()
    }

    /**
     * Enters Clean Recording Mode and starts typing cleanly from 0% (with optional 3-2-1 creator countdown).
     */
    fun startCleanShortsRecording() {
        typewriterJob?.cancel()
        countdownJob?.cancel()
        clearRuntimeDomState()
        val useCountdown = _uiState.value.useRecordingCountdown
        _uiState.update {
            it.copy(
                isRecordingMode = true,
                isCreatorCustomizeOpen = false,
                isViralTemplatesSheetOpen = false,
                isAutoWritePlaying = false,
                isEndCtaActive = false,
                isFloatingPreviewOpen = if (it.keepPreviewOpenInRecord) true else it.isFloatingPreviewOpen,
                recordingCountdownSeconds = if (useCountdown) 3 else 0,
                typedCharCount = 0,
                autoWriteCurrentLine = 1,
                autoWriteCurrentColumn = 1,
                autoWriteTab = if (it.isAutoChainTabs) CodeTab.INDEX_HTML else it.autoWriteTab
            )
        }
        updateProgressiveAutoWritePreview(force = true)

        if (useCountdown) {
            countdownJob = viewModelScope.launch {
                for (sec in 3 downTo 1) {
                    _uiState.update { it.copy(recordingCountdownSeconds = sec) }
                    delay(850L)
                }
                _uiState.update { it.copy(recordingCountdownSeconds = 0) }
                startOrResumeAutoWrite()
            }
        } else {
            startOrResumeAutoWrite()
        }
    }

    fun skipRecordingCountdownNow() {
        countdownJob?.cancel()
        _uiState.update { it.copy(recordingCountdownSeconds = 0) }
        startOrResumeAutoWrite()
    }

    fun handleSingleTapGesture() {
        val state = _uiState.value
        if (state.isAutoWritePlaying) {
            pauseAutoWrite()
        } else {
            if (state.typedCharCount >= state.autoWriteFullCode.length) {
                clearRuntimeDomState()
                _uiState.update {
                    it.copy(
                        autoWriteTab = if (it.isAutoChainTabs && it.autoWriteTab == CodeTab.SCRIPT_JS) {
                            CodeTab.INDEX_HTML
                        } else {
                            it.autoWriteTab
                        },
                        typedCharCount = 0,
                        autoWriteCurrentLine = 1,
                        autoWriteCurrentColumn = 1,
                        isEndCtaActive = false
                    )
                }
                updateProgressiveAutoWritePreview(force = true)
            } else {
                _uiState.update { it.copy(isEndCtaActive = false) }
            }
            startOrResumeAutoWrite()
        }
    }

    fun handleDoubleTapGesture() {
        val nextPreview = !_uiState.value.isFloatingPreviewOpen
        _uiState.update { it.copy(isFloatingPreviewOpen = nextPreview) }
        if (nextPreview) {
            updateProgressiveAutoWritePreview(force = true)
        }
    }

    fun handleTripleTapGesture() {
        val enteringRecord = !_uiState.value.isRecordingMode
        if (enteringRecord) {
            startCleanShortsRecording()
        } else {
            exitRecordingMode()
        }
    }

    fun exitRecordingMode() {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                isRecordingMode = false,
                recordingCountdownSeconds = 0
            )
        }
    }

    fun restartAutoWrite() {
        typewriterJob?.cancel()
        clearRuntimeDomState()
        _uiState.update {
            it.copy(
                typedCharCount = 0,
                autoWriteCurrentLine = 1,
                autoWriteCurrentColumn = 1,
                isAutoWritePlaying = false,
                isEndCtaActive = false
            )
        }
        updateProgressiveAutoWritePreview(force = true)
        startOrResumeAutoWrite()
    }

    fun skipNearCompletionForDemo() {
        val fullCode = _uiState.value.autoWriteFullCode
        val total = fullCode.length
        if (total <= 0) return
        val target = max(0, (total * 0.94f).roundToInt())
        val (line, col) = computeLineAndColumn(fullCode, target)
        _uiState.update {
            it.copy(
                typedCharCount = target,
                autoWriteCurrentLine = line,
                autoWriteCurrentColumn = col,
                isEndCtaActive = false
            )
        }
        updateProgressiveAutoWritePreview(force = true)
        if (!_uiState.value.isAutoWritePlaying) {
            startOrResumeAutoWrite()
        }
    }

    fun startOrResumeAutoWrite() {
        typewriterJob?.cancel()
        syncAutoSpeedMetricsIfNeeded()
        _uiState.update { state ->
            val (line, col) = computeLineAndColumn(state.autoWriteFullCode, state.typedCharCount)
            state.copy(
                isAutoWritePlaying = true,
                autoWriteCurrentLine = line,
                autoWriteCurrentColumn = col,
                isEndCtaActive = false
            )
        }

        typewriterJob = viewModelScope.launch {
            var lastTargetMode = _uiState.value.autoDurationTarget
            var lastAutoChain = _uiState.value.isAutoChainTabs
            var remainingSteps = countRemainingSequenceSteps(_uiState.value)
            var typingBudgetDeadlineWallMs = computeWallDeadlineMs(_uiState.value, remainingSteps)

            while (_uiState.value.isAutoWritePlaying) {
                val state = _uiState.value
                if (state.autoDurationTarget != lastTargetMode || state.isAutoChainTabs != lastAutoChain) {
                    lastTargetMode = state.autoDurationTarget
                    lastAutoChain = state.isAutoChainTabs
                    remainingSteps = countRemainingSequenceSteps(state)
                    typingBudgetDeadlineWallMs = computeWallDeadlineMs(state, remainingSteps)
                }

                val fullCode = state.autoWriteFullCode
                val fullLength = fullCode.length

                if (state.typedCharCount >= fullLength) {
                    val nextChainTab = when (state.autoWriteTab) {
                        CodeTab.INDEX_HTML -> if (state.isAutoChainTabs) CodeTab.STYLE_CSS else null
                        CodeTab.STYLE_CSS -> if (state.isAutoChainTabs) CodeTab.SCRIPT_JS else null
                        CodeTab.SCRIPT_JS -> null
                    }

                    if (nextChainTab != null) {
                        updateProgressiveAutoWritePreview(force = true)
                        val isShortPreset = state.autoDurationTarget == AutoDurationTarget.SEC_15 ||
                            state.autoDurationTarget == AutoDurationTarget.SEC_20
                        val preSlidePause = if (isShortPreset) 400L else 600L
                        val slideWait = if (isShortPreset) 680L else 800L

                        // 1. Natural pause at 100% of current file so the viewer sees the completed code
                        delay(preSlidePause)
                        _uiState.update {
                            it.copy(
                                autoWriteTab = nextChainTab,
                                typedCharCount = 0,
                                autoWriteCurrentLine = 1,
                                autoWriteCurrentColumn = 1
                            )
                        }
                        remainingSteps = countRemainingSequenceSteps(_uiState.value)
                        updateProgressiveAutoWritePreview(force = true)
                        // 2. Allow the 680ms horizontal slide transition to complete
                        delay(slideWait)
                        continue
                    } else {
                        updateProgressiveAutoWritePreview(force = true)
                        _uiState.update {
                            it.copy(
                                isAutoWritePlaying = false,
                                isFloatingPreviewOpen = if (it.autoRevealPreviewOnFinish) true else it.isFloatingPreviewOpen,
                                isEndCtaActive = it.showEndCtaAnimation
                            )
                        }
                        break
                    }
                }

                val currentChar = fullCode.getOrNull(state.typedCharCount)
                val cadenceMultiplier = when {
                    currentChar == null -> 1.0f
                    currentChar == '\n' -> 1.75f
                    currentChar == ';' || currentChar == '}' || currentChar == '>' -> 1.35f
                    currentChar == '{' || currentChar == '(' || currentChar == ':' || currentChar == '=' -> 1.18f
                    currentChar.isLetterOrDigit() -> 0.85f
                    else -> 1.0f
                }

                var stepsToAdvance = 1
                val effectiveDelayMs: Long

                if (state.autoDurationTarget != AutoDurationTarget.MANUAL) {
                    val remSteps = max(1, remainingSteps)
                    val remTransitions = countRemainingTransitions(state)
                    val isShortPreset = state.autoDurationTarget == AutoDurationTarget.SEC_15 ||
                        state.autoDurationTarget == AutoDurationTarget.SEC_20
                    val perTransitionMs = if (isShortPreset) 1080L else 1400L
                    val remainingTypingWallMs = max(
                        80L,
                        typingBudgetDeadlineWallMs - System.currentTimeMillis() - (remTransitions * perTransitionMs)
                    )
                    val idealMsPerStep = remainingTypingWallMs.toFloat() / remSteps.toFloat()
                    val maxNaturalDelayMs = when (state.autoDurationTarget) {
                        AutoDurationTarget.SEC_15 -> 16f
                        AutoDurationTarget.SEC_20 -> 22f
                        AutoDurationTarget.SEC_30 -> 30f
                        AutoDurationTarget.SEC_45 -> 38f
                        AutoDurationTarget.SEC_60 -> 44f
                        AutoDurationTarget.MANUAL -> 44f
                    }
                    val cappedMsPerStep = min(idealMsPerStep, maxNaturalDelayMs)

                    if (cappedMsPerStep < 12.5f) {
                        // High-speed 60fps multi-step pacing so long code still finishes inside 15s/20s/30s/1m!
                        stepsToAdvance = ceil(12.5f / max(1.0f, cappedMsPerStep)).toInt().coerceIn(1, 16)
                        effectiveDelayMs = 11L
                    } else {
                        stepsToAdvance = 1
                        effectiveDelayMs = (cappedMsPerStep * cadenceMultiplier).toLong().coerceIn(8L, 60L)
                    }
                } else {
                    val level = state.speedLevel.coerceIn(1, 20)
                    val baseDelayMs = max(10L, (108L - (level * 4.8f)).toLong())
                    val rawDelay = (baseDelayMs * cadenceMultiplier).toLong().coerceAtLeast(8L)
                    effectiveDelayMs = if (state.isTurboMode) {
                        max(6L, rawDelay / 2L)
                    } else {
                        rawDelay
                    }
                }

                delay(effectiveDelayMs)

                var actualAdvancedSteps = 0
                var lastTypedChar: Char? = null
                _uiState.update { current ->
                    val code = current.autoWriteFullCode
                    val len = code.length
                    var nextIndex = current.typedCharCount
                    var line = current.autoWriteCurrentLine
                    var col = current.autoWriteCurrentColumn
                    var advanced = 0

                    while (nextIndex < len && advanced < stepsToAdvance) {
                        val ch = code[nextIndex]
                        lastTypedChar = ch
                        nextIndex++
                        if (ch == '\n') {
                            line++
                            col = 1
                            while (nextIndex < len && (code[nextIndex] == ' ' || code[nextIndex] == '\t')) {
                                nextIndex++
                                col++
                            }
                        } else {
                            col++
                        }
                        advanced++
                    }
                    actualAdvancedSteps = advanced

                    current.copy(
                        typedCharCount = nextIndex.coerceAtMost(len),
                        autoWriteCurrentLine = line,
                        autoWriteCurrentColumn = col
                    )
                }
                remainingSteps = max(1, remainingSteps - actualAdvancedSteps)

                // Synthesized ASMR Mechanical Key Tick for zero-editing creator videos
                val soundProfile = _uiState.value.typingSoundProfile
                if (soundProfile != TypingSoundProfile.OFF && actualAdvancedSteps > 0) {
                    TypingSoundEngine.playKeyTick(soundProfile, lastTypedChar)
                }

                // Real-time Live Preview update (deduplicated & frame-paced for large code)
                updateProgressiveAutoWritePreview(force = false)
            }
        }
    }

    private fun countRemainingTransitions(state: StudioUiState): Int {
        if (!state.isAutoChainTabs) return 0
        return when (state.autoWriteTab) {
            CodeTab.INDEX_HTML -> 2
            CodeTab.STYLE_CSS -> 1
            CodeTab.SCRIPT_JS -> 0
        }
    }

    private fun countRemainingSequenceSteps(state: StudioUiState): Int {
        val currentRem = AutoSpeedOptimizer.countStepsAndWeight(
            state.autoWriteFullCode,
            state.typedCharCount
        ).first
        if (!state.isAutoChainTabs) return max(1, currentRem)

        val upcomingSteps = when (state.autoWriteTab) {
            CodeTab.INDEX_HTML -> {
                AutoSpeedOptimizer.countStepsAndWeight(state.cssCode, 0).first +
                    AutoSpeedOptimizer.countStepsAndWeight(state.jsCode, 0).first
            }
            CodeTab.STYLE_CSS -> {
                AutoSpeedOptimizer.countStepsAndWeight(state.jsCode, 0).first
            }
            CodeTab.SCRIPT_JS -> 0
        }
        return max(1, currentRem + upcomingSteps)
    }

    private fun computeWallDeadlineMs(state: StudioUiState, remSteps: Int = countRemainingSequenceSteps(state)): Long {
        val now = System.currentTimeMillis()
        val target = state.autoDurationTarget
        if (target == AutoDurationTarget.MANUAL) return now + 60_000L

        val totalSteps = max(1, state.sequenceMetrics.effectiveSteps)
        val remFraction = (remSteps.toFloat() / totalSteps.toFloat()).coerceIn(0.05f, 1.0f)

        val isShortPreset = target == AutoDurationTarget.SEC_15 || target == AutoDurationTarget.SEC_20
        val outroReserveMs = if (state.showEndCtaAnimation) {
            if (isShortPreset) 1300L else 2000L
        } else {
            400L
        }
        val usableTargetMs = max(2500L, (target.seconds * 1000L) - outroReserveMs)
        return now + (usableTargetMs * remFraction).toLong()
    }

    fun pauseAutoWrite() {
        typewriterJob?.cancel()
        TypingSoundEngine.stopAll()
        _uiState.update { it.copy(isAutoWritePlaying = false) }
    }

    override fun onCleared() {
        autoSaveJob?.cancel()
        typewriterJob?.cancel()
        countdownJob?.cancel()
        TypingSoundEngine.release()
        super.onCleared()
    }

    companion object {
        fun provideFactory(repository: CodeProjectRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StudioViewModel(repository) as T
                }
            }
        }
    }
}
