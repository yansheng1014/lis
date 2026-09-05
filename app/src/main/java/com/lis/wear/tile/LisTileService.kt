package com.lis.wear.tile

import android.content.Context
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import com.lis.wear.model.PlayerState
import com.lis.wear.playback.EngineHolder

/**
 * Material 3 Wear tile mirroring the current listening session with previous /
 * play-pause / next controls. Actions are PendingIntents fired at our broadcast
 * receiver; the renderer prefers PendingIntents when supported.
 */
class LisTileService : Material3TileService() {

    override suspend fun tileResponse(requestParams: TileRequest): Tile {
        val state = EngineHolder.peek()?.state?.value ?: PlayerState()
        return Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                androidx.wear.protolayout.TimelineBuilders.Timeline.fromLayoutElement(
                    materialScope(requestParams.deviceConfiguration) {
                        tileContent(applicationContext, this, state)
                    }
                )
            )
            .setFreshnessIntervalMillis(30_000)
            .build()
    }

    private fun tileContent(
        context: Context,
        scope: MaterialScope,
        state: PlayerState,
    ): LayoutElementBuilders.LayoutElement = with(scope) {
        primaryLayout(
            titleSlot = {
                text(
                    text = state.bookTitle.ifBlank { "Lis 听书" }.layoutString,
                    typography = Typography.TITLE_MEDIUM,
                )
            },
            mainSlot = {
                if (state.hasBook) {
                    val subtitle =
                        if (state.chapterTitle.isNotBlank()) {
                            "${state.chapterTitle} · ${state.sentenceIndex + 1}/${state.sentences.size}"
                        } else {
                            "第 ${state.chapterIndex + 1}/${state.chapterCount} 章"
                        }
                    LayoutElementBuilders.Column.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHeight(DimensionBuilders.expand())
                        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                        .addContent(
                            text(subtitle.layoutString, typography = Typography.BODY_MEDIUM)
                        )
                        .addContent(
                            text(
                                state.currentSentence.ifBlank { "（已暂停）" }.layoutString,
                                typography = Typography.BODY_SMALL,
                                maxLines = 3,
                            )
                        )
                        .build()
                } else {
                    text(
                        "打开 App 选择一本书开始朗读".layoutString,
                        typography = Typography.BODY_MEDIUM,
                    )
                }
            },
            bottomSlot = {
                LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .addContent(controlButton(scope, context, "上一章", LisTileActionReceiver.ACTION_PREV, 3))
                    .addContent(controlButton(scope, context, "播放/暂停", LisTileActionReceiver.ACTION_TOGGLE, 1))
                    .addContent(controlButton(scope, context, "下一章", LisTileActionReceiver.ACTION_NEXT, 2))
                    .build()
            },
        )
    }

    private fun controlButton(
        scope: MaterialScope,
        context: Context,
        label: String,
        action: String,
        requestCode: Int,
    ): LayoutElementBuilders.LayoutElement = with(scope) {
        val pending = LisTileActionReceiver.pendingIntent(context, action, requestCode)
        val clickable: Clickable = if (hasProtoLayoutScope) {
            protoLayoutScope.clickable(pendingIntent = pending, id = "$action#$requestCode")
        } else {
            clickable(id = "$action#$requestCode")
        }
        textButton(onClick = clickable, labelContent = { text(label.layoutString) })
    }

    companion object {
        private const val RESOURCES_VERSION = "1"

        fun requestUpdate(context: Context) {
            runCatching {
                androidx.wear.tiles.TileUpdateRequester(context)
                    .requestUpdate(LisTileService::class.java)
            }
        }
    }
}