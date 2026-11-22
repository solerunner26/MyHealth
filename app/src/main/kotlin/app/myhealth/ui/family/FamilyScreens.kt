package app.myhealth.ui.family

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myhealth.R
import app.myhealth.core.Images
import app.myhealth.core.designsystem.components.Emphasis
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthChipRow
import app.myhealth.core.designsystem.components.MyHealthDateField
import app.myhealth.core.designsystem.components.MyHealthDialog
import app.myhealth.core.designsystem.components.MyHealthDisclaimer
import app.myhealth.core.designsystem.components.MyHealthEmptyState
import app.myhealth.core.designsystem.components.MyHealthIconButton
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthInfoBanner
import app.myhealth.core.designsystem.components.MyHealthListRow
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.components.MyHealthSecondaryButton
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.components.MyHealthStatusBadge
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.components.MyHealthTextField
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.components.StatusKind
import app.myhealth.core.designsystem.components.appear
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.model.HealthCard
import app.myhealth.domain.model.HealthCardKind
import app.myhealth.domain.model.MediaKind
import app.myhealth.domain.model.Vaccination
import app.myhealth.ui.LocalSession
import app.myhealth.ui.common.analyteName
import app.myhealth.ui.common.analyteValue
import app.myhealth.ui.common.focusText
import app.myhealth.ui.common.formatDate
import app.myhealth.ui.common.label
import app.myhealth.ui.common.rememberVm
import app.myhealth.ui.common.vitalValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Picks one image with the system photo picker (no storage permission) and hands back a metadata-free JPEG. */
@Composable
private fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { Images.readLimited(it, 25 * 1024 * 1024) } }.getOrNull()?.let { Images.documentPhoto(it) }
            }
            if (jpeg != null) onPicked(jpeg)
        }
    }
    return { runCatching { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
}

/** Decrypts and shows an image from the media store; tap opens it full width. */
@Composable
private fun MediaThumb(id: String, modifier: Modifier = Modifier) {
    val session = LocalSession.current
    var bmp by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    var big by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(id) { bmp = withContext(Dispatchers.IO) { session.stack.media.bytes(id)?.let { b -> runCatching { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() } } }
    val b = bmp
    Box(modifier.clip(MyHealthTheme.radii.mdShape).clickable(enabled = b != null) { big = true }) {
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
    if (big && b != null) androidx.compose.ui.window.Dialog(onDismissRequest = { big = false }) {
        Image(b, null, Modifier.fillMaxWidth().clip(MyHealthTheme.radii.lgShape).clickable { big = false }, contentScale = ContentScale.Fit)
    }
}

// ================================================================ vaccinations

@Composable
fun VaccinationsScreen(onBack: () -> Unit) {
    val session = LocalSession.current
    val vm = rememberVm { VaccinationsViewModel(session) }
    val state by vm.state.collectAsStateWithLifecycle()
    val sp = MyHealthTheme.spacing
    var editing by remember { mutableStateOf<Vaccination?>(null) }
    var certFor by remember { mutableStateOf<Vaccination?>(null) }
    val pick = rememberPhotoPicker { jpeg -> certFor?.let { vm.addCertificate(it, jpeg) } }
    Column(Modifier.fillMaxSize().testTag("vaccinations")) {
        MyHealthTopBar(stringResource(R.string.more_vaccines), subtitle = state.member?.name, onBack = onBack, backLabel = stringResource(R.string.action_back))
        if (state.loading) { MyHealthLoadingState(stringResource(R.string.state_loading)); return@Column }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                    MyHealthPrimaryButton(stringResource(R.string.vax_add), { editing = Vaccination(vm.newId(), state.member!!.id, "", createdAtEpochMs = vm.now()) },
                        Modifier.weight(1f).testTag("addVaccine"), icon = MyHealthIcons.Add)
                }
            }
            item {
                if (state.canSuggest) MyHealthSecondaryButton(stringResource(R.string.vax_suggest), vm::addSuggested, Modifier.fillMaxWidth().testTag("suggestVaccines"), icon = MyHealthIcons.Insights)
                else MyHealthInfoBanner(stringResource(R.string.vax_need_dob))
            }
            if (state.canSuggest) item { MyHealthInfoBanner(stringResource(R.string.vax_suggest_note)) }
            if (state.isEmpty) item { MyHealthEmptyState(MyHealthIcons.Medicines, stringResource(R.string.vax_empty_title), stringResource(R.string.vax_empty_body), Modifier.height(260.dp)) }
            fun section(title: Int, list: List<Vaccination>) {
                if (list.isEmpty()) return
                item { MyHealthSectionHeader(stringResource(title)) }
                items(list, key = { it.id }) { v -> VaccineCard(v, LocalDate.now(session.container.clock), { editing = v }, { vm.markGiven(v) }, { certFor = v; pick() }, Modifier.appear(0)) }
            }
            section(R.string.vax_due_section, state.due)
            section(R.string.vax_upcoming, state.upcoming)
            section(R.string.vax_given_section, state.given)
        }
    }
    editing?.let { v -> VaccineDialog(v, onSave = { vm.save(it); editing = null }, onDelete = { vm.delete(v); editing = null }, onDismiss = { editing = null }) }
}

@Composable
private fun VaccineCard(v: Vaccination, today: LocalDate, onEdit: () -> Unit, onGiven: () -> Unit, onCertificate: () -> Unit, modifier: Modifier = Modifier) {
    val session = LocalSession.current
    val media by remember(v.id) { session.stack.media.observe(v.id) }.collectAsStateWithLifecycle(emptyList())
    val overdue = v.isDue(today)
    MyHealthCard(modifier.testTag("vaccine_${v.id}"), onClick = onEdit, alert = overdue) {
        MyHealthListRow(listOf(v.name, v.dose).filter { it.isNotBlank() }.joinToString(" · "),
            subtitle = when {
                v.givenDate != null -> stringResource(R.string.vax_given_date, formatDate(v.givenDate!!))
                v.dueDate != null -> stringResource(R.string.vax_due_date, formatDate(v.dueDate!!))
                else -> null
            }, leadingIcon = MyHealthIcons.Medicines) {
            when {
                overdue -> MyHealthStatusBadge(stringResource(R.string.vax_overdue), StatusKind.High)
                v.given -> MyHealthStatusBadge(stringResource(R.string.vax_given_section), StatusKind.Normal)
            }
        }
        if (media.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm), modifier = Modifier.padding(top = 4.dp)) {
            media.take(3).forEach { MediaThumb(it.id, Modifier.size(72.dp)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!v.given) MyHealthTextButton(stringResource(R.string.vax_mark_given), onGiven, Modifier.testTag("given_${v.id}"), icon = MyHealthIcons.Check)
            MyHealthTextButton(stringResource(R.string.vax_add_certificate), onCertificate, icon = MyHealthIcons.Camera)
        }
    }
}

@Composable
private fun VaccineDialog(v: Vaccination, onSave: (Vaccination) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(v.name) }
    var dose by rememberSaveable { mutableStateOf(v.dose) }
    var given by rememberSaveable { mutableStateOf(v.given) }
    var date by remember { mutableStateOf(v.givenDate ?: v.dueDate) }
    val ok = stringResource(R.string.action_ok); val cancel = stringResource(R.string.action_cancel)
    MyHealthDialog(stringResource(R.string.vax_add), "", stringResource(R.string.action_save), {
        if (name.isNotBlank()) onSave(v.copy(name = name.trim().take(80), dose = dose.trim().take(40), givenDate = if (given) date ?: LocalDate.now() else null, dueDate = if (given) v.dueDate else date))
    }, if (v.name.isBlank()) cancel else stringResource(R.string.action_delete), if (v.name.isBlank()) onDismiss else onDelete, icon = MyHealthIcons.Medicines) {
        Column(verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm), modifier = Modifier.testTag("vaccineDialog")) {
            MyHealthTextField(name, { name = it }, stringResource(R.string.vax_name), Modifier.testTag("vaxName"))
            MyHealthTextField(dose, { dose = it }, stringResource(R.string.vax_dose))
            app.myhealth.core.designsystem.components.MyHealthSwitchRow(stringResource(R.string.vax_is_given), given, { given = it })
            MyHealthDateField(stringResource(if (given) R.string.vax_given_on else R.string.vax_due_on), date, { formatDate(it) }, "", { date = it }, ok, cancel,
                maxDate = if (given) LocalDate.now() else null)
        }
    }
}

// ================================================================ health cards

@Composable
fun HealthCardsScreen(onBack: () -> Unit) {
    val session = LocalSession.current
    val vm = rememberVm { HealthCardsViewModel(session) }
    val state by vm.state.collectAsStateWithLifecycle()
    val sp = MyHealthTheme.spacing
    var editing by remember { mutableStateOf<HealthCard?>(null) }
    var photoFor by remember { mutableStateOf<Pair<HealthCard, MediaKind>?>(null) }
    val pick = rememberPhotoPicker { jpeg -> photoFor?.let { (c, k) -> vm.addImage(c, k, jpeg) } }
    Column(Modifier.fillMaxSize().testTag("healthCards")) {
        MyHealthTopBar(stringResource(R.string.more_cards), subtitle = state.member?.name, onBack = onBack, backLabel = stringResource(R.string.action_back))
        if (state.loading) { MyHealthLoadingState(stringResource(R.string.state_loading)); return@Column }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
            item {
                MyHealthPrimaryButton(stringResource(R.string.cards_add), { editing = HealthCard(vm.newId(), state.member!!.id, HealthCardKind.Abha, holderName = state.member!!.name, createdAtEpochMs = vm.now()) },
                    Modifier.fillMaxWidth().testTag("addCard"), icon = MyHealthIcons.Add)
            }
            if (state.cards.isEmpty()) item { MyHealthEmptyState(MyHealthIcons.Lock, stringResource(R.string.cards_empty_title), stringResource(R.string.cards_empty_body), Modifier.height(280.dp)) }
            items(state.cards, key = { it.id }) { c -> CardItem(c, { editing = c }, { k -> photoFor = c to k; pick() }) }
        }
    }
    editing?.let { c -> CardDialog(c, isNew = state.cards.none { it.id == c.id }, onSave = { vm.save(it); editing = null }, onDelete = { vm.delete(c); editing = null }, onDismiss = { editing = null }) }
}

@Composable
private fun cardLabel(k: HealthCardKind) = stringResource(when (k) {
    HealthCardKind.Abha -> R.string.card_abha; HealthCardKind.PmJay -> R.string.card_pmjay; HealthCardKind.Cghs -> R.string.card_cghs
    HealthCardKind.Esic -> R.string.card_esic; HealthCardKind.StateScheme -> R.string.card_state; HealthCardKind.Insurance -> R.string.card_insurance
    HealthCardKind.Other -> R.string.card_other
})

@Composable
private fun CardItem(c: HealthCard, onEdit: () -> Unit, onPhoto: (MediaKind) -> Unit) {
    val session = LocalSession.current
    val media by remember(c.id) { session.stack.media.observe(c.id) }.collectAsStateWithLifecycle(emptyList())
    // The card itself is drawn as a hero card (like a real card in a wallet).
    MyHealthCard(Modifier.testTag("card_${c.id}"), onClick = onEdit, emphasis = Emphasis.Strong) {
        Text(c.title.ifBlank { cardLabel(c.kind) }.uppercase(), style = MyHealthTheme.typography.overline, color = MyHealthTheme.colors.textSecondary)
        Text(c.number.ifBlank { "—" }, style = MyHealthTheme.typography.title, color = MyHealthTheme.colors.textPrimary, modifier = Modifier.padding(top = 6.dp))
        Text(listOf(c.holderName, c.issuer, c.validUntil?.let { formatDate(it) }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
            style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary)
        Row(Modifier.padding(top = MyHealthTheme.spacing.md), horizontalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) {
            listOf(MediaKind.CardFront to R.string.card_front, MediaKind.CardBack to R.string.card_back).forEach { (k, l) ->
                val m = media.lastOrNull { it.kind == k }
                Column(Modifier.weight(1f)) {
                    if (m != null) MediaThumb(m.id, Modifier.fillMaxWidth().aspectRatio(1.586f))
                    else Box(Modifier.fillMaxWidth().aspectRatio(1.586f).clip(MyHealthTheme.radii.mdShape).clickable { onPhoto(k) }
                        .padding(8.dp), contentAlignment = Alignment.Center) {
                        Icon(MyHealthIcons.Camera, stringResource(R.string.card_add_photo), tint = MyHealthTheme.colors.accent)
                    }
                    MyHealthTextButton(stringResource(l), { onPhoto(k) }, Modifier.testTag("cardPhoto_${k.name}"))
                }
            }
        }
    }
}

@Composable
private fun CardDialog(c: HealthCard, isNew: Boolean, onSave: (HealthCard) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf(c.kind) }
    var number by rememberSaveable { mutableStateOf(c.number) }
    var holder by rememberSaveable { mutableStateOf(c.holderName) }
    var issuer by rememberSaveable { mutableStateOf(c.issuer) }
    var valid by remember { mutableStateOf(c.validUntil) }
    val ok = stringResource(R.string.action_ok); val cancel = stringResource(R.string.action_cancel)
    MyHealthDialog(stringResource(R.string.cards_add), "", stringResource(R.string.action_save), {
        onSave(c.copy(kind = kind, number = number.trim().take(40), holderName = holder.trim().take(80), issuer = issuer.trim().take(80), validUntil = valid))
    }, if (isNew) cancel else stringResource(R.string.card_delete), if (isNew) onDismiss else onDelete, icon = MyHealthIcons.Lock) {
        Column(verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm), modifier = Modifier.testTag("cardDialog")) {
            Text(stringResource(R.string.card_kind), style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textSecondary)
            MyHealthChipRow(HealthCardKind.entries, kind, { cardLabel(it) }, { kind = it })
            MyHealthTextField(number, { number = it }, stringResource(R.string.card_number), Modifier.testTag("cardNumber"))
            MyHealthTextField(holder, { holder = it }, stringResource(R.string.card_holder))
            MyHealthTextField(issuer, { issuer = it }, stringResource(R.string.card_issuer))
            MyHealthDateField(stringResource(R.string.card_valid), valid, { formatDate(it) }, "", { valid = it }, ok, cancel)
        }
    }
}

// ================================================================ family history

@Composable
fun FamilyHistoryScreen(onBack: () -> Unit) {
    val session = LocalSession.current
    val vm = rememberVm { FamilyHistoryViewModel(session) }
    val state by vm.state.collectAsStateWithLifecycle()
    val sp = MyHealthTheme.spacing
    var adding by rememberSaveable { mutableStateOf(false) }
    val relLabels = app.myhealth.domain.model.Relationship.entries.associateWith { label(it) }
    Column(Modifier.fillMaxSize().testTag("familyHistory")) {
        MyHealthTopBar(stringResource(R.string.more_family_history), onBack = onBack, backLabel = stringResource(R.string.action_back))
        if (state.loading) { MyHealthLoadingState(stringResource(R.string.state_loading)); return@Column }
        val rows = vm.rows(state) { relLabels.getValue(it.relationship) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
            item { MyHealthInfoBanner(stringResource(R.string.fam_intro)) }
            item { MyHealthPrimaryButton(stringResource(R.string.fam_add), { adding = true }, Modifier.fillMaxWidth().testTag("addRelative"), icon = MyHealthIcons.Add) }
            if (rows.isEmpty()) item { MyHealthEmptyState(MyHealthIcons.Family, stringResource(R.string.fam_empty_title), stringResource(R.string.fam_empty_body), Modifier.height(260.dp)) }
            items(rows, key = { it.condition }) { r ->
                MyHealthCard(Modifier.testTag("famRow"), emphasis = if (r.runsInFamily) Emphasis.Medium else Emphasis.Subtle) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.condition, style = MyHealthTheme.typography.subtitle, color = MyHealthTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                        if (r.runsInFamily) MyHealthStatusBadge(stringResource(R.string.fam_runs), StatusKind.Worsening)
                    }
                    Text(r.carriers.joinToString(", ") { c -> if (c.isMember) "${c.label} (${c.relation})" else c.label }, style = MyHealthTheme.typography.bodySmall,
                        color = MyHealthTheme.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (state.relatives.isNotEmpty()) {
                item { MyHealthSectionHeader(stringResource(R.string.fam_add)) }
                items(state.relatives, key = { it.id }) { rel ->
                    MyHealthCard {
                        MyHealthListRow(rel.condition, subtitle = listOfNotNull(rel.relation, rel.onsetAge?.let { stringResource(R.string.fam_onset_age, it) }).joinToString(" · "),
                            leadingIcon = MyHealthIcons.Family) { MyHealthIconButton(MyHealthIcons.Delete, stringResource(R.string.action_delete), { vm.delete(rel.id) }) }
                    }
                }
            }
            item { MyHealthDisclaimer(stringResource(R.string.disclaimer_short)) }
        }
    }
    if (adding) {
        var relation by rememberSaveable { mutableStateOf("") }
        var condition by rememberSaveable { mutableStateOf("") }
        var onset by rememberSaveable { mutableStateOf("") }
        MyHealthDialog(stringResource(R.string.fam_add), "", stringResource(R.string.action_save), {
            if (relation.isNotBlank() && condition.isNotBlank()) { vm.add(relation, condition, onset.toIntOrNull()?.takeIf { it in 0..120 }); adding = false }
        }, stringResource(R.string.action_cancel), { adding = false }, icon = MyHealthIcons.Family) {
            Column(verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) {
                MyHealthTextField(relation, { relation = it }, stringResource(R.string.fam_relation), Modifier.testTag("famRelation"))
                MyHealthTextField(condition, { condition = it }, stringResource(R.string.fam_condition), Modifier.testTag("famCondition"))
                MyHealthTextField(onset, { onset = it.filter(Char::isDigit).take(3) }, stringResource(R.string.fam_onset), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }
    }
}

// ================================================================ visit preparation

@Composable
fun VisitPrepScreen(onBack: () -> Unit) {
    val session = LocalSession.current
    val vm = rememberVm { VisitPrepViewModel(session) }
    val state by vm.state.collectAsStateWithLifecycle()
    val sp = MyHealthTheme.spacing
    val context = LocalContext.current
    var question by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().testTag("visitPrep")) {
        MyHealthTopBar(stringResource(R.string.more_visit), subtitle = state.member?.name, onBack = onBack, backLabel = stringResource(R.string.action_back))
        val p = state.prep
        if (state.loading || p == null) { MyHealthLoadingState(stringResource(R.string.state_loading)); return@Column }
        val shareText = visitText(state)
        LazyColumn(Modifier.fillMaxSize().testTag("visitList"), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
            item {
                MyHealthDateField(stringResource(R.string.visit_since), state.since, { formatDate(it) }, "", vm::setSince,
                    stringResource(R.string.action_ok), stringResource(R.string.action_cancel), maxDate = LocalDate.now(session.container.clock))
            }
            if (p.changedTests.isEmpty() && p.newVitals.isEmpty() && p.started.isEmpty() && p.stopped.isEmpty()) item { MyHealthInfoBanner(stringResource(R.string.visit_nothing)) }
            if (p.changedTests.isNotEmpty()) {
                item { MyHealthSectionHeader(stringResource(R.string.visit_tests)) }
                items(p.changedTests, key = { it.first.id }) { (now, before) ->
                    MyHealthCard {
                        MyHealthListRow(analyteName(now.analyteId), subtitle = listOf(
                            analyteValue(now.analyteId, now.value, now.comparator) + " · " + formatDate(now.date),
                            before?.let { stringResource(R.string.visit_was, analyteValue(it.analyteId, it.value, it.comparator)) } ?: stringResource(R.string.visit_first),
                        ).joinToString("\n"), leadingIcon = MyHealthIcons.Labs)
                    }
                }
            }
            if (p.newVitals.isNotEmpty()) {
                item { MyHealthSectionHeader(stringResource(R.string.dash_vitals)) }
                item {
                    MyHealthCard {
                        p.newVitals.forEach { v ->
                            MyHealthListRow(label(v.type), subtitle = vitalValue(v.type, v.value, v.value2, v.unit))
                        }
                    }
                }
            }
            if (p.alerts.isNotEmpty()) {
                item { MyHealthSectionHeader(stringResource(R.string.visit_alerts)) }
                items(p.alerts.take(6), key = { it.key }) { f -> app.myhealth.ui.insights.FocusCard(f, onClick = null) }
            }
            if (p.started.isNotEmpty() || p.stopped.isNotEmpty()) item {
                MyHealthCard {
                    if (p.started.isNotEmpty()) MyHealthListRow(stringResource(R.string.visit_meds_started), subtitle = p.started.joinToString(", ") { it.name }, leadingIcon = MyHealthIcons.Medicines)
                    if (p.stopped.isNotEmpty()) MyHealthListRow(stringResource(R.string.visit_meds_stopped), subtitle = p.stopped.joinToString(", ") { it.name }, leadingIcon = MyHealthIcons.Medicines)
                }
            }
            item { MyHealthSectionHeader(stringResource(R.string.visit_questions)) }
            items(p.questions, key = { it.id }) { q ->
                MyHealthCard {
                    MyHealthListRow(q.text, leadingIcon = MyHealthIcons.Help) {
                        MyHealthIconButton(MyHealthIcons.Check, stringResource(R.string.action_ok), { vm.toggle(q) })
                        MyHealthIconButton(MyHealthIcons.Delete, stringResource(R.string.action_delete), { vm.delete(q) })
                    }
                }
            }
            item {
                MyHealthTextField(question, { question = it.take(300) }, stringResource(R.string.visit_add_question), Modifier.fillMaxWidth().testTag("visitQuestion"),
                    supportingText = stringResource(R.string.visit_question_hint), singleLine = false)
                MyHealthTextButton(stringResource(R.string.visit_add_question), { vm.addQuestion(question); question = "" }, Modifier.testTag("addQuestion"), icon = MyHealthIcons.Add)
            }
            item {
                MyHealthSecondaryButton(stringResource(R.string.visit_share), {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, Modifier.fillMaxWidth(), icon = MyHealthIcons.Share)
            }
            item { Spacer(Modifier.height(sp.sm)); MyHealthDisclaimer(stringResource(R.string.disclaimer_short)) }
        }
    }
}

/** Plain-text version of the page, for the share sheet (the user chooses where it goes). */
@Composable
private fun visitText(state: VisitState): String {
    val p = state.prep ?: return ""
    val lines = mutableListOf(stringResource(R.string.visit_header, state.member?.name.orEmpty(), formatDate(p.since)))
    val tests = p.changedTests.map { (now, before) ->
        "• " + analyteName(now.analyteId) + ": " + analyteValue(now.analyteId, now.value, now.comparator) + " (" + formatDate(now.date) + ")" +
            (before?.let { ", " + stringResource(R.string.visit_was, analyteValue(it.analyteId, it.value, it.comparator)) } ?: "")
    }
    if (tests.isNotEmpty()) { lines += ""; lines += stringResource(R.string.visit_tests); lines += tests }
    val alerts = p.alerts.take(6).map { "• " + focusText(it).title }
    if (alerts.isNotEmpty()) { lines += ""; lines += stringResource(R.string.visit_alerts); lines += alerts }
    if (p.started.isNotEmpty()) { lines += ""; lines += stringResource(R.string.visit_meds_started) + ": " + p.started.joinToString(", ") { it.name } }
    if (p.stopped.isNotEmpty()) { lines += stringResource(R.string.visit_meds_stopped) + ": " + p.stopped.joinToString(", ") { it.name } }
    if (p.questions.isNotEmpty()) { lines += ""; lines += stringResource(R.string.visit_questions); lines += p.questions.map { "• " + it.text } }
    lines += ""; lines += stringResource(R.string.disclaimer_short)
    return lines.joinToString("\n")
}
