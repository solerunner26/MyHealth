package app.myhealth.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.rememberBreath
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.model.FamilyMember
import java.time.LocalTime

/** Next member in the list after [current], wrapping around. */
fun nextMember(members: List<FamilyMember>, current: FamilyMember): FamilyMember? =
    if (members.size < 2) null else members[(members.indexOfFirst { it.id == current.id } + 1).mod(members.size)]

/**
 * Profile photo (or initials) with a glowing ring. Swipe DOWN to jump to the next family member (like switching
 * accounts by pulling the avatar); tap to open the full picker. TalkBack gets a "next profile" custom action.
 */
@Composable
fun SwipeAvatar(member: FamilyMember, photo: ImageBitmap?, canSwitch: Boolean, onNext: () -> Unit, onOpen: () -> Unit, size: Dp = 64.dp) {
    val c = MyHealthTheme.colors
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val breath = rememberBreath()
    val cd = stringResource(R.string.dash_switch_cd, member.name)
    val nextLabel = stringResource(R.string.dash_swipe_hint)
    Box(
        Modifier.size(size + 12.dp).testTag("swipeAvatar")
            .semantics {
                contentDescription = cd
                if (canSwitch) customActions = listOf(CustomAccessibilityAction(nextLabel) { onNext(); true })
            }
            .pointerInput(member.id, canSwitch) {
                detectVerticalDragGestures(
                    onDragEnd = { if (drag > threshold && canSwitch) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onNext() }; drag = 0f },
                    onDragCancel = { drag = 0f },
                ) { change, dy -> change.consume(); drag = (drag + dy).coerceIn(0f, threshold * 1.6f) }
            }
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        // halo
        Box(Modifier.size(size + 12.dp).clip(CircleShape).background(Brush.radialGradient(listOf(c.glow.copy(alpha = c.glow.alpha * (0.6f + 0.4f * breath)), androidx.compose.ui.graphics.Color.Transparent))))
        AnimatedContent(member to photo, transitionSpec = {
            (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
        }, contentKey = { it.first.id }, label = AVATAR) { (m, p) ->
            Box(
                Modifier.size(size).graphicsLayer { translationY = drag * 0.45f; alpha = 1f - (drag / (threshold * 3f)); rotationZ = drag / threshold * 6f }
                    .shadow(10.dp, CircleShape, ambientColor = c.glow, spotColor = c.glow).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(c.accent, c.accentBright)))
                    .border(2.dp, Brush.sweepGradient(listOf(c.accentBright, c.accent, c.accentBright)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (p != null) Image(p, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(m.initials, style = MyHealthTheme.typography.title, color = c.onAccent)
            }
        }
        if (canSwitch) Icon(MyHealthIcons.Expand, null, tint = c.accent, modifier = Modifier.align(Alignment.BottomCenter).size(18.dp).graphicsLayer { translationY = 8.dp.toPx() + drag * 0.2f })
    }
}

private const val AVATAR = "avatar" // animation inspector id, not UI copy

@Composable
fun DashboardHeader(member: FamilyMember, photo: ImageBitmap?, members: List<FamilyMember>, onSwitch: (String) -> Unit, onQuickSettings: () -> Unit = {}, onOpenPicker: () -> Unit) {
    val c = MyHealthTheme.colors
    val greeting = when (LocalTime.now().hour) { in 5..11 -> R.string.dash_greeting_morning; in 12..16 -> R.string.dash_greeting_afternoon; else -> R.string.dash_greeting_evening }
    val next = nextMember(members, member)
    var hintSeen by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = MyHealthTheme.spacing.gutter, vertical = MyHealthTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwipeAvatar(member, photo, canSwitch = next != null, onNext = { next?.let { hintSeen = true; onSwitch(it.id) } }, onOpen = onOpenPicker)
        Spacer(Modifier.width(MyHealthTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            Text(stringResource(greeting), style = MyHealthTheme.typography.caption, color = c.textSecondary)
            Text(member.name, style = MyHealthTheme.typography.headline, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (next != null && !hintSeen) Text(stringResource(R.string.dash_swipe_hint), style = MyHealthTheme.typography.caption, color = c.accent, maxLines = 2)
        }
        // item 5: the animated logo opens quick settings (theme, dark mode, language)
        val cd = stringResource(R.string.qs_title)
        Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = cd, onClick = onQuickSettings).testTag("logoQuickSettings"), contentAlignment = Alignment.Center) {
            app.myhealth.ui.common.MyHealthLogo(36.dp, alive = true)
        }
    }
}
