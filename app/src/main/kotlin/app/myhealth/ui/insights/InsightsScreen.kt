package app.myhealth.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthDisclaimer
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.analytics.FocusItem
import app.myhealth.ui.LocalSession
import app.myhealth.ui.common.rememberVm
import app.myhealth.ui.home.HomeViewModel

/** All focus items (Phase 8 adds evidence drill-down and the score breakdown). */
@Composable
fun InsightsRoute(onBack: () -> Unit, onOpenTrend: (String) -> Unit) {
    val session = LocalSession.current
    val vm = rememberVm { HomeViewModel(session) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MyHealthTheme.colors.background).testTag("insights")) {
        MyHealthTopBar(stringResource(R.string.home_focus_title), onBack = onBack, backLabel = stringResource(R.string.action_back))
        if (state.loading) { MyHealthLoadingState(stringResource(R.string.state_loading)); return@Column }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(MyHealthTheme.spacing.gutter), verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.md)) {
            items(state.focus, key = { it.key }) { f ->
                val analyte = when (f) { is FocusItem.OutOfRange -> f.analyteId; is FocusItem.RapidChange -> f.analyteId; is FocusItem.Overdue -> f.analyteId; else -> null }
                FocusCard(f, onClick = analyte?.let { a -> { onOpenTrend(a) } })
            }
            item { MyHealthDisclaimer(stringResource(R.string.disclaimer_short)) }
        }
    }
}
