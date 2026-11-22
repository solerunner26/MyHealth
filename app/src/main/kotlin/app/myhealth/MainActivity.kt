package app.myhealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.ui.shell.AppShell
import app.myhealth.ui.startup.StorageGate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = container
        setContent {
            val prefs by c.preferences.state.collectAsStateWithLifecycle()
            val storage by c.storage.collectAsStateWithLifecycle()
            MyHealthTheme(prefs.theme) {
                StorageGate(storage, onRetry = c::retry, onErase = { c.eraseEverything() }) { AppShell() }
            }
        }
    }
}
