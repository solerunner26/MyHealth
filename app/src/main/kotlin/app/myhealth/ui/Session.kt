package app.myhealth.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.myhealth.AppContainer
import app.myhealth.data.DataStack
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Relationship
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
}

val LocalSession = staticCompositionLocalOf<Session> { error("Session not provided") }
