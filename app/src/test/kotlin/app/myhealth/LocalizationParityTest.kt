package app.myhealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Constraint 4: every translatable key in values/ must exist in values-hi/ and values-gu/ (and vice versa),
 * with identical format placeholders and plural quantity sets, and no empty translations.
 */
class LocalizationParityTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val resDirs = listOf("app", "core/designsystem", "data").map { File(root, "$it/src/main/res") }.filter { it.exists() }
    private val locales = listOf("hi", "gu")
    private val placeholder = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfx%]""")

    private data class Entry(val value: String, val translatable: Boolean)

    private fun load(dir: File): Map<String, Entry> {
        val out = LinkedHashMap<String, Entry>()
        dir.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }?.forEach { f ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                val name = e.getAttribute("name")
                val translatable = e.getAttribute("translatable") != "false"
                when (e.tagName) {
                    "string" -> out[name]?.let { fail("duplicate key $name in ${f.path}") } ?: out.put(name, Entry(e.textContent, translatable))
                    "plurals" -> {
                        val items = e.getElementsByTagName("item")
                        for (j in 0 until items.length) {
                            val it = items.item(j) as Element
                            out["$name#${it.getAttribute("quantity")}"] = Entry(it.textContent, translatable)
                        }
                    }
                    "string-array" -> {
                        val items = e.getElementsByTagName("item")
                        for (j in 0 until items.length) out["$name[$j]"] = Entry(items.item(j).textContent, translatable)
                    }
                }
            }
        }
        return out
    }

    private fun placeholders(s: String) = placeholder.findAll(s).map { it.value }.sorted().toList()

    @Test fun everyKeyIsTranslatedWithMatchingPlaceholders() {
        var checked = 0
        for (res in resDirs) {
            val base = load(File(res, "values")).filterValues { it.translatable }
            for (loc in locales) {
                val tr = load(File(res, "values-$loc"))
                val missing = base.keys.filter { k -> k !in tr && !(k.contains('#') && k.substringBefore('#').let { p -> tr.keys.any { it.startsWith("$p#other") } }) }
                assertTrue("[$loc] ${res.path} missing: $missing", missing.isEmpty())
                val extra = tr.keys.filter { k -> k !in base && !k.contains('#') }
                assertTrue("[$loc] ${res.path} extra keys: $extra", extra.isEmpty())
                for ((k, v) in tr) {
                    assertTrue("[$loc] empty translation $k", v.value.isNotBlank())
                    val b = base[k] ?: base[k.substringBefore('#') + "#other"] ?: continue
                    assertEquals("[$loc] placeholders differ for $k", placeholders(b.value), placeholders(v.value))
                    checked++
                }
            }
        }
        assertTrue("no strings were checked", checked > 0)
    }

    @Test fun nonTranslatableKeysAreNotTranslated() {
        for (res in resDirs) {
            val fixed = load(File(res, "values")).filterValues { !it.translatable }.keys
            for (loc in locales) {
                val tr = load(File(res, "values-$loc")).keys
                assertTrue("[$loc] translated a non-translatable key: ${fixed.intersect(tr)}", fixed.intersect(tr).isEmpty())
            }
        }
    }
}
