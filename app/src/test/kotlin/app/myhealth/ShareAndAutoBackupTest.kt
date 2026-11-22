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

    @Test fun updaterPicksTheNewestValidReleaseAndOnlyTalksToGithub() {
        val sha = "a".repeat(64)
        fun rel(tag: String, manifest: Boolean, draft: Boolean = false) = """{"tag_name":"$tag","draft":$draft,"assets":[
            ${if (manifest) """{"name":"update.json","browser_download_url":"https://github.com/x/$tag/update.json"},""" else ""}
            {"name":"MyHealth-$tag-release.apk","browser_download_url":"https://github.com/x/$tag/app.apk"}]}"""
        val releases = "[" + listOf(rel("0.4.0", true, draft = true), rel("0.3.1", true), rel("0.3.0", true), rel("0.2.0", false)).joinToString(",") + "]"
        val manifests = mapOf(
            "https://github.com/x/0.4.0/update.json" to """{"versionCode":5,"versionName":"0.4.0","apk":"MyHealth-0.4.0-release.apk","sha256":"$sha"}""",
            "https://github.com/x/0.3.1/update.json" to """{"versionCode":4,"versionName":"0.3.1","apk":"MyHealth-0.3.1-release.apk","sha256":"$sha","size":10}""",
            "https://github.com/x/0.3.0/update.json" to """{"versionCode":3,"versionName":"0.3.0","apk":"MyHealth-0.3.0-release.apk","sha256":"$sha"}""",
        )
        val a = app.myhealth.core.Updater.pick(releases, currentCode = 3) { manifests.getValue(it) }!!
        assertEquals(4, a.versionCode); assertEquals("https://github.com/x/0.3.1/app.apk", a.apkUrl) // drafts ignored
        assertNull(app.myhealth.core.Updater.pick(releases, currentCode = 4) { manifests.getValue(it) }) // already newest
        // a manifest with a bad checksum or a non-APK file is ignored
        val bad = mapOf("https://github.com/x/0.3.1/update.json" to """{"versionCode":9,"apk":"evil.sh","sha256":"zz"}""") + manifests.filterKeys { "0.3.1" !in it }
        assertEquals(3L, app.myhealth.core.Updater.pick(releases, currentCode = 2) { bad.getValue(it) }!!.versionCode)
        // only HTTPS to GitHub hosts
        for (u in listOf("http://github.com/a", "https://evil.example/a", "https://github.com.evil.example/a")) {
            assertTrue(runCatching { app.myhealth.core.Updater.get(u, 10) { it.readBytes() } }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
