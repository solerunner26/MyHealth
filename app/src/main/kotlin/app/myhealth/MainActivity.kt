package app.myhealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.ui.shell.AppShell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MyHealthTheme { AppShell() } }
    }
}
