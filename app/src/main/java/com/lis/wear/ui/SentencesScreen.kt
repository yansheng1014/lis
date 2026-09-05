package com.lis.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
fun SentencesScreen(
    state: PlayerState,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val sentences = state.sentences

    LaunchedEffect(state.sentenceIndex) {
        listState.scrollToItem(state.sentenceIndex)
    }

    ScreenScaffold(scrollState = listState) {
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader { Text("正在朗读", style = MaterialTheme.typography.titleMedium) }
            }
            itemsIndexed(sentences) { index, sentence ->
                val isCurrent = index == state.sentenceIndex
                TitleCard(
                    onClick = { onSelect(index) },
                    title = {
                        Text(
                            text = (if (isCurrent) "▶ " else "") + sentence,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                ) {
                    Text("句 ${index + 1}/${sentences.size}")
                }
            }
        }
    }
}