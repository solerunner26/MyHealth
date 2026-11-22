package app.myhealth

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.core.AutoBackup
import app.myhealth.core.ShareCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class ShareAndAutoBackupTest {
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun shareCodeIsLongTypableAndSurvivesAQrRoundTrip() {
        val code = ShareCode.generate()
        assertTrue(code.matches(Regex("([A-HJ-NP-Z2-9]{5}-){4}[A-HJ-NP-Z2-9]{5}")))
        assertEquals(code, ShareCode.normalise(code.lowercase().replace("-", " ")))
        assertNull(ShareCode.normalise("1234")); assertNull(ShareCode.normalise(code.replace(code[0], '0')))
        assertEquals(code, ShareCode.scan(ShareCode.qr(code)))
        assertTrue((1..50).map { ShareCode.generate() }.toSet().size == 50)
    }

    @Test fun automaticBackupsKeepTheNewestFiveAndNeverTouchOtherFiles() {
        val names = (1..8).map { AutoBackup.fileName(Instant.parse("2026-11-0${it}T10:00:00Z"), ZoneOffset.UTC) } + listOf("MyHealth-2026-01-01.myhealth", "photo.jpg")
        assertEquals("MyHealth-auto-20261101-1000.myhealth", names.first())
        val prune = AutoBackup.toPrune(names.shuffled())
        assertEquals(names.take(3), prune)
        assertTrue("manual backup kept", "MyHealth-2026-01-01.myhealth" !in prune)
    }
}
