package app.myhealth.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import app.myhealth.data.security.Crypto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Item 6: self-update from this project's GitHub releases. The ONLY network use in the app:
 * - HTTPS GET to GitHub only (every redirect is checked against [HOSTS]); nothing about the user is sent.
 * - A release carries `update.json` {versionCode, versionName, apk, sha256, size}; the newest higher versionCode wins.
 * - The APK must match the published SHA-256, have this package name, a higher version and be signed with the SAME
 *   certificate as the installed app (so only the project's own key can update it), then PackageInstaller installs it.
 *   On Android 12+ later updates need no tap once the user has allowed "install unknown apps" for MyHealth.
 */
object Updater {
    const val REPO = "solerunner26/MyHealth"
    val HOSTS = setOf("api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
    private const val MAX_JSON = 512 * 1024
    const val MAX_APK = 200L * 1024 * 1024

    data class Available(val versionCode: Long, val versionName: String, val apkUrl: String, val sha256: String, val size: Long)

    /** Only the store-signed release build updates itself (the debug build has its own id and never does). */
    fun enabledFor(context: Context) = context.packageName == "app.myhealth" &&
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0

    fun installedVersionCode(context: Context): Long = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)) else @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(Long.MAX_VALUE)

    /** Picks the newest release that publishes an update manifest; [fetch] returns a URL's body (tests inject it). */
    fun pick(releasesJson: String, currentCode: Long, fetch: (String) -> String): Available? {
        val releases = JSONArray(releasesJson)
        var best: Available? = null
        for (i in 0 until minOf(releases.length(), 10)) {
            val r = releases.getJSONObject(i)
            if (r.optBoolean("draft")) continue
            val assets = r.optJSONArray("assets") ?: continue
            val urls = (0 until assets.length()).map { assets.getJSONObject(it) }.associate { it.getString("name") to it.getString("browser_download_url") }
            val manifestUrl = urls["update.json"] ?: continue
            val m = runCatching { JSONObject(fetch(manifestUrl)) }.getOrNull() ?: continue
            val code = m.optLong("versionCode", -1); val apk = m.optString("apk")
            val sha = m.optString("sha256").lowercase()
            val apkUrl = urls[apk] ?: continue
            if (code <= currentCode || !sha.matches(Regex("[0-9a-f]{64}")) || !apk.endsWith(".apk")) continue
            if (best == null || code > best.versionCode) best = Available(code, m.optString("versionName"), apkUrl, sha, m.optLong("size", -1))
        }
        return best
    }

    fun check(context: Context): Available? {
        if (!enabledFor(context)) return null
        val json = String(get("https://api.github.com/repos/$REPO/releases?per_page=10", MAX_JSON.toLong()) { it.readBytes() }, Charsets.UTF_8)
        return pick(json, installedVersionCode(context)) { url -> String(get(url, MAX_JSON.toLong()) { it.readBytes() }, Charsets.UTF_8) }
    }

    /** Downloads into [dest], checking size and SHA-256 while streaming. */
    fun download(a: Available, dest: File, progress: (Float) -> Unit): Boolean {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        get(a.apkUrl, MAX_APK) { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024); var total = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    total += n; if (total > MAX_APK) error("too large")
                    digest.update(buf, 0, n); out.write(buf, 0, n)
                    if (a.size > 0) progress((total.toFloat() / a.size).coerceIn(0f, 1f))
                }
            }
        }
        val ok = digest.digest().joinToString("") { "%02x".format(it) } == a.sha256
        if (!ok) dest.delete()
        return ok
    }

    /** Same package, higher version, and signed by the same certificate as the running app. */
    fun verify(context: Context, apk: File): Boolean = runCatching {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return false
        if (archive.packageName != context.packageName) return false
        val newCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else @Suppress("DEPRECATION") archive.versionCode.toLong()
        if (newCode <= installedVersionCode(context)) return false
        val installed = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong())) else @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, flags)
        fun certs(p: android.content.pm.PackageInfo): Set<String> = if (Build.VERSION.SDK_INT >= 28) {
            val s = p.signingInfo ?: return emptySet()
            (if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory).map { Crypto.sha256Hex(it.toByteArray()) }.toSet()
        } else @Suppress("DEPRECATION") p.signatures.orEmpty().map { Crypto.sha256Hex(it.toByteArray()) }.toSet()
        val mine = certs(installed); val theirs = certs(archive)
        mine.isNotEmpty() && theirs.isNotEmpty() && theirs.any { it in mine }
    }.getOrDefault(false)

    fun canInstall(context: Context) = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            apk.inputStream().use { input -> s.openWrite("base.apk", 0, apk.length()).use { out -> input.copyTo(out); s.fsync(out) } }
            val pi = PendingIntent.getBroadcast(context, id, Intent(context, UpdateStatusReceiver::class.java).setAction(ACTION_STATUS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            s.commit(pi.intentSender)
        }
    }

    const val ACTION_STATUS = "app.myhealth.UPDATE_STATUS"

    /** HTTPS GET with redirects followed by hand: every hop must be HTTPS to an allowed GitHub host. */
    fun <T> get(url: String, maxBytes: Long, read: (InputStream) -> T): T {
        var current = URL(url)
        repeat(6) {
            require(current.protocol == "https" && current.host in HOSTS) { "host not allowed" }
            val c = (current.openConnection() as HttpsURLConnection).apply {
                instanceFollowRedirects = false; connectTimeout = 8_000; readTimeout = 30_000
                setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
                setRequestProperty("User-Agent", "MyHealth-updater")
            }
            try {
                when (val code = c.responseCode) {
                    in 300..399 -> { current = URL(current, c.getHeaderField("Location") ?: error("redirect without location")); return@repeat }
                    HttpURLConnection.HTTP_OK -> {
                        val len = c.contentLengthLong
                        require(len < 0 || len <= maxBytes) { "too large" }
                        return c.inputStream.use(read)
                    }
                    else -> error("http $code")
                }
            } finally { c.disconnect() }
        }
        error("too many redirects")
    }
}

/** Not exported. Hands the system confirmation screen to the user when Android asks for one (first update). */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Updater.ACTION_STATUS) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the app is replaced and restarts on next launch
            else -> UpdateState.failed()
        }
    }
}

/** Process-wide update progress for the splash screen. */
object UpdateState {
    val failedFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    fun failed() { failedFlow.value = true }
}
