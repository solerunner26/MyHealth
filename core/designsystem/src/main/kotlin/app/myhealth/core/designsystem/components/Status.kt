package app.myhealth.core.designsystem.components

import androidx.compose.ui.graphics.vector.ImageVector

/** Fill weight: Subtle = outline only, Medium = tinted container, Strong = solid accent. */
enum class Emphasis { Subtle, Medium, Strong }

/** Visual status vocabulary. Each status has a distinct icon AND emphasis; text label comes from the caller. */
enum class StatusKind(val icon: ImageVector, val emphasis: Emphasis) {
    Normal(MyHealthIcons.StatusNormal, Emphasis.Subtle),
    Low(MyHealthIcons.StatusLow, Emphasis.Medium),
    High(MyHealthIcons.StatusHigh, Emphasis.Medium),
    Critical(MyHealthIcons.StatusCritical, Emphasis.Strong),
    Improving(MyHealthIcons.TrendUp, Emphasis.Subtle),
    Worsening(MyHealthIcons.TrendDown, Emphasis.Medium),
    Stable(MyHealthIcons.TrendFlat, Emphasis.Subtle),
    Unknown(MyHealthIcons.StatusUnknown, Emphasis.Subtle),
    Info(MyHealthIcons.Info, Emphasis.Subtle),
    Attention(MyHealthIcons.StatusWarning, Emphasis.Medium),
}
