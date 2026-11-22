package app.myhealth.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.myhealth.AppContainer
import app.myhealth.data.DataStack
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.analytics.FocusInputs
import app.myhealth.domain.model.DailyActivity
import app.myhealth.domain.model.DoseLog
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.Treatment
import app.myhealth.domain.model.Vital
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Per-unlock session: the open data stack plus the member whose records every screen is scoped to.
 * Switching the active member re-scopes all screens because they observe [activeMember].
 */
class Session(val container: AppContainer, val stack: DataStack) {
    private val scope = container.appScope

    /** null while loading. */
    val members: StateFlow<List<FamilyMember>?> = stack.members.observeAll().stateIn(scope, SharingStarted.Eagerly, null)

    val activeMember: StateFlow<FamilyMember?> = combine(members, container.preferences.state) { list, prefs ->
        list?.let { all -> all.firstOrNull { it.id == prefs.activeMemberId } ?: all.firstOrNull { it.relationship == Relationship.Self } ?: all.firstOrNull() }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun select(memberId: String) = container.preferences.setActiveMember(memberId)

    /** Everything about the active member, re-emitted on any change; drives Home, Trends and Insights. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val snapshot: Flow<MemberSnapshot> = activeMember.filterNotNull().distinctUntilChanged().flatMapLatest { m ->
        val since = container.clock.instant().minus(Duration.ofDays(40))
        combine(
            stack.records.observeResults(m.id), stack.vitals.observe(m.id), stack.medicines.observe(m.id),
            stack.medicines.observeDoseLogs(m.id, since), stack.treatments.observe(m.id), stack.activity.observe(m.id),
            container.preferences.state.map { it.historyYears }.distinctUntilChanged(),
        ) { a -> 
            @Suppress("UNCHECKED_CAST")
            MemberSnapshot(m, a[0] as List<LabResult>, a[1] as List<Vital>, a[2] as List<Medicine>, a[3] as List<DoseLog>, a[4] as List<Treatment>, a[5] as List<DailyActivity>, a[6] as Int)
        }
    }

    fun focusInputs(s: MemberSnapshot) = FocusInputs(s.member, LocalDate.now(container.clock), container.clock.zone, s.results, s.vitals, s.medicines, s.doseLogs, s.treatments, s.activity, s.historyYears)
}

data class MemberSnapshot(
    val member: FamilyMember, val results: List<LabResult>, val vitals: List<Vital>, val medicines: List<Medicine>,
    val doseLogs: List<DoseLog>, val treatments: List<Treatment>, val activity: List<DailyActivity>, val historyYears: Int,
)

val LocalSession = staticCompositionLocalOf<Session> { error("Session not provided") }
