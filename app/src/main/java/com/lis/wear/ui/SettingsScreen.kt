package com.lis.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.lis.wear.R

/**
 * 设置：只保留文件访问相关。语速/音调/章节/定时已移入播放页的上滑面板，
 * 避免功能重复。
 */
@Composable
fun SettingsScreen(
    shizukuReady: Boolean,
    shizukuRunning: Boolean,
    onGrantShizuku: () -> Unit,
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
                FilledTonalButton(
                    onClick = onGrantShizuku,
                    icon = {
                        Icon(
                            painter = painterResource(
                                if (shizukuReady) R.drawable.ic_check else R.drawable.ic_lock
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    label = {
                        Text(
                            when {
                                shizukuReady -> "Shizuku 已就绪"
                                shizukuRunning -> "点此授权 Shizuku"
                                else -> "未检测到 Shizuku"
                            },
                            maxLines = 1,
                        )
                    },
                    secondaryLabel = {
                        Text(
                            if (shizukuReady) "可直接扫描手表文件" else "先在手表启动 Shizuku",
                            maxLines = 1,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }

            item {
                FilledTonalButton(
                    onClick = onOpenPicker,
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
                    style = androidx.wear.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.wear.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
        }
    }
}