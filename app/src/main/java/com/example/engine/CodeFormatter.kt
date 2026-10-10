package com.example.engine

import com.example.data.CodeTab
import kotlin.math.max

object CodeFormatter {

    private val voidHtmlTags = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    fun format(code: String, tab: CodeTab): String {
        if (code.isBlank()) return code
        return when (tab) {
            CodeTab.INDEX_HTML -> formatHtml(code)
            CodeTab.STYLE_CSS -> formatBraceLanguage(code, isCss = true)
            CodeTab.SCRIPT_JS -> formatBraceLanguage(code, isCss = false)
        }
    }

    private fun formatHtml(raw: String): String {
        val rawLines = raw
            .replace(Regex(">\\s+<"), ">\n<")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var indent = 0
        val out = StringBuilder()

        for (line in rawLines) {
            val isClosingTag = line.startsWith("</")
            val isCommentOrDecl = line.startsWith("<!--") || line.startsWith("<!") || line.startsWith("<?")
            if (isClosingTag) {
                indent = max(0, indent - 1)
            }

            out.append("  ".repeat(indent)).append(line).append("\n")

            if (!isClosingTag && !isCommentOrDecl && line.startsWith("<") && !line.endsWith("/>")) {
                val tagName = line.removePrefix("<")
                    .takeWhile { it.isLetterOrDigit() || it == '-' }
                    .lowercase()
                val closesOnSameLine = line.contains("</$tagName>", ignoreCase = true)
                if (tagName.isNotEmpty() && tagName !in voidHtmlTags && !closesOnSameLine) {
                    indent += 1
                }
            }
        }
        return out.toString().trimEnd()
    }

    private fun formatBraceLanguage(raw: String, isCss: Boolean): String {
        val normalized = if (isCss) {
            raw.replace(Regex("\\*/\\s*"), "*/\n")
                .replace(Regex("\\s*\\{\\s*"), " {\n")
                .replace(Regex(";\\s*"), ";\n")
                .replace(Regex("\\s*\\}\\s*"), "\n}\n\n")
        } else {
            raw
        }

        val lines = normalized.lines()
        var indent = 0
        val out = StringBuilder()
        var previousBlank = false

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                if (!previousBlank && out.isNotEmpty()) {
                    out.append("\n")
                    previousBlank = true
                }
                continue
            }
            previousBlank = false

            val leadingClosers = line.takeWhile { it == '}' || it == ']' || it == ')' }.count { it == '}' || it == ']' }
            val lineIndent = max(0, indent - leadingClosers)

            out.append("  ".repeat(lineIndent)).append(line).append("\n")

            val openBraces = line.count { it == '{' || it == '[' }
            val closeBraces = line.count { it == '}' || it == ']' }
            indent = max(0, indent + (openBraces - closeBraces))
        }
        return out.toString().trimEnd()
    }
}
