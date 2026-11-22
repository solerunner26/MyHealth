package com.solerunner.myhealth.design

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solerunner.myhealth.design.components.LineChart
import com.solerunner.myhealth.design.rare.RareBadge
import com.solerunner.myhealth.design.rare.RareButton
import com.solerunner.myhealth.design.rare.RareButtonStyle
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareCheckbox
import com.solerunner.myhealth.design.rare.RareKeyValue
import com.solerunner.myhealth.design.rare.RarePageHeader
import com.solerunner.myhealth.design.rare.RarePinEntry
import com.solerunner.myhealth.design.rare.RareSectionLabel
import com.solerunner.myhealth.design.rare.RareSegmented
import com.solerunner.myhealth.design.rare.RareStat
import com.solerunner.myhealth.design.rare.RareSwitch
import com.solerunner.myhealth.design.rare.RareTextField
import com.solerunner.myhealth.design.rare.RareTone
import com.solerunner.myhealth.design.theme.MyHealthTheme
import com.solerunner.myhealth.domain.model.AnalyteCategory
import com.solerunner.myhealth.domain.model.AnalyteSeries
import com.solerunner.myhealth.domain.model.DataSource
import com.solerunner.myhealth.domain.model.DirectionOfGood
import com.solerunner.myhealth.domain.model.FocusCode
import com.solerunner.myhealth.domain.model.FocusItem
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.domain.model.Report
import com.solerunner.myhealth.domain.model.ReportKind
import com.solerunner.myhealth.domain.model.SeriesPoint
import com.solerunner.myhealth.domain.model.Severity
import com.solerunner.myhealth.domain.model.ValueStatus
import com.solerunner.myhealth.data.HealthSnapshot
import com.solerunner.myhealth.ui.dashboard.DashboardContent
import com.solerunner.myhealth.ui.dashboard.DashboardUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Renders the real Compose UI under Robolectric with native graphics and writes PNGs to
 * `app/build/screenshots`. This is how the design can be reviewed without a device: no mockups, the
 * actual composables are drawn into a bitmap.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h1100dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotGalleryTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 11, 22)

    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MyHealthTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF0A0B0B) else Color(0xFFFDFDFB))) {
                    content()
                }
            }
        }
        repeat(6) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.advanceTimeBy(900)
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val directory = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(directory, name)).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    }

    private fun profile() = Profile(displayName = "Ananya Shah", dateOfBirth = LocalDate.of(1986, 4, 12))

    private fun sampleSnapshot(): HealthSnapshot {
        val hemoglobin = AnalyteSeries(
            key = "hemoglobin", displayName = "Haemoglobin", category = AnalyteCategory.HEMATOLOGY,
            unit = "g/dL", directionOfGood = DirectionOfGood.HIGHER, referenceLow = 12.0, referenceHigh = 15.5,
            points = listOf(
                SeriesPoint(LocalDate.of(2026, 2, 20), 11.4, ValueStatus.LOW, DataSource.REPORT_IMPORT),
                SeriesPoint(LocalDate.of(2026, 5, 30), 11.9, ValueStatus.LOW, DataSource.REPORT_IMPORT),
                SeriesPoint(LocalDate.of(2026, 10, 2), 12.6, ValueStatus.IN_RANGE, DataSource.REPORT_IMPORT),
            ), decimals = 1,
        )
        val ldl = AnalyteSeries(
            key = "ldl_cholesterol", displayName = "LDL Cholesterol", category = AnalyteCategory.LIPIDS,
            unit = "mg/dL", directionOfGood = DirectionOfGood.LOWER, referenceLow = null, referenceHigh = 100.0,
            points = listOf(
                SeriesPoint(LocalDate.of(2026, 2, 20), 112.0, ValueStatus.HIGH, DataSource.REPORT_IMPORT),
                SeriesPoint(LocalDate.of(2026, 10, 2), 134.0, ValueStatus.HIGH, DataSource.REPORT_IMPORT),
            ), decimals = 0,
        )
        val hba1c = AnalyteSeries(
            key = "hba1c", displayName = "HbA1c", category = AnalyteCategory.DIABETES,
            unit = "%", directionOfGood = DirectionOfGood.LOWER, referenceLow = 4.0, referenceHigh = 5.6,
            points = listOf(
                SeriesPoint(LocalDate.of(2026, 2, 20), 5.7, ValueStatus.HIGH, DataSource.REPORT_IMPORT),
                SeriesPoint(LocalDate.of(2026, 10, 2), 5.4, ValueStatus.IN_RANGE, DataSource.REPORT_IMPORT),
            ), decimals = 1,
        )
        return HealthSnapshot(
            today = today,
            profile = profile(),
            series = listOf(ldl, hba1c, hemoglobin),
            reports = listOf(
                Report(id = 3, title = "Lipid profile", labName = "City Diagnostics", sampleDate = LocalDate.of(2026, 10, 2), kind = ReportKind.LAB),
                Report(id = 2, title = "CBC with ESR", labName = "City Diagnostics", sampleDate = LocalDate.of(2026, 5, 30), kind = ReportKind.LAB),
                Report(id = 1, title = "Annual health check", labName = "Sunrise Lab", sampleDate = LocalDate.of(2026, 2, 20), kind = ReportKind.LAB),
            ),
            measurements = listOf(
                Measurement(kind = MeasurementKind.BLOOD_PRESSURE, primaryValue = 128.0, secondaryValue = 82.0, unit = "mmHg", recordedAt = Instant.parse("2026-11-20T08:00:00Z"), recordedDay = LocalDate.of(2026, 11, 20)),
                Measurement(kind = MeasurementKind.WEIGHT, primaryValue = 68.4, unit = "kg", recordedAt = Instant.parse("2026-11-18T07:00:00Z"), recordedDay = LocalDate.of(2026, 11, 18)),
                Measurement(kind = MeasurementKind.PULSE, primaryValue = 74.0, unit = "bpm", recordedAt = Instant.parse("2026-11-20T08:00:00Z"), recordedDay = LocalDate.of(2026, 11, 20)),
            ),
            medicines = listOf(
                Medicine(id = 1, name = "Metformin", strength = "500 mg", frequencyText = "Twice a day"),
                Medicine(id = 2, name = "Atorvastatin", strength = "10 mg", frequencyText = "At night"),
            ),
            treatments = emptyList(),
            dailyMetrics = emptyList(),
        )
    }

    private fun sampleState(): DashboardUiState = DashboardUiState(
        loading = false,
        snapshot = sampleSnapshot(),
        focus = listOf(
            FocusItem(
                code = FocusCode.VALUE_HIGH,
                severity = Severity.WATCH,
                priority = 60,
                analyteKey = "ldl_cholesterol",
                analyteName = "LDL Cholesterol",
                args = mapOf("analyte" to "LDL Cholesterol", "value" to "134", "unit" to "mg/dL", "range" to "< 100"),
                evidence = listOf("134  ·  2026-10-02", "112  ·  2026-02-20"),
            ),
            FocusItem(
                code = FocusCode.VALUE_BACK_IN_RANGE,
                severity = Severity.INFO,
                priority = 30,
                analyteKey = "hba1c",
                analyteName = "HbA1c",
                args = mapOf("analyte" to "HbA1c", "value" to "5.4", "unit" to "%", "range" to "4.0 – 5.6"),
                discussionPoint = false,
            ),
        ),
    )

    @Test
    fun home() {
        capture("home.png") {
            DashboardContent(
                state = sampleState(),
                profile = profile(),
                onNavigate = {},
                onOpenReport = {},
            )
        }
    }

    @Test
    fun homeDark() {
        capture("home-dark.png", dark = true) {
            DashboardContent(
                state = sampleState(),
                profile = profile(),
                onNavigate = {},
                onOpenReport = {},
            )
        }
    }

    @Test
    fun pin() {
        capture("pin.png") {
            Column(
                Modifier.fillMaxSize().padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("MY HEALTH", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(20.dp))
                RarePinEntry(
                    pin = "1234",
                    onPinChange = {},
                    title = "Welcome back",
                    subtitle = "Enter your PIN to open your records.",
                    actionLabel = "Continue",
                    onAction = {},
                    onBiometric = {},
                    note = "Offline by design. Nothing is uploaded.",
                )
            }
        }
    }

    @Test
    fun components() {
        capture("components.png") {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RarePageHeader(title = "Design system", eyebrow = "Rare UI, one hue")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RareButton(text = "Primary", onClick = {})
                    RareButton(text = "Secondary", onClick = {}, style = RareButtonStyle.SECONDARY)
                    RareButton(text = "Ghost", onClick = {}, style = RareButtonStyle.GHOST)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RareBadge(text = "In range", tone = RareTone.ACCENT, icon = Icons.Filled.Check)
                    RareBadge(text = "Watch", tone = RareTone.NEUTRAL)
                    RareBadge(text = "Discuss", tone = RareTone.SOLID, icon = Icons.Filled.Warning)
                    RareBadge(text = "No range", tone = RareTone.OUTLINE)
                }
                RareCard {
                    RareTextField(value = "Haemoglobin", onValueChange = {}, label = "Analyte")
                    Spacer(Modifier.height(10.dp))
                    RareTextField(value = "13.2", onValueChange = {}, label = "Value", trailing = { Text("g/dL") })
                }
                RareSegmented(options = listOf("Active", "Past"), selected = "Active", onSelect = {}, label = { it })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RareSwitch(checked = true, onCheckedChange = {})
                    RareSwitch(checked = false, onCheckedChange = {})
                    RareCheckbox(checked = true, onCheckedChange = {})
                    RareCheckbox(checked = false, onCheckedChange = {})
                }
                RareCard(inset = true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        RareStat(label = "Latest", value = "128/82", unit = "mmHg", modifier = Modifier.weight(1f))
                        RareStat(label = "7-day avg", value = "72.4", unit = "kg", modifier = Modifier.weight(1f))
                    }
                }
                RareCard {
                    RareSectionLabel(text = "Recent reports", actionLabel = "See all")
                    RareKeyValue(label = "Lipid profile", value = "02 Oct 2026")
                    RareKeyValue(label = "CBC with ESR", value = "30 May 2026")
                }
            }
        }
    }

    @Test
    fun trend() {
        val series = AnalyteSeries(
            key = "ldl_cholesterol", displayName = "LDL Cholesterol", category = AnalyteCategory.LIPIDS,
            unit = "mg/dL", directionOfGood = DirectionOfGood.LOWER, referenceLow = null, referenceHigh = 100.0,
            points = listOf(
                SeriesPoint(LocalDate.of(2025, 11, 2), 98.0, ValueStatus.IN_RANGE, DataSource.REPORT_IMPORT),
                SeriesPoint(LocalDate.of(2026, 2, 20), 112.0, ValueStatus.HIGH, DataSource.REPORT_IMPORT),
                SeriesPoint(LocalDate.of(2026, 5, 30), 121.0, ValueStatus.HIGH, DataSource.REPORT_IMPORT),
                SeriesPoint(LocalDate.of(2026, 10, 2), 134.0, ValueStatus.HIGH, DataSource.REPORT_IMPORT),
            ), decimals = 0,
        )
        capture("trend.png") {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RarePageHeader(title = "LDL Cholesterol", onBack = {})
                RareCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        RareStat(label = "Latest", value = "134", unit = "mg/dL", modifier = Modifier.weight(1f))
                        RareStat(label = "Change", value = "+13", unit = "mg/dL", modifier = Modifier.weight(1f))
                        RareStat(label = "Per month", value = "+2.4", unit = "mg/dL", modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    LineChart(
                        points = series.points,
                        referenceLow = series.referenceLow,
                        referenceHigh = series.referenceHigh,
                        unit = series.unit,
                        decimals = series.decimals,
                        contentDescription = "LDL over time",
                    )
                }
                RareCard(inset = true) {
                    RareSectionLabel(text = "Every result")
                    RareKeyValue(label = "02 Oct 2026", value = "134 mg/dL")
                    RareKeyValue(label = "30 May 2026", value = "121 mg/dL")
                    RareKeyValue(label = "20 Feb 2026", value = "112 mg/dL")
                }
            }
        }
    }
}
