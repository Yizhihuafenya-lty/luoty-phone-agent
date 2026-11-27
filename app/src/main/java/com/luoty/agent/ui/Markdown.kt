package com.luoty.agent.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luoty.agent.ui.theme.AppColors
import com.luoty.agent.ui.theme.Mono

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullets(val items: List<String>, val ordered: Boolean) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String, val code: String, val complete: Boolean) : MdBlock
    data class Table(val text: String) : MdBlock
    data object Rule : MdBlock
}

private fun parseMarkdown(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    var i = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.joinToString("\n").trimEnd())
            paragraph.clear()
        }
    }

    fun isBullet(s: String): Boolean =
        (s.startsWith("- ") || s.startsWith("* ") || s.startsWith("+ "))

    fun isOrdered(s: String): Boolean = Regex("^\\d{1,3}[.)]\\s+").containsMatchIn(s)

    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trimStart()
        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val lang = trimmed.removePrefix("```").trim().substringBefore(' ')
                i++
                val code = StringBuilder()
                var closed = false
                while (i < lines.size) {
                    if (lines[i].trimStart().startsWith("```")) {
                        closed = true
                        i++
                        break
                    }
                    code.append(lines[i]).append('\n')
                    i++
                }
                blocks += MdBlock.Code(lang, code.toString().trimEnd('\n'), closed)
            }

            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length
                if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
                    flushParagraph()
                    blocks += MdBlock.Heading(level, trimmed.substring(level + 1).trim())
                    i++
                } else {
                    paragraph += raw
                    i++
                }
            }

            trimmed.startsWith(">") -> {
                flushParagraph()
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quote += lines[i].trimStart().removePrefix(">").trimStart()
                    i++
                }
                blocks += MdBlock.Quote(quote.joinToString("\n"))
            }

            trimmed.matches(Regex("^([-*_])\\1{2,}\\s*$")) -> {
                flushParagraph()
                blocks += MdBlock.Rule
                i++
            }

            isBullet(trimmed) -> {
                flushParagraph()
                val items = mutableListOf<String>()
                while (i < lines.size && isBullet(lines[i].trimStart())) {
                    items += lines[i].trimStart().removePrefix("- ").removePrefix("* ").removePrefix("+ ")
                    i++
                    while (i < lines.size && lines[i].startsWith("  ") && lines[i].isNotBlank() &&
                        !isBullet(lines[i].trimStart()) && !isOrdered(lines[i].trimStart())
                    ) {
                        items[items.lastIndex] = items.last() + "\n" + lines[i].trim()
                        i++
                    }
                }
                blocks += MdBlock.Bullets(items, ordered = false)
            }

            isOrdered(trimmed) -> {
                flushParagraph()
                val items = mutableListOf<String>()
                val prefix = Regex("^\\d{1,3}[.)]\\s+")
                while (i < lines.size && isOrdered(lines[i].trimStart())) {
                    items += lines[i].trimStart().replace(prefix, "")
                    i++
                }
                blocks += MdBlock.Bullets(items, ordered = true)
            }

            trimmed.startsWith("|") -> {
                flushParagraph()
                val rows = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    rows += lines[i].trimEnd()
                    i++
                }
                blocks += MdBlock.Table(rows.joinToString("\n"))
            }

            trimmed.isBlank() -> {
                flushParagraph()
                i++
            }

            else -> {
                paragraph += raw
                i++
            }
        }
    }
    flushParagraph()
    return blocks
}

@Composable
fun MarkdownView(markdown: String, modifier: Modifier = Modifier, streaming: Boolean = false) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> Text(
                    text = inline(block.text),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = when (block.level) {
                            1 -> 21.sp
                            2 -> 19.sp
                            3 -> 17.sp
                            else -> 15.5.sp
                        },
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = if (block.level <= 2) 6.dp else 2.dp),
                )

                is MdBlock.Paragraph -> Text(
                    text = inline(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                text = if (block.ordered) "${index + 1}." else "•",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(22.dp),
                            )
                            Text(
                                text = inline(item),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Quote -> Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = inline(block.text),
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is MdBlock.Code -> CodeBlock(block.language, block.code, block.complete)

                is MdBlock.Table -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.CodeBg)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                ) {
                    Text(
                        text = block.text,
                        fontFamily = Mono,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                MdBlock.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun CodeBlock(language: String, code: String, complete: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shape = RoundedCornerShape(11.dp)
    val highlighted = remember(code, language) { CodeHighlighter.highlight(code, language) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.CodeBg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(AppColors.CodeHeader)
                .padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language.ifBlank { "code" }.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = Mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (!complete) {
                Text("生成中…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(code))
                Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "复制代码",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(16.dp).height(16.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = highlighted,
                fontFamily = Mono,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                softWrap = false,
                color = Color(0xFFD7DEEB),
            )
        }
    }
}

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end < 0) {
                    append(c)
                    i++
                } else {
                    withStyle(
                        SpanStyle(
                            fontFamily = Mono,
                            color = AppColors.Code,
                            background = Color(0xFF1A2130),
                        ),
                    ) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
            }

            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end < 0) {
                    append("**")
                    i += 2
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
            }

            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end < 0) {
                    append("~~")
                    i += 2
                } else {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                }
            }

            c == '[' -> {
                val close = text.indexOf("](", i)
                val end = if (close > 0) text.indexOf(")", close + 2) else -1
                if (close > 0 && end > close) {
                    val label = text.substring(i + 1, close)
                    val url = text.substring(close + 2, end)
                    withLink(
                        LinkAnnotation.Url(
                            url,
                            TextLinkStyles(
                                SpanStyle(color = AppColors.Link, textDecoration = TextDecoration.Underline),
                            ),
                        ),
                    ) { append(label) }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }

            (c == '*' || c == '_') -> {
                val end = text.indexOf(c, i + 1)
                if (end > i + 1 && end - i < 200) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }

            else -> {
                append(c)
                i++
            }
        }
    }
}
