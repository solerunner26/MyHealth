package com.solerunner.myhealth.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.solerunner.myhealth.R

/** Every route in the app, in one place so deep links and tests can reference strings, not literals. */
object Routes {
    const val HOME = "home"
    const val RECORDS = "records"
    const val REPORT_DETAIL = "report/{reportId}"
    const val IMPORT = "import"
    const val TRENDS = "trends"
    const val TREND_DETAIL = "trend/{analyteKey}"
    const val VITALS = "vitals"
    const val MEDICINES = "medicines"
    const val TREATMENTS = "treatments"
    const val INSIGHTS = "insights"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val DOCTOR_SUMMARY = "doctor-summary"

    fun reportDetail(id: Long): String = "report/$id"
    fun trendDetail(key: String): String = "trend/$key"
}

/** One entry of the bottom bar / navigation rail. */
data class TopDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

val topDestinations: List<TopDestination> = listOf(
    TopDestination(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    TopDestination(Routes.RECORDS, R.string.nav_records, Icons.Filled.List),
    TopDestination(Routes.TRENDS, R.string.nav_trends, Icons.Filled.Star),
    TopDestination(Routes.VITALS, R.string.nav_vitals, Icons.Filled.Favorite),
    TopDestination(Routes.MEDICINES, R.string.nav_medicines, Icons.Filled.ShoppingCart),
)
