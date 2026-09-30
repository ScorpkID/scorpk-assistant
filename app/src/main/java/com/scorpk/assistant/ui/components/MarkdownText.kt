package com.scorpk.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scorpk.assistant.ui.theme.AccentBlue
import com.scorpk.assistant.ui.theme.DividerColor
import com.scorpk.assistant.ui.theme.InputSurface
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.ui.theme.TextSecondary

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Code(val code: String) : MdBlock
}

/**
 * Renderizador Markdown ligero para las respuestas del asistente: encabezados, listas,
 * bloques de código y estilos en línea (**negrita**, *cursiva*, `código`, [enlaces](url)).
 */
@Composable
fun MarkdownText(text: String, color: Color = TextPrimary, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> Text(
                    inline(block.text),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                    color = color
                )
                is MdBlock.Heading -> Text(
                    inline(block.text),
                    style = if (block.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    modifier = Modifier.padding(top = 4.dp)
                )
                is MdBlock.Bullet -> Row(modifier = Modifier.padding(start = (block.indent * 16).dp)) {
                    Text(
                        block.marker,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        modifier = Modifier.width(22.dp)
                    )
                    Text(
                        inline(block.text),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                        color = color
                    )
                }
                is MdBlock.Code -> Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = InputSurface,
                    border = BorderStroke(1.dp, DividerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        block.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = TextPrimary,
                        softWrap = false,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(14.dp)
                    )
                }
            }
        }
    }
}

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^(\s*)([-*+•]|\d+[.)])\s+(.*)$""")

private fun parseBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }
    val lines = text.replace("\r\n", "\n").lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.appendLine(lines[i])
                    i++
                }
                blocks += MdBlock.Code(code.toString().trimEnd())
            }
            HEADING.matches(line.trim()) -> {
                flush()
                val m = HEADING.find(line.trim())!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
            }
            BULLET.matches(line) -> {
                flush()
                val m = BULLET.find(line)!!
                val raw = m.groupValues[2]
                val marker = if (raw.first().isDigit()) raw.trimEnd(')').trimEnd('.') + "." else "•"
                blocks += MdBlock.Bullet(marker, m.groupValues[3], m.groupValues[1].length / 2)
            }
            line.isBlank() -> flush()
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line.trim())
            }
        }
        i++
    }
    flush()
    return blocks
}

private val INLINE = Regex("""(\*\*|__)(.+?)\1|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?!\w)|`([^`]+)`|\[([^\]]+)]\(([^)]+)\)""")

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    INLINE.findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        val g = m.groupValues
        when {
            g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(g[2]) }
            g[3].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[3]) }
            g[4].isNotEmpty() -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = InputSurfaceStatic, fontSize = 14.sp)
            ) { append(" ${g[4]} ") }
            g[5].isNotEmpty() -> withLink(
                LinkAnnotation.Url(
                    g[6],
                    TextLinkStyles(SpanStyle(color = AccentBlue, textDecoration = TextDecoration.Underline))
                )
            ) { append(g[5]) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

/** Fondo del código en línea (no depende del tema porque AnnotatedString se crea fuera de composición). */
private val InputSurfaceStatic = Color(0xFF1E1E26)
