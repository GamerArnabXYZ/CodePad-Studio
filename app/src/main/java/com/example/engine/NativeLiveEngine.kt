package com.example.engine

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel(val badge: String) {
    LOG("LOG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERR"),
    SYSTEM("SYS")
}

data class ConsoleLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()),
    val level: LogLevel,
    val message: String,
    val sourceLocation: String = "runtime",
    val stackTrace: String? = null
)

data class ComputedNodeStyle(
    val backgroundColor: Color? = null,
    val textColor: Color? = null,
    val borderColor: Color? = null,
    val borderRadiusDp: Float = 0f,
    val paddingDp: Float = 0f,
    val fontSizeSp: Float? = null,
    val isBold: Boolean = false
)

data class NativeDomNode(
    val uid: String,
    val tag: String,
    val id: String = "",
    val classes: List<String> = emptyList(),
    val text: String = "",
    val onClickFunction: String? = null,
    val progressValue: Float? = null,
    val progressMax: Float = 100f,
    val style: ComputedNodeStyle = ComputedNodeStyle(),
    val children: List<NativeDomNode> = emptyList()
)

data class JsFunctionDef(
    val name: String,
    val startLine: Int,
    val bodyLines: List<Pair<Int, String>>
)

data class NativeRuntimeSnapshot(
    val rootNodes: List<NativeDomNode>,
    val bodyStyle: ComputedNodeStyle,
    val functions: Map<String, JsFunctionDef>,
    val variables: Map<String, String>,
    val logs: List<ConsoleLogEntry>
)

/**
 * Pure Kotlin Native HTML + CSS + JS Engine.
 * Zero WebView / Zero HTML-engine jugad: parses DOM trees, computes CSS styles,
 * executes JS statements & onclick handlers, mutates DOM state, and traps stack traces!
 */
object NativeLiveEngine {

    private val cssCommentRegex = Regex("""/\*[\s\S]*?\*/""")
    private val cssBlockRegex = Regex("""([^{]+)\{([^}]*)\}""")
    private val htmlCommentRegex = Regex("""<!--[\s\S]*?-->""")
    private val htmlHeadOrScriptRegex = Regex("""(?i)<(head|style|script|title)\b[^>]*>[\s\S]*?</\1>""")
    private val htmlDoctypeRegex = Regex("""(?i)<!doctype[^>]*>""")
    private val htmlTokenRegex = Regex("""(</?[a-zA-Z0-9!-]+[^>]*>|[^<]+)""")
    private val htmlAttrRegex = Regex("""([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    private val whitespaceSplitRegex = Regex("\\s+")
    private val jsFnMatchRegex = Regex("""^(?:async\s+)?function\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s*\([^)]*\)\s*\{?""")
    private val jsArrowFnMatchRegex = Regex(
        """^(?:const|let|var)\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s*=\s*(?:async\s*)?\([^)]*\)\s*=>\s*\{?"""
    )
    private val jsThrowRegex = Regex("""^throw\s+(?:new\s+Error\s*\(\s*)?(.+?)\)?$""")
    private val jsConsoleRegex = Regex("""^console\.(log|info|warn|error)\s*\((.*)\)$""")
    private val jsDomTextRegex = Regex(
        """^document\.(?:getElementById|querySelector)\(\s*["']#?([^"']+)["']\s*\)\.(?:innerText|textContent|innerHTML)\s*=\s*(.+)$"""
    )
    private val jsDomColorRegex = Regex(
        """^document\.(?:(?:getElementById|querySelector)\(\s*["']#?([^"']+)["']\s*\)|(body))\.style\.color\s*=\s*(.+)$"""
    )
    private val jsDomBgColorRegex = Regex(
        """^document\.(?:(?:getElementById|querySelector)\(\s*["']#?([^"']+)["']\s*\)|(body))\.style\.(?:backgroundColor|background)\s*=\s*(.+)$"""
    )
    private val jsIncDecRegex = Regex("""^([a-zA-Z_$][a-zA-Z0-9_$]*)\s*(\+\+|--)$""")
    private val jsCompoundAssignRegex = Regex("""^([a-zA-Z_$][a-zA-Z0-9_$]*)\s*(\+=|-=)\s*(.+)$""")
    private val jsVarRegex = Regex("""^(?:(?:let|const|var)\s+)?([a-zA-Z_$][a-zA-Z0-9_$]*)\s*=\s*(.+)$""")
    private val templateVarRegex = Regex("""\$\{\s*([a-zA-Z_$][a-zA-Z0-9_$]*)\s*\}""")
    private val rgbColorRegex = Regex("""rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})""")
    private val eightDigitHexRegex = Regex("""#([0-9a-fA-F]{8})\b""")
    private val sixDigitHexRegex = Regex("""#([0-9a-fA-F]{6})\b""")
    private val threeDigitHexRegex = Regex("""#([0-9a-fA-F]{3})\b""")
    private val numberPxRegex = Regex("""(\d+(?:\.\d+)?)""")

    private var cachedCssInput: String? = null
    private var cachedCssStyleMap: Map<String, ComputedNodeStyle> = emptyMap()

    fun normalizeProgressiveHtml(html: String): String {
        if (html.isEmpty()) return ""
        val lastOpenBracket = html.lastIndexOf('<')
        val lastCloseBracket = html.lastIndexOf('>')
        return if (lastOpenBracket > lastCloseBracket) {
            html.substring(0, lastOpenBracket).trimEnd()
        } else {
            html
        }
    }

    fun evaluateProject(
        html: String,
        css: String,
        js: String,
        existingVariables: Map<String, String> = emptyMap(),
        domTextOverrides: Map<String, String> = emptyMap(),
        domColorOverrides: Map<String, Color> = emptyMap(),
        domBgOverrides: Map<String, Color> = emptyMap(),
        runTopLevelJs: Boolean = true
    ): NativeRuntimeSnapshot {
        val logs = mutableListOf<ConsoleLogEntry>()
        val styleMap = parseCssRules(css, logs)
        val defaultBodyStyle = ComputedNodeStyle(
            backgroundColor = Color(0xFF0B0E17),
            textColor = Color(0xFFF1F5F9),
            paddingDp = 14f
        )
        val bodyStyle = styleMap["body"]?.let { mergeStyles(defaultBodyStyle, it) } ?: defaultBodyStyle

        val rawNodes = parseHtmlNodes(
            html = html,
            styleMap = styleMap,
            domTextOverrides = domTextOverrides,
            domColorOverrides = domColorOverrides,
            domBgOverrides = domBgOverrides,
            logs = logs
        )

        val variables = existingVariables.toMutableMap()
        val functions = mutableMapOf<String, JsFunctionDef>()
        val mutableTextOverrides = domTextOverrides.toMutableMap()
        val mutableColorOverrides = domColorOverrides.toMutableMap()
        val mutableBgOverrides = domBgOverrides.toMutableMap()

        parseAndRunJs(
            js = js,
            executeTopLevel = runTopLevelJs,
            variables = variables,
            functions = functions,
            textOverrides = mutableTextOverrides,
            colorOverrides = mutableColorOverrides,
            bgOverrides = mutableBgOverrides,
            logs = logs
        )

        val finalNodes = if (
            mutableTextOverrides != domTextOverrides ||
            mutableColorOverrides != domColorOverrides ||
            mutableBgOverrides != domBgOverrides
        ) {
            parseHtmlNodes(
                html = html,
                styleMap = styleMap,
                domTextOverrides = mutableTextOverrides,
                domColorOverrides = mutableColorOverrides,
                domBgOverrides = mutableBgOverrides,
                logs = mutableListOf()
            )
        } else {
            rawNodes
        }

        val finalBodyStyle = bodyStyle.copy(
            backgroundColor = mutableBgOverrides["body"] ?: bodyStyle.backgroundColor,
            textColor = mutableColorOverrides["body"] ?: bodyStyle.textColor
        )

        return NativeRuntimeSnapshot(
            rootNodes = finalNodes,
            bodyStyle = finalBodyStyle,
            functions = functions,
            variables = variables,
            logs = logs
        )
    }

    fun invokeJsFunction(
        functionName: String,
        functions: Map<String, JsFunctionDef>,
        variables: MutableMap<String, String>,
        textOverrides: MutableMap<String, String>,
        colorOverrides: MutableMap<String, Color>,
        bgOverrides: MutableMap<String, Color> = mutableMapOf()
    ): List<ConsoleLogEntry> {
        val logs = mutableListOf<ConsoleLogEntry>()
        val cleanName = functionName.substringBefore("(").trim()
        val fn = functions[cleanName]
        if (fn == null) {
            logs.add(
                ConsoleLogEntry(
                    level = LogLevel.ERROR,
                    message = "ReferenceError: $cleanName is not defined",
                    sourceLocation = "index.html:onclick",
                    stackTrace = "ReferenceError: $cleanName is not defined\n    at HTMLButtonElement.onclick (index.html:1:1)\n    at NativeComposeDomRenderer.dispatchTap (NativeLiveEngine.kt:190)"
                )
            )
            return logs
        }

        for ((lineNum, rawStmt) in fn.bodyLines) {
            val stmt = rawStmt.trim()
            if (stmt.isEmpty() || stmt.startsWith("//")) continue
            val halted = executeSingleStatement(
                stmt = stmt,
                lineNum = lineNum,
                scopeName = "$cleanName()",
                variables = variables,
                textOverrides = textOverrides,
                colorOverrides = colorOverrides,
                bgOverrides = bgOverrides,
                logs = logs
            )
            if (halted) break
        }
        return logs
    }

    @Synchronized
    private fun parseCssRules(
        css: String,
        logs: MutableList<ConsoleLogEntry>
    ): Map<String, ComputedNodeStyle> {
        if (css == cachedCssInput) {
            return cachedCssStyleMap
        }
        val result = mutableMapOf<String, ComputedNodeStyle>()
        val noComments = css.replace(cssCommentRegex, "")
        val openBraces = noComments.count { it == '{' }
        val closeBraces = noComments.count { it == '}' }
        val cleaned = if (openBraces > closeBraces) {
            noComments + "}".repeat(openBraces - closeBraces)
        } else {
            noComments
        }

        cssBlockRegex.findAll(cleaned).forEach { match ->
            val selectorGroup = match.groupValues[1].trim()
            val declarations = match.groupValues[2].trim()
            val computed = parseCssDeclarations(declarations, logs)

            selectorGroup.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { sel ->
                val prev = result[sel] ?: ComputedNodeStyle()
                result[sel] = mergeStyles(prev, computed)
            }
        }
        cachedCssInput = css
        cachedCssStyleMap = result
        return result
    }

    private fun parseCssDeclarations(
        declarations: String,
        logs: MutableList<ConsoleLogEntry>
    ): ComputedNodeStyle {
        var bg: Color? = null
        var fg: Color? = null
        var border: Color? = null
        var radius = 0f
        var padding = 0f
        var fontSize: Float? = null
        var isBold = false

        declarations.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { decl ->
            val parts = decl.split(":", limit = 2)
            if (parts.size == 2) {
                val prop = parts[0].trim().lowercase()
                val value = parts[1].trim()
                if (value.isNotEmpty()) {
                    when (prop) {
                        "background-color", "background" -> parseHexColor(value)?.let { bg = it }
                        "color" -> parseHexColor(value)?.let { fg = it }
                        "border-color", "border" -> parseHexColor(value)?.let { border = it }
                        "border-radius" -> parseNumberPxOrNull(value)?.let { radius = it }
                        "padding" -> parseNumberPxOrNull(value)?.let { padding = it }
                        "font-size" -> {
                            val parsed = parseNumberPxOrNull(value)
                            val hasUnit = value.contains("px", ignoreCase = true) ||
                                value.contains("sp", ignoreCase = true) ||
                                value.contains("rem", ignoreCase = true) ||
                                value.contains("em", ignoreCase = true)
                            if (parsed != null && (parsed >= 8f || hasUnit)) {
                                fontSize = parsed.coerceIn(8f, 48f)
                            }
                        }
                        "font-weight" -> isBold = value.contains("bold", ignoreCase = true) || value == "700" || value == "600" || value == "800"
                    }
                }
            } else {
                logs.add(
                    ConsoleLogEntry(
                        level = LogLevel.WARN,
                        message = "CSS warning: Malformed declaration '$decl'",
                        sourceLocation = "style.css"
                    )
                )
            }
        }
        return ComputedNodeStyle(
            backgroundColor = bg,
            textColor = fg,
            borderColor = border,
            borderRadiusDp = radius,
            paddingDp = padding,
            fontSizeSp = fontSize,
            isBold = isBold
        )
    }

    private fun mergeStyles(base: ComputedNodeStyle, override: ComputedNodeStyle): ComputedNodeStyle {
        return ComputedNodeStyle(
            backgroundColor = override.backgroundColor ?: base.backgroundColor,
            textColor = override.textColor ?: base.textColor,
            borderColor = override.borderColor ?: base.borderColor,
            borderRadiusDp = if (override.borderRadiusDp > 0f) override.borderRadiusDp else base.borderRadiusDp,
            paddingDp = if (override.paddingDp > 0f) override.paddingDp else base.paddingDp,
            fontSizeSp = override.fontSizeSp ?: base.fontSizeSp,
            isBold = override.isBold || base.isBold
        )
    }

    private fun parseHtmlNodes(
        html: String,
        styleMap: Map<String, ComputedNodeStyle>,
        domTextOverrides: Map<String, String>,
        domColorOverrides: Map<String, Color>,
        domBgOverrides: Map<String, Color>,
        logs: MutableList<ConsoleLogEntry>
    ): List<NativeDomNode> {
        val withoutComments = html
            .replace(htmlCommentRegex, "")
            .replace(htmlDoctypeRegex, "")
            .replace(htmlHeadOrScriptRegex, "")
            .trim()
        val lastOpenBracket = withoutComments.lastIndexOf('<')
        val lastCloseBracket = withoutComments.lastIndexOf('>')
        val cleaned = if (lastOpenBracket > lastCloseBracket) {
            withoutComments.substring(0, lastOpenBracket).trim()
        } else {
            withoutComments
        }
        if (cleaned.isEmpty()) return emptyList()

        val stack = ArrayDeque<MutableDomBuilder>()
        val rootBuilder = MutableDomBuilder(uid = "root", tag = "root")
        stack.addLast(rootBuilder)

        var nodeCounter = 0
        htmlTokenRegex.findAll(cleaned).forEach { match ->
            val token = match.value.trim()
            if (token.isEmpty()) return@forEach

            if (token.startsWith("<!") || token.startsWith("<?")) {
                return@forEach
            } else if (token.startsWith("</")) {
                val closeTag = token.removePrefix("</").removeSuffix(">").trim().lowercase()
                if (closeTag == "html" || closeTag == "body" || closeTag == "head") {
                    return@forEach
                }
                if (stack.size > 1 && stack.last().tag == closeTag) {
                    val finished = stack.removeLast().build(styleMap, domTextOverrides, domColorOverrides, domBgOverrides)
                    stack.last().children.add(finished)
                } else if (stack.size > 1) {
                    val finished = stack.removeLast().build(styleMap, domTextOverrides, domColorOverrides, domBgOverrides)
                    stack.last().children.add(finished)
                }
            } else if (token.startsWith("<")) {
                nodeCounter++
                val selfClosing = token.endsWith("/>")
                val inner = token.removePrefix("<").removeSuffix("/>").removeSuffix(">").trim()
                val tag = inner.takeWhile { !it.isWhitespace() }.lowercase()
                if (tag == "html" || tag == "body" || tag == "head") {
                    return@forEach
                }
                val attrPart = inner.drop(tag.length).trim()
                val attrs = parseAttributes(attrPart)
                val inlineStyle = attrs["style"]?.let { parseCssDeclarations(it, mutableListOf()) }

                val builder = MutableDomBuilder(
                    uid = "node_$nodeCounter",
                    tag = tag,
                    id = attrs["id"] ?: "",
                    classes = (attrs["class"] ?: "").split(whitespaceSplitRegex).filter { it.isNotEmpty() },
                    onClickFunction = attrs["onclick"],
                    progressValue = attrs["value"]?.toFloatOrNull(),
                    progressMax = attrs["max"]?.toFloatOrNull() ?: 100f,
                    inlineStyle = inlineStyle
                )

                val isVoid = selfClosing || tag in setOf(
                    "area", "base", "br", "col", "embed", "hr", "img",
                    "input", "link", "meta", "param", "source", "track", "wbr"
                )
                if (isVoid) {
                    stack.last().children.add(
                        builder.build(styleMap, domTextOverrides, domColorOverrides, domBgOverrides)
                    )
                } else {
                    stack.addLast(builder)
                }
            } else {
                val decoded = decodeHtmlEntities(token)
                val current = stack.last()
                current.textBuffer.append(if (current.textBuffer.isEmpty()) decoded else " $decoded")
            }
        }

        while (stack.size > 1) {
            val unclosed = stack.removeLast()
            logs.add(
                ConsoleLogEntry(
                    level = LogLevel.WARN,
                    message = "DOM Parser: Unclosed <${unclosed.tag}> auto-balanced by native renderer",
                    sourceLocation = "index.html"
                )
            )
            stack.last().children.add(
                unclosed.build(styleMap, domTextOverrides, domColorOverrides, domBgOverrides)
            )
        }

        return rootBuilder.children
    }

    private fun decodeHtmlEntities(raw: String): String {
        if (!raw.contains('&')) return raw
        return raw
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&bull;", "•")
            .replace("&copy;", "©")
            .replace("&rarr;", "→")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
    }

    private class MutableDomBuilder(
        val uid: String,
        val tag: String,
        val id: String = "",
        val classes: List<String> = emptyList(),
        val onClickFunction: String? = null,
        val progressValue: Float? = null,
        val progressMax: Float = 100f,
        val inlineStyle: ComputedNodeStyle? = null
    ) {
        val textBuffer = StringBuilder()
        val children = mutableListOf<NativeDomNode>()

        fun build(
            styleMap: Map<String, ComputedNodeStyle>,
            domTextOverrides: Map<String, String>,
            domColorOverrides: Map<String, Color>,
            domBgOverrides: Map<String, Color>
        ): NativeDomNode {
            var style = ComputedNodeStyle()
            styleMap[tag]?.let { style = mergeStyles(style, it) }
            classes.forEach { cls ->
                styleMap[".$cls"]?.let { style = mergeStyles(style, it) }
            }
            if (id.isNotEmpty()) {
                styleMap["#$id"]?.let { style = mergeStyles(style, it) }
            }
            inlineStyle?.let { style = mergeStyles(style, it) }
            if (id.isNotEmpty()) {
                domColorOverrides[id]?.let { overrideColor ->
                    style = style.copy(textColor = overrideColor)
                }
                domBgOverrides[id]?.let { overrideBg ->
                    style = style.copy(backgroundColor = overrideBg)
                }
            }
            val finalText = if (id.isNotEmpty() && domTextOverrides.containsKey(id)) {
                domTextOverrides[id] ?: textBuffer.toString()
            } else {
                textBuffer.toString()
            }

            return NativeDomNode(
                uid = uid,
                tag = tag,
                id = id,
                classes = classes,
                text = finalText,
                onClickFunction = onClickFunction,
                progressValue = progressValue,
                progressMax = progressMax,
                style = style,
                children = children.toList()
            )
        }
    }

    private fun parseAttributes(attrString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        htmlAttrRegex.findAll(attrString).forEach { m ->
            val key = m.groupValues[1].lowercase()
            val value = m.groupValues[2].ifEmpty { m.groupValues[3] }
            map[key] = value
        }
        return map
    }

    private fun parseAndRunJs(
        js: String,
        executeTopLevel: Boolean,
        variables: MutableMap<String, String>,
        functions: MutableMap<String, JsFunctionDef>,
        textOverrides: MutableMap<String, String>,
        colorOverrides: MutableMap<String, Color>,
        bgOverrides: MutableMap<String, Color>,
        logs: MutableList<ConsoleLogEntry>
    ) {
        val lines = js.lines()
        var currentFnName: String? = null
        var currentFnStart = 1
        var braceDepth = 0
        val currentFnBody = mutableListOf<Pair<Int, String>>()

        for ((idx, rawLine) in lines.withIndex()) {
            val lineNum = idx + 1
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                continue
            }

            if (currentFnName == null) {
                val fnMatch = jsFnMatchRegex.find(trimmed) ?: jsArrowFnMatchRegex.find(trimmed)
                if (fnMatch != null) {
                    currentFnName = fnMatch.groupValues[1]
                    currentFnStart = lineNum
                    currentFnBody.clear()
                    braceDepth = trimmed.count { it == '{' } - trimmed.count { it == '}' }
                    if (braceDepth <= 0 && trimmed.endsWith("}")) {
                        functions[currentFnName] = JsFunctionDef(currentFnName, currentFnStart, emptyList())
                        currentFnName = null
                    }
                    continue
                }

                if (executeTopLevel) {
                    val halted = executeSingleStatement(
                        stmt = trimmed,
                        lineNum = lineNum,
                        scopeName = "<anonymous>",
                        variables = variables,
                        textOverrides = textOverrides,
                        colorOverrides = colorOverrides,
                        bgOverrides = bgOverrides,
                        logs = logs
                    )
                    if (halted) break
                }
            } else {
                val openCount = trimmed.count { it == '{' }
                val closeCount = trimmed.count { it == '}' }
                braceDepth += openCount - closeCount
                if (braceDepth <= 0) {
                    val beforeBrace = trimmed.removeSuffix(";").removeSuffix("}").trim()
                    if (beforeBrace.isNotEmpty()) {
                        currentFnBody.add(lineNum to beforeBrace)
                    }
                    functions[currentFnName] = JsFunctionDef(
                        name = currentFnName,
                        startLine = currentFnStart,
                        bodyLines = currentFnBody.toList()
                    )
                    currentFnName = null
                } else {
                    currentFnBody.add(lineNum to trimmed)
                }
            }
        }
    }

    private fun executeSingleStatement(
        stmt: String,
        lineNum: Int,
        scopeName: String,
        variables: MutableMap<String, String>,
        textOverrides: MutableMap<String, String>,
        colorOverrides: MutableMap<String, Color>,
        bgOverrides: MutableMap<String, Color>,
        logs: MutableList<ConsoleLogEntry>
    ): Boolean {
        val clean = stmt.removeSuffix(";").trim()
        if (clean.isEmpty() || clean == "{" || clean == "}") return false

        // 1. Check throw new Error(...)
        val throwMatch = jsThrowRegex.find(clean)
        if (throwMatch != null) {
            val rawMsg = evaluateExpression(throwMatch.groupValues[1], variables)
            logs.add(
                ConsoleLogEntry(
                    level = LogLevel.ERROR,
                    message = "Uncaught $rawMsg",
                    sourceLocation = "script.js:$lineNum",
                    stackTrace = "Uncaught $rawMsg\n    at $scopeName (script.js:$lineNum:5)\n    at NativeJsRuntime.execute (NativeLiveEngine.kt:290)"
                )
            )
            return true
        }

        // 2. Check console.log / info / warn / error
        val consoleMatch = jsConsoleRegex.find(clean)
        if (consoleMatch != null) {
            val method = consoleMatch.groupValues[1]
            val argExpr = consoleMatch.groupValues[2]
            val evaluated = evaluateExpression(argExpr, variables)
            val level = when (method) {
                "info" -> LogLevel.INFO
                "warn" -> LogLevel.WARN
                "error" -> LogLevel.ERROR
                else -> LogLevel.LOG
            }
            logs.add(
                ConsoleLogEntry(
                    level = level,
                    message = evaluated,
                    sourceLocation = "script.js:$lineNum",
                    stackTrace = if (level == LogLevel.ERROR) {
                        "ConsoleError: $evaluated\n    at $scopeName (script.js:$lineNum:1)"
                    } else null
                )
            )
            return false
        }

        // 3. Check document.getElementById/querySelector("...").innerText / textContent = ...
        val domTextMatch = jsDomTextRegex.find(clean)
        if (domTextMatch != null) {
            val targetId = domTextMatch.groupValues[1]
            val newValue = evaluateExpression(domTextMatch.groupValues[2], variables)
            textOverrides[targetId] = newValue
            return false
        }

        // 4. Check document.getElementById/querySelector("...").style.color = ...
        val domColorMatch = jsDomColorRegex.find(clean)
        if (domColorMatch != null) {
            val targetId = domColorMatch.groupValues[1].ifEmpty { domColorMatch.groupValues[2] }
            val colorStr = evaluateExpression(domColorMatch.groupValues[3], variables)
            parseHexColor(colorStr)?.let { colorOverrides[targetId] = it }
            return false
        }

        // 4b. Check document.getElementById/querySelector("...").style.backgroundColor = ...
        val domBgMatch = jsDomBgColorRegex.find(clean)
        if (domBgMatch != null) {
            val targetId = domBgMatch.groupValues[1].ifEmpty { domBgMatch.groupValues[2] }
            val colorStr = evaluateExpression(domBgMatch.groupValues[3], variables)
            parseHexColor(colorStr)?.let { bgOverrides[targetId] = it }
            return false
        }

        // 5a. Check increment / decrement (count++ or count--)
        val incDecMatch = jsIncDecRegex.find(clean)
        if (incDecMatch != null) {
            val varName = incDecMatch.groupValues[1]
            val op = incDecMatch.groupValues[2]
            val currentVal = variables[varName]?.toDoubleOrNull() ?: 0.0
            val nextVal = if (op == "++") currentVal + 1.0 else currentVal - 1.0
            variables[varName] = if (nextVal % 1.0 == 0.0) nextVal.toInt().toString() else nextVal.toString()
            return false
        }

        // 5b. Check compound assignment (count += 5 or count -= 1)
        val compoundMatch = jsCompoundAssignRegex.find(clean)
        if (compoundMatch != null) {
            val varName = compoundMatch.groupValues[1]
            val op = compoundMatch.groupValues[2]
            val rhs = evaluateExpression(compoundMatch.groupValues[3], variables)
            val lhsNum = variables[varName]?.toDoubleOrNull()
            val rhsNum = rhs.toDoubleOrNull()
            if (lhsNum != null && rhsNum != null) {
                val res = if (op == "+=") lhsNum + rhsNum else lhsNum - rhsNum
                variables[varName] = if (res % 1.0 == 0.0) res.toInt().toString() else res.toString()
            } else if (op == "+=") {
                variables[varName] = (variables[varName] ?: "") + rhs
            }
            return false
        }

        // 5c. Check variable declaration or assignment
        val varMatch = jsVarRegex.find(clean)
        if (varMatch != null) {
            val varName = varMatch.groupValues[1]
            val expr = varMatch.groupValues[2]
            variables[varName] = evaluateExpression(expr, variables)
            return false
        }

        // 6. Unknown statement -> syntax/reference warning or error
        if (!clean.startsWith("{") && !clean.startsWith("}")) {
            logs.add(
                ConsoleLogEntry(
                    level = LogLevel.ERROR,
                    message = "SyntaxError: Unexpected statement '$clean'",
                    sourceLocation = "script.js:$lineNum",
                    stackTrace = "SyntaxError: Unexpected token in '$clean'\n    at $scopeName (script.js:$lineNum:1)"
                )
            )
            return true
        }
        return false
    }

    private fun evaluateExpression(expr: String, variables: Map<String, String>): String {
        val trimmedExpr = expr.trim()
        // Support ES6 template literals: `Hello ${name}!`
        if (trimmedExpr.startsWith("`") && trimmedExpr.endsWith("`") && trimmedExpr.length >= 2) {
            val inner = trimmedExpr.substring(1, trimmedExpr.length - 1)
            return templateVarRegex.replace(inner) { m ->
                val varName = m.groupValues[1]
                variables[varName] ?: ""
            }
        }

        val parts = trimmedExpr.split("+").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ""

        // Check if purely numeric addition
        val numericValues = parts.map { token ->
            val resolved = variables[token] ?: token
            resolved.toDoubleOrNull()
        }
        if (parts.size > 1 && numericValues.all { it != null }) {
            val sum = numericValues.sumOf { it ?: 0.0 }
            return if (sum % 1.0 == 0.0) sum.toInt().toString() else sum.toString()
        }

        return parts.joinToString("") { token ->
            when {
                (token.startsWith("\"") && token.endsWith("\"") && token.length >= 2) ||
                    (token.startsWith("'") && token.endsWith("'") && token.length >= 2) -> {
                    token.substring(1, token.length - 1)
                }
                token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                    val inner = token.substring(1, token.length - 1)
                    templateVarRegex.replace(inner) { m -> variables[m.groupValues[1]] ?: "" }
                }
                variables.containsKey(token) -> variables[token] ?: ""
                else -> token
            }
        }
    }

    fun parseHexColor(raw: String): Color? {
        val trimmed = raw.trim().lowercase()
        when (trimmed) {
            "transparent" -> return Color.Transparent
            "white" -> return Color.White
            "black" -> return Color.Black
            "red", "crimson" -> return Color(0xFFEF4444)
            "green", "emerald", "lime" -> return Color(0xFF10B981)
            "blue" -> return Color(0xFF3B82F6)
            "cyan", "sky", "aqua" -> return Color(0xFF38BDF8)
            "yellow", "amber", "gold" -> return Color(0xFFF59E0B)
            "orange", "coral" -> return Color(0xFFF97316)
            "purple", "violet", "magenta" -> return Color(0xFF8B5CF6)
            "indigo" -> return Color(0xFF6366F1)
            "pink", "rose" -> return Color(0xFFF43F5E)
            "teal" -> return Color(0xFF14B8A6)
            "gray", "grey", "slate", "silver" -> return Color(0xFF64748B)
        }

        val rgbMatch = rgbColorRegex.find(raw)
        if (rgbMatch != null) {
            val r = rgbMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 255) ?: 0
            val g = rgbMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: 0
            val b = rgbMatch.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: 0
            return Color(r, g, b)
        }

        val eightDigitMatch = eightDigitHexRegex.find(raw)
        if (eightDigitMatch != null) {
            return try {
                val rgba = eightDigitMatch.groupValues[1].toLong(16)
                val r = ((rgba shr 24) and 0xFF).toInt()
                val g = ((rgba shr 16) and 0xFF).toInt()
                val b = ((rgba shr 8) and 0xFF).toInt()
                val a = (rgba and 0xFF).toInt()
                Color(r, g, b, a)
            } catch (_: Exception) {
                null
            }
        }

        val sixDigitMatch = sixDigitHexRegex.find(raw)
        if (sixDigitMatch != null) {
            return try {
                val rgb = sixDigitMatch.groupValues[1].toLong(16)
                Color(0xFF000000 or rgb)
            } catch (_: Exception) {
                null
            }
        }
        val threeDigitMatch = threeDigitHexRegex.find(raw) ?: return null
        val hex = threeDigitMatch.groupValues[1]
        val fullHex = "${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}"
        return try {
            val rgb = fullHex.toLong(16)
            Color(0xFF000000 or rgb)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseNumberPxOrNull(raw: String): Float? {
        val numMatch = numberPxRegex.find(raw) ?: return null
        return numMatch.groupValues[1].toFloatOrNull()
    }
}
