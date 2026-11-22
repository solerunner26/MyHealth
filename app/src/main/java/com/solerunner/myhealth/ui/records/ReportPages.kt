package com.solerunner.myhealth.ui.records

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.rare.RareDivider
import com.solerunner.myhealth.design.rare.RareSpinner
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Attachment

/** Horizontal strip of decrypted page thumbnails; tapping one opens the full page. */
@Composable
fun ReportPagesStrip(
    container: AppContainer,
    attachments: List<Attachment>,
    onOpen: (Attachment) -> Unit,
) {
    val visible = attachments.filter { it.mimeType.startsWith("image/") || it.mimeType == "application/pdf" }
    if (visible.isEmpty()) {
        Text(
            stringResource(R.string.records_no_pages),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(visible, key = { it.id }) { attachment ->
            PageThumbnail(container = container, attachment = attachment, onClick = { onOpen(attachment) })
        }
    }
}

@Composable
private fun PageThumbnail(container: AppContainer, attachment: Attachment, onClick: () -> Unit) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, attachment) {
        value = ReportPageRenderer.render(container, attachment)
    }
    Column(
        modifier = Modifier.width(104.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(width = 104.dp, height = 132.dp),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image == null) {
                RareSpinner(size = 18)
            } else {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = stringResource(R.string.records_view_page),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(132.dp),
                )
            }
        }
        Text(
            stringResource(R.string.records_page_label, attachment.pageIndex.coerceAtLeast(1)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Full-page preview shown in a dialog when a thumbnail is tapped. */
@Composable
fun ReportPageDialog(
    container: AppContainer,
    attachment: Attachment,
    onDismiss: () -> Unit,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, attachment) {
        value = ReportPageRenderer.render(container, attachment, maxDimension = 2400)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
        text = {
            val image = bitmap
            if (image == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { RareSpinner() }
            } else {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = stringResource(R.string.records_view_page),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
