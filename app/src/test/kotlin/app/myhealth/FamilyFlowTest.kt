package app.myhealth

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.Sex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class FamilyFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()
    private fun waitFor(ms: Long = 10_000, c: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.waitForIdle(); if (c()) return; Thread.sleep(20) }
        error("timeout")
    }
    private fun waitTag(t: String) = waitFor { rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty() }
    private fun back() = rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    private fun openFromMore(tag: String) {
        if (rule.onAllNodes(hasTestTag("more")).fetchSemanticsNodes().isEmpty())
            rule.onNode(hasText(ctx.getString(R.string.nav_more)) and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("bottomBar"))).performClick()
        waitTag("more")
        rule.onNodeWithTag("more").performScrollToNode(hasTestTag(tag)); rule.onNodeWithTag(tag).performClick()
    }

    @Test fun vaccinationsCardsFamilyHistoryAndVisitPrep() {
        val stack = runBlocking { ctx.container.openNow()!! }
        runBlocking {
            stack.members.upsert(FamilyMember("kid", "Kiara", Relationship.Self, LocalDate.now().minusMonths(3), Sex.Female, conditions = listOf("Asthma")))
            stack.members.upsert(FamilyMember("dad", "Ravi", Relationship.Parent, conditions = listOf("Type 2 diabetes")))
            ctx.container.preferences.setActiveMember("kid")
        }
        waitTag("shell")

        // vaccinations: suggested schedule from the date of birth; overdue doses show first; mark one given
        openFromMore("openVaccines"); waitTag("vaccinations")
        rule.onNodeWithTag("suggestVaccines").performClick()
        waitFor { runBlocking { stack.vaccinations.all("kid") }.isNotEmpty() }
        val bcg = runBlocking { stack.vaccinations.all("kid") }.first { it.name == "BCG" }
        waitTag("given_${bcg.id}")
        rule.onNodeWithTag("given_${bcg.id}").performScrollTo().performClick()
        waitFor { runBlocking { stack.vaccinations.all("kid") }.first { it.id == bcg.id }.given }
        back(); waitTag("shell")

        // health card with its number
        openFromMore("openCards"); waitTag("healthCards")
        rule.onNodeWithTag("addCard").performClick(); waitTag("cardDialog")
        rule.onNodeWithTag("cardNumber").performTextInput("12-3456-7890-1234")
        rule.onNodeWithText(ctx.getString(R.string.action_save)).performClick()
        waitFor { runBlocking { stack.healthCards.observe("kid").first() }.singleOrNull()?.number == "12-3456-7890-1234" }
        back(); waitTag("shell")

        // family history: grandfather's diabetes + Ravi's → "runs in the family"
        openFromMore("openFamilyHistory"); waitTag("familyHistory")
        rule.onNodeWithTag("addRelative").performClick()
        rule.onNodeWithTag("famRelation").performTextInput("Grandfather")
        rule.onNodeWithTag("famCondition").performTextInput("Diabetes")
        rule.onNodeWithText(ctx.getString(R.string.action_save)).performClick()
        waitFor { rule.onAllNodes(hasText(ctx.getString(R.string.fam_runs)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        back(); waitTag("shell")

        // visit prep: built on demand, keeps a question
        openFromMore("openVisitPrep"); waitTag("visitPrep")
        rule.onNodeWithTag("visitPrep").assertExists()
        waitTag("visitList")
        rule.onNodeWithTag("visitList").performScrollToNode(hasTestTag("visitQuestion"))
        rule.onNodeWithTag("visitQuestion").performTextInput("When is the next vaccine?")
        rule.onNodeWithTag("visitList").performScrollToNode(hasTestTag("addQuestion")); rule.onNodeWithTag("addQuestion").performClick()
        waitFor { runBlocking { stack.visitQuestions.observe("kid").first() }.size == 1 }
        assertEquals("When is the next vaccine?", runBlocking { stack.visitQuestions.observe("kid").first() }.single().text)
    }
}
