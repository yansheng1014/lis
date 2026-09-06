package com.lis.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.VerticalPagerScaffold
import com.lis.wear.model.PlayerState
import com.lis.wear.model.SleepTimer

/**
 * 播放页手势结构（全部原生 Wear 分页动画）：
 *
 *  垂直分页
 *   ├─ 第 0 屏：水平分页
 *   │    ├─ 左：控制盘（进度环 + 上一章/播放/下一章）
 *   │    └─ 右：当前朗读整段正文
 *   └─ 第 1 屏：播放设置（语速/音调/章节/定时）——向上滑进入
 */
@Composable
fun PlayerPager(
    state: PlayerState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onSleepTimer: (SleepTimer) -> Unit,
    onOpenChapters: () -> Unit,
) {
    val verticalState = rememberPagerState { 2 }

    VerticalPagerScaffold(pagerState = verticalState) {
        VerticalPager(state = verticalState) { vPage ->
            AnimatedPage(pageIndex = vPage, pagerState = verticalState) {
                when (vPage) {
                    0 -> ControlAndText(
                        state = state,
                        onToggle = onToggle,
                        onNext = onNext,
                        onPrevious = onPrevious,
                    )
                    else -> PlayerOptionsScreen(
                        state = state,
                        onSpeed = onSpeed,
                        onPitch = onPitch,
                        onSleepTimer = onSleepTimer,
                        onOpenChapters = onOpenChapters,
                        onBack = { /* swipe down returns; button is a hint */ },
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlAndText(
    state: PlayerState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val horizontalState = rememberPagerState { 2 }
    HorizontalPagerScaffold(pagerState = horizontalState) {
        HorizontalPager(state = horizontalState) { page ->
            AnimatedPage(pageIndex = page, pagerState = horizontalState) {
                when (page) {
                    0 -> PlayerScreen(
                        state = state,
                        onToggle = onToggle,
                        onNext = onNext,
                        onPrevious = onPrevious,
                    )
                    else -> ReadingTextPage(state = state)
                }
            }
        }
    }
}

/** 正文页：随播放自动刷新的整句。 */
@Composable
private fun ReadingTextPage(state: PlayerState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.currentSentence.ifBlank {
                if (state.hasBook) "本章还没有开始朗读" else "还没有选择书籍"
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
    }
}