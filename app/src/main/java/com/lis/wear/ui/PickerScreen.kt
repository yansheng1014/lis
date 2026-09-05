package com.lis.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import java.io.File

@Composable
fun PickerScreen(
    files: List<File>,
    scanning: Boolean,
    onImport: (File) -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) {
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader { Text("选择文件", style = MaterialTheme.typography.titleMedium) }
            }

            if (scanning) {
                item { Text("正在扫描…", style = MaterialTheme.typography.bodyMedium) }
            } else if (files.isEmpty()) {
                item { Text("没有找到书籍文件", style = MaterialTheme.typography.bodyMedium) }
                item { Text("请把 txt / epub 放到 Download 或 Documents 目录", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(files, key = { it.absolutePath }) { file ->
                    TitleCard(
                        onClick = { onImport(file) },
                        title = {
                            Text(
                                file.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    ) {
                        Text(file.parent ?: "")
                    }
                }
            }

            item {
                Button(onClick = onRescan) {
                    Text("重新扫描")
                }
            }
            item {
                Button(onClick = onBack) {
                    Text("返回")
                }
            }
        }
    }
}