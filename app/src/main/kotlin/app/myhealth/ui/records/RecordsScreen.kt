package app.myhealth.ui.records

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthEmptyState
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthListRow
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.model.Report
import app.myhealth.ui.Session
import app.myhealth.ui.common.formatDate
import app.myhealth.ui.common.label
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RecordsState(val loading: Boolean = true, val reports: List<Report> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(session: Session) : ViewModel() {
    val state: StateFlow<RecordsState> = session.activeMember.filterNotNull()
        .flatMapLatest { m -> session.stack.records.observeReports(m.id) }
        .map { RecordsState(false, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsState())
}

@Composable
fun RecordsList(state: RecordsState, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    when {
        state.loading -> MyHealthLoadingState(stringResource(R.string.state_loading))
        state.reports.isEmpty() -> MyHealthEmptyState(MyHealthIcons.Records, stringResource(R.string.shell_placeholder_title), stringResource(R.string.shell_placeholder_body),
            actionLabel = stringResource(R.string.import_title), onAction = onAdd)
        else -> LazyColumn(Modifier.fillMaxSize().testTag("recordsList"), contentPadding = PaddingValues(MyHealthTheme.spacing.gutter), verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) {
            items(state.reports, key = { it.id }) { r ->
                MyHealthCard(onClick = { onOpen(r.id) }) {
                    val base = stringResource(R.string.join_dot, label(r.type), formatDate(r.reportDate))
                    MyHealthListRow(r.title, subtitle = r.labName?.let { stringResource(R.string.join_dot, base, it) } ?: base, leadingIcon = MyHealthIcons.Document)
                }
            }
        }
    }
}
