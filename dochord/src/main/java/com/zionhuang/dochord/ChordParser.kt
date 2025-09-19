package com.zionhuang.dochord

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal object ChordParser {
    fun parse(html: String): String {
        val document = Jsoup.parse(html)
        val container = document.selectFirst("div.single-entry-content-cord")
            ?: throw IllegalStateException("Chord content not found")

        val lines = mutableListOf<String>()
        container.childNodes().forEach { node ->
            collect(node, lines)
        }

        return lines
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString(separator = "\n")
    }

    private fun collect(node: Node, out: MutableList<String>) {
        when (node) {
            is Element -> when (node.tagName()) {
                "p" -> {
                    val paragraph = node.childNodes().joinToString(separator = "") { child ->
                        child.toChordText()
                    }.normalizeWhitespace()
                    paragraph.split('\n').forEach { rawLine ->
                        appendLine(out, rawLine)
                    }
                }
                "blockquote", "div", "section" -> node.childNodes().forEach { child ->
                    collect(child, out)
                }
                "hr" -> appendSeparator(out)
                else -> node.childNodes().forEach { child ->
                    collect(child, out)
                }
            }
        }
    }

    private fun appendLine(out: MutableList<String>, rawLine: String) {
        val normalized = rawLine.trim()
        if (normalized.isEmpty()) {
            appendSeparator(out)
        } else {
            out += normalized
        }
    }

    private fun appendSeparator(out: MutableList<String>) {
        if (out.lastOrNull()?.isNotEmpty() == true) {
            out += ""
        }
    }

    private fun Node.toChordText(): String = when (this) {
        is TextNode -> text().replace('\u00A0', ' ')
        is Element -> when {
            tagName() == "span" && id() == "c_chord" -> "[${text().trim()}]"
            tagName() == "br" -> "\n"
            else -> childNodes().joinToString(separator = "") { child ->
                child.toChordText()
            }
        }
        else -> ""
    }

    private fun String.normalizeWhitespace(): String =
        replace('\u00A0', ' ')
            .replace("\r", "")
            .replace("\t", " ")
}
