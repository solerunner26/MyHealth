package app.myhealth.ui.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myhealth.core.Safe
import app.myhealth.domain.catalogue.AnalyteCatalogue
import app.myhealth.domain.importing.DraftIssue
import app.myhealth.domain.importing.ImportDraft
import app.myhealth.domain.importing.ImportDrafts
import app.myhealth.domain.importing.LabRowDraft
import app.myhealth.domain.importing.MedicineDraft
import app.myhealth.domain.importing.VitalDraft
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.ReportType
import app.myhealth.domain.repository.NewAttachment
import app.myhealth.domain.util.IdGenerator
import app.myhealth.domain.util.UuidIds
import app.myhealth.ingest.DocumentReader
import app.myhealth.ingest.ExtractionProgress
import app.myhealth.ingest.ImportPipeline
import app.myhealth.ingest.IngestError
import app.myhealth.ingest.IngestException
import app.myhealth.ingest.PickedDocument
import app.myhealth.ui.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate

sealed interface ImportPhase {
    data object Choose : ImportPhase
    data class Reading(val done: Int, val total: Int) : ImportPhase
    data object Review : ImportPhase
    /** The printed patient name matched nobody (or several people): the user picks, or adds a new member. */
    data class ChooseMember(val printedName: String, val ranked: List<app.myhealth.domain.importing.MemberMatch>) : ImportPhase
    data object Saving : ImportPhase
    data class Saved(val reportId: String, val memberName: String) : ImportPhase
    /** Several files with different test dates: one report per date, listed for review (item 14). */
    data object Batch : ImportPhase
    data class Failed(val error: IngestError) : ImportPhase
}

data class ImportUiState(
    val phase: ImportPhase = ImportPhase.Choose,
    val member: FamilyMember? = null,
    val members: List<FamilyMember> = emptyList(),
    val draft: ImportDraft? = null,
    val issues: List<DraftIssue> = emptyList(),
    val showIssues: Boolean = false,
    /** analyteId → value that would be replaced (same member, analyte and date). */
    val conflicts: Map<String, LabResult> = emptyMap(),
    val saveFailed: Boolean = false,
    /** Set when the draft was assigned to a member because the printed name matched (shown as a banner). */
    val autoMatchedName: String? = null,
    /** An extra page could not be read (the record keeps everything else). */
    val pageFailed: IngestError? = null,
    /** Reports found in a multi-file pick, one per test date (empty for a single report). */
    val batch: List<BatchItem> = emptyList(),
    /** Files of a multi-file pick that could not be read (the others are kept). */
    val batchFailedFiles: Int = 0,
    /** True while reviewing one report of a batch (back returns to the list). */
    val inBatch: Boolean = false,
) {
    val ocrUsed get() = (draft?.source?.ocrPages ?: 0) > 0
    fun issuesFor(key: String?) = if (showIssues) issues.filter { it.rowKey == key } else emptyList()
}

/** One report of a multi-file import. */
data class BatchItem(
    val index: Int, val date: LocalDate?, val title: String, val type: ReportType, val lab: String, val files: List<String>,
    val values: Int, val issues: Int, val memberName: String?, val saved: Boolean,
) {
    val ready get() = !saved && issues == 0 && memberName != null && date != null
}

sealed interface ImportEvent {
    data class PickUri(val uri: Uri, val fallbackName: String) : ImportEvent
    /** Several files picked at once: the first starts the record, the rest become extra pages. */
    data class PickUris(val uris: List<Uri>, val fallbackName: String) : ImportEvent
    /** Another page/file for the record under review. */
    data class AddPage(val uri: Uri, val fallbackName: String) : ImportEvent
    data object Manual : ImportEvent
    data class SetMember(val id: String) : ImportEvent
    data class SetDate(val date: LocalDate) : ImportEvent
    data class SetTitle(val title: String) : ImportEvent
    data class SetLab(val lab: String) : ImportEvent
    data class SetType(val type: ReportType) : ImportEvent
    data class EditLab(val key: String, val transform: (LabRowDraft) -> LabRowDraft) : ImportEvent
    data class EditVital(val key: String, val transform: (VitalDraft) -> VitalDraft) : ImportEvent
    data class EditMedicine(val key: String, val transform: (MedicineDraft) -> MedicineDraft) : ImportEvent
    data class AddLab(val analyteId: String) : ImportEvent
    data class RemoveLab(val key: String) : ImportEvent
    data class Save(val fallbackTitle: String) : ImportEvent
    data object Reset : ImportEvent
    data class ChooseMember(val id: String) : ImportEvent
    data class AddMemberFromReport(val name: String) : ImportEvent
    data class ReviewGroup(val index: Int) : ImportEvent
    data object BackToBatch : ImportEvent
    data class SaveAllReady(val fallbackTitle: String) : ImportEvent
}

const val MAX_FILES = 20

class ImportViewModel(
    private val session: Session,
    private val reader: DocumentReader,
    private val pipeline: ImportPipeline,
    private val clock: Clock,
    private val ids: IdGenerator = UuidIds,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()
    /** Every file of the record being reviewed, in order (first = the one that started the import). */
    private val documents = mutableListOf<PickedDocument>()
    private var document: PickedDocument?
        get() = documents.firstOrNull()
        set(v) { documents.clear(); if (v != null) documents += v }
    private var addCounter = 0

    init {
        viewModelScope.launch(Safe.handler) {
            session.activeMember.value?.let { m -> _state.update { it.copy(member = m) } }
            session.members.collect { list -> _state.update { s -> s.copy(members = list.orEmpty(), member = s.member ?: session.activeMember.value) } }
        }
    }

    private val today get() = LocalDate.now(clock)

    fun onEvent(e: ImportEvent) {
        when (e) {
            is ImportEvent.PickUri -> read(e.uri, e.fallbackName)
            is ImportEvent.PickUris -> when {
                e.uris.size == 1 -> read(e.uris.first(), e.fallbackName)
                e.uris.size > 1 -> readBatch(e.uris.take(MAX_FILES), e.fallbackName)
            }
            is ImportEvent.AddPage -> addPages(listOf(e.uri), e.fallbackName)
            ImportEvent.Manual -> _state.value.member?.let { m -> document = null; setDraft(ImportDrafts.emptyManual(m, today)); _state.update { it.copy(phase = ImportPhase.Review) } }
            is ImportEvent.SetMember -> {
                val m = _state.value.members.firstOrNull { it.id == e.id } ?: return
                _state.update { it.copy(member = m) }
                _state.value.draft?.let { setDraft(it.copy(memberId = m.id)) }
            }
            is ImportEvent.SetDate -> mutate { it.copy(reportDate = e.date) }
            is ImportEvent.SetTitle -> mutate { it.copy(title = e.title) }
            is ImportEvent.SetLab -> mutate { it.copy(labName = e.lab) }
            is ImportEvent.SetType -> mutate { it.copy(type = e.type) }
            is ImportEvent.EditLab -> mutate { d -> d.copy(labRows = d.labRows.map { if (it.key == e.key) e.transform(it) else it }) }
            is ImportEvent.EditVital -> mutate { d -> d.copy(vitals = d.vitals.map { if (it.key == e.key) e.transform(it) else it }) }
            is ImportEvent.EditMedicine -> mutate { d -> d.copy(medicines = d.medicines.map { if (it.key == e.key) e.transform(it) else it }) }
            is ImportEvent.AddLab -> if (AnalyteCatalogue.byId(e.analyteId) != null) mutate { d -> d.copy(labRows = d.labRows + ImportDrafts.newLabRow("add${addCounter++}", e.analyteId)) }
            is ImportEvent.RemoveLab -> mutate { d -> d.copy(labRows = d.labRows.filterNot { it.key == e.key }) }
            is ImportEvent.Save -> save(e.fallbackTitle)
            ImportEvent.Reset -> { document = null; groups.clear(); current = null; _state.update { ImportUiState(member = it.member, members = it.members) } }
            is ImportEvent.ChooseMember -> {
                val m = _state.value.members.firstOrNull { it.id == e.id } ?: return
                _state.update { it.copy(member = m, phase = ImportPhase.Review, autoMatchedName = null) }
                _state.value.draft?.let { setDraft(it.copy(memberId = m.id)) }
            }
            is ImportEvent.AddMemberFromReport -> addMember(e.name)
            is ImportEvent.ReviewGroup -> openGroup(e.index)
            ImportEvent.BackToBatch -> backToBatch()
            is ImportEvent.SaveAllReady -> saveAllReady(e.fallbackTitle)
        }
    }

    // ---- multi-file import: one report per test date ------------------------------------------------------------

    private data class Group(val draft: ImportDraft, val docs: List<PickedDocument>, val memberId: String?, val saved: Boolean = false, val reportId: String? = null)
    private val groups = mutableListOf<Group>()
    private var current: Int? = null

    private fun readBatch(uris: List<Uri>, fallbackName: String) {
        val member = _state.value.member ?: return
        _state.update { it.copy(phase = ImportPhase.Reading(0, uris.size), saveFailed = false) }
        viewModelScope.launch(Safe.handler) {
            val read = mutableListOf<Pair<ImportDraft, PickedDocument>>()
            var firstError: IngestError? = null; var failed = 0
            uris.forEachIndexed { i, u ->
                _state.update { it.copy(phase = ImportPhase.Reading(i, uris.size)) }
                try {
                    val doc = withContext(Dispatchers.IO) { reader.read(u, fallbackName) }
                    val prepared = withContext(Dispatchers.Default) { pipeline.prepare(doc, member, today, "", ExtractionProgress { _, _ -> }) }
                    read += prepared.draft to doc
                } catch (ex: IngestException) { failed++; firstError = firstError ?: ex.error } catch (ex: Exception) { failed++; firstError = firstError ?: IngestError.Unreadable }
            }
            if (read.isEmpty()) { _state.update { it.copy(phase = ImportPhase.Failed(firstError ?: IngestError.Unreadable)) }; return@launch }
            val grouped = ImportDrafts.groupByDate(read)
            if (grouped.size == 1) {
                // everything is one test date: one record, exactly like adding pages
                val (d, docs) = grouped.single()
                documents.clear(); documents += docs
                _state.update { it.copy(batchFailedFiles = failed, pageFailed = firstError.takeIf { failed > 0 }) }
                setDraft(d)
                routeByName(d.patientName)
                return@launch
            }
            groups.clear()
            grouped.forEach { (d, docs) -> groups += Group(d, docs, memberFor(d.patientName)) }
            current = null
            _state.update { it.copy(phase = ImportPhase.Batch, batchFailedFiles = failed, inBatch = false, draft = null) }
            publishBatch()
        }
    }

    /** The member a report belongs to: the printed name's clear match, the active member when no name is printed, else unknown. */
    private fun memberFor(printed: String?): String? {
        val s = _state.value
        if (printed.isNullOrBlank()) return s.member?.id
        return app.myhealth.domain.importing.NameMatcher.best(printed, s.members)?.member?.id
    }

    private fun publishBatch() {
        val members = _state.value.members
        _state.update { st ->
            st.copy(batch = groups.mapIndexed { i, g ->
                val d = g.draft.copy(memberId = g.memberId ?: g.draft.memberId)
                BatchItem(i, d.reportDate, d.title, d.type, d.labName, g.docs.map { it.displayName }, d.includedCount,
                    ImportDrafts.validate(d, today).size, members.firstOrNull { it.id == g.memberId }?.name, g.saved)
            })
        }
    }

    private fun openGroup(i: Int) {
        val g = groups.getOrNull(i) ?: return
        if (g.saved) return
        current = i
        documents.clear(); documents += g.docs
        val m = _state.value.members.firstOrNull { it.id == g.memberId }
        _state.update { it.copy(inBatch = true, member = m ?: it.member, autoMatchedName = null, showIssues = false) }
        setDraft(g.draft.copy(memberId = m?.id ?: g.draft.memberId))
        if (m == null) routeByName(g.draft.patientName) else _state.update { it.copy(phase = ImportPhase.Review) }
    }

    private fun backToBatch() {
        val i = current ?: return
        _state.value.draft?.let { d -> groups[i] = groups[i].copy(draft = d, memberId = _state.value.member?.id?.takeIf { _state.value.phase !is ImportPhase.ChooseMember } ?: groups[i].memberId) }
        current = null
        _state.update { it.copy(phase = ImportPhase.Batch, inBatch = false, draft = null, showIssues = false) }
        publishBatch()
    }

    private fun saveAllReady(fallbackTitle: String) {
        if (_state.value.phase == ImportPhase.Saving) return
        _state.update { it.copy(phase = ImportPhase.Saving, saveFailed = false) }
        viewModelScope.launch(Safe.handler) {
            var failed = false
            groups.forEachIndexed { i, g ->
                val memberId = g.memberId ?: return@forEachIndexed
                val d = g.draft.copy(memberId = memberId)
                if (g.saved || d.reportDate == null || ImportDrafts.validate(d, today).isNotEmpty()) return@forEachIndexed
                try { groups[i] = g.copy(saved = true, reportId = persist(d, g.docs, fallbackTitle)) } catch (ex: Exception) { failed = true }
            }
            finishBatchStep(failed)
        }
    }

    private fun finishBatchStep(failed: Boolean) {
        publishBatch()
        if (groups.all { it.saved }) {
            val last = groups.first { it.reportId != null }
            val name = _state.value.members.firstOrNull { it.id == last.memberId }?.name.orEmpty()
            last.memberId?.let { session.select(it) }
            _state.update { it.copy(phase = ImportPhase.Saved(last.reportId!!, name), inBatch = false) }
        } else _state.update { it.copy(phase = ImportPhase.Batch, inBatch = false, draft = null, saveFailed = failed) }
    }

    private suspend fun persist(d: ImportDraft, docs: List<PickedDocument>, fallbackTitle: String): String {
        val (bundle, attachments) = ImportDrafts.toBundle(d, ids, clock.instant(), clock.zone, today, fallbackTitle)
        val files = attachments.zip(docs).map { (a, doc) -> NewAttachment(a, doc.bytes) }
        session.stack.records.saveImport(bundle, files)
        return bundle.report.id
    }

    /** Entry point for tests and the camera path: a document already in memory. */
    fun onDocument(doc: PickedDocument) {
        val member = _state.value.member ?: return
        document = doc
        _state.update { it.copy(phase = ImportPhase.Reading(0, 0), saveFailed = false) }
        viewModelScope.launch(Safe.handler) {
            try {
                val prepared = withContext(Dispatchers.Default) {
                    pipeline.prepare(doc, member, today, "", ExtractionProgress { done, total -> _state.update { s -> s.copy(phase = ImportPhase.Reading(done, total)) } })
                }
                setDraft(prepared.draft)
                val rest = pendingPages; pendingPages = emptyList()
                if (rest.isNotEmpty()) addPagesNow(rest, doc.displayName)
                routeByName(_state.value.draft?.patientName)
            } catch (ex: IngestException) {
                _state.update { it.copy(phase = ImportPhase.Failed(ex.error)) }
            } catch (ex: Exception) {
                _state.update { it.copy(phase = ImportPhase.Failed(IngestError.Unreadable)) }
            }
        }
    }

    private var pendingPages: List<Uri> = emptyList()

    private fun addPages(uris: List<Uri>, fallbackName: String) {
        if (_state.value.draft == null) return
        _state.update { it.copy(phase = ImportPhase.Reading(0, 0)) }
        viewModelScope.launch(Safe.handler) {
            val failed = addPagesNow(uris, fallbackName)
            _state.update { it.copy(phase = ImportPhase.Review, pageFailed = failed) }
        }
    }

    /** Reads and merges extra pages; returns the first error, keeping everything merged so far. */
    private suspend fun addPagesNow(uris: List<Uri>, fallbackName: String): IngestError? {
        var error: IngestError? = null
        for (u in uris) {
            if (documents.size >= MAX_FILES) break
            try {
                val doc = withContext(Dispatchers.IO) { reader.read(u, fallbackName) }
                val member = _state.value.member ?: return null
                val merged = withContext(Dispatchers.Default) { pipeline.addPage(_state.value.draft!!, doc, member, today, ExtractionProgress { _, _ -> }) }
                documents += doc
                setDraft(merged)
            } catch (ex: IngestException) { error = error ?: ex.error } catch (ex: Exception) { error = error ?: IngestError.Unreadable }
        }
        return error
    }

    /** Item 4: file the report under the person whose name is printed on it, or ask. */
    private fun routeByName(printed: String?) {
        val members = _state.value.members
        if (printed.isNullOrBlank() || members.isEmpty()) { _state.update { it.copy(phase = ImportPhase.Review) }; return }
        val best = app.myhealth.domain.importing.NameMatcher.best(printed, members)
        if (best != null) {
            val changed = best.member.id != _state.value.member?.id
            _state.update { it.copy(member = best.member, phase = ImportPhase.Review, autoMatchedName = if (changed) printed else null) }
            _state.value.draft?.let { setDraft(it.copy(memberId = best.member.id)) }
        } else {
            _state.update { it.copy(phase = ImportPhase.ChooseMember(printed, app.myhealth.domain.importing.NameMatcher.rank(printed, members))) }
        }
    }

    private fun addMember(name: String) {
        val clean = app.myhealth.domain.importing.NameMatcher.cleanName(name).ifBlank { return }
        viewModelScope.launch(Safe.handler) {
            val m = FamilyMember(ids.next(), clean.take(80), app.myhealth.domain.model.Relationship.Other, createdAtEpochMs = clock.millis())
            runCatching { session.stack.members.upsert(m) }.onSuccess {
                _state.update { s -> s.copy(member = m, members = s.members + m, phase = ImportPhase.Review, autoMatchedName = null) }
                _state.value.draft?.let { d -> setDraft(d.copy(memberId = m.id)) }
            }.onFailure { _state.update { it.copy(saveFailed = true, phase = ImportPhase.Review) } }
        }
    }

    private fun read(uri: Uri, fallbackName: String, more: List<Uri> = emptyList()) {
        _state.update { it.copy(phase = ImportPhase.Reading(0, 0)) }
        viewModelScope.launch(Safe.handler) {
            try {
                val doc = withContext(Dispatchers.IO) { reader.read(uri, fallbackName) }
                pendingPages = more
                onDocument(doc)
            } catch (ex: IngestException) {
                _state.update { it.copy(phase = ImportPhase.Failed(ex.error)) }
            } catch (ex: Exception) {
                _state.update { it.copy(phase = ImportPhase.Failed(IngestError.Unreadable)) }
            }
        }
    }

    private fun mutate(t: (ImportDraft) -> ImportDraft) { _state.value.draft?.let { setDraft(t(it)) } }

    private fun setDraft(d: ImportDraft) {
        _state.update { it.copy(draft = d, issues = ImportDrafts.validate(d, today)) }
        refreshConflicts(d)
    }

    private fun refreshConflicts(d: ImportDraft) {
        val date = d.reportDate ?: run { _state.update { it.copy(conflicts = emptyMap()) }; return }
        val keys = d.labRows.mapNotNull { it.analyteId }.distinct().map { it to date }
        viewModelScope.launch(Safe.handler) {
            val found = runCatching { session.stack.records.conflicting(d.memberId, keys) }.getOrDefault(emptyList())
            _state.update { s -> if (s.draft?.reportDate == date && s.draft.memberId == d.memberId) s.copy(conflicts = found.associateBy { it.analyteId }) else s }
        }
    }

    private fun save(fallbackTitle: String) {
        val s = _state.value
        val d = s.draft ?: return
        if (s.phase == ImportPhase.Saving) return
        val issues = ImportDrafts.validate(d, today)
        if (issues.isNotEmpty()) { _state.update { it.copy(issues = issues, showIssues = true) }; return }
        _state.update { it.copy(phase = ImportPhase.Saving, saveFailed = false) }
        viewModelScope.launch(Safe.handler) {
            try {
                val i = current
                if (i != null) {
                    // one report of a batch: mark it saved and go back to the list (or finish when it was the last)
                    groups[i] = groups[i].copy(draft = d, memberId = d.memberId, saved = true, reportId = persist(d, documents.toList(), fallbackTitle))
                    current = null
                    finishBatchStep(false)
                    return@launch
                }
                val (bundle, attachments) = ImportDrafts.toBundle(d, ids, clock.instant(), clock.zone, today, fallbackTitle)
                // attachments are in the same order as the documents that produced them
                val files = attachments.zip(documents).map { (a, doc) -> NewAttachment(a, doc.bytes) }
                session.stack.records.saveImport(bundle, files)
                session.select(d.memberId)
                document = null
                val name = s.members.firstOrNull { it.id == d.memberId }?.name ?: s.member?.name.orEmpty()
                _state.update { it.copy(phase = ImportPhase.Saved(bundle.report.id, name)) }
            } catch (ex: Exception) {
                _state.update { it.copy(phase = ImportPhase.Review, saveFailed = true) }
            }
        }
    }
}
