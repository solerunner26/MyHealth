package com.solerunner.myhealth.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the product rule that every screen is localized in English, Hindi and Gujarati.
 *
 * Two things are checked for each language:
 * 1. the key set is identical to the default `values/strings.xml` (no screen silently falls back);
 * 2. every `%1$s` / `%2$d` / `%%` placeholder survives translation, because a missing placeholder throws
 *    at format time and would crash the screen that uses it.
 */
class I18nParityTest {

    private val base = File("src/main/res")

    private fun read(folder: String): Map<String, String> {
        val file = File(base, "$folder/strings.xml")
        assertTrue("missing $file", file.exists())
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        val result = linkedMapOf<String, String>()
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            val name = node.attributes.getNamedItem("name").nodeValue
            val translatable = node.attributes.getNamedItem("translatable")?.nodeValue
            if (translatable == "false") continue
            result[name] = node.textContent
        }
        return result
    }

    private fun placeholders(text: String): List<String> {
        val matcher = Pattern.compile("%\\d+\\$[sd]|%%").matcher(text)
        val found = mutableListOf<String>()
        while (matcher.find()) found += matcher.group()
        return found.sorted()
    }

    @Test
    fun `hindi and gujarati cover exactly the same keys as english`() {
        val english = read("values")
        listOf("values-hi", "values-gu").forEach { folder ->
            val translated = read(folder)
            assertEquals("$folder key set differs", english.keys.sorted(), translated.keys.sorted())
        }
    }

    @Test
    fun `every translation keeps its format placeholders`() {
        val english = read("values")
        listOf("values-hi", "values-gu").forEach { folder ->
            val translated = read(folder)
            translated.forEach { (key, value) ->
                val expected = placeholders(english.getValue(key))
                assertEquals("$folder/$key placeholder mismatch", expected, placeholders(value))
            }
        }
    }

    @Test
    fun `no translation is left empty`() {
        val english = read("values")
        listOf("values-hi", "values-gu").forEach { folder ->
            read(folder).forEach { (key, value) ->
                assertTrue("$folder/$key is empty", value.isNotBlank())
                if (english.getValue(key).isNotBlank()) {
                    assertTrue("$folder/$key is unexpectedly short", value.trim().length >= 2)
                }
            }
        }
    }
}
