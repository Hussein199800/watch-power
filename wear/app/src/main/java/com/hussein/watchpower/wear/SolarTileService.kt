package com.hussein.watchpower.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcLine
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Tile بروح Apple Watch: أسود صافٍ، حلقة البطارية، رقم كبير، وسطر معلومات هادئ. */
class SolarTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val store = Store(this)
        val now = System.currentTimeMillis()
        val clicked = requestParams.currentState.lastClickableId == REFRESH
        // نجلب عند الضغط، أو إذا مضت 10 دقائق على آخر محاولة (حماية للبطارية)
        if (clicked || now - store.lastAttempt() > 10 * 60_000L) RefreshWorker.refreshNow(this)
        RefreshWorker.schedulePeriodic(this)
        val hasCred = SecureStore.get(this, "cred") != null
        val root = SolarLayout.build(store.state(), store.error(), hasCred, now, refreshing = clicked)
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RES)
                .setFreshnessIntervalMillis(15 * 60_000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
                .build(),
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RES).build())

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) {
        RefreshWorker.schedulePeriodic(this)
        RefreshWorker.refreshNow(this)
    }

    companion object {
        const val REFRESH = "refresh"
        private const val RES = "1"
    }
}

object SolarLayout {
    // ألوان نظام Apple الداكن
    private const val BLACK = 0xFF000000.toInt()
    private const val TRACK = 0xFF1C1C1E.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GRAY = 0xFF8E8E93.toInt()
    private const val GREEN = 0xFF30D158.toInt()
    private const val YELLOW = 0xFFFFD60A.toInt()
    private const val RED = 0xFFFF453A.toInt()
    private const val ORANGE = 0xFFFF9F0A.toInt()
    private const val BLUE = 0xFF0A84FF.toInt()

    fun socColor(soc: Int?): Int = when {
        soc == null -> GRAY
        soc >= 50 -> GREEN
        soc >= 20 -> YELLOW
        else -> RED
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false): LayoutElement =
        Text.Builder()
            .setText(s)
            .setMaxLines(1)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build(),
            )
            .build()

    private fun vspace(h: Float): LayoutElement = Spacer.Builder().setHeight(dp(h)).build()
    private fun hspace(w: Float): LayoutElement = Spacer.Builder().setWidth(dp(w)).build()

    private fun ring(fraction: Float, color: Int): LayoutElement =
        Arc.Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(
                ArcLine.Builder()
                    .setLength(degrees(360f * fraction.coerceIn(0f, 1f)))
                    .setThickness(dp(7f))
                    .setColor(argb(color))
                    .build(),
            )
            .build()

    private fun stat(value: String, label: String, color: Int): LayoutElement =
        Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(value, 15f, color, bold = true))
            .addContent(text(label, 11f, GRAY))
            .build()

    fun build(s: SolarState?, error: String?, hasCred: Boolean, now: Long, refreshing: Boolean): LayoutElement {
        val center = Column.Builder().setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        center.addContent(text("طاقتي", 13f, ORANGE, bold = true))
        when {
            !hasCred -> {
                center.addContent(vspace(8f))
                center.addContent(text("بانتظار الإعداد", 16f, WHITE, bold = true))
                center.addContent(vspace(4f))
                center.addContent(text("من Termux على الجوال", 11f, GRAY))
            }
            s == null -> {
                center.addContent(vspace(8f))
                center.addContent(text(if (error != null) "تعذّر الجلب" else "جارٍ التحميل…", 16f, WHITE, bold = true))
                center.addContent(vspace(4f))
                center.addContent(text(error ?: "اضغط للتحديث", 11f, if (error != null) RED else GRAY))
            }
            else -> {
                val stale = s.isStale(now)
                center.addContent(text(SolarText.percent(s.soc), 44f, if (stale) GRAY else WHITE, bold = true))
                center.addContent(text(SolarText.battery(s), 13f, if ((s.batW ?: 0) < -10) ORANGE else GREEN))
                center.addContent(vspace(8f))
                center.addContent(
                    Row.Builder()
                        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                        .addContent(stat(SolarText.watts(s.pvW), "الألواح", ORANGE))
                        .addContent(hspace(18f))
                        .addContent(stat(SolarText.watts(s.loadW), "الحمل", BLUE))
                        .build(),
                )
                center.addContent(vspace(6f))
                val footer = if (refreshing) "جارٍ التحديث…" else if (error != null && stale) error else SolarText.footer(s, now)
                val gridCut = s.gridV != null && s.gridV < 90 && !stale
                center.addContent(text(footer, 11f, if (gridCut) RED else GRAY))
            }
        }

        val soc = s?.soc
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId(SolarTileService.REFRESH)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(BLACK)).build())
                    .build(),
            )
            .addContent(ring(1f, TRACK))
            .addContent(ring((soc ?: 0) / 100f, if (s != null && s.isStale(now)) GRAY else socColor(soc)))
            .addContent(center.build())
            .build()
    }
}
