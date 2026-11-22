package app.myhealth.ui.nav

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import app.myhealth.core.designsystem.theme.MyHealthTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthEmptyState
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.domain.analytics.TrendCsv
import app.myhealth.domain.model.Relationship
import app.myhealth.ui.LocalSession
import app.myhealth.ui.common.Share
import app.myhealth.ui.common.analyteName
import app.myhealth.ui.common.label
import app.myhealth.ui.common.rememberVm
import app.myhealth.ui.home.HomeContent
import app.myhealth.ui.home.HomeViewModel
import app.myhealth.ui.importer.ImportRoute
import app.myhealth.ui.member.MemberEditorScreen
import app.myhealth.ui.member.MemberEditorViewModel
import app.myhealth.ui.onboarding.WelcomeScreen
import app.myhealth.ui.records.RecordsContent
import app.myhealth.ui.records.RecordsViewModel
import app.myhealth.ui.report.AttachmentViewerScreen
import app.myhealth.ui.report.AttachmentViewerViewModel
import app.myhealth.ui.report.ReportDetailScreen
import app.myhealth.ui.report.ReportDetailViewModel
import app.myhealth.ui.report.shareAttachment
import app.myhealth.ui.shell.AdaptiveNavScaffold
import app.myhealth.ui.shell.MemberSwitcher
import app.myhealth.ui.shell.TopLevel
import app.myhealth.ui.shell.topLevelItems
import app.myhealth.ui.trends.TrendDetailScreen
import app.myhealth.ui.trends.TrendDetailViewModel
import app.myhealth.ui.trends.TrendsContent
import app.myhealth.ui.trends.TrendsViewModel
import kotlinx.coroutines.launch

private const val TABS = "tabs" // animation inspector id, not UI copy

object Routes {
    const val MAIN = "main"
    const val IMPORT = "import"
    const val IMPORT_MANUAL = "import/manual"
    const val MEMBER_NEW = "member/new"
    const val MEMBER_EDIT = "member/edit/{id}"
    const val REPORT = "report/{id}"
    const val ATTACHMENT = "attachment/{reportId}/{id}"
    const val TREND = "trend/{id}"
    const val INSIGHTS = "insights"
    const val VITAL_NEW = "vital/new?type={type}"
    const val VITAL = "vital/{type}"
    const val MEDICINE_NEW = "medicine/new"
    const val MEDICINE = "medicine/{id}"
    const val TREATMENT_NEW = "treatment/new"
    const val TREATMENT = "treatment/{id}"
    const val SETTINGS = "settings"
    const val SUMMARY = "summary"
    const val BACKUP = "backup"
    const val HEALTH_CONNECT = "healthconnect"
    fun vitalNew(type: String? = null) = if (type == null) "vital/new" else "vital/new?type=$type"
    fun vital(type: String) = "vital/$type"
    fun medicine(id: String) = "medicine/$id"
    fun treatment(id: String) = "treatment/$id"
    fun memberEdit(id: String) = "member/edit/$id"
    fun report(id: String) = "report/$id"
    fun attachment(reportId: String, id: String) = "attachment/$reportId/$id"
    fun trend(id: String) = "trend/$id"
}

/** Root: onboarding until a profile exists, then the navigation graph. */
@Composable
fun AppRoot() {
    val session = LocalSession.current
    val members by session.members.collectAsStateWithLifecycle()
    CrashReportPrompt()
    when {
        members == null -> MyHealthLoadingState(stringResource(R.string.state_loading))
        members!!.isEmpty() -> OnboardingFlow()
        else -> AppNavGraph(rememberNavController())
    }
}

@Composable
private fun OnboardingFlow() {
    val session = LocalSession.current
    var step by rememberSaveable { mutableStateOf(0) }
    if (step == 0) WelcomeScreen { step = 1 }
    else {
        val vm = rememberVm("onboarding") { MemberEditorViewModel(session, null, Relationship.Self, session.container.clock) }
        val state by vm.state.collectAsStateWithLifecycle()
        LaunchedEffect(state.savedId) { state.savedId?.let { id -> session.container.preferences.update { putBoolean("onboarded", true); putString("active_member", id) } } }
        MemberEditorScreen(state, vm::onEvent, stringResource(R.string.onb_profile_title), onBack = { step = 0 },
            primaryLabel = stringResource(R.string.onb_finish), intro = stringResource(R.string.onb_profile_body), lockRelationship = true)
    }
}

@Composable
fun AppNavGraph(nav: NavHostController) {
    val session = LocalSession.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chooser = stringResource(R.string.share_chooser)
    val exportFailed = stringResource(R.string.export_failed)
    // Shared-axis page motion: a pushed screen fades in while rising 6 %, the old one fades back; reversed on pop.
    val reduce = MyHealthTheme.reduceMotion
    val enterMs = if (reduce) 0 else 360; val exitMs = if (reduce) 0 else 220
    val easing = MyHealthTheme.motion.emphasized
    NavHost(nav, startDestination = Routes.MAIN,
        enterTransition = { fadeIn(tween(enterMs, easing = easing)) + slideInVertically(tween(enterMs, easing = easing)) { it / 16 } },
        exitTransition = { fadeOut(tween(exitMs)) + scaleOut(tween(exitMs), targetScale = 0.97f) },
        popEnterTransition = { fadeIn(tween(enterMs, easing = easing)) + scaleIn(tween(enterMs, easing = easing), initialScale = 0.97f) },
        popExitTransition = { fadeOut(tween(exitMs)) + slideOutVertically(tween(exitMs)) { it / 16 } },
    ) {
        composable(Routes.MAIN) {
            MainTabs(nav)
        }
        composable(Routes.IMPORT) { ImportRoute(onBack = { nav.popBackStack() }, onSaved = { id -> nav.popBackStack(); nav.navigate(Routes.report(id)) }) }
        composable(Routes.IMPORT_MANUAL) {
            ImportRoute(onBack = { nav.popBackStack() }, onSaved = { id -> nav.popBackStack(); nav.navigate(Routes.report(id)) }, startManual = true)
        }
        composable(Routes.MEMBER_NEW) {
            val vm = rememberVm { MemberEditorViewModel(session, null, Relationship.Spouse, session.container.clock) }
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.savedId) { state.savedId?.let { session.select(it); nav.popBackStack() } }
            MemberEditorScreen(state, vm::onEvent, stringResource(R.string.member_add_title), onBack = { nav.popBackStack() })
        }
        composable(Routes.MEMBER_EDIT) { entry ->
            val id = entry.arguments?.getString("id")
            val vm = rememberVm(id) { MemberEditorViewModel(session, id, Relationship.Other, session.container.clock) }
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.savedId, state.deleted) { if (state.savedId != null || state.deleted) nav.popBackStack() }
            MemberEditorScreen(state, vm::onEvent, stringResource(R.string.member_edit_title), onBack = { nav.popBackStack() })
        }
        composable(Routes.REPORT) { entry ->
            val id = entry.arguments?.getString("id")!!
            val vm = rememberVm(id) { ReportDetailViewModel(session, id) }
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.deleted) { if (state.deleted) nav.popBackStack() }
            ReportDetailScreen(state, onBack = { nav.popBackStack() }, onView = { a -> nav.navigate(Routes.attachment(id, a.id)) },
                onShare = { a -> scope.launch { if (!shareAttachment(context, vm, a, chooser)) Toast.makeText(context, exportFailed, Toast.LENGTH_SHORT).show() } },
                onDelete = vm::delete, onOpenTrend = { nav.navigate(Routes.trend(it)) })
        }
        composable(Routes.ATTACHMENT) { entry ->
            val id = entry.arguments?.getString("id")!!
            val vm = rememberVm(id) { AttachmentViewerViewModel(session, id) }
            val state by vm.state.collectAsStateWithLifecycle()
            AttachmentViewerScreen(state, stringResource(R.string.report_attachments), onBack = { nav.popBackStack() })
        }
        composable(Routes.TREND) { entry ->
            val id = entry.arguments?.getString("id")!!
            val vm = rememberVm(id) { TrendDetailViewModel(session, id) }
            val state by vm.state.collectAsStateWithLifecycle()
            val header = listOf(R.string.csv_date, R.string.csv_test, R.string.csv_value, R.string.csv_unit, R.string.csv_low, R.string.csv_high, R.string.csv_status).map { stringResource(it) }
            val name = analyteName(id)
            val statusLabels = app.myhealth.domain.analytics.RangeStatus.entries.associateWith { label(it) }
            TrendDetailScreen(state, id, onBack = { nav.popBackStack() }, onOpenReport = { nav.navigate(Routes.report(it)) }, onExport = { t ->
                runCatching {
                    val csv = TrendCsv.history(listOf(t), { name }, { statusLabels.getValue(it) }, header)
                    Share.launch(context, Share.write(context, "$name.csv", csv.toByteArray(Charsets.UTF_8)), "text/csv", chooser)
                }.onFailure { Toast.makeText(context, exportFailed, Toast.LENGTH_SHORT).show() }
            })
        }
        composable(Routes.VITAL_NEW, arguments = listOf(androidx.navigation.navArgument("type") { nullable = true; defaultValue = null })) { entry ->
            val type = entry.arguments?.getString("type")?.let { t -> app.myhealth.domain.model.VitalType.entries.firstOrNull { it.name == t } }
            val vm = rememberVm { app.myhealth.ui.vitals.VitalEditorViewModel(session, session.container.clock, type) }
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.saved) { if (state.saved) nav.popBackStack() }
            app.myhealth.ui.vitals.VitalEditorScreen(state, vm::edit, vm::save, onBack = { nav.popBackStack() })
        }
        composable(Routes.VITAL) { entry ->
            val type = app.myhealth.domain.model.VitalType.valueOf(entry.arguments?.getString("type")!!)
            val vm = rememberVm { app.myhealth.ui.vitals.VitalsViewModel(session) }
            val state by vm.state.collectAsStateWithLifecycle()
            app.myhealth.ui.vitals.VitalDetailScreen(state, type, session.container.clock.zone, onBack = { nav.popBackStack() },
                onAdd = { nav.navigate(Routes.vitalNew(if (type == app.myhealth.domain.model.VitalType.Bmi) "Weight" else type.name)) }, onDelete = { vm.delete(it) })
        }
        composable(Routes.MEDICINE_NEW) { MedicineRoute(null) { nav.popBackStack() } }
        composable(Routes.MEDICINE) { entry -> MedicineRoute(entry.arguments?.getString("id")) { nav.popBackStack() } }
        composable(Routes.TREATMENT_NEW) { TreatmentRoute(null) { nav.popBackStack() } }
        composable(Routes.TREATMENT) { entry -> TreatmentRoute(entry.arguments?.getString("id")) { nav.popBackStack() } }
        composable(Routes.SETTINGS) { app.myhealth.ui.settings.SettingsScreen(onBack = { nav.popBackStack() }, onErased = {}) }
        composable(Routes.BACKUP) { app.myhealth.ui.backup.BackupScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.HEALTH_CONNECT) { app.myhealth.ui.backup.HealthConnectScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SUMMARY) { app.myhealth.ui.summary.SummaryScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.INSIGHTS) {
            app.myhealth.ui.insights.InsightsRoute(onBack = { nav.popBackStack() }, onOpenTrend = { nav.navigate(Routes.trend(it)) })
        }
    }
}

@Composable
private fun MainTabs(nav: NavHostController) {
    val reduce = MyHealthTheme.reduceMotion
    val easing = MyHealthTheme.motion.emphasized
    val session = LocalSession.current
    var tab by rememberSaveable { mutableStateOf(TopLevel.Home) }
    val members by session.members.collectAsStateWithLifecycle()
    val active by session.activeMember.collectAsStateWithLifecycle()
    val onImport = { nav.navigate(Routes.IMPORT) }
    AdaptiveNavScaffold(tab, { tab = it }) {
        Column(Modifier.fillMaxSize().testTag("shell")) {
            var picker by rememberSaveable { mutableStateOf(false) }
            if (tab != TopLevel.Home) MyHealthTopBar(title = topLevelItems()[tab.ordinal].label) {
                MemberSwitcher(members.orEmpty(), active, session::select, onAdd = { nav.navigate(Routes.MEMBER_NEW) }, onEdit = { nav.navigate(Routes.memberEdit(it)) })
            }
            if (picker) active?.let { a -> app.myhealth.ui.shell.MemberPickerDialog(members.orEmpty(), a, session::select, { nav.navigate(Routes.MEMBER_NEW) }, { nav.navigate(Routes.memberEdit(it)) }) { picker = false } }
            AnimatedContent(tab, transitionSpec = {
                // Tabs slide toward the side of the tapped tab and cross-fade.
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                if (reduce) (fadeIn(tween(0)) togetherWith fadeOut(tween(0)))
                else (fadeIn(tween(300, easing = easing)) + slideInHorizontally(tween(300, easing = easing)) { dir * it / 10 }) togetherWith
                    (fadeOut(tween(160)) + slideOutHorizontally(tween(200)) { -dir * it / 14 })
            }, label = TABS) { current ->
            when (current) {
                TopLevel.Home -> {
                    val vm = rememberVm { HomeViewModel(session) }
                    val state by vm.state.collectAsStateWithLifecycle()
                    val photo = app.myhealth.ui.member.rememberMemberPhoto(active)
                    HomeContent(state, onAddDocument = onImport, onVisitPrep = { nav.navigate(Routes.SUMMARY) }, onLogVital = { nav.navigate(Routes.vitalNew()) }, onAddMedicine = { nav.navigate(Routes.MEDICINE_NEW) },
                        onOpenTrend = { nav.navigate(Routes.trend(it)) }, onOpenInsights = { nav.navigate(Routes.INSIGHTS) },
                        onOpenVital = { nav.navigate(Routes.vital(it.name)) }, onAllTests = { tab = TopLevel.Records },
                        header = { active?.let { a -> app.myhealth.ui.home.DashboardHeader(a, photo, members.orEmpty(), session::select) { picker = true } } })
                }
                TopLevel.Records -> {
                    val vm = rememberVm { RecordsViewModel(session) }
                    val state by vm.state.collectAsStateWithLifecycle()
                    RecordsContent(state, { nav.navigate(Routes.report(it)) }, { nav.navigate(Routes.trend(it)) }, onImport)
                }
                TopLevel.Trends -> {
                    val vm = rememberVm { TrendsViewModel(session) }
                    val state by vm.state.collectAsStateWithLifecycle()
                    val vvm = rememberVm { app.myhealth.ui.vitals.VitalsViewModel(session) }
                    val vitals by vvm.state.collectAsStateWithLifecycle()
                    TrendsContent(state, { nav.navigate(Routes.trend(it)) }, onImport) {
                        app.myhealth.ui.vitals.VitalsSection(vitals, onAdd = { nav.navigate(Routes.vitalNew()) }, onOpen = { nav.navigate(Routes.vital(it.name)) }, zone = session.container.clock.zone)
                    }
                }
                TopLevel.Care -> {
                    val vm = rememberVm { app.myhealth.ui.care.CareViewModel(session) }
                    val state by vm.state.collectAsStateWithLifecycle()
                    app.myhealth.ui.care.CareContent(state, session.container.clock, onAddMedicine = { nav.navigate(Routes.MEDICINE_NEW) }, onEditMedicine = { nav.navigate(Routes.medicine(it)) },
                        onAddTreatment = { nav.navigate(Routes.TREATMENT_NEW) }, onEditTreatment = { nav.navigate(Routes.treatment(it)) })
                }
                TopLevel.More -> app.myhealth.ui.more.MoreContent(members.orEmpty(), app.myhealth.ui.more.MoreActions(
                    insights = { nav.navigate(Routes.INSIGHTS) }, profile = { active?.let { nav.navigate(Routes.memberEdit(it.id)) } },
                    editMember = { nav.navigate(Routes.memberEdit(it)) }, addMember = { nav.navigate(Routes.MEMBER_NEW) }, summary = { nav.navigate(Routes.SUMMARY) },
                    settings = { nav.navigate(Routes.SETTINGS) }, backup = { nav.navigate(Routes.BACKUP) }, healthConnect = { nav.navigate(Routes.HEALTH_CONNECT) },
                ))
            }
            }
        }
    }
}

@Composable
private fun MedicineRoute(id: String?, onDone: () -> Unit) {
    val session = LocalSession.current
    val vm = rememberVm(id ?: "new") { app.myhealth.ui.care.MedicineEditorViewModel(session, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    app.myhealth.ui.care.MedicineEditorScreen(state, vm::edit, { vm.save(it) }, vm::delete, onDone)
}

@Composable
private fun TreatmentRoute(id: String?, onDone: () -> Unit) {
    val session = LocalSession.current
    val vm = rememberVm(id ?: "new") { app.myhealth.ui.care.TreatmentEditorViewModel(session, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    app.myhealth.ui.care.TreatmentEditorScreen(state, vm::edit, vm::save, vm::delete, onDone)
}

/** After an unexpected close, offer to share the locally saved error report (nothing is uploaded). */
@Composable
private fun CrashReportPrompt() {
    val session = LocalSession.current
    val context = LocalContext.current
    val log = session.container.crashLog
    var show by rememberSaveable { mutableStateOf(log.hasReport()) }
    if (!show) return
    val chooser = stringResource(R.string.crash_chooser_title)
    app.myhealth.core.designsystem.components.MyHealthDialog(
        title = stringResource(R.string.crash_title), text = stringResource(R.string.crash_body),
        confirmLabel = stringResource(R.string.crash_share),
        onConfirm = { show = false; runCatching { Share.launch(context, Share.write(context, "myhealth-error.txt", log.file.readBytes()), "text/plain", chooser) } },
        dismissLabel = stringResource(R.string.crash_dismiss), onDismiss = { show = false; log.clear() }, icon = MyHealthIcons.Bug,
    )
}
