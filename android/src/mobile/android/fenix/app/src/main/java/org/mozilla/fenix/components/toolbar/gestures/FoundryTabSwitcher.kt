/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar.gestures

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.thumbnails.loader.ThumbnailLoader
import mozilla.components.concept.base.images.ImageLoadRequest
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.theme.ThemeManager
import com.google.android.material.R as materialR

/**
 * The card-based tab switcher driven by the toolbar's upward swipe, in the style of Android's recent apps.
 *
 * While the finger is down the current page shrinks into a card that follows it, with the neighbouring tabs beside
 * it; moving sideways slides through them. The card's bottom edge stays under the finger, but the card cannot be
 * pulled off the top of the screen. On release the gesture handler decides the [Outcome]: the page is restored, a
 * neighbouring card opens full screen, or the card settles into its spot in the tabs tray's grid and the tray opens
 * behind it.
 *
 * @param container The view the overlay is drawn over, normally the fragment's [SwipeGestureLayout].
 * @param captureCurrent Delivers a fresh snapshot of the current page, used for the current tab's card.
 * @param onSelectTab Switches to the chosen tab.
 * @param onShowTabsTray Opens the full tabs tray.
 * @param toolbarAtBottom Whether the gesture comes from the bottom of the screen (true) or the top.
 */
@Suppress("TooManyFunctions")
class FoundryTabSwitcher(
    private val container: ViewGroup,
    private val store: BrowserStore,
    private val captureCurrent: ((Bitmap?) -> Unit) -> Unit,
    private val onSelectTab: (TabSessionState) -> Unit,
    private val onShowTabsTray: () -> Unit,
    private val toolbarAtBottom: Boolean,
) {
    /** How a swipe in the tab switcher ends. */
    enum class Outcome { STAY, PREVIOUS_TAB, NEXT_TAB, TABS_TRAY }

    /** Where a card is drawn: its horizontal centre, top edge, scale, visible height (unscaled) and opacity. */
    private data class Frame(val centerX: Float, val top: Float, val scale: Float, val clip: Float, val alpha: Float)

    private class CardView(context: Context, radius: Float) : FrameLayout(context) {
        var clip = Float.MAX_VALUE

        init {
            clipToOutline = true
            outlineProvider =
                object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        // Corners are sized for the scaled card so they look the same at any size.
                        val r = radius / view.scaleX.coerceAtLeast(MIN_OUTLINE_SCALE)
                        outline.setRoundRect(0, 0, view.width, clip.toInt().coerceAtMost(view.height), r)
                    }
                }
        }
    }

    private class Card(val index: Int, val view: CardView, val title: TextView)

    private val context = container.context
    private val density = context.resources.displayMetrics.density
    private val thumbnailLoader = ThumbnailLoader(context.components.core.thumbnailStorage)
    private val trayBackground =
        ContextCompat.getColor(context, ThemeManager.resolveAttribute(materialR.attr.colorSurface, context))

    private var overlay: FrameLayout? = null
    private var cards = emptyList<Card>()
    private var tabs = emptyList<TabSessionState>()
    private var currentIndex = 0
    private var animator: ValueAnimator? = null

    /** Distance the finger has moved away from the toolbar edge. */
    private var travel = 0f

    /** Fractional index of the tab in the middle of the screen. */
    private var position = 0f

    /** The highest a card's top edge may rise, leaving room for its title under the status bar. */
    private var ceiling = 0f

    val isActive: Boolean
        get() = overlay != null

    /** Whether there is a tab on the given side of the current one. */
    fun hasTab(outcome: Outcome) =
        when (outcome) {
            Outcome.PREVIOUS_TAB -> currentIndex > 0
            Outcome.NEXT_TAB -> currentIndex < tabs.lastIndex
            else -> true
        }

    fun start() {
        cancelAnimation()
        removeOverlay()
        val current = store.state.selectedTab ?: return
        tabs = store.state.getNormalOrPrivateTabs(current.content.private)
        currentIndex = tabs.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        position = currentIndex.toFloat()
        travel = 0f
        ceiling = screenTop() + TITLE_ROOM_DP * density

        val root = FrameLayout(context).apply { setBackgroundColor(Color.TRANSPARENT) }
        container.addView(root, ViewGroup.LayoutParams(MATCH, MATCH))
        overlay = root

        val first = (currentIndex - WINDOW).coerceAtLeast(0)
        val last = (currentIndex + WINDOW).coerceAtMost(tabs.lastIndex)
        cards = (first..last).map { index -> buildCard(root, index, tabs[index]) }
        render()
    }

    /**
     * @param sideways Horizontal finger movement since the gesture started, in pixels.
     * @param away Finger movement away from the toolbar edge, in pixels.
     */
    fun update(sideways: Float, away: Float) {
        if (!isActive) return
        travel = away.coerceAtLeast(0f)
        position = (currentIndex - sideways / spacing()).coerceIn(firstCardIndex(), lastCardIndex())
        render()
    }

    fun finish(outcome: Outcome) {
        if (!isActive) return
        val target =
            when (outcome) {
                Outcome.PREVIOUS_TAB -> currentIndex - 1
                Outcome.NEXT_TAB -> currentIndex + 1
                else -> currentIndex
            }
        when {
            outcome == Outcome.TABS_TRAY -> settleIntoTray()
            target != currentIndex && target in tabs.indices ->
                animateTo(fullScreenFrames(target), scrimTo = Color.TRANSPARENT) {
                    onSelectTab(tabs[target])
                    // Give the newly selected tab a moment to draw before uncovering it.
                    container.postDelayed({ fadeOutAndRemove() }, SETTLE_AFTER_SWITCH_MS)
                }
            else -> animateTo(fullScreenFrames(currentIndex), scrimTo = Color.TRANSPARENT) { removeOverlay() }
        }
    }

    fun cancel() {
        if (!isActive) return
        animateTo(fullScreenFrames(currentIndex), scrimTo = Color.TRANSPARENT) { removeOverlay() }
    }

    private fun buildCard(parent: FrameLayout, index: Int, tab: TabSessionState): Card {
        val card = CardView(context, CARD_CORNER_DP * density).apply { elevation = CARD_ELEVATION_DP * density }
        card.addView(FoundryPageSnapshots.placeholder(context), FrameLayout.LayoutParams(MATCH, MATCH))
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        card.addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
        parent.addView(card, FrameLayout.LayoutParams(width(), height()))

        val cached = FoundryPageSnapshots.get(tab)
        if (cached != null) image.setImageBitmap(cached)
        if (index == currentIndex) {
            captureCurrent { bitmap -> bitmap?.let { image.setImageBitmap(it) } }
        } else if (cached == null) {
            image.post {
                thumbnailLoader.loadIntoView(
                    image,
                    ImageLoadRequest(
                        tab.id,
                        (maxOf(image.width, image.height) * THUMBNAIL_SCALE).toInt().coerceAtLeast(1),
                        tab.content.private,
                    ),
                )
            }
        }

        val title =
            TextView(context).apply {
                text = tab.content.title.ifBlank { tab.content.url }
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SP)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                alpha = 0f
            }
        parent.addView(title, FrameLayout.LayoutParams(width(), ViewGroup.LayoutParams.WRAP_CONTENT))
        return Card(index, card, title)
    }

    /** Where every card is while the finger is down. */
    private fun dragFrames(): Map<Int, Frame> {
        val p = progress()
        val scale = scaleFor(p)
        val cardHeight = height() * scale
        val top =
            if (toolbarAtBottom) {
                // The card's bottom edge follows the finger, but the card cannot be pulled off the top of the screen.
                val free = height() - travel - cardHeight
                if (free >= ceiling) free else ceiling - (ceiling - free) * OVERSHOOT_RESISTANCE
            } else {
                travel.coerceAtMost(height() - cardHeight - EDGE_MARGIN_DP * density)
            }
        return cards.associate { card ->
            val x = width() / 2f + (card.index - position) * spacing(scale)
            card.index to Frame(x, top, scale, height().toFloat(), 1f)
        }
    }

    private fun fullScreenFrames(target: Int) =
        cards.associate { card ->
            val x = width() / 2f + (card.index - target) * (width() + CARD_GAP_DP * density)
            card.index to Frame(x, 0f, 1f, height().toFloat(), 1f)
        }

    private fun settleIntoTray() {
        val cell = trayCell(currentIndex)
        val dragged = dragFrames()
        val frames =
            cards.associate { card ->
                val frame = dragged.getValue(card.index)
                card.index to
                    if (card.index == currentIndex) {
                        val scale = cell.width / width()
                        Frame(cell.left + cell.width / 2, cell.top, scale, cell.height / scale, 1f)
                    } else {
                        frame.copy(alpha = 0f)
                    }
            }
        animateTo(frames, scrimTo = trayBackground, duration = TRAY_DURATION_MS) {
            onShowTabsTray()
            container.postDelayed({ fadeOutAndRemove() }, SETTLE_AFTER_TRAY_MS)
        }
    }

    private class Cell(val left: Float, val top: Float, val width: Float, val height: Float)

    /**
     * Approximately where the tab at [index] sits in the tabs tray's grid, in the container's coordinates: two
     * columns (three on wide screens) of 4:5 cards under the tray's header, kept on screen as the tray scrolls.
     */
    private fun trayCell(index: Int): Cell {
        val widthDp = width() / density
        val columns = if (widthDp >= WIDE_TRAY_DP) WIDE_TRAY_COLUMNS else TRAY_COLUMNS
        val padding = TRAY_PADDING_DP * density
        val cellWidth = (width() - padding * 2 - padding * (columns - 1)) / columns
        val cellHeight = cellWidth / TRAY_CELL_ASPECT
        val column = index % columns
        val row = index / columns

        val gridTop = screenTop() + TRAY_HEADER_DP * density
        val lowest = height() - cellHeight - TRAY_BOTTOM_DP * density
        val top = (gridTop + row * (cellHeight + padding)).coerceAtMost(lowest.coerceAtLeast(gridTop))
        return Cell(padding + column * (cellWidth + padding), top, cellWidth, cellHeight)
    }

    /** Where the screen's usable area starts (below the status bar), in the container's coordinates. */
    private fun screenTop(): Float {
        val statusBar =
            ViewCompat.getRootWindowInsets(container)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val location = IntArray(2).also { container.getLocationOnScreen(it) }
        return (statusBar - location[1]).coerceAtLeast(0).toFloat()
    }

    private fun render(
        frames: Map<Int, Frame> = dragFrames(),
        scrim: Int = dragScrim(),
        titleAlpha: Float = dragTitleAlpha(),
    ) {
        val root = overlay ?: return
        root.setBackgroundColor(scrim)
        for (card in cards) {
            val frame = frames[card.index] ?: continue
            card.view.apply {
                pivotX = 0f
                pivotY = 0f
                scaleX = frame.scale
                scaleY = frame.scale
                translationX = frame.centerX - width() * frame.scale / 2
                translationY = frame.top
                alpha = frame.alpha
                clip = frame.clip
                invalidateOutline()
            }
            card.title.apply {
                translationX = frame.centerX - width() / 2f
                translationY = frame.top - this.height - TITLE_GAP_DP * density
                alpha = titleAlpha * frame.alpha
            }
        }
    }

    private fun dragTitleAlpha() = (progress() * 2f - 0.4f).coerceIn(0f, 1f)

    private fun dragScrim(): Int {
        val alpha = SCRIM_ALPHA * (progress() * SCRIM_RAMP).coerceAtMost(1f)
        return Color.argb(alpha.toInt(), 0, 0, 0)
    }

    private fun animateTo(
        targets: Map<Int, Frame>,
        scrimTo: Int,
        duration: Long = SNAP_DURATION_MS,
        onEnd: () -> Unit,
    ) {
        cancelAnimation()
        val starts = dragFrames()
        val scrimFrom = dragScrim()
        val titleFrom = dragTitleAlpha()
        var cancelled = false
        animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                interpolator = FastOutSlowInInterpolator()
                // cancel() also runs doOnEnd: a cancelled finish must not still switch tabs or open the tray.
                doOnCancel { cancelled = true }
                addUpdateListener {
                    val f = it.animatedValue as Float
                    val frames =
                        targets.mapValues { (index, end) ->
                            val from = starts[index] ?: end
                            Frame(
                                lerp(from.centerX, end.centerX, f),
                                lerp(from.top, end.top, f),
                                lerp(from.scale, end.scale, f),
                                lerp(from.clip, end.clip, f),
                                lerp(from.alpha, end.alpha, f),
                            )
                        }
                    render(frames, ColorUtils.blendARGB(scrimFrom, scrimTo, f), titleFrom * (1f - f))
                }
                doOnEnd { if (!cancelled) onEnd() }
                start()
            }
    }

    private fun fadeOutAndRemove() {
        val root = overlay ?: return
        root.animate().alpha(0f).setDuration(FADE_DURATION_MS).withEndAction { removeOverlay() }.start()
    }

    private fun removeOverlay() {
        overlay?.let { container.removeView(it) }
        overlay = null
        cards = emptyList()
    }

    private fun cancelAnimation() {
        animator?.cancel()
        animator = null
    }

    /** 0 at rest, 1 when fully shrunk; the first [DEAD_ZONE_DP] of travel only lifts the card. */
    private fun progress(): Float =
        ((travel - DEAD_ZONE_DP * density) / (FULL_SHRINK_DP * density)).coerceIn(0f, 1f)

    private fun scaleFor(progress: Float) = 1f - (1f - MIN_SCALE) * progress

    private fun spacing(scale: Float = scaleFor(progress())): Float = width() * scale + CARD_GAP_DP * density

    private fun firstCardIndex() = cards.firstOrNull()?.index?.toFloat() ?: currentIndex.toFloat()

    private fun lastCardIndex() = cards.lastOrNull()?.index?.toFloat() ?: currentIndex.toFloat()

    private fun width() = if (container.width > 0) container.width else context.resources.displayMetrics.widthPixels

    private fun height() =
        if (container.height > 0) container.height else context.resources.displayMetrics.heightPixels

    private fun lerp(from: Float, to: Float, f: Float) = from + (to - from) * f

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        /** Tabs shown on each side of the current one. */
        const val WINDOW = 4

        /** Smallest size of a card while dragging, as a fraction of the screen. */
        const val MIN_SCALE = 0.55f
        const val MIN_OUTLINE_SCALE = 0.3f

        /** Upward travel before the card starts to shrink. */
        const val DEAD_ZONE_DP = 16f

        /** Further upward travel over which the card shrinks fully. */
        const val FULL_SHRINK_DP = 220f

        /** How much a card still moves when dragged past its tray spot. */
        const val OVERSHOOT_RESISTANCE = 0.08f
        const val EDGE_MARGIN_DP = 16f

        /** Space kept above a card for its title. */
        const val TITLE_ROOM_DP = 40f

        /** Tab thumbnails are loaded at this fraction of the screen size: cards are never shown bigger. */
        const val THUMBNAIL_SCALE = 0.6f

        /** The tabs tray's grid, approximated: see [trayCell]. */
        const val TRAY_COLUMNS = 2
        const val WIDE_TRAY_COLUMNS = 3
        const val WIDE_TRAY_DP = 480f
        const val TRAY_PADDING_DP = 16f
        const val TRAY_CELL_ASPECT = 0.8f
        const val TRAY_HEADER_DP = 88f
        const val TRAY_BOTTOM_DP = 96f

        const val CARD_GAP_DP = 16f
        const val CARD_CORNER_DP = 20f
        const val CARD_ELEVATION_DP = 8f
        const val TITLE_GAP_DP = 8f
        const val TITLE_TEXT_SP = 14f
        const val SCRIM_ALPHA = 230f
        const val SCRIM_RAMP = 3f
        const val SNAP_DURATION_MS = 260L
        const val TRAY_DURATION_MS = 320L
        const val FADE_DURATION_MS = 180L
        const val SETTLE_AFTER_SWITCH_MS = 250L
        const val SETTLE_AFTER_TRAY_MS = 350L
    }
}
