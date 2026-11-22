package app.myhealth.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myhealth.core.Safe
import app.myhealth.domain.analytics.FocusEngine
import app.myhealth.domain.analytics.FocusItem
import app.myhealth.domain.family.FamilyHistory
import app.myhealth.domain.family.VaccineSchedule
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.HealthCard
import app.myhealth.domain.model.Media
import app.myhealth.domain.model.MediaKind
import app.myhealth.domain.model.RelativeCondition
import app.myhealth.domain.model.TreatmentKind
import app.myhealth.domain.model.Vaccination
import app.myhealth.domain.model.VisitQuestion
import app.myhealth.domain.summary.VisitPrep
import app.myhealth.domain.util.UuidIds
import app.myhealth.reminders.VaccineReminders
import app.myhealth.ui.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---------------------------------------------------------------- vaccinations

data class VaccinationsState(
    val loading: Boolean = true,
    val member: FamilyMember? = null,
    val due: List<Vaccination> = emptyList(),
    val upcoming: List<Vaccination> = emptyList(),
    val given: List<Vaccination> = emptyList(),
    val canSuggest: Boolean = false,
) { val isEmpty get() = due.isEmpty() && upcoming.isEmpty() && given.isEmpty() }

@OptIn(ExperimentalCoroutinesApi::class)
class VaccinationsViewModel(private val session: Session) : ViewModel() {
    private val clock = session.container.clock
    private val today get() = LocalDate.now(clock)

    val state: StateFlow<VaccinationsState> = session.activeMember.filterNotNull().flatMapLatest { m ->
        session.stack.vaccinations.observe(m.id).map { list ->
            val t = today
            VaccinationsState(
                false, m,
                due = list.filter { it.isDue(t) }.sortedBy { it.dueDate },
                upcoming = list.filter { !it.given && !it.isDue(t) }.sortedBy { it.dueDate ?: LocalDate.MAX },
                given = list.filter { it.given }.sortedByDescending { it.givenDate },
                canSuggest = m.dateOfBirth != null,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaccinationsState())

    fun save(v: Vaccination) = launch { session.stack.vaccinations.upsert(v) }
    fun markGiven(v: Vaccination) = save(v.copy(givenDate = today))
    fun delete(v: Vaccination) = launch { session.stack.media.deleteForOwner(v.id); session.stack.vaccinations.delete(v.id); VaccineReminders(session.container.context, clock).cancel(v.id) }
    fun addSuggested() = launch {
        val m = state.value.member ?: return@launch
        val list = VaccineSchedule.suggest(m, session.stack.vaccinations.all(m.id), UuidIds, today, clock.millis())
        session.stack.vaccinations.upsertAll(list)
    }
    fun addCertificate(v: Vaccination, jpeg: ByteArray) = launch {
        session.stack.media.add(Media(UuidIds.next(), v.memberId, v.id, MediaKind.VaccineCertificate, clock.millis()), jpeg)
    }
    fun newId() = UuidIds.next()
    fun now() = clock.millis()

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO + Safe.handler) {
        block()
        VaccineReminders(session.container.context, clock).rescheduleAll(session.stack)
    }
}

// ---------------------------------------------------------------- health cards

data class CardsState(val loading: Boolean = true, val member: FamilyMember? = null, val cards: List<HealthCard> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class HealthCardsViewModel(private val session: Session) : ViewModel() {
    private val clock = session.container.clock
    val state: StateFlow<CardsState> = session.activeMember.filterNotNull().flatMapLatest { m ->
        session.stack.healthCards.observe(m.id).map { CardsState(false, m, it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardsState())

    fun save(c: HealthCard) = io { session.stack.healthCards.upsert(c) }
    fun delete(c: HealthCard) = io { session.stack.media.deleteForOwner(c.id); session.stack.healthCards.delete(c.id) }
    fun addImage(c: HealthCard, kind: MediaKind, jpeg: ByteArray) = io {
        // one front and one back: a new photo replaces the old one
        val old = session.stack.media.observe(c.id).first().filter { it.kind == kind }
        session.stack.media.add(Media(UuidIds.next(), c.memberId, c.id, kind, clock.millis()), jpeg)
        old.forEach { session.stack.media.delete(it.id) }
    }
    fun newId() = UuidIds.next()
    fun now() = clock.millis()
    private fun io(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO + Safe.handler) { block() }
}

// ---------------------------------------------------------------- family history

data class FamilyState(val loading: Boolean = true, val members: List<FamilyMember> = emptyList(), val relatives: List<RelativeCondition> = emptyList())

class FamilyHistoryViewModel(private val session: Session) : ViewModel() {
    private val clock = session.container.clock
    val state: StateFlow<FamilyState> = combine(session.members.filterNotNull(), session.stack.familyHistory.observe()) { m, r -> FamilyState(false, m, r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FamilyState())
    fun add(relation: String, condition: String, onset: Int?) = viewModelScope.launch(Dispatchers.IO + Safe.handler) {
        session.stack.familyHistory.upsert(RelativeCondition(UuidIds.next(), relation.trim().take(60), condition.trim().take(80), onset, createdAtEpochMs = clock.millis()))
    }
    fun delete(id: String) = viewModelScope.launch(Dispatchers.IO + Safe.handler) { session.stack.familyHistory.delete(id) }
    fun rows(state: FamilyState, relationLabel: (FamilyMember) -> String) = FamilyHistory.map(state.members, state.relatives, relationLabel)
}

// ---------------------------------------------------------------- visit preparation

data class VisitState(val loading: Boolean = true, val member: FamilyMember? = null, val since: LocalDate? = null, val prep: VisitPrep? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class VisitPrepViewModel(private val session: Session) : ViewModel() {
    private val clock = session.container.clock
    private val since = MutableStateFlow<LocalDate?>(null)

    val state: StateFlow<VisitState> = session.snapshot.flatMapLatest { s ->
        combine(session.stack.visitQuestions.observe(s.member.id), since) { questions, chosen ->
            val today = LocalDate.now(clock)
            val lastVisit = s.treatments.filter { it.kind == TreatmentKind.Consultation && !it.date.isAfter(today) }.maxOfOrNull { it.date }
            val from = chosen ?: VisitPrep.defaultSince(lastVisit, today)
            val focus: List<FocusItem> = FocusEngine.evaluate(session.focusInputs(s))
            VisitState(false, s.member, from, VisitPrep.build(from, s.results, s.vitals, focus, s.medicines, questions, clock.zone))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisitState())

    fun setSince(d: LocalDate) { since.value = d }
    fun addQuestion(text: String) {
        val m = state.value.member ?: return
        val t = text.trim().take(300).ifBlank { return }
        viewModelScope.launch(Dispatchers.IO + Safe.handler) { session.stack.visitQuestions.upsert(VisitQuestion(UuidIds.next(), m.id, t, createdAtEpochMs = clock.millis())) }
    }
    fun toggle(q: VisitQuestion) = viewModelScope.launch(Dispatchers.IO + Safe.handler) { session.stack.visitQuestions.upsert(q.copy(done = !q.done)) }
    fun delete(q: VisitQuestion) = viewModelScope.launch(Dispatchers.IO + Safe.handler) { session.stack.visitQuestions.delete(q.id) }
}
