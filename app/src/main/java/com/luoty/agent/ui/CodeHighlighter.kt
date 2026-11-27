package com.luoty.agent.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * 轻量语法高亮：够用、不引入额外依赖。
 * 规则顺序 = 注释 → 字符串 → 注解 → 数字 → 关键字 → 类型 → 函数调用，
 * 因为合并成一个正则按位置扫描，字符串/注释会先被整体吃掉。
 */
object CodeHighlighter {

    private val BASE = Color(0xFFD7DEEB)
    private val COMMENT = Color(0xFF66748C)
    private val STRING = Color(0xFFCE9178)
    private val NUMBER = Color(0xFFB5CEA8)
    private val KEYWORD = Color(0xFF82AAFF)
    private val TYPE = Color(0xFF4EC9B0)
    private val FUNCTION = Color(0xFFDCDCAA)
    private val ANNOTATION = Color(0xFFD7BA7D)
    private val JSON_KEY = Color(0xFF9CDCFE)
    private val TAG = Color(0xFF7FDBFF)
    private val SELECTOR = Color(0xFFD7BA7D)

    private val KOTLIN = """
        fun val var class object interface data sealed enum when if else for while do return break continue
        import package private public protected internal override open abstract final static companion this
        super null true false is in as by try catch finally throw throwable typealias const lateinit suspend
        inline noinline crossinline reified where out in get set init constructor annotation operator infix
        tailrec external actual expect vararg dynamic
        new void int long double float boolean byte short char String
    """.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

    private val PYTHON = """
        def class import from if elif else for while return break continue pass try except finally raise
        with as lambda global nonlocal yield assert del in is not and or None True False self async await
        print len range str int float list dict set tuple bool open
    """.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

    private val JS = """
        function const let var return if else for while do switch case break continue new class extends
        import export from default async await try catch finally throw typeof instanceof this super null
        undefined true false delete in of yield static get set
    """.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

    private val SHELL = """
        if then else fi for do done while case esac function export local echo cd ls cat grep sed awk rm cp
        mv mkdir touch curl wget sh bash sudo exit set unset source
    """.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

    private val C_LIKE = """
        int long short char float double void struct class union enum typedef static const unsigned signed
        if else for while do switch case break continue return goto sizeof import package public private
        protected final new delete try catch finally throw throws this super null true false namespace
        using template typename virtual override abstract interface implements extends fun val var when object
        String boolean byte
    """.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

    fun highlight(code: String, language: String): AnnotatedString {
        val lang = language.trim().lowercase()
        val family = familyOf(lang)
        val keywords = when (family) {
            "kotlin", "java", "c" -> KOTLIN + C_LIKE
            "python" -> PYTHON
            "js" -> JS
            "shell" -> SHELL
            else -> emptySet()
        }
        if (family == "text" || family == "markdown") {
            return AnnotatedString(code)
        }
        val pattern = buildPattern(family, keywords)
        return buildAnnotatedString {
            var last = 0
            for (match in pattern.findAll(code)) {
                if (match.range.first > last) {
                    withStyle(SpanStyle(color = BASE)) { append(code.substring(last, match.range.first)) }
                }
                val group = (1 until match.groupValues.size)
                    .firstOrNull { match.groups[it]?.value != null } ?: 0
                withStyle(SpanStyle(color = colorFor(group, family))) {
                    append(match.value)
                }
                last = match.range.last + 1
            }
            if (last < code.length) {
                withStyle(SpanStyle(color = BASE)) { append(code.substring(last)) }
            }
        }
    }

    private fun familyOf(lang: String): String = when (lang) {
        "kt", "kotlin", "kts" -> "kotlin"
        "java" -> "java"
        "c", "cpp", "cc", "h", "hpp", "cs", "swift", "dart", "go", "rust", "rs" -> "c"
        "py", "python" -> "python"
        "js", "javascript", "ts", "typescript", "jsx", "tsx", "vue", "json5" -> "js"
        "sh", "bash", "shell", "zsh", "console", "terminal" -> "shell"
        "json" -> "json"
        "xml", "html", "svg", "xhtml" -> "xml"
        "css", "scss" -> "css"
        "sql" -> "sql"
        "md", "markdown" -> "markdown"
        "yaml", "yml", "toml", "ini", "gradle", "properties", "diff", "patch", "gitignore" -> "text"
        "" -> "text"
        else -> "text"
    }

    private fun colorFor(group: Int, family: String): Color = when (group) {
        1 -> COMMENT
        2 -> STRING
        3 -> ANNOTATION
        4 -> NUMBER
        5 -> KEYWORD
        6 -> TYPE
        7 -> JSON_KEY
        8 -> FUNCTION
        9 -> TAG
        10 -> SELECTOR
        else -> BASE
    }

    private fun buildPattern(family: String, keywords: Set<String>): Regex {
        val comment = when (family) {
            "python", "shell" -> "#[^\\n]*"
            "xml" -> "<!--[\\s\\S]*?-->"
            "sql" -> "--[^\\n]*|/\\*[\\s\\S]*?\\*/"
            "css" -> "/\\*[\\s\\S]*?\\*/"
            else -> "//[^\\n]*|/\\*[\\s\\S]*?\\*/"
        }
        val string = when (family) {
            "python" -> "\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'"
            "xml" -> "\"[^\"]*\"|'[^']*'"
            "shell" -> "'[^']*'|\"(?:\\\\.|[^\"\\\\])*\""
            "json" -> "\"(?:\\\\.|[^\"\\\\])*\""
            else -> "\"\"\"[\\s\\S]*?\"\"\"|`(?:\\\\.|[^`\\\\])*`|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'"
        }
        val annotation = if (family in setOf("kotlin", "java", "python", "c")) "@[A-Za-z_][\\w.]*" else "(?!)"
        val number = "\\b(?:0[xX][0-9a-fA-F]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)[fFdDlLuU]?\\b"
        val keyword = if (keywords.isEmpty()) "(?!)" else "\\b(?:${keywords.joinToString("|") { Regex.escape(it) }})\\b"
        val typeName = "\\b[A-Z][A-Za-z0-9_]*\\b"
        val jsonKey = if (family == "json") "\"[^\"]*\"(?=\\s*:)" else "(?!)"
        val function = "\\b[A-Za-z_][\\w]*(?=\\s*\\()"
        val selector = if (family == "css") "[.#][A-Za-z_-][\\w-]*" else "(?!)"
        val tag = if (family == "xml") "</?[A-Za-z][\\w:-]*|/?>" else "(?!)"

        return Regex(
            "($comment)|($string)|($annotation)|($number)|($keyword)|($typeName)|($jsonKey)|($function)|($tag)|($selector)",
            setOf(RegexOption.MULTILINE),
        )
    }
}
