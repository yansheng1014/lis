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
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.lis.wear.R
import com.lis.wear.model.PlayerState

/**
 * 播放页：中央大号播放/暂停，两侧上一章/下一章，外圈用章节进度环。
 * 上下滑动或点击标题区域进入句子/章节列表。
 */
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
    val progress = if (state.sentences.isEmpty()) {
        0f
    } else {
        (state.sentenceIndex + 1).toFloat() / state.sentences.size
    }
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    ScreenScaffold {
        Box(modifier = Modifier.fillMaxSize()) {
            // 外圈：本章朗读进度
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize().padding(2.dp),
                strokeWidth = 4.dp,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.bookTitle.ifBlank { "未选择书籍" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = state.currentSentence.ifBlank {
                        state.chapterTitle.ifBlank { "点按播放开始朗读" }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(6.dp))

                ButtonGroup(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onPrevious,
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
                        modifier = Modifier
                            .weight(1.6f)
                            .size(IconButtonDefaults.LargeButtonSize),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (state.isPlaying) R.drawable.ic_pause_filled
                                else R.drawable.ic_play_arrow
                            ),
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(IconButtonDefaults.iconSizeFor(IconButtonDefaults.LargeButtonSize)),
                        )
                    }

                    IconButton(
                        onClick = onNext,
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

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "${state.chapterIndex + 1}/${state.chapterCount} 章 · " +
                        "${state.sentenceIndex + 1}/${state.sentences.size.coerceAtLeast(1)} 句",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}