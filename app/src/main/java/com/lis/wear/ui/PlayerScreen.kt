package com.lis.wear.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.lis.wear.R
import com.lis.wear.model.PlayerState

/**
 * 播放控制盘（横向分页第 1 页）。
 *
 * 布局自上而下：书名 → 章节·序号 → 控制组 → 状态提示。
 * 正文放在左滑的第 2 页，所以这里给播放按钮留足空间。
 */
@Composable
fun PlayerScreen(
    state: PlayerState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.chapterProgress,
        label = "chapterProgress",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize().padding(3.dp),
            strokeWidth = 4.dp,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = state.bookTitle.ifBlank { "未选择书籍" },
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = if (state.hasBook) {
                    "${state.titleForChapter(state.chapterIndex)} · " +
                        "${state.chapterIndex + 1}/${state.chapterCount}"
                } else {
                    "去书架选一本书"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            ButtonGroup(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onPrevious,
                    enabled = state.hasBook,
                    modifier = Modifier
                        .weight(1f)
                        .size(IconButtonDefaults.SmallButtonSize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_previous),
                        contentDescription = "上一章",
                        modifier = Modifier.size(20.dp),
                    )
                }

                FilledIconButton(
                    onClick = onToggle,
                    enabled = state.hasBook,
                    modifier = Modifier
                        .weight(1.7f)
                        .size(IconButtonDefaults.LargeButtonSize),
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.isPlaying) R.drawable.ic_pause_filled
                            else R.drawable.ic_play_arrow
                        ),
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(
                            IconButtonDefaults.iconSizeFor(IconButtonDefaults.LargeButtonSize)
                        ),
                    )
                }

                IconButton(
                    onClick = onNext,
                    enabled = state.hasBook,
                    modifier = Modifier
                        .weight(1f)
                        .size(IconButtonDefaults.SmallButtonSize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = "下一章",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = when {
                    state.isPreparing -> "准备中…"
                    !state.hasBook -> ""
                    else ->
                        "${state.sentenceIndex + 1}/${state.sentenceTotal.coerceAtLeast(1)} 句 · 上滑设置"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}