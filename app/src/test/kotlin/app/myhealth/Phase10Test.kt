package app.myhealth

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.core.designsystem.theme.ThemeMode
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.Report
import app.myhealth.domain.model.ReportType
import app.myhealth.domain.model.Vital
import app.myhealth.domain.model.VitalType
import app.myhealth.domain.repository.ImportBundle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import java.time.Instant
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

/** Hindi and Gujarati must actually be translated: same-as-English only for technical terms, and in the right script. */
class TranslationCompletenessTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val res = File(root, "app/src/main/res")
    // Allowed to be identical to English: placeholders-only strings, brand names, language names and lab abbreviations.
    private val literal = setOf("BMI %1\$s", "%1\$s: %2\$s, %3\$s, %4\$s.", "%1\$s: %2\$s", "%1\$s · %2\$s", "%1\$d / 100", "%1\$s: %2\$s → %3\$d", "Health Connect", "MyHealth", "English", "हिन्दी", "ગુજરાતી", "—")
    private val technical = Regex("^[A-Z][A-Za-z0-9 ()/.,%₂²+-]*$")
    private fun allowed(v: String) = v in literal || technical.matches(v)

    private fun load(dir: String): Map<String, String> = res.resolve(dir).listFiles { f -> f.name.startsWith("strings") }!!.flatMap { f ->
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val n = doc.documentElement.childNodes
        (0 until n.length).mapNotNull { n.item(it) as? Element }.filter { it.tagName == "string" && it.getAttribute("translatable") != "false" }.map { it.getAttribute("name") to it.textContent }
    }.toMap()

    @Test fun hindiAndGujaratiAreTranslatedInTheirScripts() {
        val en = load("values")
        val scripts = mapOf("values-hi" to Regex("[\\u0900-\\u097F]"), "values-gu" to Regex("[\\u0A80-\\u0AFF]"))
        val problems = mutableListOf<String>()
        for ((dir, script) in scripts) {
            load(dir).forEach { (k, v) ->
                val same = v == en[k]
                if (same && !allowed(v)) problems += "$dir/$k still English: $v"
                if (!same && !script.containsMatchIn(v) && !allowed(v)) problems += "$dir/$k not in expected script: $v"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class AccessibilityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()
    private fun waitFor(c: () -> Boolean) { val end = System.currentTimeMillis() + 10_000; while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.waitForIdle(); if (c()) return; Thread.sleep(20) }; error("timeout") }
    private fun waitTag(t: String) = waitFor { rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty() }

    private fun seed() = runBlocking {
        val s = ctx.container.openNow()!!
        s.members.upsert(FamilyMember("m1", "Ravi", Relationship.Self, heightCm = 172.0))
        val d = LocalDate.now().minusDays(5)
        s.records.saveImport(ImportBundle(Report("r1", "m1", ReportType.LabReport, "Lipids", d, createdAt = Instant.EPOCH),
            listOf(LabResult("x", "m1", "r1", "ldl", d, 140.0, "mg/dL", refHigh = 100.0), LabResult("y", "m1", "r1", "hba1c", d, 5.4, "%", refLow = 4.0, refHigh = 5.6)),
            listOf(Vital("v", "m1", VitalType.BloodPressure, 128.0, 84.0, measuredAt = Instant.now())), listOf(Medicine("md", "m1", "Rosuvastatin", startDate = d))), emptyList())
    }

    /** Every clickable element must expose a spoken label (its own text or a content description). */
    private fun assertClickablesLabelled(screen: String) {
        val nodes: SemanticsNodeInteractionCollection = rule.onAllNodes(hasClickAction())
        val unlabelled = nodes.fetchSemanticsNodes().filter { n ->
            val c = n.config
            c.getOrNull(SemanticsProperties.Text).isNullOrEmpty() && c.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                c.getOrNull(SemanticsProperties.EditableText) == null
        }
        assertTrue("$screen: ${unlabelled.size} clickable node(s) without a label: ${unlabelled.map { it.config }}", unlabelled.isEmpty())
    }

    @Test fun mainScreensHaveLabelledControls() {
        seed(); waitTag("scoreCard")
        assertClickablesLabelled("Home")
        for ((tab, tag) in listOf(R.string.nav_records to "recordsList", R.string.nav_trends to "trendsList", R.string.nav_care to "care", R.string.nav_more to "more")) {
            rule.onNodeWithText(ctx.getString(tab)).performClick(); waitTag(tag); assertClickablesLabelled(ctx.getString(tab))
        }
        rule.onNodeWithTag("more").performScrollToNode(hasTestTag("openSettings")); rule.onNodeWithTag("openSettings").performClick(); waitTag("settings")
        assertClickablesLabelled("Settings")
    }

    @Test fun largeFontAndDarkThemeRender() {
        RuntimeEnvironment.setFontScale(2.0f)
        ctx.container.preferences.setTheme(ThemeMode.Dark)
        seed(); waitTag("scoreCard")
        rule.onNodeWithText(ctx.getString(R.string.nav_trends)).performClick(); waitTag("trendsList")
        rule.onNodeWithText(ctx.getString(R.string.nav_care)).performClick(); waitTag("care")
    }
}
