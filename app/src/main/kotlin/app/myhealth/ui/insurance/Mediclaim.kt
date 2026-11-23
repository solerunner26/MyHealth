package app.myhealth.ui.insurance

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myhealth.R
import app.myhealth.core.Images
import app.myhealth.core.designsystem.components.Emphasis
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthChipRow
import app.myhealth.core.designsystem.components.MyHealthDateField
import app.myhealth.core.designsystem.components.MyHealthDialog
import app.myhealth.core.designsystem.components.MyHealthDivider
import app.myhealth.core.designsystem.components.MyHealthEmptyState
import app.myhealth.core.designsystem.components.MyHealthIconButton
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthInfoBanner
import app.myhealth.core.designsystem.components.MyHealthListRow
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.components.MyHealthProgress
import app.myhealth.core.designsystem.components.MyHealthSecondaryButton
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.components.MyHealthStatusBadge
import app.myhealth.core.designsystem.components.MyHealthSwitchRow
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.components.MyHealthTextField
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.components.StatusKind
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.insurance.HospitalListParser
import app.myhealth.domain.insurance.Mediclaims
import app.myhealth.domain.model.ClaimKind
import app.myhealth.domain.model.ClaimStatus
import app.myhealth.domain.model.Media
import app.myhealth.domain.model.MediaKind
import app.myhealth.domain.model.MediclaimClaim
import app.myhealth.domain.model.MediclaimPolicy
import app.myhealth.domain.model.NetworkHospital
import app.myhealth.domain.model.PolicyFeatures
import app.myhealth.domain.model.PolicyType
import app.myhealth.domain.parse.Numbers
import app.myhealth.domain.util.UuidIds
import app.myhealth.ingest.AndroidTextExtractor
import app.myhealth.ingest.DocumentSniffer
import app.myhealth.ingest.PickedDocument
import app.myhealth.reminders.MediclaimReminders
import app.myhealth.ui.LocalSession
import app.myhealth.ui.common.Share
import app.myhealth.ui.common.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Indian grouping: ₹5,00,000. */
fun rupees(v: Double): String = "₹" + java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN")).apply { maximumFractionDigits = 0 }.format(v)

@Composable
private fun typeLabel(t: PolicyType) = stringResource(when (t) {
    PolicyType.Individual -> R.string.mc_type_individual; PolicyType.FamilyFloater -> R.string.mc_type_floater; PolicyType.TopUp -> R.string.mc_type_topup
    PolicyType.SuperTopUp -> R.string.mc_type_supertopup; PolicyType.Group -> R.string.mc_type_group; PolicyType.Government -> R.string.mc_type_government
    PolicyType.CriticalIllness -> R.string.mc_type_critical
})

@Composable
private fun statusLabel(s: ClaimStatus) = stringResource(when (s) {
    ClaimStatus.Intimated -> R.string.mc_st_intimated; ClaimStatus.Submitted -> R.string.mc_st_submitted; ClaimStatus.Approved -> R.string.mc_st_approved
    ClaimStatus.PartiallyApproved -> R.string.mc_st_partial; ClaimStatus.Rejected -> R.string.mc_st_rejected; ClaimStatus.Settled -> R.string.mc_st_settled
})

private fun dial(context: android.content.Context, phone: String) {
    val n = phone.split(',', '/').first().filter { it.isDigit() || it == '+' }
    if (n.length >= 6) runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun map(context: android.content.Context, query: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun reschedule(session: app.myhealth.ui.Session) = session.container.appScope.launch(Dispatchers.IO) {
    runCatching { MediclaimReminders(session.container.context, session.container.clock).rescheduleAll(session.stack) }
}

// ================================================================ list

@Composable
fun MediclaimListScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val session = LocalSession.current
    val active by session.activeMember.collectAsStateWithLifecycle()
    val policies by remember(active?.id) { active?.let { session.stack.mediclaims.observe(it.id) } ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())
    val today = LocalDate.now(session.container.clock)
    val sp = MyHealthTheme.spacing
    Column(Modifier.fillMaxSize().testTag("mediclaim")) {
        MyHealthTopBar(stringResource(R.string.mc_title), subtitle = active?.name, onBack = onBack, backLabel = stringResource(R.string.action_back))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
            item { MyHealthPrimaryButton(stringResource(R.string.mc_add), onAdd, Modifier.fillMaxWidth().testTag("addPolicy"), icon = MyHealthIcons.Add) }
            if (policies.isEmpty()) item { MyHealthEmptyState(MyHealthIcons.Privacy, stringResource(R.string.mc_empty_title), stringResource(R.string.mc_empty_body), Modifier.height(280.dp)) }
            items(policies, key = { it.id }) { p ->
                val claims by remember(p.id) { session.stack.mediclaims.observeClaims(p.id) }.collectAsStateWithLifecycle(emptyList())
                PolicyHero(p, claims, today, { onOpen(p.id) })
            }
        }
    }
}

/** The policy as a hero card: insurer, number, available cover with a bar, renewal state. */
@Composable
private fun PolicyHero(p: MediclaimPolicy, claims: List<MediclaimClaim>, today: LocalDate, onClick: (() -> Unit)?) {
    val used = Mediclaims.used(p, claims); val avail = Mediclaims.available(p, claims)
    val expiry = Mediclaims.expiry(p, today)
    MyHealthCard(Modifier.testTag("policy_${p.id}"), onClick = onClick, emphasis = Emphasis.Strong) {
        Text(listOf(p.insurer, p.planName).filter { it.isNotBlank() }.joinToString(" · ").uppercase(), style = MyHealthTheme.typography.overline, color = MyHealthTheme.colors.textSecondary)
        if (p.policyNumber.isNotBlank()) Text(p.policyNumber, style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textSecondary)
        Text(stringResource(R.string.mc_available), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
        Text(rupees(avail), style = MyHealthTheme.typography.metric, color = MyHealthTheme.colors.textPrimary)
        if (p.totalCover > 0) {
            LinearProgressIndicator({ (avail / p.totalCover).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(vertical = 6.dp),
                color = MyHealthTheme.colors.accent, trackColor = MyHealthTheme.colors.surfaceSunken)
            Text(stringResource(R.string.mc_used_of, rupees(used), rupees(p.totalCover)), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(typeLabel(p.type), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary, modifier = Modifier.weight(1f))
            val end = p.endDate
            when (expiry) {
                Mediclaims.Expiry.Expired -> MyHealthStatusBadge(stringResource(R.string.mc_expired, formatDate(end!!)), StatusKind.High)
                Mediclaims.Expiry.Soon -> MyHealthStatusBadge(stringResource(R.string.mc_expires_soon, p.daysToExpiry(today)!!.toInt()), StatusKind.High)
                Mediclaims.Expiry.Ok -> MyHealthStatusBadge(stringResource(R.string.mc_renews, formatDate(end!!)), StatusKind.Normal)
                Mediclaims.Expiry.Unknown -> Unit
            }
        }
    }
}

// ================================================================ detail

@Composable
fun PolicyDetailScreen(id: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val session = LocalSession.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = session.stack.mediclaims
    val policy by remember(id) { repo.observePolicy(id) }.collectAsStateWithLifecycle(null)
    val claims by remember(id) { repo.observeClaims(id) }.collectAsStateWithLifecycle(emptyList())
    val hospitals by remember(id) { repo.observeHospitals(id) }.collectAsStateWithLifecycle(emptyList())
    val docs by remember(id) { session.stack.media.observe(id) }.collectAsStateWithLifecycle(emptyList())
    val members by session.members.collectAsStateWithLifecycle()
    val today = LocalDate.now(session.container.clock)
    val sp = MyHealthTheme.spacing
    var editingClaim by remember { mutableStateOf<MediclaimClaim?>(null) }
    var openHospital by remember { mutableStateOf<NetworkHospital?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var shown by rememberSaveable { mutableStateOf(30) }
    val addDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val p = policy ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        reading = true
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val raw = context.contentResolver.openInputStream(uri)!!.use { Images.readLimited(it, 25 * 1024 * 1024) } ?: return@runCatching
                    val kind = DocumentSniffer.kind(raw) ?: return@runCatching
                    val isPdf = kind == app.myhealth.domain.model.AttachmentKind.Pdf
                    val stored = if (isPdf) raw else Images.documentPhoto(raw, 2400) ?: return@runCatching
                    session.stack.media.add(Media(UuidIds.next(), p.memberId, p.id, if (isPdf) MediaKind.PolicyPdf else MediaKind.PolicyImage, session.container.clock.millis()), stored)
                    // read the network hospital list from the document (offline: PDF text layer, or OCR for photos)
                    val text = AndroidTextExtractor(context.applicationContext, maxPages = 300).extract(PickedDocument(raw, "policy", kind))
                    val have = hospitals.map { it.name.lowercase() + it.pincode }.toSet()
                    val found = HospitalListParser.parse(text.pages.flatMap { it.lines() })
                        .filter { (it.name.lowercase() + it.pincode) !in have }
                        .map { NetworkHospital(UuidIds.next(), p.id, it.name, it.address, it.city, it.pincode, it.phone) }
                    if (found.isNotEmpty()) repo.addHospitals(found)
                }
            }
            reading = false
        }
    }
    Column(Modifier.fillMaxSize().testTag("policyDetail")) {
        val p = policy
        MyHealthTopBar(p?.insurer ?: stringResource(R.string.mc_title), subtitle = p?.planName?.takeIf { it.isNotBlank() }, onBack = onBack, backLabel = stringResource(R.string.action_back)) {
            MyHealthIconButton(MyHealthIcons.Edit, stringResource(R.string.mc_edit), onEdit)
        }
        if (p == null) return@Column
        val filtered = hospitals.filter { h -> query.isBlank() || listOf(h.name, h.city, h.pincode, h.address).any { it.contains(query.trim(), ignoreCase = true) } }
        LazyColumn(Modifier.fillMaxSize().testTag("policyList"), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
            item { PolicyHero(p, claims, today, null) }
            item {
                MyHealthCard {
                    val names = members.orEmpty().filter { it.id in p.coveredMemberIds || it.id == p.memberId }.joinToString(", ") { it.name }
                    MyHealthListRow(stringResource(R.string.mc_covered), subtitle = names.ifBlank { "—" }, leadingIcon = MyHealthIcons.Family)
                    MyHealthDivider()
                    MyHealthListRow(listOfNotNull(p.startDate?.let { formatDate(it) }, p.endDate?.let { formatDate(it) }).joinToString(" → ").ifBlank { "—" },
                        subtitle = p.premium?.let { stringResource(R.string.mc_premium) + ": " + rupees(it) }, leadingIcon = MyHealthIcons.Date)
                }
            }
            val contacts = listOf(R.string.mc_tpa to (p.tpaName to p.tpaPhone), R.string.mc_helpline to ("" to p.helpline), R.string.mc_agent to (p.agentName to p.agentPhone))
                .filter { (_, v) -> v.first.isNotBlank() || v.second.isNotBlank() }
            if (contacts.isNotEmpty()) {
                item { MyHealthSectionHeader(stringResource(R.string.mc_contacts)) }
                item {
                    MyHealthCard {
                        contacts.forEachIndexed { i, (label, v) ->
                            if (i > 0) MyHealthDivider()
                            MyHealthListRow(stringResource(label), subtitle = listOf(v.first, v.second).filter { it.isNotBlank() }.joinToString(" · ")) {
                                if (v.second.isNotBlank()) MyHealthTextButton(stringResource(R.string.mc_call), { dial(context, v.second) })
                            }
                        }
                    }
                }
            }
            val f = p.features
            val featureRows = buildList {
                if (f.roomRent.isNotBlank()) add(R.string.mc_room_rent to f.roomRent)
                f.coPayPercent?.let { add(R.string.mc_copay to "$it%") }
                f.deductible?.let { add(R.string.mc_deductible to rupees(it)) }
                f.initialWaitingDays?.let { add(R.string.mc_initial_wait to it.toString()) }
                f.pedWaitingMonths?.let { add(R.string.mc_ped_wait to it.toString()) }
                f.specificWaitingMonths?.let { add(R.string.mc_specific_wait to it.toString()) }
                f.preHospitalisationDays?.let { add(R.string.mc_pre to it.toString()) }
                f.postHospitalisationDays?.let { add(R.string.mc_post to it.toString()) }
                f.noClaimBonusPercent?.let { add(R.string.mc_ncb to "$it%") }
                f.ambulanceLimit?.let { add(R.string.mc_ambulance to rupees(it)) }
                listOf(f.dayCare to R.string.mc_daycare, f.restoration to R.string.mc_restoration, f.ayush to R.string.mc_ayush, f.maternity to R.string.mc_maternity,
                    f.opd to R.string.mc_opd, f.healthCheckup to R.string.mc_checkup, f.domiciliary to R.string.mc_domiciliary, f.organDonor to R.string.mc_organ)
                    .filter { it.first }.forEach { add(it.second to "✓") }
                if (f.other.isNotBlank()) add(R.string.mc_other to f.other)
            }
            if (featureRows.isNotEmpty()) {
                item { MyHealthSectionHeader(stringResource(R.string.mc_features)) }
                item {
                    MyHealthCard(Modifier.testTag("features")) {
                        featureRows.forEachIndexed { i, (label, v) ->
                            if (i > 0) MyHealthDivider()
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(stringResource(label), style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                                Text(v, style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textPrimary, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            }
            item { MyHealthSectionHeader(stringResource(R.string.mc_claims)) { MyHealthTextButton(stringResource(R.string.mc_add_claim), {
                editingClaim = MediclaimClaim(UuidIds.next(), p.id, p.memberId, admittedOn = today, createdAtEpochMs = session.container.clock.millis())
            }, Modifier.testTag("addClaim"), icon = MyHealthIcons.Add) } }
            item { MyHealthInfoBanner(stringResource(R.string.mc_claim_tips)) }
            if (claims.isEmpty()) item { Text(stringResource(R.string.mc_no_claims), style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary) }
            items(claims, key = { it.id }) { c ->
                MyHealthCard(onClick = { editingClaim = c }) {
                    val who = members.orEmpty().firstOrNull { it.id == c.memberId }?.name.orEmpty()
                    MyHealthListRow(c.hospital.ifBlank { "—" }, subtitle = listOfNotNull(c.admittedOn?.let { formatDate(it) }, who.takeIf { it.isNotBlank() },
                        rupees(c.approvedAmount ?: c.claimedAmount)).joinToString(" · ")) {
                        MyHealthStatusBadge(statusLabel(c.status), if (c.status == ClaimStatus.Rejected) StatusKind.High else StatusKind.Normal)
                    }
                }
            }
            item { MyHealthSectionHeader(stringResource(R.string.mc_documents)) { MyHealthTextButton(stringResource(R.string.mc_add_document), {
                runCatching { addDoc.launch(arrayOf("application/pdf", "image/*")) }
            }, Modifier.testTag("addPolicyDoc"), icon = MyHealthIcons.Add) } }
            if (reading) item { MyHealthProgress(stringResource(R.string.mc_reading), null) }
            items(docs, key = { it.id }) { d ->
                MyHealthCard {
                    MyHealthListRow(formatDate(java.time.Instant.ofEpochMilli(d.createdAtEpochMs).atZone(session.container.clock.zone).toLocalDate()),
                        leadingIcon = if (d.kind == MediaKind.PolicyPdf) MyHealthIcons.Pdf else MyHealthIcons.Camera) {
                        MyHealthTextButton(stringResource(R.string.mc_open), {
                            scope.launch {
                                val bytes = withContext(Dispatchers.IO) { session.stack.media.bytes(d.id) } ?: return@launch
                                val pdf = d.kind == MediaKind.PolicyPdf
                                val file = withContext(Dispatchers.IO) { Share.write(context, if (pdf) "policy.pdf" else "policy.jpg", bytes) }
                                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, if (pdf) "application/pdf" else "image/jpeg")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        })
                        MyHealthIconButton(MyHealthIcons.Delete, stringResource(R.string.action_delete), { scope.launch(Dispatchers.IO) { session.stack.media.delete(d.id) } })
                    }
                }
            }
            item {
                MyHealthSectionHeader(stringResource(R.string.mc_hospitals))
                if (hospitals.isEmpty()) MyHealthInfoBanner(stringResource(R.string.mc_hospitals_none))
                else MyHealthTextField(query, { query = it; shown = 30 }, stringResource(R.string.mc_hospital_search), Modifier.fillMaxWidth().testTag("hospitalSearch"),
                    supportingText = pluralStringResource(R.plurals.mc_hospitals_count, filtered.size, filtered.size), leadingIcon = MyHealthIcons.Search)
            }
            items(filtered.take(shown), key = { it.id }) { h ->
                MyHealthCard(onClick = { openHospital = h }, modifier = Modifier.testTag("hospital")) {
                    MyHealthListRow(h.name, subtitle = listOf(h.city, h.pincode).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }, leadingIcon = MyHealthIcons.Heart)
                }
            }
            if (filtered.size > shown) item { MyHealthTextButton("+ ${filtered.size - shown}", { shown += 50 }, Modifier.fillMaxWidth()) }
        }
    }
    editingClaim?.let { c ->
        ClaimDialog(c, members.orEmpty().filter { policy?.let { p -> it.id in p.coveredMemberIds || it.id == p.memberId } ?: false }, isNew = claims.none { it.id == c.id },
            onSave = { scope.launch(Dispatchers.IO) { repo.upsertClaim(it) }; editingClaim = null },
            onDelete = { scope.launch(Dispatchers.IO) { repo.deleteClaim(c.id) }; editingClaim = null }, onDismiss = { editingClaim = null })
    }
    openHospital?.let { h ->
        MyHealthDialog(h.name, listOf(h.address, h.city, h.pincode).filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { "—" },
            stringResource(R.string.mc_map), { map(context, listOf(h.name, h.address, h.city, h.pincode).filter { it.isNotBlank() }.joinToString(", ")); openHospital = null },
            stringResource(R.string.action_close), { openHospital = null }, icon = MyHealthIcons.Heart) {
            if (h.phone.isNotBlank()) Column {
                Text(h.phone, style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textPrimary)
                MyHealthSecondaryButton(stringResource(R.string.mc_call), { dial(context, h.phone) }, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            MyHealthTextButton(stringResource(R.string.action_delete), { scope.launch(Dispatchers.IO) { repo.deleteHospital(h.id) }; openHospital = null })
        }
    }
}

@Composable
private fun ClaimDialog(c: MediclaimClaim, members: List<app.myhealth.domain.model.FamilyMember>, isNew: Boolean, onSave: (MediclaimClaim) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var hospital by rememberSaveable { mutableStateOf(c.hospital) }
    var member by rememberSaveable { mutableStateOf(c.memberId) }
    var kind by rememberSaveable { mutableStateOf(c.kind) }
    var status by rememberSaveable { mutableStateOf(c.status) }
    var claimed by rememberSaveable { mutableStateOf(if (c.claimedAmount > 0) c.claimedAmount.toLong().toString() else "") }
    var approved by rememberSaveable { mutableStateOf(c.approvedAmount?.toLong()?.toString().orEmpty()) }
    var number by rememberSaveable { mutableStateOf(c.claimNumber) }
    var admitted by remember { mutableStateOf(c.admittedOn) }
    var discharged by remember { mutableStateOf(c.dischargedOn) }
    val ok = stringResource(R.string.action_ok); val cancel = stringResource(R.string.action_cancel)
    MyHealthDialog(stringResource(R.string.mc_add_claim), "", stringResource(R.string.action_save), {
        onSave(c.copy(hospital = hospital.trim().take(120), memberId = member, kind = kind, status = status, claimedAmount = Numbers.parse(claimed) ?: 0.0,
            approvedAmount = Numbers.parse(approved), claimNumber = number.trim().take(40), admittedOn = admitted, dischargedOn = discharged))
    }, if (isNew) cancel else stringResource(R.string.action_delete), if (isNew) onDismiss else onDelete, icon = MyHealthIcons.Document) {
        Column(Modifier.verticalScroll(rememberScrollState()).testTag("claimDialog"), verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) {
            MyHealthTextField(hospital, { hospital = it }, stringResource(R.string.mc_claim_hospital), Modifier.testTag("claimHospital"))
            if (members.size > 1) MyHealthChipRow(members.map { it.id }, member, { id -> members.first { it.id == id }.name }, { member = it })
            MyHealthChipRow(ClaimKind.entries, kind, { stringResource(if (it == ClaimKind.Cashless) R.string.mc_cashless else R.string.mc_reimbursement) }, { kind = it })
            MyHealthDateField(stringResource(R.string.mc_admitted), admitted, { formatDate(it) }, "", { admitted = it }, ok, cancel)
            MyHealthDateField(stringResource(R.string.mc_discharged), discharged, { formatDate(it) }, "", { discharged = it }, ok, cancel)
            MyHealthTextField(claimed, { claimed = it.filter { ch -> ch.isDigit() || ch == '.' } }, stringResource(R.string.mc_claimed), Modifier.testTag("claimAmount"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text(stringResource(R.string.mc_status), style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textSecondary)
            MyHealthChipRow(ClaimStatus.entries, status, { statusLabel(it) }, { status = it })
            MyHealthTextField(approved, { approved = it.filter { ch -> ch.isDigit() || ch == '.' } }, stringResource(R.string.mc_approved), Modifier.testTag("claimApproved"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            MyHealthTextField(number, { number = it }, stringResource(R.string.mc_claim_number))
        }
    }
}

// ================================================================ editor

@Composable
fun PolicyEditorScreen(id: String?, onDone: (String?) -> Unit) {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    val members by session.members.collectAsStateWithLifecycle()
    val active by session.activeMember.collectAsStateWithLifecycle()
    val existing by remember(id) { id?.let { session.stack.mediclaims.observePolicy(it) } ?: flowOf(null) }.collectAsStateWithLifecycle(null)
    val base = existing
    if (id != null && base == null) return
    // a new policy is created ONCE (its id must not change while the user types)
    val fresh = remember { MediclaimPolicy(UuidIds.next(), active?.id.orEmpty(), "", coveredMemberIds = listOfNotNull(active?.id), createdAtEpochMs = session.container.clock.millis()) }
    val start = base ?: fresh
    var p by remember(start.id) { mutableStateOf(start) }
    fun num(s: String) = Numbers.parse(s)
    var sum by remember(start.id) { mutableStateOf(if (start.sumInsured > 0) start.sumInsured.toLong().toString() else "") }
    var bonus by remember(start.id) { mutableStateOf(if (start.bonus > 0) start.bonus.toLong().toString() else "") }
    var premium by remember(start.id) { mutableStateOf(start.premium?.toLong()?.toString().orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ok = stringResource(R.string.action_ok); val cancel = stringResource(R.string.action_cancel)
    val sp = MyHealthTheme.spacing
    val context = LocalContext.current
    // item 1: a NEW policy starts from its document (PDF or photos); every filled field stays editable
    var step by rememberSaveable { mutableStateOf(if (id == null) STEP_PICK else STEP_EDIT) }
    var filled by rememberSaveable { mutableStateOf(-1) }
    var progress by remember { mutableStateOf<Float?>(null) }
    val pendingDocs = remember { mutableListOf<Pair<MediaKind, ByteArray>>() }
    val pendingHospitals = remember { mutableListOf<HospitalListParser.Parsed>() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        step = STEP_READING; progress = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val pages = mutableListOf<String>()
                uris.take(MAX_POLICY_DOCS).forEachIndexed { i, uri ->
                    runCatching {
                        val raw = context.contentResolver.openInputStream(uri)!!.use { Images.readLimited(it, 25 * 1024 * 1024) } ?: return@runCatching
                        val kind = DocumentSniffer.kind(raw) ?: return@runCatching
                        val isPdf = kind == app.myhealth.domain.model.AttachmentKind.Pdf
                        val stored = if (isPdf) raw else Images.documentPhoto(raw, 2400) ?: return@runCatching
                        pendingDocs += (if (isPdf) MediaKind.PolicyPdf else MediaKind.PolicyImage) to stored
                        pages += AndroidTextExtractor(context.applicationContext, maxPages = 300).extract(PickedDocument(raw, "policy", kind)) { done, total ->
                            progress = ((i + done.toFloat() / total.coerceAtLeast(1)) / uris.size.coerceAtMost(MAX_POLICY_DOCS))
                        }.pages
                    }
                }
                pendingHospitals += HospitalListParser.parse(pages.flatMap { it.lines() })
                app.myhealth.domain.insurance.PolicyParser.parse(pages, members.orEmpty())
            }
            p = fill(p, result)
            result.sumInsured?.let { sum = it.toLong().toString() }
            result.bonus?.let { bonus = it.toLong().toString() }
            result.premium?.let { premium = it.toLong().toString() }
            filled = result.found
            step = STEP_EDIT
        }
    }
    fun feature(t: (PolicyFeatures) -> PolicyFeatures) { p = p.copy(features = t(p.features)) }
    @Composable fun intField(label: Int, v: Int?, set: (Int?) -> Unit) =
        MyHealthTextField(v?.toString().orEmpty(), { s -> set(s.filter(Char::isDigit).take(4).toIntOrNull()) }, stringResource(label), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    Column(Modifier.fillMaxSize().testTag("policyEditor")) {
        MyHealthTopBar(stringResource(if (id == null) R.string.mc_add else R.string.mc_edit), onBack = { onDone(null) }, backLabel = stringResource(R.string.action_back))
        if (step == STEP_PICK || step == STEP_READING) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = sp.gutter).testTag("policyPick"), verticalArrangement = Arrangement.spacedBy(sp.md)) {
                MyHealthCard {
                    Text(stringResource(R.string.mc_pick_title), style = MyHealthTheme.typography.title, color = MyHealthTheme.colors.textPrimary)
                    Text(stringResource(R.string.mc_pick_body), style = MyHealthTheme.typography.body, color = MyHealthTheme.colors.textSecondary, modifier = Modifier.padding(top = sp.xs))
                }
                if (step == STEP_READING) MyHealthProgress(stringResource(R.string.mc_reading), progress, Modifier.testTag("policyReading"))
                else {
                    MyHealthPrimaryButton(stringResource(R.string.mc_pick_docs), { runCatching { pick.launch(arrayOf("application/pdf", "image/*")) } },
                        Modifier.fillMaxWidth().testTag("pickPolicyDocs"), icon = MyHealthIcons.Import)
                    MyHealthSecondaryButton(stringResource(R.string.mc_fill_by_hand), { step = STEP_EDIT }, Modifier.fillMaxWidth().testTag("fillByHand"))
                    MyHealthInfoBanner(stringResource(R.string.mc_pick_private))
                }
            }
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
            if (filled > 0) MyHealthInfoBanner(pluralStringResource(R.plurals.mc_filled, filled, filled), Modifier.testTag("policyFilled"), icon = MyHealthIcons.Check)
            else if (filled == 0) MyHealthInfoBanner(stringResource(R.string.mc_filled_none), Modifier.testTag("policyFilled"))
            MyHealthTextButton(stringResource(R.string.mc_fill_from_doc), { runCatching { pick.launch(arrayOf("application/pdf", "image/*")) } }, Modifier.fillMaxWidth().testTag("fillFromDoc"), icon = MyHealthIcons.Import)
            MyHealthTextField(p.insurer, { p = p.copy(insurer = it.take(80)) }, stringResource(R.string.mc_insurer), Modifier.testTag("insurer"))
            MyHealthTextField(p.planName, { p = p.copy(planName = it.take(80)) }, stringResource(R.string.mc_plan))
            MyHealthTextField(p.policyNumber, { p = p.copy(policyNumber = it.take(40)) }, stringResource(R.string.mc_number), Modifier.testTag("policyNumber"))
            Text(stringResource(R.string.mc_type), style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textSecondary)
            MyHealthChipRow(PolicyType.entries, p.type, { typeLabel(it) }, { p = p.copy(type = it) })
            MyHealthTextField(sum, { sum = it.filter { c -> c.isDigit() } }, stringResource(R.string.mc_sum_insured), Modifier.testTag("sumInsured"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            MyHealthTextField(bonus, { bonus = it.filter { c -> c.isDigit() } }, stringResource(R.string.mc_bonus), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            MyHealthTextField(premium, { premium = it.filter { c -> c.isDigit() } }, stringResource(R.string.mc_premium), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            MyHealthDateField(stringResource(R.string.mc_start), p.startDate, { formatDate(it) }, "", { p = p.copy(startDate = it) }, ok, cancel)
            MyHealthDateField(stringResource(R.string.mc_end), p.endDate, { formatDate(it) }, "", { p = p.copy(endDate = it) }, ok, cancel, modifier = Modifier.testTag("policyEnd"))
            Text(stringResource(R.string.mc_covered), style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textSecondary)
            members.orEmpty().forEach { m ->
                MyHealthSwitchRow(m.name, m.id in p.coveredMemberIds, { on -> p = p.copy(coveredMemberIds = if (on) p.coveredMemberIds + m.id else p.coveredMemberIds - m.id) })
            }
            MyHealthSectionHeader(stringResource(R.string.mc_contacts))
            MyHealthTextField(p.tpaName, { p = p.copy(tpaName = it.take(80)) }, stringResource(R.string.mc_tpa))
            MyHealthTextField(p.tpaPhone, { p = p.copy(tpaPhone = it.take(30)) }, stringResource(R.string.mc_tpa_phone), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            MyHealthTextField(p.helpline, { p = p.copy(helpline = it.take(30)) }, stringResource(R.string.mc_helpline), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            MyHealthTextField(p.agentName, { p = p.copy(agentName = it.take(80)) }, stringResource(R.string.mc_agent))
            MyHealthTextField(p.agentPhone, { p = p.copy(agentPhone = it.take(30)) }, stringResource(R.string.mc_agent_phone), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            MyHealthSectionHeader(stringResource(R.string.mc_features))
            MyHealthTextField(p.features.roomRent, { v -> feature { it.copy(roomRent = v.take(80)) } }, stringResource(R.string.mc_room_rent))
            intField(R.string.mc_copay, p.features.coPayPercent) { v -> feature { it.copy(coPayPercent = v) } }
            MyHealthTextField(p.features.deductible?.toLong()?.toString().orEmpty(), { s -> feature { it.copy(deductible = num(s.filter(Char::isDigit))) } }, stringResource(R.string.mc_deductible), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            intField(R.string.mc_initial_wait, p.features.initialWaitingDays) { v -> feature { it.copy(initialWaitingDays = v) } }
            intField(R.string.mc_ped_wait, p.features.pedWaitingMonths) { v -> feature { it.copy(pedWaitingMonths = v) } }
            intField(R.string.mc_specific_wait, p.features.specificWaitingMonths) { v -> feature { it.copy(specificWaitingMonths = v) } }
            intField(R.string.mc_pre, p.features.preHospitalisationDays) { v -> feature { it.copy(preHospitalisationDays = v) } }
            intField(R.string.mc_post, p.features.postHospitalisationDays) { v -> feature { it.copy(postHospitalisationDays = v) } }
            intField(R.string.mc_ncb, p.features.noClaimBonusPercent) { v -> feature { it.copy(noClaimBonusPercent = v) } }
            MyHealthTextField(p.features.ambulanceLimit?.toLong()?.toString().orEmpty(), { s -> feature { it.copy(ambulanceLimit = num(s.filter(Char::isDigit))) } }, stringResource(R.string.mc_ambulance), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            listOf(
                R.string.mc_daycare to (p.features.dayCare to { v: Boolean -> feature { it.copy(dayCare = v) } }),
                R.string.mc_restoration to (p.features.restoration to { v: Boolean -> feature { it.copy(restoration = v) } }),
                R.string.mc_ayush to (p.features.ayush to { v: Boolean -> feature { it.copy(ayush = v) } }),
                R.string.mc_maternity to (p.features.maternity to { v: Boolean -> feature { it.copy(maternity = v) } }),
                R.string.mc_opd to (p.features.opd to { v: Boolean -> feature { it.copy(opd = v) } }),
                R.string.mc_checkup to (p.features.healthCheckup to { v: Boolean -> feature { it.copy(healthCheckup = v) } }),
                R.string.mc_domiciliary to (p.features.domiciliary to { v: Boolean -> feature { it.copy(domiciliary = v) } }),
                R.string.mc_organ to (p.features.organDonor to { v: Boolean -> feature { it.copy(organDonor = v) } }),
            ).forEach { (label, pair) -> MyHealthSwitchRow(stringResource(label), pair.first, pair.second) }
            MyHealthTextField(p.features.other, { v -> feature { it.copy(other = v.take(500)) } }, stringResource(R.string.mc_other), singleLine = false)
            MyHealthTextField(p.notes, { p = p.copy(notes = it.take(1000)) }, stringResource(R.string.mc_notes), singleLine = false)
            MyHealthPrimaryButton(stringResource(R.string.action_save), {
                if (p.insurer.isBlank()) return@MyHealthPrimaryButton
                val final = p.copy(insurer = p.insurer.trim(), sumInsured = num(sum) ?: 0.0, bonus = num(bonus) ?: 0.0, premium = num(premium),
                    coveredMemberIds = (p.coveredMemberIds + p.memberId).distinct())
                scope.launch {
                    withContext(Dispatchers.IO) {
                        session.stack.mediclaims.upsert(final)
                        // the documents the details were read from are kept with the policy, plus any hospital list in them
                        pendingDocs.forEach { (kind, bytes) -> session.stack.media.add(Media(UuidIds.next(), final.memberId, final.id, kind, session.container.clock.millis()), bytes) }
                        pendingDocs.clear()
                        val have = session.stack.mediclaims.observeHospitals(final.id).first().map { it.name.lowercase() + it.pincode }.toSet()
                        pendingHospitals.filter { (it.name.lowercase() + it.pincode) !in have }
                            .map { NetworkHospital(UuidIds.next(), final.id, it.name, it.address, it.city, it.pincode, it.phone) }
                            .takeIf { it.isNotEmpty() }?.let { session.stack.mediclaims.addHospitals(it) }
                        pendingHospitals.clear()
                    }
                    reschedule(session)
                    withContext(Dispatchers.Main) { onDone(final.id) } // navigation must run on the main thread
                }
            }, Modifier.fillMaxWidth().testTag("savePolicy"), icon = MyHealthIcons.Check, enabled = p.insurer.isNotBlank())
            if (id != null) MyHealthTextButton(stringResource(R.string.mc_delete), { confirmDelete = true }, Modifier.fillMaxWidth(), icon = MyHealthIcons.Delete)
            androidx.compose.foundation.layout.Spacer(Modifier.height(sp.xl))
        }
    }
    if (confirmDelete) MyHealthDialog(stringResource(R.string.mc_delete), "", stringResource(R.string.action_delete), {
        confirmDelete = false
        scope.launch {
            withContext(Dispatchers.IO) {
                MediclaimReminders(session.container.context, session.container.clock).cancelAll(start.id)
                session.stack.media.deleteForOwner(start.id); session.stack.mediclaims.delete(start.id)
            }
            withContext(Dispatchers.Main) { onDone(null) }
        }
    }, stringResource(R.string.action_cancel), { confirmDelete = false }, icon = MyHealthIcons.Delete)
}

private const val STEP_PICK = 0
private const val STEP_READING = 1
private const val STEP_EDIT = 2
private const val MAX_POLICY_DOCS = 6

/** Copies what the parser found onto the policy; fields it did not find keep what the user already had. */
internal fun fill(p: MediclaimPolicy, r: app.myhealth.domain.insurance.PolicyParser.Result): MediclaimPolicy {
    fun pick(new: String, old: String) = new.ifBlank { old }
    val f = r.features; val o = p.features
    return p.copy(
        insurer = pick(r.insurer, p.insurer), planName = pick(r.planName, p.planName), policyNumber = pick(r.policyNumber, p.policyNumber),
        type = r.type ?: p.type, sumInsured = r.sumInsured ?: p.sumInsured, bonus = r.bonus ?: p.bonus, premium = r.premium ?: p.premium,
        startDate = r.startDate ?: p.startDate, endDate = r.endDate ?: p.endDate,
        tpaName = pick(r.tpaName, p.tpaName), tpaPhone = pick(r.tpaPhone, p.tpaPhone), helpline = pick(r.helpline, p.helpline),
        agentName = pick(r.agentName, p.agentName), agentPhone = pick(r.agentPhone, p.agentPhone),
        coveredMemberIds = (p.coveredMemberIds + r.memberIds).distinct(),
        features = o.copy(
            roomRent = pick(f.roomRent, o.roomRent), coPayPercent = f.coPayPercent ?: o.coPayPercent, deductible = f.deductible ?: o.deductible,
            initialWaitingDays = f.initialWaitingDays ?: o.initialWaitingDays, pedWaitingMonths = f.pedWaitingMonths ?: o.pedWaitingMonths,
            specificWaitingMonths = f.specificWaitingMonths ?: o.specificWaitingMonths, preHospitalisationDays = f.preHospitalisationDays ?: o.preHospitalisationDays,
            postHospitalisationDays = f.postHospitalisationDays ?: o.postHospitalisationDays, noClaimBonusPercent = f.noClaimBonusPercent ?: o.noClaimBonusPercent,
            ambulanceLimit = f.ambulanceLimit ?: o.ambulanceLimit,
            dayCare = f.dayCare || o.dayCare, restoration = f.restoration || o.restoration, ayush = f.ayush || o.ayush, maternity = f.maternity || o.maternity,
            opd = f.opd || o.opd, healthCheckup = f.healthCheckup || o.healthCheckup, domiciliary = f.domiciliary || o.domiciliary, organDonor = f.organDonor || o.organDonor,
        ),
    )
}
