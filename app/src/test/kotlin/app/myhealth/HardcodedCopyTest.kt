package app.myhealth

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lint's HardcodedText check covers XML only. This test covers Compose: user-visible copy must come from
 * string resources, so we reject string literals containing letters passed to Text/label/title parameters.
 */
class HardcodedCopyTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    private val patterns = listOf(
        Regex("""\bText\(\s*"[^"]*\p{L}[^"]*""""),
        Regex("""\b(text|title|label|message|subtitle|actionLabel|contentDescription|placeholder|caption|confirmLabel|dismissLabel)\s*=\s*"[^"]*\p{L}[^"]*""""),
    )

    @Test fun composeSourcesHaveNoHardcodedCopy() {
        val offenders = mutableListOf<String>()
        listOf("app/src/main/kotlin", "core/designsystem/src/main/kotlin").map { File(root, it) }.forEach { dir ->
            dir.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
                f.readLines().forEachIndexed { i, line ->
                    if (line.trimStart().startsWith("//")) return@forEachIndexed
                    if (patterns.any { it.containsMatchIn(line) }) offenders += "${f.relativeTo(root)}:${i + 1}: ${line.trim()}"
                }
            }
        }
        assertTrue("Hardcoded UI copy found:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
