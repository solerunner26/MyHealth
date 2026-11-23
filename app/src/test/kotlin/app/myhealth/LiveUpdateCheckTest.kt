package app.myhealth

import app.myhealth.core.Updater
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in (-PliveUpdate=<dir>): runs the app's real release-picking code on a snapshot of the LIVE GitHub releases
 * (releases.json + every update.json), to check what an installed build would be offered.
 */
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.Config(application = TestApp::class)
class LiveUpdateCheckTest {
    @Test fun installedBuildsFindTheNewestRelease() {
        val dir = System.getProperty("liveUpdate")?.let(::File)
        assumeTrue(dir != null && File(dir, "releases.json").exists())
        val releases = File(dir, "releases.json").readText()
        val manifests = JSONObject(File(dir, "manifests.json").readText())
        val fetch: (String) -> String = { manifests.getString(it) }
        val newest = (0 until manifests.length()).maxOf { JSONObject(manifests.getString(manifests.names()!!.getString(it))).getLong("versionCode") }
        // an installed older build is offered the newest one; the newest itself is told it is up to date
        assertEquals(newest, Updater.pick(releases, newest - 1, fetch)!!.versionCode)
        assertEquals(newest, Updater.pick(releases, 104, fetch)!!.versionCode)
        assertNull(Updater.pick(releases, newest, fetch))
        println("LIVE: newest build on GitHub = $newest; older installs are offered it")
    }
}
