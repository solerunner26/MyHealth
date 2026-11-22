package app.myhealth.domain.parse

/** Shared text clean-up for both PDF text layers and OCR output. */
object TextNormalizer {
    private val dashes = Regex("[‐-―−]")
    private val spaces = Regex("[\\t    ]+")

    /** Canonicalise characters that vary between PDF generators and OCR engines. */
    fun cleanLine(raw: String): String = raw
        .replace(dashes, "-")
        .replace('≤', '≤').replace('≥', '≥')
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace(spaces, " ")
        .trim()

    /** Lower-case name text for alias matching: brackets/colons become spaces, sentence dots removed, numbers untouched. */
    fun forMatching(line: String): String = line.lowercase()
        .replace(Regex("[()\\[\\]{}:;|_\"'=]"), " ")
        .replace(Regex("(?<!\\d),|,(?!\\d)"), " ")
        .replace(Regex("(?<!\\d)\\.|\\.(?!\\d)"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun lines(pages: List<String>): List<String> = pages.flatMap { it.split('\n', '\r') }.map(::cleanLine).filter { it.isNotEmpty() }
}

/** Number parsing tolerant of Indian digit grouping ("1,50,000") and OCR noise ("O" in digits). */
object Numbers {
    val token = Regex("""(?<![\w.])(\d{1,3}(?:,\d{2,3})+(?:\.\d+)?|\d+(?:\.\d+)?|\.\d+)(?![\w.])""")

    fun parse(text: String): Double? {
        val t = text.trim().replace(" ", "")
        if (t.isEmpty()) return null
        val cleaned = if (Regex("""^\d{1,3}(,\d{2,3})+(\.\d+)?$""").matches(t)) t.replace(",", "")
        else if (Regex("""^\d+,\d{1,2}$""").matches(t)) t.replace(',', '.') // decimal comma
        else t
        return cleaned.toDoubleOrNull()
    }
}
