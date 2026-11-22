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
