package app.myhealth

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Relationship
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class MediclaimFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()
    private fun waitFor(ms: Long = 10_000, c: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.waitForIdle(); if (c()) return; Thread.sleep(20) }
        error("timeout")
    }
    private fun waitTag(t: String) = waitFor { rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun addPolicyThenAClaimReducesTheAvailableCover() {
        val stack = runBlocking { ctx.container.openNow()!! }
        runBlocking { stack.members.upsert(FamilyMember("m1", "Ravi", Relationship.Self)); ctx.container.preferences.setActiveMember("m1") }
        waitTag("shell")
        rule.onNode(hasText(ctx.getString(R.string.nav_more)) and hasAnyAncestor(hasTestTag("bottomBar"))).performClick()
        waitTag("more"); rule.onNodeWithTag("more").performScrollToNode(hasTestTag("openMediclaim")); rule.onNodeWithTag("openMediclaim").performClick()
        waitTag("mediclaim"); rule.onNodeWithTag("addPolicy").performClick()
        waitTag("policyPick") // a new policy starts from its document; this test types it in
        rule.onNodeWithTag("pickPolicyDocs").assertExists()
        rule.onNodeWithTag("fillByHand").performClick()
        waitTag("insurer")
        rule.onNodeWithTag("insurer").performTextInput("Star Health")
        rule.onNodeWithTag("policyNumber").performTextInput("P/2026/123")
        rule.onNodeWithTag("sumInsured").performScrollTo().performTextInput("500000")
        rule.onNodeWithTag("savePolicy").performScrollTo().performClick()
        waitTag("policyDetail")
        val policy = runBlocking { stack.mediclaims.all() }.single()
        assertEquals(500_000.0, policy.sumInsured, 0.0); assertEquals(listOf("m1"), policy.coveredMemberIds)

        // a settled claim of ₹80,000 leaves ₹4,20,000
        rule.onNodeWithTag("policyList").performScrollToNode(hasTestTag("addClaim")); rule.onNodeWithTag("addClaim").performClick()
        waitTag("claimDialog")
        rule.onNodeWithTag("claimHospital").performTextInput("City Hospital")
        rule.onNodeWithTag("claimAmount").performScrollTo().performTextInput("90000")
        rule.onNode(androidx.compose.ui.test.hasClickAction() and androidx.compose.ui.test.hasAnyDescendant(hasText(ctx.getString(R.string.mc_st_settled))), useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) // chip sits in a horizontally scrolled row
        rule.onNodeWithTag("claimApproved").performScrollTo().performTextInput("80000")
        rule.onNodeWithText(ctx.getString(R.string.action_save)).performClick()
        waitFor { runBlocking { stack.mediclaims.observeClaims(policy.id).first() }.isNotEmpty() }
        val claim = runBlocking { stack.mediclaims.observeClaims(policy.id).first() }.single()
        assertEquals(app.myhealth.domain.model.ClaimStatus.Settled, claim.status); assertEquals(80_000.0, claim.approvedAmount!!, 0.0)
        rule.onNodeWithTag("policyList").performScrollToNode(hasText(app.myhealth.ui.insurance.rupees(420_000.0)))
    }

    @Test fun documentDetailsFillTheEditorWithoutLosingWhatTheUserTyped() {
        val typed = app.myhealth.domain.model.MediclaimPolicy("p", "m1", "My insurer", notes = "keep", coveredMemberIds = listOf("m1"),
            features = app.myhealth.domain.model.PolicyFeatures(roomRent = "Twin sharing", opd = true))
        val read = app.myhealth.domain.insurance.PolicyParser.Result(policyNumber = "P-77", sumInsured = 300_000.0, memberIds = listOf("m2"),
            features = app.myhealth.domain.model.PolicyFeatures(coPayPercent = 10), found = 3)
        val out = app.myhealth.ui.insurance.fill(typed, read)
        assertEquals("My insurer", out.insurer); assertEquals("P-77", out.policyNumber); assertEquals(300_000.0, out.sumInsured, 0.0)
        assertEquals(listOf("m1", "m2"), out.coveredMemberIds); assertEquals("keep", out.notes)
        assertEquals("Twin sharing", out.features.roomRent); assertEquals(10, out.features.coPayPercent); assertEquals(true, out.features.opd)
    }
}
