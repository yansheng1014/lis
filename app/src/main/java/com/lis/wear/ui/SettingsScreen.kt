package com.lis.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.lis.wear.R
import com.lis.wear.model.PlayerState

/**
 * 设置：语速 / 音调用 Material 3 Slider（自带 +/- 端点，符合 Wear 规范），
 * 存储状态用带图标的状态卡展示，Shizuku 授权后立即刷新。
 */
@Composable
fun SettingsScreen(
    state: PlayerState,
    storageReady: Boolean,
    shizukuRunning: Boolean,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onOpenPicker: () -> Unit,
    onGrantShizuku: () -> Unit,
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
                ListSubHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text("朗读") }
            }

            item {
                Slider(
                    value = state.speed,
                    onValueChange = onSpeed,
                    valueRange = 0.5f..2.5f,
                    steps = 7,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                )
            }
            item {
                Text(
                    "语速 ${"%.1f".format(state.speed)}×",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Slider(
                    value = state.pitch,
                    onValueChange = onPitch,
                    valueRange = 0.6f..1.6f,
                    steps = 9,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                )
            }
            item {
                Text(
                    "音调 ${"%.1f".format(state.pitch)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                ListSubHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text("文件访问") }
            }

            item {
                FilledTonalButton(
                    onClick = onGrantShizuku,
                    icon = {
                        Icon(
                            painter = painterResource(
                                if (storageReady) R.drawable.ic_check else R.drawable.ic_lock
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    label = {
                        Text(
                            when {
                                storageReady -> "Shizuku 已就绪"
                                shizukuRunning -> "点此授权 Shizuku"
                                else -> "未检测到 Shizuku"
                            },
                            maxLines = 1,
                        )
                    },
                    secondaryLabel = {
                        Text(
                            if (storageReady) "可直接扫描手表文件" else "先在手表启动 Shizuku",
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
        }
    }
}