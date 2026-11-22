package app.myhealth.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.ui.graphics.vector.ImageVector

/** Semantic icon set. Screens refer to meaning (MyHealthIcons.Labs), not to a glyph. */
object MyHealthIcons {
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Forward: ImageVector = Icons.AutoMirrored.Filled.ArrowForward
    val Add = Icons.Filled.Add
    val Close = Icons.Filled.Close
    val Check = Icons.Filled.Check
    val Expand = Icons.Filled.KeyboardArrowDown
    val Home = Icons.Outlined.Home
    val Records = Icons.AutoMirrored.Outlined.ListAlt
    val Trends = Icons.Outlined.Insights
    val Insights = Icons.Outlined.HealthAndSafety
    val Labs = Icons.Outlined.Science
    val Vitals = Icons.Outlined.MonitorHeart
    val Medicines = Icons.Outlined.Medication
    val Treatments = Icons.Outlined.Healing
    val Document = Icons.Outlined.Description
    val Import = Icons.Outlined.UploadFile
    val Camera = Icons.Outlined.CameraAlt
    val Search = Icons.Outlined.Search
    val Settings = Icons.Outlined.Settings
    val Profile = Icons.Outlined.Person
    val Family = Icons.Outlined.FamilyRestroom
    val Edit = Icons.Outlined.Edit
    val Delete = Icons.Outlined.Delete
    val Erase = Icons.Outlined.DeleteForever
    val Share = Icons.Outlined.Share
    val Pdf = Icons.Outlined.PictureAsPdf
    val Csv = Icons.Outlined.TableChart
    val Backup = Icons.Outlined.Backup
    val Restore = Icons.Outlined.Restore
    val Lock = Icons.Outlined.Lock
    val Biometric = Icons.Outlined.Fingerprint
    val Privacy = Icons.Outlined.VisibilityOff
    val Language = Icons.Outlined.Language
    val Theme = Icons.Outlined.Palette
    val Reminder = Icons.Outlined.Notifications
    val Schedule = Icons.Outlined.Schedule
    val Date = Icons.Outlined.Event
    val Info = Icons.Outlined.Info
    val Help = Icons.Outlined.HelpOutline
    val Offline = Icons.Outlined.CloudOff
    val Sync = Icons.Outlined.Sync
    val Copy = Icons.Outlined.ContentCopy
    val Heart = Icons.Outlined.Favorite
    val Activity = Icons.Outlined.DirectionsWalk
    val Bug = Icons.Outlined.BugReport

    // Status glyphs: status is shown by shape, never by hue.
    val StatusNormal = Icons.Filled.CheckCircle
    val StatusHigh = Icons.Filled.ArrowUpward
    val StatusLow = Icons.Filled.ArrowDownward
    val StatusCritical = Icons.Filled.PriorityHigh
    val StatusError = Icons.Filled.Error
    val StatusWarning = Icons.Outlined.WarningAmber
    val StatusUnknown = Icons.Outlined.HelpOutline
    val TrendUp = Icons.AutoMirrored.Filled.TrendingUp
    val TrendDown = Icons.AutoMirrored.Filled.TrendingDown
    val TrendFlat = Icons.AutoMirrored.Filled.TrendingFlat
}
