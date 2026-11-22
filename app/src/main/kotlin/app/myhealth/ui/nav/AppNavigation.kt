package app.myhealth.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import app.myhealth.domain.model.Relationship
import app.myhealth.ui.LocalSession
import app.myhealth.ui.common.rememberVm
import app.myhealth.ui.importer.ImportRoute
import app.myhealth.ui.member.MemberEditorScreen
import app.myhealth.ui.member.MemberEditorViewModel
import app.myhealth.ui.onboarding.WelcomeScreen
import app.myhealth.ui.records.RecordsList
import app.myhealth.ui.records.RecordsViewModel
import app.myhealth.ui.shell.AdaptiveNavScaffold
import app.myhealth.ui.shell.TopLevel
import app.myhealth.ui.shell.topLevelItems

object Routes {
    const val MAIN = "main"
    const val IMPORT = "import"
    const val IMPORT_MANUAL = "import/manual"
    const val MEMBER_NEW = "member/new"
    const val MEMBER_EDIT = "member/edit/{id}"
    const val REPORT = "report/{id}"
    fun memberEdit(id: String) = "member/edit/$id"
    fun report(id: String) = "report/$id"
}

/** Root: onboarding until a profile exists, then the navigation graph. */
@Composable
fun AppRoot() {
    val session = LocalSession.current
    val members by session.members.collectAsStateWithLifecycle()
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
        state.savedId?.let { id -> session.container.preferences.update { putBoolean("onboarded", true); putString("active_member", id) } }
        MemberEditorScreen(state, vm::onEvent, stringResource(R.string.onb_profile_title), onBack = { step = 0 },
            primaryLabel = stringResource(R.string.onb_finish), intro = stringResource(R.string.onb_profile_body), lockRelationship = true)
    }
}

@Composable
fun AppNavGraph(nav: NavHostController) {
    val session = LocalSession.current
    NavHost(nav, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainTabs(onImport = { nav.navigate(Routes.IMPORT) }, onOpenReport = { nav.navigate(Routes.report(it)) })
        }
        composable(Routes.IMPORT) {
            ImportRoute(onBack = { nav.popBackStack() }, onSaved = { id -> nav.popBackStack(); nav.navigate(Routes.report(id)) })
        }
        composable(Routes.IMPORT_MANUAL) {
            ImportRoute(onBack = { nav.popBackStack() }, onSaved = { id -> nav.popBackStack(); nav.navigate(Routes.report(id)) }, startManual = true)
        }
        composable(Routes.MEMBER_NEW) {
            val vm = rememberVm { MemberEditorViewModel(session, null, Relationship.Spouse, session.container.clock) }
            val state by vm.state.collectAsStateWithLifecycle()
            if (state.savedId != null) { session.select(state.savedId!!); nav.popBackStack() }
            MemberEditorScreen(state, vm::onEvent, stringResource(R.string.member_add_title), onBack = { nav.popBackStack() })
        }
        composable(Routes.MEMBER_EDIT) { entry ->
            val id = entry.arguments?.getString("id")
            val vm = rememberVm(id) { MemberEditorViewModel(session, id, Relationship.Other, session.container.clock) }
            val state by vm.state.collectAsStateWithLifecycle()
            if (state.savedId != null || state.deleted) nav.popBackStack()
            MemberEditorScreen(state, vm::onEvent, stringResource(R.string.member_edit_title), onBack = { nav.popBackStack() })
        }
        composable(Routes.REPORT) { entry ->
            // Full report detail arrives in Phase 6; until then show the member's report list.
            Column(Modifier.fillMaxSize().testTag("reportDetail")) {
                MyHealthTopBar(stringResource(R.string.nav_records), onBack = { nav.popBackStack() }, backLabel = stringResource(R.string.action_back))
                val vm = rememberVm { RecordsViewModel(session) }
                val state by vm.state.collectAsStateWithLifecycle()
                RecordsList(state, onOpen = {}, onAdd = { nav.navigate(Routes.IMPORT) })
            }
        }
    }
}

@Composable
private fun MainTabs(onImport: () -> Unit, onOpenReport: (String) -> Unit) {
    val session = LocalSession.current
    var tab by rememberSaveable { mutableStateOf(TopLevel.Home) }
    AdaptiveNavScaffold(tab, { tab = it }) {
        Column(Modifier.fillMaxSize().testTag("shell")) {
            MyHealthTopBar(title = topLevelItems()[tab.ordinal].label)
            when (tab) {
                TopLevel.Records -> {
                    val vm = rememberVm { RecordsViewModel(session) }
                    val state by vm.state.collectAsStateWithLifecycle()
                    RecordsList(state, onOpenReport, onImport)
                }
                else -> MyHealthEmptyState(MyHealthIcons.Document, stringResource(R.string.shell_placeholder_title), stringResource(R.string.shell_placeholder_body),
                    actionLabel = stringResource(R.string.import_title), onAction = onImport)
            }
        }
    }
}
