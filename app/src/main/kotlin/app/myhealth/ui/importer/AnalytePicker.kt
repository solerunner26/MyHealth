package app.myhealth.ui.importer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.components.MyHealthTextField
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.catalogue.AnalyteCatalogue
import app.myhealth.ui.common.analyteName

/** Searchable list of catalogue tests; matches localised names, English names and aliases. */
@Composable
fun AnalytePickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val names = AnalyteCatalogue.all.map { it to analyteName(it.id) }
    val q = query.trim().lowercase()
    val shown = if (q.isEmpty()) names else names.filter { (a, local) ->
        local.lowercase().contains(q) || a.name.lowercase().contains(q) || a.aliases.any { it.contains(q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.review_pick_analyte), style = MyHealthTheme.typography.title) },
        text = {
            Column {
                MyHealthTextField(query, { query = it }, stringResource(R.string.review_search_tests), leadingIcon = MyHealthIcons.Search)
                if (shown.isEmpty()) Text(stringResource(R.string.review_no_match), style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = MyHealthTheme.spacing.md))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.first.id }) { (a, local) ->
                        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onPick(a.id) }.padding(vertical = MyHealthTheme.spacing.sm)) {
                            Text(local, style = MyHealthTheme.typography.body, color = MyHealthTheme.colors.textPrimary)
                            Text(a.canonicalUnit, style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textTertiary)
                        }
                    }
                }
            }
        },
        confirmButton = { MyHealthTextButton(stringResource(R.string.action_cancel), onDismiss) },
        containerColor = MyHealthTheme.colors.surfaceRaised,
        shape = MyHealthTheme.radii.xlShape,
    )
}
