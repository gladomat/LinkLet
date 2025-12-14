package com.gladomat.linklet.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * A simple, regex-based syntax highlighter for code blocks.
 * Produces an [AnnotatedString] with highlighting applied.
 */
object SyntaxHighlighter {

    data class Theme(
        val keyword: Color,
        val string: Color,
        val comment: Color,
        val number: Color,
        val function: Color,
        val type: Color,
        val defaultText: Color,
    )

    fun highlight(code: String, language: String?, theme: Theme): AnnotatedString {
        val lang = language?.lowercase()
        return when (lang) {
            "kotlin", "kt" -> highlightKotlin(code, theme)
            "java" -> highlightJava(code, theme)
            "python", "py" -> highlightPython(code, theme)
            "shell", "sh", "bash", "zsh" -> highlightShell(code, theme)
            "elisp", "emacs-lisp" -> highlightLisp(code, theme)
            else -> buildAnnotatedString { append(code) } // No highlighting
        }
    }

    // ==========================================================================
    // Kotlin Highlighting
    // ==========================================================================
    private val kotlinKeywords = setOf(
        "fun", "val", "var", "if", "else", "when", "for", "while", "do", "return",
        "class", "interface", "object", "data", "sealed", "open", "abstract", "override",
        "private", "public", "internal", "protected", "import", "package", "true", "false",
        "null", "is", "in", "as", "try", "catch", "finally", "throw", "suspend", "inline",
        "reified", "companion", "init", "constructor", "enum", "typealias", "annotation",
    )
    private val kotlinKeywordRegex = Regex("\\b(${kotlinKeywords.joinToString("|")})\\b")
    private val kotlinStringRegex = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'")
    private val kotlinCommentRegex = Regex("//.*|/\\*[\\s\\S]*?\\*/")
    private val kotlinNumberRegex = Regex("\\b\\d+\\.?\\d*[fFLl]?\\b")
    private val kotlinFunctionRegex = Regex("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")

    private fun highlightKotlin(code: String, theme: Theme): AnnotatedString {
        return highlightGeneric(
            code,
            theme,
            keywordRegex = kotlinKeywordRegex,
            stringRegex = kotlinStringRegex,
            commentRegex = kotlinCommentRegex,
            numberRegex = kotlinNumberRegex,
            functionRegex = kotlinFunctionRegex,
        )
    }

    // ==========================================================================
    // Java Highlighting
    // ==========================================================================
    private val javaKeywords = setOf(
        "public", "private", "protected", "class", "interface", "extends", "implements",
        "static", "final", "void", "int", "float", "double", "boolean", "char", "long",
        "short", "byte", "new", "return", "if", "else", "for", "while", "do", "switch",
        "case", "break", "continue", "default", "try", "catch", "finally", "throw",
        "throws", "import", "package", "true", "false", "null", "this", "super",
        "abstract", "synchronized", "volatile", "transient", "native", "instanceof",
    )
    private val javaKeywordRegex = Regex("\\b(${javaKeywords.joinToString("|")})\\b")

    private fun highlightJava(code: String, theme: Theme): AnnotatedString {
        return highlightGeneric(
            code,
            theme,
            keywordRegex = javaKeywordRegex,
            stringRegex = kotlinStringRegex, // Same as Kotlin
            commentRegex = kotlinCommentRegex, // Same as Kotlin
            numberRegex = kotlinNumberRegex, // Same as Kotlin
            functionRegex = kotlinFunctionRegex, // Same as Kotlin
        )
    }

    // ==========================================================================
    // Python Highlighting
    // ==========================================================================
    private val pythonKeywords = setOf(
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
        "del", "elif", "else", "except", "finally", "for", "from", "global", "if",
        "import", "in", "is", "lambda", "not", "or", "pass", "raise", "return", "try",
        "while", "with", "yield", "True", "False", "None",
    )
    private val pythonKeywordRegex = Regex("\\b(${pythonKeywords.joinToString("|")})\\b")
    private val pythonStringRegex = Regex("'''[\\s\\S]*?'''|\"\"\"[\\s\\S]*?\"\"\"|'(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\"")
    private val pythonCommentRegex = Regex("#.*")

    private fun highlightPython(code: String, theme: Theme): AnnotatedString {
        return highlightGeneric(
            code,
            theme,
            keywordRegex = pythonKeywordRegex,
            stringRegex = pythonStringRegex,
            commentRegex = pythonCommentRegex,
            numberRegex = kotlinNumberRegex,
            functionRegex = kotlinFunctionRegex,
        )
    }

    // ==========================================================================
    // Shell Highlighting
    // ==========================================================================
    private val shellKeywords = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
        "in", "function", "return", "exit", "break", "continue", "export", "local",
        "readonly", "source", "alias", "unalias", "set", "unset", "echo", "cd", "pwd",
    )
    private val shellKeywordRegex = Regex("\\b(${shellKeywords.joinToString("|")})\\b")
    private val shellCommentRegex = Regex("#.*")
    private val shellStringRegex = Regex("'[^']*'|\"(?:[^\"\\\\$`]|\\\\.)*\"")

    private fun highlightShell(code: String, theme: Theme): AnnotatedString {
        return highlightGeneric(
            code,
            theme,
            keywordRegex = shellKeywordRegex,
            stringRegex = shellStringRegex,
            commentRegex = shellCommentRegex,
            numberRegex = kotlinNumberRegex,
            functionRegex = null,
        )
    }

    // ==========================================================================
    // Lisp / Emacs Lisp Highlighting
    // ==========================================================================
    private val lispKeywords = setOf(
        "defun", "defvar", "defconst", "defmacro", "let", "let*", "if", "cond", "when",
        "unless", "lambda", "progn", "setq", "quote", "require", "provide", "nil", "t",
        "car", "cdr", "cons", "list", "append", "mapcar", "funcall", "apply",
    )
    private val lispKeywordRegex = Regex("\\b(${lispKeywords.joinToString("|")})\\b")
    private val lispStringRegex = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")
    private val lispCommentRegex = Regex(";.*")

    private fun highlightLisp(code: String, theme: Theme): AnnotatedString {
        return highlightGeneric(
            code,
            theme,
            keywordRegex = lispKeywordRegex,
            stringRegex = lispStringRegex,
            commentRegex = lispCommentRegex,
            numberRegex = kotlinNumberRegex,
            functionRegex = null,
        )
    }

    // ==========================================================================
    // Generic Highlighter
    // ==========================================================================
    private fun highlightGeneric(
        code: String,
        theme: Theme,
        keywordRegex: Regex?,
        stringRegex: Regex?,
        commentRegex: Regex?,
        numberRegex: Regex?,
        functionRegex: Regex?,
    ): AnnotatedString {
        val covered = BooleanArray(code.length)

        return buildAnnotatedString {
            append(code)
            val priorityOrder = listOf(
                commentRegex to SpanStyle(color = theme.comment, fontStyle = FontStyle.Italic),
                stringRegex to SpanStyle(color = theme.string),
            )
            val lowerPriority = listOf(
                keywordRegex to SpanStyle(color = theme.keyword, fontWeight = FontWeight.Bold),
                numberRegex to SpanStyle(color = theme.number),
            )

            fun applyNonOverlapping(regex: Regex?, style: SpanStyle) {
                regex?.findAll(code)?.forEach { match ->
                    val start = match.range.first
                    val end = match.range.last + 1
                    if ((start until end).none { covered[it] }) {
                        addStyle(style, start, end)
                        for (i in start until end) covered[i] = true
                    }
                }
            }

            priorityOrder.forEach { (r, s) -> applyNonOverlapping(r, s) }
            lowerPriority.forEach { (r, s) -> applyNonOverlapping(r, s) }

            // Functions (don't override covered text)
            functionRegex?.findAll(code)?.forEach { match ->
                val funcName = match.groups[1]
                if (funcName != null) {
                    val start = funcName.range.first
                    val end = funcName.range.last + 1
                    if ((start until end).none { covered[it] }) {
                        addStyle(SpanStyle(color = theme.function), start, end)
                        for (i in start until end) covered[i] = true
                    }
                }
            }
        }
    }
}
