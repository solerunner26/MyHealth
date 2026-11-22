package com.solerunner.myhealth.design.rare

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Respects the system animation scale. Infinite animations (PIN caret, skeleton shimmer) freeze when
 * the user has asked the device for reduced motion or has disabled animations in developer options.
 */
@Composable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}
