package com.lis.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.lis.wear.R
import com.lis.wear.model.BookMeta
import com.lis.wear.model.PlayerState

/**
 * 书架：Material 3 Expressive 列表。列表项随滚动缩放/淡出（TransformingLazyColumn
 * + TransformationSpec），底部是贴合屏幕弧度的 EdgeButton 作为主操作。
 */
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
    val spec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onScan,
                buttonSize = EdgeButtonSize.Medium,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "  扫描书籍",
                    maxLines = 1,
                )
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
                ) {
                    Text("书架")
                }
            }

            if (playerState.hasBook) {
                item {
                    TitleCard(
                        onClick = onOpenPlayer,
                        title = {
                            Text(
                                playerState.bookTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        subtitle = {
                            Text(
                                if (playerState.isPlaying) "正在朗读" else "继续收听",
                                maxLines = 1,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text(
                            playerState.chapterTitle.ifBlank {
                                "第 ${playerState.chapterIndex + 1} 章"
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (library.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Text(
                            "书架空空如也\n扫描或导入 txt / epub",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                items(library, key = { it.id }) { meta ->
                    TitleCard(
                        onClick = { onOpen(meta) },
                        onLongClick = { onDelete(meta.id) },
                        onLongClickLabel = "删除",
                        title = {
                            Text(
                                meta.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        subtitle = { Text("${meta.chapterCount} 章") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            }

            item {
                FilledTonalButton(
                    onClick = onImportUri,
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text("从系统导入") },
                )
            }

            item {
                FilledTonalButton(
                    onClick = onOpenSettings,
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text("设置") },
                )
            }
        }
    }
}