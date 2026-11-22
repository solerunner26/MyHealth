package app.myhealth.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthAvatar
import app.myhealth.core.designsystem.components.MyHealthDivider
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthListRow
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.model.FamilyMember
import app.myhealth.ui.common.label

/** Top-bar chip showing whose records are on screen; opens a picker to switch, add or edit members. */
@Composable
fun MemberSwitcher(
    members: List<FamilyMember>,
    active: FamilyMember?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    if (active == null) return
    var open by rememberSaveable { mutableStateOf(false) }
    val cd = stringResource(R.string.switcher_cd, active.name)
    Surface(
        onClick = { open = true },
        shape = MyHealthTheme.radii.pillShape,
        color = MyHealthTheme.colors.surfaceSunken,
        modifier = Modifier.heightIn(min = 48.dp).padding(end = MyHealthTheme.spacing.sm).semantics { contentDescription = cd }.testTag("memberSwitcher"),
    ) {
        Row(Modifier.padding(start = 4.dp, end = MyHealthTheme.spacing.md, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            MyHealthAvatar(active.initials, selected = true, size = 32.dp, photo = app.myhealth.ui.member.rememberMemberPhoto(active))
            Spacer(Modifier.width(MyHealthTheme.spacing.sm))
            Text(active.name, style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
            Icon(MyHealthIcons.Expand, null, tint = MyHealthTheme.colors.textSecondary)
        }
    }
    if (open) MemberPickerDialog(members, active, onSelect, onAdd, onEdit) { open = false }
}

/** List of family members with add/edit actions; used by the top-bar chip and the dashboard avatar. */
@Composable
fun MemberPickerDialog(members: List<FamilyMember>, active: FamilyMember, onSelect: (String) -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit, onDismiss: () -> Unit) {
    run {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text(stringResource(R.string.switcher_title), style = MyHealthTheme.typography.title) },
            text = {
                Column {
                    members.forEach { m ->
                        val selected = m.id == active.id
                        val selectedCd = stringResource(R.string.switcher_selected, m.name)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.RadioButton) { onSelect(m.id); onDismiss() }
                                .semantics { this.selected = selected; if (selected) contentDescription = selectedCd }.testTag("member_${m.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MyHealthAvatar(m.initials, selected, photo = app.myhealth.ui.member.rememberMemberPhoto(m))
                            Spacer(Modifier.width(MyHealthTheme.spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MyHealthTheme.typography.subtitle, color = MyHealthTheme.colors.textPrimary)
                                Text(label(m.relationship), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary)
                            }
                            if (selected) Icon(MyHealthIcons.Check, null, tint = MyHealthTheme.colors.accent)
                        }
                    }
                    MyHealthDivider(Modifier.padding(vertical = MyHealthTheme.spacing.sm))
                    MyHealthListRow(stringResource(R.string.switcher_edit), leadingIcon = MyHealthIcons.Edit, onClick = { onDismiss(); onEdit(active.id) })
                    MyHealthListRow(stringResource(R.string.switcher_add), leadingIcon = MyHealthIcons.Family, onClick = { onDismiss(); onAdd() }, modifier = Modifier.testTag("addMember"))
                }
            },
            confirmButton = { MyHealthTextButton(stringResource(R.string.action_close), { onDismiss() }) },
            containerColor = MyHealthTheme.colors.surfaceRaised,
            shape = MyHealthTheme.radii.xlShape,
        )
    }
}
