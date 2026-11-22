package app.myhealth

import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.domain.model.DailyActivity
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.Report
import app.myhealth.domain.model.ReportType
import app.myhealth.domain.repository.ActivityRepository
import app.myhealth.domain.repository.ImportBundle
import app.myhealth.domain.repository.NewAttachment
import app.myhealth.health.DayAggregate
import app.myhealth.health.HcChanges
import app.myhealth.health.HcData
import app.myhealth.health.HcStatus
import app.myhealth.health.HealthSource
import app.myhealth.health.HealthSync
import app.myhealth.ui.Session
import app.myhealth.ui.backup.BackupPhase
import app.myhealth.ui.backup.BackupViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate

class FakeActivityRepo : ActivityRepository {
    val rows = mutableMapOf<LocalDate, DailyActivity>(); var token: String? = null
    override fun observe(memberId: String) = MutableStateFlow(rows.values.toList())
    override suspend fun all(memberId: String) = rows.values.sortedBy { it.date }
    override suspend fun upsertAll(days: List<DailyActivity>) { days.forEach { rows[it.date] = it } }
    override suspend fun syncToken(memberId: String) = token
    override suspend fun setSyncToken(memberId: String, token: String?) { this.token = token }
    override suspend fun clear(memberId: String) { rows.clear(); token = null }
}

class FakeSource(var status: HcStatus = HcStatus.Available) : HealthSource {
    var granted = setOf(HcData.Steps, HcData.Weight); var steps = 5000L; var changes: HcChanges? = null; var tokenN = 0; var revoked = false
    val requestedFrom = mutableListOf<LocalDate>()
    override fun status() = status
    override suspend fun granted() = granted
    override suspend fun dailyAggregates(from: LocalDate, toExclusive: LocalDate): List<DayAggregate> {
        requestedFrom += from
        return generateSequence(from) { it.plusDays(1) }.takeWhile { it.isBefore(toExclusive) }.map { DayAggregate(it, steps, null, null, null, 70.5) }.toList()
    }
    override suspend fun changesToken() = "t${tokenN++}"
    override suspend fun changes(token: String) = changes ?: HcChanges(emptySet(), false, "t${tokenN++}")
    override suspend fun revokeAll() { revoked = true }
}

class HealthSyncTest {
    private val clock = MutableClock(Instant.parse("2025-06-15T12:00:00Z"))
    private val today = LocalDate.of(2025, 6, 15)

    @Test fun firstSyncIsFull30DaysThenIncremental() = runBlocking {
        val repo = FakeActivityRepo(); val src = FakeSource(); val sync = HealthSync(src, repo, clock)
        val first = sync.sync("m")
        assertTrue(first.full); assertEquals(30, first.daysWritten); assertEquals(today.minusDays(29), src.requestedFrom.last())
        assertEquals("t0", repo.token)
        // incremental: one upstream change 3 days ago → that day + yesterday + today re-aggregated
        src.steps = 9000; src.changes = HcChanges(setOf(today.minusDays(3)), false, "t9")
        val second = sync.sync("m")
        assertFalse(second.full); assertEquals(3, second.daysWritten); assertEquals("t9", repo.token)
        assertEquals(9000L, repo.rows.getValue(today.minusDays(3)).steps); assertEquals(5000L, repo.rows.getValue(today.minusDays(10)).steps)
        assertEquals(70.5, repo.rows.getValue(today).weightKg!!, 0.0)
    }

    @Test fun expiredTokenOrDeletionForcesFullResync() = runBlocking {
        val repo = FakeActivityRepo().apply { token = "old" }; val src = FakeSource().apply { changes = HcChanges(emptySet(), true, "x") }
        assertTrue(HealthSync(src, repo, clock).sync("m").full)
        assertEquals(30, repo.rows.size)
    }

    @Test fun unavailableOrNoPermissionDoesNothingAndDisconnectClears() = runBlocking {
        val repo = FakeActivityRepo()
        assertEquals(0, HealthSync(FakeSource(HcStatus.NotInstalled), repo, clock).sync("m").daysWritten)
        assertEquals(0, HealthSync(FakeSource().apply { granted = emptySet() }, repo, clock).sync("m").daysWritten)
        val src = FakeSource(); val sync = HealthSync(src, repo, clock)
        sync.sync("m"); assertTrue(repo.rows.isNotEmpty())
        sync.disconnect("m")
        assertTrue(src.revoked); assertTrue(repo.rows.isEmpty()); assertNull(repo.token)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class BackupRestoreFlowTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()
    private fun waitFor(c: () -> Boolean) { val end = System.currentTimeMillis() + 20_000; while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); if (c()) return; Thread.sleep(25) }; error("timeout") }

    @Test fun backupEraseRestoreBringsEverythingBack() {
        var stack = runBlocking { ctx.container.openNow() }!!
        runBlocking {
            stack.members.upsert(FamilyMember("m1", "Ravi", Relationship.Self))
            stack.records.saveImport(ImportBundle(Report("r1", "m1", ReportType.LabReport, "CBC", LocalDate.of(2024, 1, 2), createdAt = Instant.EPOCH), emptyList(), emptyList(), emptyList()),
                listOf(NewAttachment(app.myhealth.domain.model.Attachment("a1", "r1", app.myhealth.domain.model.AttachmentKind.Pdf, "cbc.pdf", "sha", 3, 1), "pdf".toByteArray())))
        }
        val file = File(ctx.cacheDir, "test.myhealth")
        var vm = BackupViewModel(Session(ctx.container, stack), ctx as android.app.Application)
        vm.export(Uri.fromFile(file), "a long password".toCharArray())
        waitFor { vm.phase.value is BackupPhase.Exported || vm.phase.value is BackupPhase.Failed }
        assertTrue(vm.phase.value.toString(), vm.phase.value is BackupPhase.Exported)
        assertTrue(file.length() > 100)

        // secure erase, as from Settings (new keys → old data unreadable), then restore into the fresh install
        var erased = false
        ctx.container.eraseEverything { erased = true }
        waitFor { erased && ctx.container.stackOrNull != null }
        stack = ctx.container.stackOrNull!!
        assertTrue(runBlocking { stack.members.all() }.isEmpty())
        vm = BackupViewModel(Session(ctx.container, stack), ctx as android.app.Application)
        vm.restore(Uri.fromFile(file), "wrong password".toCharArray())
        waitFor { vm.phase.value is BackupPhase.Failed }
        assertEquals(app.myhealth.data.backup.BackupFailure.WrongPassword, (vm.phase.value as BackupPhase.Failed).failure)
        vm.restore(Uri.fromFile(file), "a long password".toCharArray())
        waitFor { vm.phase.value is BackupPhase.Restored }
        assertEquals(listOf("Ravi"), runBlocking { stack.members.all() }.map { it.name })
        assertArrayEquals("pdf".toByteArray(), runBlocking { stack.records.attachmentBytes("a1") })
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class SecureEraseFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()
    private fun waitFor(c: () -> Boolean) { val end = System.currentTimeMillis() + 15_000; while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.waitForIdle(); if (c()) return; Thread.sleep(20) }; error("timeout") }
    private fun waitTag(t: String) = waitFor { rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun eraseFromSettingsDestroysDataAndReturnsToWelcome() {
        runBlocking { ctx.container.openNow()!!.members.upsert(FamilyMember("m1", "Ravi", Relationship.Self)) }
        ctx.container.preferences.setTheme(app.myhealth.core.designsystem.theme.ThemeMode.Dark)
        waitTag("shell")
        val oldKeys = File(ctx.noBackupFilesDir, "secure/keys/keys.bin").readBytes()
        rule.onNodeWithText(ctx.getString(R.string.nav_more)).performClick()
        waitTag("more"); rule.onNodeWithTag("more").performScrollToNode(hasTestTag("openSettings")); rule.onNodeWithTag("openSettings").performClick()
        waitTag("settings"); rule.onNodeWithTag("eraseAll").performScrollTo().performClick()
        rule.onNodeWithText(ctx.getString(R.string.storage_erase_confirm)).performClick()
        waitTag("welcome")
        assertEquals(app.myhealth.core.designsystem.theme.ThemeMode.System, ctx.container.preferences.state.value.theme) // prefs reset
        assertFalse(File(ctx.noBackupFilesDir, "secure/keys/keys.bin").readBytes().contentEquals(oldKeys)) // new keys
        assertTrue(File(ctx.noBackupFilesDir, "secure/vault").listFiles().isNullOrEmpty())
    }
}
