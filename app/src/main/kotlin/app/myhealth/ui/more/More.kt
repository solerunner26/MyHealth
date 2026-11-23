package app.myhealth.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import app.myhealth.core.designsystem.components.MyHealthDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
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
    val vaccines: () -> Unit = {}, val cards: () -> Unit = {}, val family: () -> Unit = {}, val visitPrep: () -> Unit = {},
    val mediclaim: (() -> Unit)? = null,
)

/** One topic = one card; rows inside are separated by hairlines (short, scannable, no long flat list). */
@Composable
private fun Category(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column {
        androidx.compose.material3.Text(title.uppercase(), style = MyHealthTheme.typography.overline, color = MyHealthTheme.colors.textSecondary,
            modifier = Modifier.padding(start = MyHealthTheme.spacing.xs, top = MyHealthTheme.spacing.md, bottom = MyHealthTheme.spacing.xs))
        MyHealthCard(padding = MyHealthTheme.spacing.md, content = content)
    }
}

/** The Settings tab (was "More"): Profile first, then Family, then topics (item 5). */
@Composable
fun MoreContent(members: List<FamilyMember>, a: MoreActions, active: FamilyMember? = null) {
    val sp = MyHealthTheme.spacing
    var about by rememberSaveable { mutableStateOf(false) }
    val version = BuildInfo.versionName(LocalContext.current)
    LazyColumn(Modifier.fillMaxSize().testTag("more"), contentPadding = PaddingValues(start = sp.gutter, end = sp.gutter, bottom = sp.xl), verticalArrangement = Arrangement.spacedBy(sp.xs)) {
        item {
            Category(stringResource(R.string.cat_profile)) {
                val me = active ?: members.firstOrNull()
                MyHealthListRow(me?.name ?: stringResource(R.string.more_profile), subtitle = stringResource(R.string.more_profile_sub), onClick = a.profile) {
                    if (me != null) MyHealthAvatar(me.initials, true, photo = app.myhealth.ui.member.rememberMemberPhoto(me))
                }
            }
        }
        item {
            Category(stringResource(R.string.cat_family)) {
                members.filter { it.id != active?.id }.forEach { m ->
                    MyHealthListRow(m.name, subtitle = label(m.relationship), onClick = { a.editMember(m.id) }) { MyHealthAvatar(m.initials, false, photo = app.myhealth.ui.member.rememberMemberPhoto(m)) }
                    MyHealthDivider()
                }
                MyHealthListRow(stringResource(R.string.switcher_add), leadingIcon = MyHealthIcons.Add, onClick = a.addMember)
                MyHealthDivider()
                MyHealthListRow(stringResource(R.string.more_family_history), subtitle = stringResource(R.string.more_family_history_sub), leadingIcon = MyHealthIcons.Family, onClick = a.family, modifier = Modifier.testTag("openFamilyHistory"))
            }
        }
        item {
            Category(stringResource(R.string.cat_health)) {
                MyHealthListRow(stringResource(R.string.more_insights), subtitle = stringResource(R.string.more_insights_sub), leadingIcon = MyHealthIcons.Insights, onClick = a.insights)
                MyHealthDivider()
                MyHealthListRow(stringResource(R.string.more_visit), subtitle = stringResource(R.string.more_visit_sub), leadingIcon = MyHealthIcons.Document, onClick = a.visitPrep, modifier = Modifier.testTag("openVisitPrep"))
                MyHealthDivider()
                MyHealthListRow(stringResource(R.string.more_summary), subtitle = stringResource(R.string.more_summary_sub), leadingIcon = MyHealthIcons.Pdf, onClick = a.summary, modifier = Modifier.testTag("openSummary"))
                MyHealthDivider()
                MyHealthListRow(stringResource(R.string.more_vaccines), subtitle = stringResource(R.string.more_vaccines_sub), leadingIcon = MyHealthIcons.Medicines, onClick = a.vaccines, modifier = Modifier.testTag("openVaccines"))
            }
        }
        item {
            Category(stringResource(R.string.cat_insurance)) {
                a.mediclaim?.let { open ->
                    MyHealthListRow(stringResource(R.string.more_mediclaim), subtitle = stringResource(R.string.more_mediclaim_sub), leadingIcon = MyHealthIcons.Privacy, onClick = open, modifier = Modifier.testTag("openMediclaim"))
                    MyHealthDivider()
                }
                MyHealthListRow(stringResource(R.string.more_cards), subtitle = stringResource(R.string.more_cards_sub), leadingIcon = MyHealthIcons.Lock, onClick = a.cards, modifier = Modifier.testTag("openCards"))
            }
        }
        item {
            Category(stringResource(R.string.cat_data)) {
                a.backup?.let { open ->
                    MyHealthListRow(stringResource(R.string.more_backup), subtitle = stringResource(R.string.more_backup_sub), leadingIcon = MyHealthIcons.Backup, onClick = open, modifier = Modifier.testTag("openBackup"))
                    MyHealthDivider()
                }
                a.healthConnect?.let { open ->
                    MyHealthListRow(stringResource(R.string.more_health_connect), subtitle = stringResource(R.string.more_health_connect_sub), leadingIcon = MyHealthIcons.Activity, onClick = open, modifier = Modifier.testTag("openHealthConnect"))
                }
            }
        }
        item {
            Category(stringResource(R.string.cat_app)) {
                MyHealthListRow(stringResource(R.string.more_settings), subtitle = stringResource(R.string.more_settings_sub), leadingIcon = MyHealthIcons.Settings, onClick = a.settings, modifier = Modifier.testTag("openSettings"))
                MyHealthDivider()
                val ctx = LocalContext.current
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                MyHealthListRow(stringResource(R.string.upd_check_now), subtitle = stringResource(R.string.about_version_build, version, app.myhealth.core.Updater.installedVersionCode(ctx).toInt()),
                    leadingIcon = MyHealthIcons.Sync, onClick = { scope.launch { app.myhealth.ui.startup.UpdateController.check(ctx, manual = true) } }, modifier = Modifier.testTag("checkUpdates"))
                MyHealthDivider()
                MyHealthListRow(stringResource(R.string.more_about), subtitle = stringResource(R.string.about_version_build, version, app.myhealth.core.Updater.installedVersionCode(ctx).toInt()), leadingIcon = MyHealthIcons.Info, onClick = { about = true })
            }
        }
    }
    if (about) MyHealthDialog(stringResource(R.string.more_about), stringResource(R.string.about_body), stringResource(R.string.action_ok), { about = false },
        stringResource(R.string.action_close), { about = false }, icon = MyHealthIcons.Privacy)
}
