package com.encryptedjournal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp

class MarkdownOffsetMapping(val originalText: String, val transformedText: AnnotatedString) : OffsetMapping {
    private val origLen = originalText.length
    private val transLen = transformedText.length

    private val origToTrans = IntArray(origLen + 1)
    private val transToOrig = IntArray(transLen + 1)

    init {
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        val italicRegex = Regex("_(.*?)_")
        val sizeRegex = Regex("<s:(\\d+)>(.*?)</s>")
        val colorRegex = Regex("<c:([0-9a-fA-F]+)>(.*?)</c>")

        val isTagChar = BooleanArray(origLen)
        val matches = (boldRegex.findAll(originalText) + 
                       italicRegex.findAll(originalText) + 
                       sizeRegex.findAll(originalText) + 
                       colorRegex.findAll(originalText))
            .sortedBy { it.range.first }

        for (match in matches) {
            val v = match.value
            val start = match.range.first.coerceIn(0, origLen)
            val end = match.range.last.coerceIn(0, origLen - 1)
            
            if (v.startsWith("**")) {
                for (i in start until (start + 2).coerceAtMost(origLen)) isTagChar[i] = true
                for (i in (end - 1).coerceAtLeast(0)..end) isTagChar[i] = true
            } else if (v.startsWith("_")) {
                if (start < origLen) isTagChar[start] = true
                if (end < origLen) isTagChar[end] = true
            } else if (v.startsWith("<s:")) {
                val startTagLen = "<s:${match.groupValues[1]}>".length
                val endTagLen = "</s>".length
                for (i in start until (start + startTagLen).coerceAtMost(origLen)) isTagChar[i] = true
                for (i in (end - endTagLen + 1).coerceAtLeast(0)..end) isTagChar[i] = true
            } else if (v.startsWith("<c:")) {
                val startTagLen = "<c:${match.groupValues[1]}>".length
                val endTagLen = "</c>".length
                for (i in start until (start + startTagLen).coerceAtMost(origLen)) isTagChar[i] = true
                for (i in (end - endTagLen + 1).coerceAtLeast(0)..end) isTagChar[i] = true
            }
        }

        var tIdx = 0
        for (oIdx in 0 until origLen) {
            origToTrans[oIdx] = tIdx.coerceIn(0, transLen)
            if (!isTagChar[oIdx]) {
                if (tIdx < transLen) {
                    transToOrig[tIdx] = oIdx.coerceIn(0, origLen)
                    tIdx++
                }
            }
        }
        origToTrans[origLen] = transLen
        transToOrig[transLen] = origLen
    }

    override fun originalToTransformed(offset: Int): Int {
        if (origLen == 0 || transLen == 0) return 0
        val clamped = offset.coerceIn(0, origLen)
        return origToTrans[clamped].coerceIn(0, transLen)
    }

    override fun transformedToOriginal(offset: Int): Int {
        if (origLen == 0 || transLen == 0) return 0
        val clamped = offset.coerceIn(0, transLen)
        return transToOrig[clamped].coerceIn(0, origLen)
    }
}

object MarkdownUtils {
    fun parseMarkdown(text: String, hideMarkers: Boolean = true): AnnotatedString {
        return buildAnnotatedString {
            parseRecursive(text, hideMarkers)
        }
    }

    private fun AnnotatedString.Builder.parseRecursive(text: String, hideMarkers: Boolean) {
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        val italicRegex = Regex("_(.*?)_")
        val sizeRegex = Regex("<s:(\\d+)>(.*?)</s>")
        val colorRegex = Regex("<c:([0-9a-fA-F]+)>(.*?)</c>")
        
        var lastIndex = 0
        val allMatches = (boldRegex.findAll(text) + 
                         italicRegex.findAll(text) + 
                         sizeRegex.findAll(text) + 
                         colorRegex.findAll(text))
            .sortedBy { it.range.first }
        
        for (match in allMatches) {
            if (match.range.first < lastIndex) continue
            
            append(text.substring(lastIndex, match.range.first))
            
            val value = match.value
            if (value.startsWith("**")) {
                val content = match.groupValues[1]
                if (!hideMarkers) append("**")
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                parseRecursive(content, hideMarkers)
                pop()
                if (!hideMarkers) append("**")
            } else if (value.startsWith("_")) {
                val content = match.groupValues[1]
                if (!hideMarkers) append("_")
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                parseRecursive(content, hideMarkers)
                pop()
                if (!hideMarkers) append("_")
            } else if (value.startsWith("<s:")) {
                val size = match.groupValues[1].toIntOrNull() ?: 16
                val content = match.groupValues[2]
                val startTag = "<s:${match.groupValues[1]}>"
                val endTag = "</s>"
                
                if (!hideMarkers) append(startTag)
                pushStyle(SpanStyle(fontSize = size.sp))
                parseRecursive(content, hideMarkers)
                pop()
                if (!hideMarkers) append(endTag)
            } else if (value.startsWith("<c:")) {
                val colorHex = match.groupValues[1]
                val content = match.groupValues[2]
                val startTag = "<c:$colorHex>"
                val endTag = "</c>"
                
                val color = try {
                    Color(android.graphics.Color.parseColor("#$colorHex"))
                } catch (_: Exception) {
                    Color.Unspecified
                }
                
                if (!hideMarkers) append(startTag)
                pushStyle(SpanStyle(color = color))
                parseRecursive(content, hideMarkers)
                pop()
                if (!hideMarkers) append(endTag)
            }
            
            lastIndex = match.range.last + 1
        }
        
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }

    val markdownTransformation = VisualTransformation { text ->
        val raw = text.text
        if (raw.contains("**") || raw.contains("_") || raw.contains("<s:") || raw.contains("<c:")) {
            val transformed = parseMarkdown(raw, hideMarkers = true)
            TransformedText(transformed, MarkdownOffsetMapping(raw, transformed))
        } else {
            TransformedText(parseMarkdown(raw, hideMarkers = false), OffsetMapping.Identity)
        }
    }

    fun parseToHtml(text: String): String {
        // Escape basic HTML entities first
        var html = text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

        // Restore our custom tags
        html = html.replace(Regex("&lt;s:(\\d+)&gt;(.*?)&lt;/s&gt;"), "<span style='font-size: $1px'>$2</span>")
        html = html.replace(Regex("&lt;c:([0-9a-fA-F]+)&gt;(.*?)&lt;/c&gt;"), "<span style='color: #$1'>$2</span>")
        
        // Bold
        html = html.replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
        // Italic
        html = html.replace(Regex("_(.*?)_"), "<i>$1</i>")
        
        return html.replace("\n", "<br/>")
    }
}
