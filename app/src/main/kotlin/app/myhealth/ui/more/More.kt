package app.myhealth.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.myhealth.BuildInfo
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthAvatar
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthDialog
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthListRow
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.model.FamilyMember
import app.myhealth.ui.common.label

data class MoreActions(
    val insights: () -> Unit, val profile: () -> Unit, val editMember: (String) -> Unit, val addMember: () -> Unit, val summary: () -> Unit,
    val settings: () -> Unit, val backup: (() -> Unit)?, val healthConnect: (() -> Unit)?,
)

@Composable
fun MoreContent(members: List<FamilyMember>, a: MoreActions) {
    val sp = MyHealthTheme.spacing
    var about by rememberSaveable { mutableStateOf(false) }
    val version = BuildInfo.versionName(LocalContext.current)
    LazyColumn(Modifier.fillMaxSize().testTag("more"), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
        item { MyHealthCard { MyHealthListRow(stringResource(R.string.more_insights), subtitle = stringResource(R.string.more_insights_sub), leadingIcon = MyHealthIcons.Insights, onClick = a.insights) } }
        item { MyHealthCard { MyHealthListRow(stringResource(R.string.more_summary), subtitle = stringResource(R.string.more_summary_sub), leadingIcon = MyHealthIcons.Pdf, onClick = a.summary,
            modifier = Modifier.testTag("openSummary")) } }
        item { MyHealthSectionHeader(stringResource(R.string.more_family)) }
        items(members, key = { it.id }) { m ->
            MyHealthCard(onClick = { a.editMember(m.id) }) {
                MyHealthListRow(m.name, subtitle = label(m.relationship)) { MyHealthAvatar(m.initials, false) }
            }
        }
        item { MyHealthCard { MyHealthListRow(stringResource(R.string.switcher_add), leadingIcon = MyHealthIcons.Family, onClick = a.addMember) } }
        item { MyHealthSectionHeader(stringResource(R.string.more_settings)) }
        item { MyHealthCard { MyHealthListRow(stringResource(R.string.more_settings), subtitle = stringResource(R.string.more_settings_sub), leadingIcon = MyHealthIcons.Settings, onClick = a.settings,
            modifier = Modifier.testTag("openSettings")) } }
        a.backup?.let { open -> item { MyHealthCard { MyHealthListRow(stringResource(R.string.more_backup), subtitle = stringResource(R.string.more_backup_sub), leadingIcon = MyHealthIcons.Backup, onClick = open,
            modifier = Modifier.testTag("openBackup")) } } }
        a.healthConnect?.let { open -> item { MyHealthCard { MyHealthListRow(stringResource(R.string.more_health_connect), subtitle = stringResource(R.string.more_health_connect_sub), leadingIcon = MyHealthIcons.Activity, onClick = open,
            modifier = Modifier.testTag("openHealthConnect")) } } }
        item { MyHealthCard { MyHealthListRow(stringResource(R.string.more_about), subtitle = stringResource(R.string.about_version, version), leadingIcon = MyHealthIcons.Info, onClick = { about = true }) } }
    }
    if (about) MyHealthDialog(stringResource(R.string.more_about), stringResource(R.string.about_body), stringResource(R.string.action_ok), { about = false },
        stringResource(R.string.action_close), { about = false }, icon = MyHealthIcons.Privacy)
}
