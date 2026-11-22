package app.myhealth.ui.member

import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.myhealth.R
import app.myhealth.core.Safe
import app.myhealth.core.designsystem.components.MyHealthChoiceField
import app.myhealth.core.designsystem.components.MyHealthDateField
import app.myhealth.core.designsystem.components.MyHealthDialog
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthInfoBanner
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.components.MyHealthTextField
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.model.BloodGroup
import app.myhealth.domain.model.EmergencyContact
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.Sex
import app.myhealth.domain.util.IdGenerator
import app.myhealth.domain.util.UuidIds
import app.myhealth.ui.Session
import app.myhealth.ui.common.formatDate
import app.myhealth.ui.common.label
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

enum class FormError { NameRequired, HeightInvalid, DobFuture }

data class MemberForm(
    val name: String = "", val relationship: Relationship = Relationship.Self, val dob: LocalDate? = null, val sex: Sex = Sex.Unspecified,
    val heightText: String = "", val bloodGroup: BloodGroup = BloodGroup.Unknown, val allergies: String = "", val conditions: String = "",
    val ecName: String = "", val ecPhone: String = "", val ecRelation: String = "",
) {
    fun validate(today: LocalDate): Map<String, FormError> = buildMap {
        if (name.isBlank()) put("name", FormError.NameRequired)
        if (heightText.isNotBlank() && heightText.trim().toDoubleOrNull()?.let { it in 30.0..250.0 } != true) put("height", FormError.HeightInvalid)
        if (dob != null && dob.isAfter(today)) put("dob", FormError.DobFuture)
    }

    fun toMember(id: String, createdAt: Long) = FamilyMember(
        id = id, name = name.trim(), relationship = relationship, dateOfBirth = dob, sex = sex, heightCm = heightText.trim().toDoubleOrNull(),
        bloodGroup = bloodGroup, allergies = split(allergies), conditions = split(conditions),
        emergencyContact = EmergencyContact(ecName.trim(), ecPhone.trim(), ecRelation.trim()), createdAtEpochMs = createdAt,
    )

    companion object {
        fun split(s: String) = s.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        fun of(m: FamilyMember) = MemberForm(
            m.name, m.relationship, m.dateOfBirth, m.sex, m.heightCm?.let { app.myhealth.domain.format.ValueFormat.compact(it, 1) } ?: "",
            m.bloodGroup, m.allergies.joinToString(", "), m.conditions.joinToString(", "), m.emergencyContact.name, m.emergencyContact.phone, m.emergencyContact.relation,
        )
    }
}

data class MemberEditorState(
    val loading: Boolean = true,
    val form: MemberForm = MemberForm(),
    val errors: Map<String, FormError> = emptyMap(),
    val isNew: Boolean = true,
    val canDelete: Boolean = false,
    val saving: Boolean = false,
    val savedId: String? = null,
    val deleted: Boolean = false,
    val failed: Boolean = false,
    /** Newly chosen photo (processed JPEG), pending save. */
    val memberId: String? = null,
    val newPhoto: ByteArray? = null,
    val removePhoto: Boolean = false,
    val photoFailed: Boolean = false,
)

sealed interface MemberEvent {
    data class Edit(val transform: (MemberForm) -> MemberForm) : MemberEvent
    data object Save : MemberEvent
    data object Delete : MemberEvent
    /** Raw image bytes from the picker/camera; processed (square, 512 px, metadata stripped) off the main thread. */
    class PhotoPicked(val bytes: ByteArray) : MemberEvent
    data object RemovePhoto : MemberEvent
}

class MemberEditorViewModel(
    private val session: Session,
    private val memberId: String?,
    private val defaultRelationship: Relationship,
    private val clock: Clock,
    private val ids: IdGenerator = UuidIds,
) : ViewModel() {
    private val _state = MutableStateFlow(MemberEditorState())
    val state: StateFlow<MemberEditorState> = _state.asStateFlow()
    private var existing: FamilyMember? = null

    init {
        viewModelScope.launch(Safe.handler) {
            existing = memberId?.let { session.stack.members.get(it) }
            _state.value = MemberEditorState(
                loading = false,
                form = existing?.let(MemberForm::of) ?: MemberForm(relationship = defaultRelationship),
                isNew = existing == null,
                memberId = existing?.id,
                canDelete = existing != null && existing?.relationship != Relationship.Self,
            )
        }
    }

    fun onEvent(e: MemberEvent) {
        when (e) {
            is MemberEvent.Edit -> _state.update { s -> s.copy(form = e.transform(s.form), errors = if (s.errors.isEmpty()) s.errors else e.transform(s.form).validate(LocalDate.now(clock))) }
            MemberEvent.Save -> save()
            MemberEvent.Delete -> delete()
            is MemberEvent.PhotoPicked -> viewModelScope.launch(Safe.handler) {
                val jpeg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { app.myhealth.core.Images.profilePhoto(e.bytes) }
                _state.update { it.copy(newPhoto = jpeg, removePhoto = false, photoFailed = jpeg == null) }
            }
            MemberEvent.RemovePhoto -> _state.update { it.copy(newPhoto = null, removePhoto = true) }
        }
    }

    private fun save() {
        val s = _state.value
        if (s.saving) return
        val errors = s.form.validate(LocalDate.now(clock))
        if (errors.isNotEmpty()) { _state.update { it.copy(errors = errors) }; return }
        _state.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch(Safe.handler) {
            runCatching {
                val m = s.form.toMember(existing?.id ?: ids.next(), existing?.createdAtEpochMs ?: clock.millis())
                session.stack.members.upsert(m)
                s.newPhoto?.let { session.stack.photos.put(m.id, it) }
                if (s.removePhoto) session.stack.photos.delete(m.id)
                if (s.newPhoto != null || s.removePhoto) session.photoRevision.value++
                m.id
            }.onSuccess { id -> _state.update { it.copy(saving = false, savedId = id) } }
                .onFailure { _state.update { it.copy(saving = false, failed = true) } }
        }
    }

    private fun delete() {
        val m = existing ?: return
        if (m.relationship == Relationship.Self) return
        viewModelScope.launch(Safe.handler) {
            runCatching { session.stack.members.delete(m.id) }
                .onSuccess { if (session.activeMember.value?.id == m.id) session.container.preferences.setActiveMember(null); _state.update { it.copy(deleted = true) } }
                .onFailure { _state.update { it.copy(failed = true) } }
        }
    }
}

@Composable
fun MemberEditorScreen(
    state: MemberEditorState,
    onEvent: (MemberEvent) -> Unit,
    title: String,
    onBack: (() -> Unit)?,
    primaryLabel: String = stringResource(R.string.action_save),
    intro: String? = null,
    lockRelationship: Boolean = false,
) {
    Column(Modifier.fillMaxSize().testTag("memberEditor")) {
        MyHealthTopBar(title = title, onBack = onBack, backLabel = stringResource(R.string.action_back))
        if (state.loading) { MyHealthLoadingState(stringResource(R.string.state_loading)); return@Column }
        MemberFormContent(state, onEvent, primaryLabel, intro, lockRelationship)
    }
}

@Composable
private fun MemberFormContent(state: MemberEditorState, onEvent: (MemberEvent) -> Unit, primaryLabel: String, intro: String?, lockRelationship: Boolean) {
    val f = state.form
    val sp = MyHealthTheme.spacing
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    fun edit(t: (MemberForm) -> MemberForm) = onEvent(MemberEvent.Edit(t))
    fun err(e: FormError?) = when (e) { FormError.NameRequired -> R.string.err_name_required; FormError.HeightInvalid -> R.string.err_height_invalid; FormError.DobFuture -> R.string.err_dob_future; null -> null }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = sp.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = sp.contentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(sp.md)) {
            if (intro != null) MyHealthInfoBanner(intro)
            PhotoPickerRow(state, onEvent)
            MyHealthSectionHeader(stringResource(R.string.section_basics))
            MyHealthTextField(f.name, { v -> edit { it.copy(name = v) } }, stringResource(R.string.field_name), Modifier.testTag("field_name"),
                errorText = err(state.errors["name"])?.let { stringResource(it) })
            if (!lockRelationship) {
                MyHealthChoiceField(stringResource(R.string.field_relationship), Relationship.entries.filter { it != Relationship.Self || f.relationship == Relationship.Self },
                    f.relationship, { label(it) }, { v -> edit { it.copy(relationship = v) } }, confirmLabel = stringResource(R.string.action_close))
            }
            MyHealthDateField(stringResource(R.string.field_dob), f.dob, { formatDate(it) }, stringResource(R.string.date_not_set), { v -> edit { it.copy(dob = v) } },
                stringResource(R.string.action_ok), stringResource(R.string.action_cancel), maxDate = null, errorText = err(state.errors["dob"])?.let { stringResource(it) })
            MyHealthChoiceField(stringResource(R.string.field_sex), Sex.entries, f.sex, { label(it) }, { v -> edit { it.copy(sex = v) } }, confirmLabel = stringResource(R.string.action_close))
            MyHealthSectionHeader(stringResource(R.string.section_medical))
            MyHealthTextField(f.heightText, { v -> edit { it.copy(heightText = v) } }, stringResource(R.string.field_height),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), errorText = err(state.errors["height"])?.let { stringResource(it) })
            MyHealthChoiceField(stringResource(R.string.field_blood_group), BloodGroup.entries, f.bloodGroup, { label(it) }, { v -> edit { it.copy(bloodGroup = v) } }, confirmLabel = stringResource(R.string.action_close))
            MyHealthTextField(f.allergies, { v -> edit { it.copy(allergies = v) } }, stringResource(R.string.field_allergies), supportingText = stringResource(R.string.hint_comma_separated), singleLine = false)
            MyHealthTextField(f.conditions, { v -> edit { it.copy(conditions = v) } }, stringResource(R.string.field_conditions), supportingText = stringResource(R.string.hint_comma_separated), singleLine = false)
            MyHealthSectionHeader(stringResource(R.string.section_emergency))
            MyHealthTextField(f.ecName, { v -> edit { it.copy(ecName = v) } }, stringResource(R.string.field_ec_name))
            MyHealthTextField(f.ecPhone, { v -> edit { it.copy(ecPhone = v) } }, stringResource(R.string.field_ec_phone), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            MyHealthTextField(f.ecRelation, { v -> edit { it.copy(ecRelation = v) } }, stringResource(R.string.field_ec_relation))
            if (state.failed) MyHealthInfoBanner(stringResource(R.string.state_error_body), icon = MyHealthIcons.StatusError)
            Spacer(Modifier.height(sp.sm))
            MyHealthPrimaryButton(if (state.saving) stringResource(R.string.saving) else primaryLabel, { onEvent(MemberEvent.Save) },
                Modifier.fillMaxWidth().testTag("saveMember"), enabled = !state.saving, icon = MyHealthIcons.Check)
            if (!state.isNew && f.relationship == Relationship.Self) MyHealthInfoBanner(stringResource(R.string.member_cannot_delete_self), icon = MyHealthIcons.Info)
            if (state.canDelete) MyHealthTextButton(stringResource(R.string.member_delete), { confirmDelete = true }, Modifier.fillMaxWidth(), icon = MyHealthIcons.Delete)
            Spacer(Modifier.height(sp.xl))
        }
    }
    if (confirmDelete) {
        MyHealthDialog(stringResource(R.string.member_delete_title, f.name), stringResource(R.string.member_delete_body),
            stringResource(R.string.action_delete), { confirmDelete = false; onEvent(MemberEvent.Delete) },
            stringResource(R.string.action_cancel), { confirmDelete = false }, icon = MyHealthIcons.Delete)
    }
}

@Composable
private fun PhotoPickerRow(state: MemberEditorState, onEvent: (MemberEvent) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val session = app.myhealth.ui.LocalSession.current
    val members by session.members.collectAsStateWithLifecycle()
    val existing = rememberMemberPhoto(state.memberId?.let { id -> members?.firstOrNull { it.id == id } })
    val preview = state.newPhoto?.let { b -> androidx.compose.runtime.remember(b) { android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() } }
        ?: if (state.removePhoto) null else existing
    // Photo picker: no storage permission needed; the app only receives the one image the user picks.
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { u -> runCatching { ctx.contentResolver.openInputStream(u)?.use { app.myhealth.core.Images.readLimited(it, 12 * 1024 * 1024) } }.getOrNull()?.let { onEvent(MemberEvent.PhotoPicked(it)) } }
    }
    val c = MyHealthTheme.colors
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(88.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.accent, c.accentBright))), contentAlignment = Alignment.Center) {
            if (preview != null) androidx.compose.foundation.Image(preview, stringResource(R.string.member_photo_cd, state.form.name), Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else androidx.compose.material3.Icon(MyHealthIcons.Profile, null, tint = c.onAccent, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(MyHealthTheme.spacing.md))
        Column {
            MyHealthTextButton(stringResource(if (preview == null) R.string.member_photo_add else R.string.member_photo_change), {
                runCatching { pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }, icon = MyHealthIcons.Camera, modifier = Modifier.testTag("pickPhoto"))
            if (preview != null) MyHealthTextButton(stringResource(R.string.member_photo_remove), { onEvent(MemberEvent.RemovePhoto) }, icon = MyHealthIcons.Delete)
        }
    }
    if (state.photoFailed) MyHealthInfoBanner(stringResource(R.string.member_photo_failed), icon = MyHealthIcons.StatusError)
    else androidx.compose.material3.Text(stringResource(R.string.member_photo_note), style = MyHealthTheme.typography.caption, color = c.textSecondary)
}
