package com.lis.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.lis.wear.R

/**
 * 设置：只保留存储授权。语速/音调/章节/定时都在播放页上滑面板里。
 */
@Composable
fun SettingsScreen(
    storage: StorageStage,
    busy: Boolean,
    onPrepareStorage: () -> Unit,
    onOpenPicker: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onBack, buttonSize = EdgeButtonSize.Small) {
                Text("完成")
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
                ) { Text("设置") }
            }

            item {
                val ready = storage == StorageStage.READY
                FilledTonalButton(
                    onClick = onPrepareStorage,
                    enabled = !busy && !ready,
                    icon = {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                painter = painterResource(
                                    if (ready) R.drawable.ic_check else R.drawable.ic_lock
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    label = { Text(storage.title(), maxLines = 1) },
                    secondaryLabel = { Text(storage.subtitle(), maxLines = 2) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }

            item {
                FilledTonalButton(
                    onClick = onOpenPicker,
                    enabled = !busy,
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    label = { Text("浏览手表文件") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }

            item {
                Text(
                    "语速、音调、章节、定时在播放页向上滑设置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
        }
    }
}

private fun StorageStage.title(): String = when (this) {
    StorageStage.READY -> "存储已就绪"
    StorageStage.NEED_RESTART -> "重启应用生效"
    StorageStage.NEED_GRANT -> "点此授权存储"
    StorageStage.NEED_SHIZUKU -> "未检测到 Shizuku"
}

private fun StorageStage.subtitle(): String = when (this) {
    StorageStage.READY -> "已不再依赖 Shizuku"
    StorageStage.NEED_RESTART -> "权限已写入，重开即可"
    StorageStage.NEED_GRANT -> "授权一次，永久有效"
    StorageStage.NEED_SHIZUKU -> "先在手表启动 Shizuku"
}