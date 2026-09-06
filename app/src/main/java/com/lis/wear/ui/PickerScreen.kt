package com.lis.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.lis.wear.fs.BookScanner

/** 文件浏览：扫到的手表本地书籍，点一下导入。 */
@Composable
fun PickerScreen(
    files: List<BookScanner.LocalFile>,
    busy: Boolean,
    storage: StorageStage,
    onImport: (BookScanner.LocalFile) -> Unit,
    onRescan: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onRescan,
                buttonSize = EdgeButtonSize.Small,
                enabled = !busy,
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(if (storage == StorageStage.READY) "重新扫描" else "授权并扫描")
                }
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text("手表文件") }
            }

            when {
                busy -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }

                files.isEmpty() -> item {
                    Text(
                        text = storage.emptyHint(),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }

                else -> items(files, key = { it.path }) { file ->
                    TitleCard(
                        onClick = { onImport(file) },
                        title = {
                            Text(
                                file.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        subtitle = { Text(file.humanSize(), maxLines = 1) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            }
        }
    }
}

private fun StorageStage.emptyHint(): String = when (this) {
    StorageStage.READY -> "没找到 txt / epub\n放到 Download 再扫一次"
    StorageStage.NEED_RESTART -> "已授权\n重开 Lis 后即可读取"
    StorageStage.NEED_GRANT -> "点下方按钮授权\n授权后无需再开 Shizuku"
    StorageStage.NEED_SHIZUKU -> "先在手表启动 Shizuku\n只需授权这一次"
}

private fun BookScanner.LocalFile.humanSize(): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${size / 1024} KB"
    else -> "${"%.1f".format(size / 1024f / 1024f)} MB"
}