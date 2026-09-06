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
import com.lis.wear.fs.ShizukuFiles

/** 文件浏览：扫出来的手表本地书籍，点一下导入。 */
@Composable
fun PickerScreen(
    files: List<ShizukuFiles.RemoteFile>,
    busy: Boolean,
    shizukuReady: Boolean,
    onImport: (ShizukuFiles.RemoteFile) -> Unit,
    onRescan: () -> Unit,
    onGrantShizuku: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = if (shizukuReady) onRescan else onGrantShizuku,
                buttonSize = EdgeButtonSize.Small,
                enabled = !busy,
            ) {
                Text(if (shizukuReady) "重新扫描" else "授权 Shizuku")
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
                        text = if (shizukuReady) {
                            "没找到 txt / epub\n放到 Download 或 Documents 再试"
                        } else {
                            "需要 Shizuku 才能读取手表存储\n点下方按钮授权"
                        },
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

private fun ShizukuFiles.RemoteFile.humanSize(): String = when {
    size < 0 -> parent
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${size / 1024} KB"
    else -> "${"%.1f".format(size / 1024f / 1024f)} MB"
}