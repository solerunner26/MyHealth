package app.myhealth.ui.importer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.importing.Pt
import app.myhealth.domain.importing.Quad
import app.myhealth.ingest.PageCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shown after a photo is taken: the detected page outline over the photo, four draggable corners, then the page is
 * straightened. "Use full photo" keeps the original. Runs entirely on the phone.
 */
@Composable
fun CropDialog(file: File, onDone: (cropped: Boolean) -> Unit) {
    var preview by remember(file) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var quad by remember(file) { mutableStateOf<Quad?>(null) }
    var found by remember(file) { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(file) {
        val (bmp, q) = withContext(Dispatchers.Default) { PageCrop.load(file, 1200).let { it to it?.let(PageCrop::detect) } }
        preview = bmp; found = q != null; quad = q ?: PageCrop.fallback
        if (bmp == null) onDone(false)
    }
    var finish by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(finish) {
        val f = finish ?: return@LaunchedEffect
        val ok = if (f) withContext(Dispatchers.IO) { quad?.let { PageCrop.cropInPlace(file, it) } ?: false } else false
        onDone(ok)
    }
    Dialog(onDismissRequest = { finish = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MyHealthCard(Modifier.padding(16.dp).testTag("cropDialog")) {
            Column(verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) {
                Text(stringResource(R.string.crop_title), style = MyHealthTheme.typography.title, color = MyHealthTheme.colors.textPrimary)
                Text(stringResource(if (found) R.string.crop_body else R.string.crop_none), style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary)
                val bmp = preview; val q = quad
                if (bmp != null && q != null) {
                    val img = remember(bmp) { bmp.asImageBitmap() }
                    val accent = MyHealthTheme.colors.accentBright
                    val cornerCd = (1..4).map { stringResource(R.string.crop_corner, it) }
                    Box(Modifier.fillMaxWidth().heightIn(max = 460.dp).aspectRatio(bmp.width / bmp.height.toFloat())) {
                        Image(img, null, Modifier.matchParentSize())
                        Canvas(Modifier.matchParentSize().semantics { contentDescription = cornerCd.joinToString() }.pointerInput(Unit) {
                            var active = -1
                            detectDragGestures(
                                onDragStart = { o ->
                                    val pts = quad!!.points.map { Offset(it.x * size.width, it.y * size.height) }
                                    active = pts.indices.minBy { (pts[it] - o).getDistance() }
                                },
                                onDragEnd = { active = -1 },
                            ) { change, drag ->
                                change.consume()
                                val cur = quad ?: return@detectDragGestures
                                if (active < 0) return@detectDragGestures
                                val p = cur.points[active]
                                val np = Pt((p.x + drag.x / size.width).coerceIn(0f, 1f), (p.y + drag.y / size.height).coerceIn(0f, 1f))
                                quad = when (active) { 0 -> cur.copy(tl = np); 1 -> cur.copy(tr = np); 2 -> cur.copy(br = np); else -> cur.copy(bl = np) }
                            }
                        }) {
                            val pts = q.points.map { Offset(it.x * size.width, it.y * size.height) }
                            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                            drawPath(path, accent.copy(alpha = 0.18f))
                            drawPath(path, accent, style = Stroke(3.dp.toPx()))
                            pts.forEach { drawCircle(accent, 12.dp.toPx(), it); drawCircle(androidx.compose.ui.graphics.Color.White, 5.dp.toPx(), it) }
                        }
                    }
                }
                MyHealthPrimaryButton(stringResource(R.string.crop_use), { working = true; finish = true }, Modifier.fillMaxWidth().testTag("cropUse"),
                    icon = MyHealthIcons.Check, enabled = !working && preview != null)
                MyHealthTextButton(stringResource(R.string.crop_keep), { working = true; finish = false }, Modifier.fillMaxWidth().testTag("cropKeep"), enabled = !working)
            }
        }
    }
}
