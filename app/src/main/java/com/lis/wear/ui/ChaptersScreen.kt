package com.lis.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import com.lis.wear.model.PlayerState

@Composable
fun ChaptersScreen(
    state: PlayerState,
    initialSelection: Int,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val titles = remember(state.bookId) {
        // We only have the current chapter's title and a count; render stable labels.
        List(state.chapterCount) { index ->
            if (index == state.chapterIndex && state.chapterTitle.isNotBlank()) {
                state.chapterTitle
            } else {
                "第 ${index + 1} 章"
            }
        }
    }

    LaunchedEffect(initialSelection) {
        if (initialSelection >= 0 && initialSelection < titles.size) {
            listState.scrollToItem(initialSelection)
        }
    }

    ScreenScaffold(scrollState = listState) {
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader { Text("章节", style = MaterialTheme.typography.titleMedium) }
            }
            itemsIndexed(titles) { index, title ->
                val isCurrent = index == state.chapterIndex
                TitleCard(
                    onClick = { onSelect(index) },
                    title = {
                        Text(
                            text = (if (isCurrent) "▶ " else "") + title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                ) {
                    Text(if (isCurrent) "当前章节" else "点击跳转")
                }
            }
        }
    }
}