package com.lis.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.lis.wear.model.PlayerState

@Composable
fun PlayerScreen(
    state: PlayerState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenSentences: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                state.bookTitle.ifBlank { "未选择书籍" },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.chapterTitle.ifBlank { "第 ${state.chapterIndex + 1}/${state.chapterCount} 章" },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "句 ${state.sentenceIndex + 1}/${state.sentences.size}",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious) {
                    Text("⏮", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onToggle) {
                    Text(
                        if (state.isPlaying) "⏸" else "▶",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                IconButton(onClick = onNext) {
                    Text("⏭", style = MaterialTheme.typography.titleLarge)
                }
            }

            Button(onClick = onOpenSentences) {
                Text("正在朗读的句子")
            }
            Button(onClick = onOpenChapters) {
                Text("章节列表")
            }
            Button(onClick = onOpenSettings) {
                Text("设置")
            }
            Button(onClick = onBack) {
                Text("返回书架")
            }
        }
    }
}