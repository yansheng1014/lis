package com.lis.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
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
import com.lis.wear.model.SleepTimer

/**
 * 播放选项：语速 / 音调 / 章节 / 定时。从播放页往上滑进入。
 *
 * 数值标签放在 Slider 上方并直接绑定 [state]，所以拖动后立刻刷新。
 */
@Composable
fun PlayerOptionsScreen(
    state: PlayerState,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onSleepTimer: (SleepTimer) -> Unit,
    onOpenChapters: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onBack, buttonSize = EdgeButtonSize.Small) {
                Text("返回播放")
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
                ) { Text("播放设置") }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "语速 ${"%.1f".format(state.speed)}×",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Slider(
                        value = state.speed,
                        onValueChange = onSpeed,
                        valueRange = 0.5f..2.5f,
                        steps = 7,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "音调 ${"%.1f".format(state.pitch)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Slider(
                        value = state.pitch,
                        onValueChange = onPitch,
                        valueRange = 0.6f..1.6f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                FilledTonalButton(
                    onClick = onOpenChapters,
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_library),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    label = { Text("选择章节") },
                    secondaryLabel = {
                        Text("共 ${state.chapterCount} 章", maxLines = 1)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }

            item {
                ListSubHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text("定时停止") }
            }

            items(SleepTimer.entries.size) { index ->
                val timer = SleepTimer.entries[index]
                val selected = state.sleepTimer == timer
                FilledTonalButton(
                    onClick = { onSleepTimer(timer) },
                    icon = if (selected) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                    label = { Text(timer.label, maxLines = 1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
        }
    }
}