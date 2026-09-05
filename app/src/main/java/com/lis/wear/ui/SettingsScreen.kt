package com.lis.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Text
import com.lis.wear.model.PlayerState

@Composable
fun SettingsScreen(
    state: PlayerState,
    storageGranted: Boolean,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onScan: () -> Unit,
    onOpenPicker: () -> Unit,
    onGrantShizuku: () -> Unit,
    onOpenAllFilesSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) {
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader { Text("设置", style = MaterialTheme.typography.titleMedium) }
            }

            item {
                Text("语速 ${"%.1f".format(state.speed)}x", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = state.speed,
                    onValueChange = onSpeed,
                    valueRange = 0.4f..3.0f,
                    steps = 12,
                )
            }

            item {
                Text("音调 ${"%.1f".format(state.pitch)}", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = state.pitch,
                    onValueChange = onPitch,
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                )
            }

            item {
                Text(
                    if (storageGranted) "存储已授权" else "存储未授权",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Button(onClick = onGrantShizuku) {
                    Text("用 Shizuku 授权")
                }
            }
            item {
                Button(onClick = onOpenAllFilesSettings) {
                    Text("系统设置里授权")
                }
            }
            item {
                Button(onClick = onOpenPicker) {
                    Text("扫描并选择文件")
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