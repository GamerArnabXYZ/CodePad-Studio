package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.CodeProjectEntity
import com.example.data.CodeTab
import com.example.engine.CodeFormatter
import com.example.engine.NativeLiveEngine
import com.example.engine.SyntaxHighlighter
import com.example.engine.TypingSoundEngine
import com.example.engine.TypingSoundProfile
import com.example.ui.theme.DarkEditorPalette
import com.example.ui.theme.createAppTypography
import com.example.viewmodel.CreatorAccentPalette
import com.example.viewmodel.EndCtaStyle
import com.example.viewmodel.OverlaySizeScale
import com.example.viewmodel.ViralHookStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

    @Test
    fun appContext_readsUpdatedAppName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("CodePad Studio", context.getString(R.string.app_name))
    }

    @Test
    fun nativeLiveEngine_parsesHtmlCssJs_andExecutesDomMutationsWithCommentsAndHex8() {
        val html = """
            <div class="card">
                <h1 id="title">Initial Title</h1>
                <p id="status">Waiting</p>
                <button onclick="boostPower()">Boost</button>
            </div>
        """.trimIndent()

        val css = """
            /* Multi-line CSS comment
               with { braces } inside that should be ignored */
            body {
                background-color: #0D111A;
            }
            .card {
                background-color: #161F30FF;
                border-radius: 16px;
                padding: 18px;
            }
            #title {
                color: #38BDF8;
                font-size: 20px;
            }
        """.trimIndent()

        val js = """
            let power = 10;
            console.log("Engine online");
            function boostPower() {
                power += 5;
                document.getElementById("title").innerText = `Power: ${'$'}{power}`;
                document.getElementById("status").style.backgroundColor = "#22C55E";
                document.body.style.backgroundColor = "#1E293B";
                console.info("Boosted power");
            }
        """.trimIndent()

        val initialSnapshot = NativeLiveEngine.evaluateProject(
            html = html,
            css = css,
            js = js
        )
        assertTrue(initialSnapshot.rootNodes.isNotEmpty())
        assertTrue(initialSnapshot.functions.containsKey("boostPower"))
        assertTrue(initialSnapshot.logs.any { it.message.contains("Engine online") })

        val mutableVars = initialSnapshot.variables.toMutableMap()
        val textOverrides = mutableMapOf<String, String>()
        val colorOverrides = mutableMapOf<String, Color>()
        val bgOverrides = mutableMapOf<String, Color>()

        val callLogs = NativeLiveEngine.invokeJsFunction(
            functionName = "boostPower",
            functions = initialSnapshot.functions,
            variables = mutableVars,
            textOverrides = textOverrides,
            colorOverrides = colorOverrides,
            bgOverrides = bgOverrides
        )

        assertEquals("15", mutableVars["power"])
        assertEquals("Power: 15", textOverrides["title"])
        assertNotNull(bgOverrides["status"])
        assertNotNull(bgOverrides["body"])
        assertTrue(callLogs.any { it.message.contains("Boosted power") })

        val updatedSnapshot = NativeLiveEngine.evaluateProject(
            html = html,
            css = css,
            js = js,
            existingVariables = mutableVars,
            domTextOverrides = textOverrides,
            domColorOverrides = colorOverrides,
            domBgOverrides = bgOverrides,
            runTopLevelJs = false
        )
        val cardNode = updatedSnapshot.rootNodes.first()
        assertNotNull(cardNode.style.backgroundColor)
        val titleNode = cardNode.children.first { it.id == "title" }
        assertEquals("Power: 15", titleNode.text)
    }

    @Test
    fun codeFormatter_handlesSingleLineRulesAndMultiClosingBracesWithoutMisindent() {
        val rawCss = "/* header */ h1 { color: #38bdf8; } .box { padding: 12px; }"
        val formattedCss = CodeFormatter.format(rawCss, CodeTab.STYLE_CSS)
        assertTrue(formattedCss.contains("h1 {"))
        assertTrue(formattedCss.contains(".box {"))

        val rawJs = """
            function run() {
            if (true) {
            console.log("ok");
            }}
        """.trimIndent()
        val formattedJs = CodeFormatter.format(rawJs, CodeTab.SCRIPT_JS)
        assertTrue(formattedJs.endsWith("}"))
    }

    @Test
    fun syntaxHighlighter_preservesUrlsBeforeInlineCommentsAndRespectsCacheBounds() {
        val line = """const api = "https://example.com/api"; // trailing comment"""
        val annotated = SyntaxHighlighter.highlight(line, CodeTab.SCRIPT_JS, DarkEditorPalette)
        assertEquals(line, annotated.text)
        val precomputed = SyntaxHighlighter.precomputeLines(line, CodeTab.SCRIPT_JS, DarkEditorPalette)
        assertEquals(1, precomputed.lines.size)
    }

    @Test
    fun typingSoundEngine_lifecycleSafeInitAndRelease() {
        TypingSoundEngine.playKeyTick(TypingSoundProfile.MECH_THOCK, 'a')
        TypingSoundEngine.playKeyTick(TypingSoundProfile.CRISP_CLICK, '\n')
        TypingSoundEngine.stopAll()
        TypingSoundEngine.release()
    }

    @Test
    fun hookAndCtaStyles_supportTransparentAndAllPresets() {
        assertTrue(ViralHookStyle.entries.contains(ViralHookStyle.TRANSPARENT_CLEAN))
        assertTrue(EndCtaStyle.entries.contains(EndCtaStyle.TRANSPARENT_BAR))
        assertEquals(CreatorAccentPalette.CYBER_CYAN_PINK, CreatorAccentPalette.fromIndex(999))
        assertEquals(OverlaySizeScale.STANDARD, OverlaySizeScale.fromIndex(999))
    }

    @Test
    fun typographyScale_andDefaultFontSizes_areCompactAndConfigurable() {
        val defaultEntity = CodeProjectEntity()
        assertEquals(11.5f, defaultEntity.editorFontSize, 0.01f)
        assertEquals(11f, defaultEntity.autoWriteFontSize, 0.01f)
        assertEquals(0.92f, defaultEntity.uiFontScale, 0.01f)

        val compactTypography = createAppTypography(0.80f)
        val largeTypography = createAppTypography(1.20f)
        assertTrue(compactTypography.bodyLarge.fontSize.value < largeTypography.bodyLarge.fontSize.value)
        assertTrue(compactTypography.titleMedium.fontSize.value < largeTypography.titleMedium.fontSize.value)
    }
}
