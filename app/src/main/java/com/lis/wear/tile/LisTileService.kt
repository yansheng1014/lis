package com.lis.wear.tile

import android.content.Context
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconButton
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.lis.wear.R
import com.lis.wear.model.PlayerState
import com.lis.wear.playback.EngineHolder

/**
 * Wear tile: title slot = book, main slot = chapter + current sentence,
 * bottom slot = a proper Material 3 buttonGroup with prev / play-pause / next
 * (buttonGroup handles the spacing, which is what the earlier cramped row lacked).
 */
class LisTileService : Material3TileService() {

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val state = EngineHolder.peek()?.state?.value ?: PlayerState()
        return Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                androidx.wear.protolayout.TimelineBuilders.Timeline.fromLayoutElement(
                    if (state.hasBook) {
                        playingLayout(applicationContext, this, state)
                    } else {
                        emptyLayout(applicationContext, this)
                    }
                )
            )
            .setFreshnessIntervalMillis(if (state.isPlaying) 20_000 else 120_000)
            .build()
    }

    /** No book yet: single call-to-action that opens the app. */
    private fun emptyLayout(
        context: Context,
        scope: MaterialScope,
    ): LayoutElementBuilders.LayoutElement = with(scope) {
        primaryLayout(
            titleSlot = { text("Lis 听书".layoutString, typography = Typography.TITLE_MEDIUM) },
            mainSlot = {
                text(
                    "还没有书籍\n打开应用扫描导入".layoutString,
                    typography = Typography.BODY_MEDIUM,
                    maxLines = 3,
                )
            },
            bottomSlot = {
                textEdgeButton(
                    onClick = openApp(context, this),
                    labelContent = { text("打开 Lis".layoutString) },
                )
            },
        )
    }

    private fun playingLayout(
        context: Context,
        scope: MaterialScope,
        state: PlayerState,
    ): LayoutElementBuilders.LayoutElement = with(scope) {
        primaryLayout(
            titleSlot = {
                text(
                    state.bookTitle.ifBlank { "Lis 听书" }.layoutString,
                    typography = Typography.TITLE_SMALL,
                    maxLines = 1,
                )
            },
            mainSlot = {
                LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.wrap())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        text(
                            state.currentSentence
                                .ifBlank { state.chapterTitle.ifBlank { "已暂停" } }
                                .layoutString,
                            typography = Typography.BODY_MEDIUM,
                            maxLines = 2,
                        )
                    )
                    .addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setHeight(DimensionBuilders.dp(4f))
                            .build()
                    )
                    .addContent(
                        text(
                            ("${state.chapterIndex + 1}/${state.chapterCount} 章 · " +
                                "${state.sentenceIndex + 1}/${state.sentenceTotal.coerceAtLeast(1)}")
                                .layoutString,
                            typography = Typography.LABEL_SMALL,
                            maxLines = 1,
                        )
                    )
                    .addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setHeight(DimensionBuilders.dp(8f))
                            .build()
                    )
                    .addContent(
                        buttonGroup(
                            width = DimensionBuilders.expand(),
                            height = DimensionBuilders.dp(52f),
                            spacing = 6f,
                        ) {
                            buttonGroupItem {
                                iconButton(
                                    onClick = broadcast(
                                        context, this, LisTileActionReceiver.ACTION_PREV, 3
                                    ),
                                    iconContent = { iconFor(R.drawable.ic_skip_previous, "prev") },
                                )
                            }
                            buttonGroupItem {
                                iconButton(
                                    onClick = broadcast(
                                        context, this, LisTileActionReceiver.ACTION_TOGGLE, 1
                                    ),
                                    iconContent = {
                                        if (state.isPlaying) {
                                            iconFor(R.drawable.ic_pause_filled, "pause")
                                        } else {
                                            iconFor(R.drawable.ic_play_arrow, "play")
                                        }
                                    },
                                )
                            }
                            buttonGroupItem {
                                iconButton(
                                    onClick = broadcast(
                                        context, this, LisTileActionReceiver.ACTION_NEXT, 2
                                    ),
                                    iconContent = { iconFor(R.drawable.ic_skip_next, "next") },
                                )
                            }
                        }
                    )
                    .build()
            },
        )
    }

    private fun broadcast(
        context: Context,
        scope: MaterialScope,
        action: String,
        requestCode: Int,
    ): Clickable = with(scope) {
        val pending = LisTileActionReceiver.pendingIntent(context, action, requestCode)
        if (hasProtoLayoutScope) {
            protoLayoutScope.clickable(pendingIntent = pending, id = "$action#$requestCode")
        } else {
            clickable(id = "$action#$requestCode")
        }
    }

    private fun openApp(context: Context, scope: MaterialScope): Clickable = with(scope) {
        val pending = LisTileActionReceiver.openAppIntent(context)
        if (hasProtoLayoutScope) {
            protoLayoutScope.clickable(pendingIntent = pending, id = "open")
        } else {
            clickable(id = "open")
        }
    }

    /**
     * Tiles 1.6 collects resources automatically from inlined [ImageResource]s, so
     * icons are declared inline instead of in a separate resources callback.
     */
    private fun MaterialScope.iconFor(resId: Int, tag: String) =
        icon(
            resource = androidx.wear.protolayout.ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(
                    androidx.wear.protolayout.ResourceBuilders.AndroidImageResourceByResId
                        .Builder()
                        .setResourceId(resId)
                        .build()
                )
                .build(),
            protoLayoutResourceId = tag,
        )

    companion object {
        private const val RESOURCES_VERSION = "2"

        fun requestUpdate(context: Context) {
            runCatching {
                TileService.getUpdater(context).requestUpdate(LisTileService::class.java)
            }
        }
    }
}