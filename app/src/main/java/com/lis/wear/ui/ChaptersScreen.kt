package com.lis.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.lis.wear.model.PlayerState

/** 章节列表：当前章高亮，点击直接跳转。 */
@Composable
fun ChaptersScreen(
    state: PlayerState,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    LaunchedEffect(state.chapterIndex) {
        if (state.chapterCount > 0) {
            listState.scrollToItem((state.chapterIndex + 1).coerceAtMost(state.chapterCount))
        }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text("章节 · ${state.chapterCount}") }
            }

            items(state.chapterCount) { index ->
                val isCurrent = index == state.chapterIndex
                FilledTonalButton(
                    onClick = { onSelect(index) },
                    label = {
                        Text(
                            if (isCurrent && state.chapterTitle.isNotBlank()) state.chapterTitle
                            else "第 ${index + 1} 章",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    secondaryLabel = if (isCurrent) {
                        { Text("正在朗读", maxLines = 1) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
        }
    }
}