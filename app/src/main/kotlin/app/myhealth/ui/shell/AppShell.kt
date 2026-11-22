package app.myhealth.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthBottomBar
import app.myhealth.core.designsystem.components.MyHealthEmptyState
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthNavRail
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.components.NavItem
import app.myhealth.core.designsystem.theme.MyHealthTheme

enum class TopLevel { Home, Records, Trends, Care, More }

/** Width class without an extra dependency: < 600dp is compact (bottom bar), otherwise a rail. */
const val COMPACT_MAX_WIDTH_DP = 600

@Composable
fun topLevelItems(): List<NavItem> = listOf(
    NavItem(TopLevel.Home.name, stringResource(R.string.nav_home), MyHealthIcons.Home),
    NavItem(TopLevel.Records.name, stringResource(R.string.nav_records), MyHealthIcons.Records),
    NavItem(TopLevel.Trends.name, stringResource(R.string.nav_trends), MyHealthIcons.Trends),
    NavItem(TopLevel.Care.name, stringResource(R.string.nav_care), MyHealthIcons.Medicines),
    NavItem(TopLevel.More.name, stringResource(R.string.nav_more), MyHealthIcons.Settings),
)

@Composable
fun AdaptiveNavScaffold(selected: TopLevel, onSelect: (TopLevel) -> Unit, content: @Composable () -> Unit) {
    val items = topLevelItems()
    val bg = Modifier.fillMaxSize()
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    if (maxWidth.value < COMPACT_MAX_WIDTH_DP) {
        Column(bg) {
            Box(Modifier.weight(1f)) { content() }
            MyHealthBottomBar(items, selected.name, { onSelect(TopLevel.valueOf(it)) }, Modifier.testTag("bottomBar"))
        }
    } else {
        Row(bg) {
            MyHealthNavRail(items, selected.name, { onSelect(TopLevel.valueOf(it)) }, Modifier.testTag("navRail"))
            Box(Modifier.weight(1f).navigationBarsPadding()) { content() }
        }
    }
    }
}

