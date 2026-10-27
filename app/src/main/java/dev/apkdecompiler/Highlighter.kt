package dev.apkdecompiler

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/** Syntax highlight Java sederhana, per baris (dengan state block comment). */
object Highlighter {
    private val KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null"
    )

    private val TOKEN = Regex(
        """/\*.*?\*/|/\*.*|//.*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|@\w+|\b\d[\w.]*|\b[A-Za-z_]\w*"""
    )

    private val comment = SpanStyle(color = Color(0xFF808080))
    private val string = SpanStyle(color = Color(0xFF6A8759))
    private val number = SpanStyle(color = Color(0xFF6897BB))
    private val annotation = SpanStyle(color = Color(0xFFBBB529))
    private val keyword = SpanStyle(color = Color(0xFFCC7832))
    private val type = SpanStyle(color = Color(0xFFFFC66D))

    fun highlight(code: String, plain: Boolean): List<AnnotatedString> {
        val lines = code.split('\n')
        if (plain) return lines.map { AnnotatedString(it.trimEnd('\r').ifEmpty { " " }) }

        var inBlock = false
        return lines.map { raw ->
            val line = raw.trimEnd('\r').ifEmpty { " " }
            buildAnnotatedString {
                append(line)
                var pos = 0
                if (inBlock) {
                    val end = line.indexOf("*/")
                    if (end < 0) {
                        addStyle(comment, 0, line.length)
                        return@buildAnnotatedString
                    }
                    addStyle(comment, 0, end + 2)
                    inBlock = false
                    pos = end + 2
                }
                for (m in TOKEN.findAll(line, pos)) {
                    val t = m.value
                    val style: SpanStyle? = when {
                        t.startsWith("/*") -> {
                            if (!t.endsWith("*/") || t.length < 4) inBlock = true
                            comment
                        }
                        t.startsWith("//") -> comment
                        t[0] == '"' || t[0] == '\'' -> string
                        t[0] == '@' -> annotation
                        t[0].isDigit() -> number
                        t in KEYWORDS -> keyword
                        t[0].isUpperCase() -> type
                        else -> null
                    }
                    if (style != null) addStyle(style, m.range.first, m.range.last + 1)
                }
            }
        }
    }
}
