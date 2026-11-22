package app.myhealth

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.Report
import app.myhealth.domain.model.ReportType
import app.myhealth.domain.repository.ImportBundle
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()
    private fun str(id: Int, vararg a: Any) = ctx.getString(id, *a)

    private fun waitTag(tag: String, ms: Long = 10_000) = waitFor(ms) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(t: String, ms: Long = 10_000) = waitFor(ms) { rule.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    private fun waitFor(ms: Long, c: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.waitForIdle(); if (c()) return; Thread.sleep(20) }
        error("timeout")
    }

    private fun onboard(name: String) {
        waitTag("welcome")
        rule.onNodeWithTag("onbStart").performClick()
        waitTag("field_name")
        rule.onNodeWithTag("field_name").performTextInput(name)
        rule.onNodeWithTag("saveMember").performScrollTo().performClick()
        waitTag("shell")
    }

    private fun seed(memberId: String) = runBlocking {
        val stack = ctx.container.stackOrNull!!
        fun r(id: String, d: LocalDate, title: String) = Report(id, memberId, ReportType.LabReport, title, d, "Sunrise Lab", createdAt = Instant.EPOCH)
        fun v(id: String, rep: String, a: String, d: LocalDate, x: Double, lo: Double?, hi: Double?) = LabResult(id, memberId, rep, a, d, x, "", refLow = lo, refHigh = hi, printedName = a, printedValue = x.toString())
        val d1 = LocalDate.now().minusMonths(7); val d2 = LocalDate.now().minusDays(10)
        stack.records.saveImport(ImportBundle(r("r1", d1, "Annual check"), listOf(v("a", "r1", "ldl", d1, 120.0, null, 100.0), v("b", "r1", "hba1c", d1, 5.9, 4.0, 5.6)), emptyList(), emptyList()), emptyList())
        stack.records.saveImport(ImportBundle(r("r2", d2, "Lipid follow-up"), listOf(v("c", "r2", "ldl", d2, 165.0, null, 100.0)), emptyList(), emptyList()), emptyList())
    }

    @Test fun onboardingDashboardRecordsReportTrendAndMemberScoping() {
        onboard("Ravi")
        // clean profile → designed empty state with one primary action
        waitTag("homeEmpty")
        val self = runBlocking { ctx.container.stackOrNull!!.members.all() }.single()
        seed(self.id)
        waitTag("scoreCard")
        // focus: LDL 65 % above range → shown on the dashboard with its rationale
        rule.onNodeWithTag("home").performScrollToNode(hasText(str(R.string.focus_high_title, str(R.string.analyte_ldl))))
        rule.onNodeWithTag("home").performScrollToNode(hasTestTag("tile_ldl"))
        rule.onNodeWithTag("tile_ldl").performClick()

        // trend detail: chart + history
        waitTag("trendDetail"); waitTag("chart")
        rule.onNodeWithTag("trendDetailList").performScrollToNode(hasTestTag("historyHeader"))
        rule.onNodeWithText(str(R.string.trend_history)).assertExists()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitTag("shell")

        // records tab: search filters the report list
        rule.onNodeWithText(str(R.string.nav_records)).performClick()
        waitTag("recordsList")
        rule.onNodeWithText("Annual check").assertExists()
        rule.onNodeWithTag("recordsSearch").performTextInput("lipid")
        waitFor(5_000) { rule.onAllNodes(hasText("Annual check")).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Lipid follow-up").performClick()
        waitTag("result_ldl")

        // back to the shell; add a second family member through the switcher and check scoping
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitTag("memberSwitcher")
        rule.onNodeWithTag("memberSwitcher").performClick()
        rule.onNodeWithTag("addMember").performClick()
        waitTag("field_name")
        rule.onNodeWithTag("field_name").performTextReplacement("Meera")
        rule.onNodeWithTag("saveMember").performScrollTo().performClick()
        waitTag("shell")
        rule.onNodeWithText(str(R.string.nav_home)).performClick()
        // Meera is now active and has no records: her dashboard is empty, Ravi's data is not shown
        waitTag("homeEmpty")
        rule.onNodeWithText(str(R.string.home_empty_title, "Meera")).assertExists()
        // swipe DOWN on the profile photo → next member (Ravi) with his data
        rule.onNodeWithTag("swipeAvatar").performTouchInput { swipeDown(startY = top, endY = top + 400f, durationMillis = 300) }
        waitTag("scoreCard")
        rule.onNodeWithText("Ravi").assertExists()
        // tap the photo → picker → choose Meera again
        rule.onNodeWithTag("swipeAvatar").performClick()
        rule.onNodeWithTag("member_${runBlocking { ctx.container.stackOrNull!!.members.all() }.first { it.name == "Meera" }.id}").performClick()
        waitTag("homeEmpty")
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, qualifiers = "w900dp-h700dp")
class WideLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun tabletUsesNavigationRail() {
        val ctx = ApplicationProvider.getApplicationContext<TestApp>()
        runBlocking { ctx.container.openNow()!!.members.upsert(FamilyMember("m1", "Asha", Relationship.Self)) }
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("navRail")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("bottomBar").assertDoesNotExist()
    }
}
