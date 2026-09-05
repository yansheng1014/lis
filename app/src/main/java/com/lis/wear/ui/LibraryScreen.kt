package com.lis.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import com.lis.wear.model.BookMeta
import com.lis.wear.model.PlayerState

@Composable
fun LibraryScreen(
    library: List<BookMeta>,
    playerState: PlayerState,
    onOpen: (BookMeta) -> Unit,
    onImportUri: () -> Unit,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onDelete: (String) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) {
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader {
                    Text("书架", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (playerState.hasBook) {
                item {
                    TitleCard(
                        onClick = onOpenPlayer,
                        title = { Text("继续收听", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    ) {
                        Text(
                            playerState.bookTitle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (library.isEmpty()) {
                item {
                    Text(
                        "书架空空如也",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    Text(
                        "点下方按钮导入 txt / epub",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                items(library, key = { it.id }) { meta ->
                    TitleCard(
                        onClick = { onOpen(meta) },
                        title = { Text(meta.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    ) {
                        Text("${meta.chapterCount} 章 · ${meta.format.name}")
                    }
                }
            }

            item {
                Button(onClick = onImportUri) {
                    Text("导入书籍")
                }
            }
            item {
                Button(onClick = onScan) {
                    Text("扫描书籍")
                }
            }
            item {
                Button(onClick = onOpenSettings) {
                    Text("设置")
                }
            }
        }
    }
}