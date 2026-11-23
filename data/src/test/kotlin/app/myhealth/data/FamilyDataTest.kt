package app.myhealth.data

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.myhealth.data.security.SoftwareKeyWrapper
import app.myhealth.domain.model.HealthCard
import app.myhealth.domain.model.HealthCardKind
import app.myhealth.domain.model.Media
import app.myhealth.domain.model.MediaKind
import app.myhealth.domain.model.RelativeCondition
import app.myhealth.domain.model.Vaccination
import app.myhealth.domain.model.VisitQuestion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class FamilyDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun newStack(name: String) = StorageOpener(context, StoragePaths(File(context.noBackupFilesDir, "$name-${System.nanoTime()}")), SoftwareKeyWrapper()) { FrameworkSQLiteOpenHelperFactory() }.open()
    private val pw = "family history".toCharArray()

    @Test fun vaccinationsCardsHistoryQuestionsAndImagesRoundTripThroughBackupAndDeleteWithTheMember() = runBlocking {
        val a = newStack("fam-a")
        val m = TestData.member()
        a.members.upsert(m)
        a.vaccinations.upsert(Vaccination("v1", m.id, "Td", "Booster", dueDate = LocalDate.of(2026, 1, 1)))
        a.vaccinations.upsert(Vaccination("v2", m.id, "BCG", "Birth", givenDate = LocalDate.of(1990, 1, 1)))
        a.healthCards.upsert(HealthCard("c1", m.id, HealthCardKind.Abha, number = "12-3456-7890-1234"))
        a.familyHistory.upsert(RelativeCondition("r1", "Paternal grandfather", "Type 2 diabetes", 55))
        a.visitQuestions.upsert(VisitQuestion("q1", m.id, "Should I repeat the lipid test?"))
        val front = ByteArray(5000) { (it % 251).toByte() }
        a.media.add(Media("img1", m.id, "c1", MediaKind.CardFront), front)
        assertEquals(listOf("v1"), a.vaccinations.allMembersPending().map { it.id })

        val out = ByteArrayOutputStream(); a.backups(context).export(out, pw, 0, "test")
        val b = newStack("fam-b")
        b.backups(context).restore(ByteArrayInputStream(out.toByteArray()), pw)
        assertEquals(2, b.vaccinations.all(m.id).size)
        assertEquals("12-3456-7890-1234", b.healthCards.observe(m.id).first().single().number)
        assertEquals("Type 2 diabetes", b.familyHistory.observe().first().single().condition)
        assertEquals(1, b.visitQuestions.observe(m.id).first().size)
        assertArrayEquals(front, b.media.bytes("img1"))

        // deleting the member removes their vaccinations, cards, questions and the encrypted images
        b.members.delete(m.id)
        assertTrue(b.vaccinations.all(m.id).isEmpty())
        assertTrue(b.healthCards.observe(m.id).first().isEmpty())
        assertNull(b.media.bytes("img1"))
        assertFalse(b.vault.names().any { it.startsWith("media-") })
        // relatives are family-wide and stay
        assertEquals(1, b.familyHistory.observe().first().size)
    }
}

@RunWith(RobolectricTestRunner::class)
class MediclaimDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun newStack(name: String) = StorageOpener(context, StoragePaths(File(context.noBackupFilesDir, "$name-${System.nanoTime()}")), SoftwareKeyWrapper()) { FrameworkSQLiteOpenHelperFactory() }.open()

    @Test fun policyWithFeaturesClaimsAndHospitalsSurvivesBackupAndIsSeenByCoveredMembers() = runBlocking {
        val a = newStack("mc-a")
        a.members.upsert(TestData.member("m1", "Ravi")); a.members.upsert(TestData.member("m2", "Meera", app.myhealth.domain.model.Relationship.Spouse))
        val p = app.myhealth.domain.model.MediclaimPolicy("p1", "m1", "Star Health", "Family Optima", "P/123", app.myhealth.domain.model.PolicyType.FamilyFloater, 500_000.0, 50_000.0,
            12_000.0, LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), listOf("m1", "m2"), tpaName = "Medi Assist",
            features = app.myhealth.domain.model.PolicyFeatures(roomRent = "Single private AC room", coPayPercent = 10, pedWaitingMonths = 36, restoration = true))
        a.mediclaims.upsert(p)
        a.mediclaims.upsertClaim(app.myhealth.domain.model.MediclaimClaim("c1", "p1", "m2", "City Hospital", LocalDate.of(2026, 7, 1), claimedAmount = 60_000.0, approvedAmount = 55_000.0, status = app.myhealth.domain.model.ClaimStatus.Settled))
        a.mediclaims.addHospitals(listOf(app.myhealth.domain.model.NetworkHospital("h1", "p1", "Sterling Hospital", "Memnagar", "Ahmedabad", "380052", "07940011111")))
        assertEquals(listOf("p1"), a.mediclaims.observe("m2").first().map { it.id }) // covered member sees the floater
        val out = ByteArrayOutputStream(); a.backups(context).export(out, "pw-pw-pw-pw".toCharArray(), 0, "t")
        val b = newStack("mc-b"); b.backups(context).restore(ByteArrayInputStream(out.toByteArray()), "pw-pw-pw-pw".toCharArray())
        val restored = b.mediclaims.observePolicy("p1").first()!!
        assertEquals(10, restored.features.coPayPercent); assertTrue(restored.features.restoration); assertEquals(listOf("m1", "m2"), restored.coveredMemberIds)
        assertEquals(55_000.0, b.mediclaims.observeClaims("p1").first().single().approvedAmount!!, 0.0)
        assertEquals("380052", b.mediclaims.observeHospitals("p1").first().single().pincode)
        b.members.delete("m1") // holder deleted → policy, claims and hospitals go with it
        assertTrue(b.mediclaims.all().isEmpty()); assertTrue(b.mediclaims.observeClaims("p1").first().isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
class MemberShareTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun newStack(name: String) = StorageOpener(context, StoragePaths(File(context.noBackupFilesDir, "$name-${System.nanoTime()}")), SoftwareKeyWrapper()) { FrameworkSQLiteOpenHelperFactory() }.open()
    private val code = "ABCD-EFGH-JKLM-NPQR-STUV".toCharArray()

    @Test fun oneMemberTravelsToAnotherPhoneAndReplacesTheOlderCopy() = runBlocking {
        val a = newStack("share-a")
        val ravi = TestData.member("m1", "Ravi"); val meera = TestData.member("m2", "Meera", app.myhealth.domain.model.Relationship.Spouse)
        a.members.upsert(ravi); a.members.upsert(meera)
        a.records.saveImport(app.myhealth.domain.repository.ImportBundle(TestData.report("r1", "m1"), listOf(TestData.result("x1", v = 13.2)), emptyList(), emptyList()),
            listOf(app.myhealth.domain.repository.NewAttachment(TestData.attachment(), "ravi's pdf".toByteArray())))
        a.records.saveImport(app.myhealth.domain.repository.ImportBundle(TestData.report("r2", "m2"), emptyList(), emptyList(), emptyList()), emptyList())
        a.healthCards.upsert(HealthCard("c1", "m1", HealthCardKind.Abha, number = "1"))
        a.familyHistory.upsert(RelativeCondition("fh", "Grandfather", "Diabetes"))

        val out = ByteArrayOutputStream(); a.backups(context).exportMember(out, code, 0, "t", "m1")
        val b = newStack("share-b")
        b.members.upsert(TestData.member("me", "Phone owner"))
        val info = b.backups(context).importMember(ByteArrayInputStream(out.toByteArray()), code)
        assertEquals(1, info.members)
        assertEquals(setOf("me", "m1"), b.members.all().map { it.id }.toSet()) // Meera was never shared
        assertEquals(1, b.records.observeReports("m1").first().size)
        assertArrayEquals("ravi's pdf".toByteArray(), b.records.attachmentBytes(b.records.observeReports("m1").first().single().attachments.single().id))
        assertTrue(b.familyHistory.observe().first().isEmpty()) // family-wide notes stay on the sender's phone

        // the sender adds a report and shares again: the copy is replaced, not duplicated
        a.records.saveImport(app.myhealth.domain.repository.ImportBundle(TestData.report("r3", "m1", LocalDate.of(2024, 5, 1)), emptyList(), emptyList(), emptyList()), emptyList())
        val out2 = ByteArrayOutputStream(); a.backups(context).exportMember(out2, code, 0, "t", "m1")
        b.backups(context).importMember(ByteArrayInputStream(out2.toByteArray()), code)
        assertEquals(setOf("r1", "r3"), b.records.observeReports("m1").first().map { it.id }.toSet())

        // a full backup is not accepted as a member share, and vice versa
        try { b.backups(context).restore(ByteArrayInputStream(out2.toByteArray()), code); org.junit.Assert.fail() } catch (e: app.myhealth.data.backup.BackupException) { assertEquals(app.myhealth.data.backup.BackupFailure.NotABackup, e.failure) }
    }

    @Test fun aCraftedShareCannotWriteIntoAnotherMembersRecord() = runBlocking {
        val b = newStack("share-evil")
        b.members.upsert(TestData.member("me", "Owner"))
        val manifest = """{"schema":3,"kind":"member","attachments":0}"""
        val data = """{"members":[{"id":"x","name":"X","relationship":"Self","sex":"Unknown","blood_group":"Unknown","allergies":"","conditions":"","ec_name":"","ec_phone":"","ec_relation":"","created_at":0}],
            "vitals":[{"id":"v","member_id":"me","type":"Pulse","value":200.0,"unit":"bpm","measured_at":0,"source":"Manual"}]}"""
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(app.myhealth.data.backup.BackupCrypto.encrypting(out, code, iterations = 10_000)).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("manifest.json")); z.write(manifest.toByteArray()); z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("data.json")); z.write(data.toByteArray()); z.closeEntry()
        }
        try { b.backups(context).importMember(ByteArrayInputStream(out.toByteArray()), code); org.junit.Assert.fail() } catch (e: app.myhealth.data.backup.BackupException) { assertEquals(app.myhealth.data.backup.BackupFailure.Corrupt, e.failure) }
        assertTrue(b.vitals.all("me").isEmpty())
        assertEquals(listOf("me"), b.members.all().map { it.id })
    }
}
