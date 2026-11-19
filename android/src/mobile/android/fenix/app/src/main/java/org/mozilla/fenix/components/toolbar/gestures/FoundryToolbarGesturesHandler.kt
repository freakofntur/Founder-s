/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar.gestures

import android.app.Activity
import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.contains
import androidx.core.graphics.toPoint
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.support.utils.ext.bottom
import mozilla.components.support.utils.ext.getWindowInsets
import mozilla.components.support.utils.ext.isKeyboardVisible
import mozilla.components.support.utils.ext.mandatorySystemGestureInsets
import mozilla.telemetry.glean.private.NoExtras
import org.mozilla.fenix.GleanMetrics.Events
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.SwipeGestureListener
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.components.AppStore
import org.mozilla.fenix.components.toolbar.ToolbarPosition
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.tabstray.redux.state.Page

/**
 * Foundry's address bar gestures. One handler owns every swipe that starts on the toolbar (or navigation bar) and
 * decides what it means once the finger has moved far enough to read a direction:
 *
 * - **Sideways**: back (finger moves right) or forward (finger moves left), mirrored for right-to-left layouts. As in
 *   Safari, the page slides with the finger and reveals a snapshot of the page it is going to underneath.
 * - **Up** (away from the toolbar's screen edge): the [FoundryTabSwitcher], which shrinks the page into a card that
 *   follows the finger.
 *
 * On release the finger's momentum is projected forward, so a quick flick counts as much as a long drag.
 *
 * @param page The view that slides for back/forward (the web content, not the toolbar).
 * @param tabSwitcher The card tab switcher, or null to disable the upward gesture.
 * @param onSwipeStarted Called when a swipe is recognised, e.g. to capture a thumbnail.
 *
 * Nothing happens unless the finger (with momentum) ends past the [FoundrySwipeLimit], which [FoundryBrowsGuide]
 * draws while the swipe is under way.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class FoundryToolbarGesturesHandler(
    private val activity: Activity,
    private val page: View,
    private val toolbarLayoutRect: () -> Rect?,
    private val navBarLayoutRect: () -> Rect?,
    private val toolbarPosition: ToolbarPosition,
    private val store: BrowserStore,
    private val sessionUseCases: SessionUseCases,
    private val tabSwitcher: FoundryTabSwitcher?,
    private val onSwipeStarted: () -> Unit = {},
) : SwipeGestureListener {

    private enum class Mode { UNDECIDED, BACK_FORWARD, TAB_SWITCHER, IGNORED }

    private val density = activity.resources.displayMetrics.density
    private val isLtr: Boolean
        get() = activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR

    private var mode = Mode.UNDECIDED
    private var dx = 0f
    private var dy = 0f
    private var startedInSystemGestureInset = false
    private var underlay: FrameLayout? = null
    private var navigating = false
    private val limit = FoundrySwipeLimit(density)
    private val brows = FoundryBrowsGuide(activity, limit, awayIsUp = toolbarPosition == ToolbarPosition.BOTTOM)

    override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
        if (activity.window.decorView.isKeyboardVisible() || !start.isInBars()) {
            return false
        }
        // Let the previous gesture's animation finish first.
        if (navigating || tabSwitcher?.isActive == true) return false
        mode = Mode.UNDECIDED
        dx = next.x - start.x
        dy = next.y - start.y
        startedInSystemGestureInset = start.isInSystemGestureInset()
        brows.show(start)
        classifyWhenReady()
        updateBrows()
        return true
    }

    override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {
        dx -= distanceX
        dy -= distanceY
        classifyWhenReady()
        updateBrows()

        when (mode) {
            Mode.BACK_FORWARD -> updateBackForward()
            Mode.TAB_SWITCHER -> tabSwitcher?.update(dx, awayFromEdge())
            else -> Unit
        }
    }

    override fun onSwipeFinished(velocityX: Float, velocityY: Float) {
        val velocityAway = if (toolbarPosition == ToolbarPosition.BOTTOM) -velocityY else velocityY
        // Where the finger would be once its momentum is spent, judged against the brows.
        val projectedX = dx + velocityX * MOMENTUM_SECONDS
        val projectedAway = awayFromEdge() + velocityAway * MOMENTUM_SECONDS
        val crossed = limit.isCrossed(projectedX, projectedAway)
        when (mode) {
            Mode.BACK_FORWARD -> finishBackForward(projectedX, crossed)
            Mode.TAB_SWITCHER -> tabSwitcher?.finish(tabOutcome(projectedX, projectedAway))
            else -> Unit
        }
        brows.dismiss()
        mode = Mode.UNDECIDED
    }

    /**
     * Where a tab switcher swipe ending at ([x], [away]) leads: nowhere under the brows; past a brow out to the side,
     * the tab on that side (the cards follow the finger, so moving right brings in the previous tab); past the middle,
     * the tabs tray.
     */
    private fun tabOutcome(x: Float, away: Float): FoundryTabSwitcher.Outcome {
        val switcher = tabSwitcher ?: return FoundryTabSwitcher.Outcome.STAY
        val outcome =
            when {
                !limit.isCrossed(x, away) -> FoundryTabSwitcher.Outcome.STAY
                !limit.isToTheSide(x) -> FoundryTabSwitcher.Outcome.TABS_TRAY
                x > 0 -> FoundryTabSwitcher.Outcome.PREVIOUS_TAB
                else -> FoundryTabSwitcher.Outcome.NEXT_TAB
            }
        return if (switcher.hasTab(outcome)) outcome else FoundryTabSwitcher.Outcome.STAY
    }

    private fun updateBrows() {
        val away = awayFromEdge()
        when (mode) {
            Mode.UNDECIDED -> brows.update(dx, away, FoundryBrowsGuide.Style.UNDECIDED, canGoToSide = false)
            Mode.BACK_FORWARD -> brows.update(dx, away, FoundryBrowsGuide.Style.BACK_FORWARD, canNavigate())
            Mode.TAB_SWITCHER -> {
                val neighbour =
                    if (dx > 0) FoundryTabSwitcher.Outcome.PREVIOUS_TAB else FoundryTabSwitcher.Outcome.NEXT_TAB
                brows.update(
                    dx,
                    away,
                    FoundryBrowsGuide.Style.TABS,
                    canGoToSide = tabSwitcher?.hasTab(neighbour) == true,
                    canGoUp = true,
                )
            }
            Mode.IGNORED -> brows.dismiss(quick = true)
        }
    }

    private fun classifyWhenReady() {
        if (mode != Mode.UNDECIDED || hypot(dx, dy) < DECIDE_DP * density) return

        // 0 degrees is sideways, 90 is straight away from the toolbar, negative is toward the screen edge.
        val angle = Math.toDegrees(atan2(awayFromEdge(), abs(dx)).toDouble())
        mode =
            when {
                abs(angle) <= SIDEWAYS_MAX_ANGLE -> Mode.BACK_FORWARD
                angle > SIDEWAYS_MAX_ANGLE && tabSwitcher != null && !startedInSystemGestureInset -> Mode.TAB_SWITCHER
                else -> Mode.IGNORED
            }

        when (mode) {
            Mode.TAB_SWITCHER -> {
                onSwipeStarted()
                tabSwitcher?.start()
                tabSwitcher?.update(dx, awayFromEdge())
            }
            Mode.BACK_FORWARD -> {
                onSwipeStarted()
                showUnderlay()
            }
            else -> Unit
        }
    }

    private fun awayFromEdge() = if (toolbarPosition == ToolbarPosition.BOTTOM) -dy else dy

    private fun isGoingBack() = (dx > 0) == isLtr

    private fun canNavigate(): Boolean {
        val content = store.state.selectedTab?.content ?: return false
        return if (isGoingBack()) content.canGoBack else content.canGoForward
    }

    /** Puts a snapshot of the destination page (or the home look, if there is none) behind the sliding page. */
    private fun showUnderlay() {
        removeUnderlay()
        val parent = page.parent as? ViewGroup ?: return
        val holder = FrameLayout(activity)
        holder.addView(FoundryPageSnapshots.placeholder(activity), FrameLayout.LayoutParams(MATCH, MATCH))
        val image = ImageView(activity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        holder.addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
        parent.addView(holder, parent.indexOfChild(page), ViewGroup.LayoutParams(page.width, page.height))
        holder.x = page.left.toFloat()
        holder.y = page.top.toFloat()
        holder.visibility = View.INVISIBLE
        underlay = holder
    }

    private fun updateUnderlayImage() {
        val holder = underlay ?: return
        val image = holder.getChildAt(1) as? ImageView ?: return
        val tab = store.state.selectedTab ?: return
        image.setImageBitmap(FoundryPageSnapshots.get(tab, if (isGoingBack()) -1 else 1))
    }

    private fun updateBackForward() {
        val width = page.width.toFloat().coerceAtLeast(1f)
        val holder = underlay
        if (!canNavigate()) {
            // Nothing to go to: a small rubber-band pull and no page underneath.
            holder?.visibility = View.INVISIBLE
            page.translationX = dx * BLOCKED_RESISTANCE
            return
        }
        if (holder != null && holder.visibility != View.VISIBLE) {
            updateUnderlayImage()
            holder.visibility = View.VISIBLE
        }
        page.translationX = dx.coerceIn(-width, width)
        // The page underneath starts slightly offset and settles into place as the top page slides away (parallax).
        val revealed = abs(page.translationX) / width
        holder?.translationX = (if (dx > 0) -1 else 1) * width * PARALLAX * (1 - revealed)
    }

    private fun finishBackForward(projectedX: Float, crossed: Boolean) {
        val width = page.width.toFloat().coerceAtLeast(1f)
        val committed = canNavigate() && crossed && (projectedX > 0) == (dx > 0)

        if (!committed) {
            // Block new swipes until the page has settled, and only remove this swipe's own underlay: a new swipe
            // started mid-settle would otherwise lose its underlay to this end action and show a blank page.
            navigating = true
            val holder = underlay
            page.animate().translationX(0f).setDuration(SETTLE_DURATION_MS).withEndAction { removeUnderlay(holder) }
                .start()
            holder?.animate()?.translationX((if (dx > 0) -1 else 1) * width * PARALLAX)?.setDuration(SETTLE_DURATION_MS)
                ?.start()
            return
        }

        navigating = true
        val goingBack = isGoingBack()
        val tab = store.state.selectedTab
        val fromIndex = tab?.content?.history?.currentIndex
        val target = if (dx > 0) width else -width
        underlay?.animate()?.translationX(0f)?.setDuration(FINISH_DURATION_MS)?.start()
        page
            .animate()
            .translationX(target)
            .setDuration(FINISH_DURATION_MS)
            .withEndAction {
                if (tab != null) {
                    if (goingBack) sessionUseCases.goBack(tab.id) else sessionUseCases.goForward(tab.id)
                }
                // Keep the snapshot on top while the real page loads underneath, then uncover it.
                underlay?.let { holder -> holder.bringToFront() }
                page.translationX = 0f
                waitForNavigation(fromIndex, System.currentTimeMillis())
            }
            .start()
    }

    /**
     * Keeps the snapshot up until the history entry has changed and finished loading. The history index is compared
     * rather than the URL, because back/forward between entries with the same URL (e.g. after a form post or an
     * in-page anchor) would otherwise wait for the full timeout.
     */
    private fun waitForNavigation(fromIndex: Int?, startedAt: Long) {
        val content = store.state.selectedTab?.content
        val arrived = content != null && content.history.currentIndex != fromIndex && !content.loading
        val timedOut = System.currentTimeMillis() - startedAt > NAVIGATION_TIMEOUT_MS
        if (arrived || timedOut) {
            page.postDelayed({ fadeOutUnderlay() }, PAINT_GRACE_MS)
        } else {
            page.postDelayed({ waitForNavigation(fromIndex, startedAt) }, POLL_MS)
        }
    }

    private fun fadeOutUnderlay() {
        val holder = underlay ?: run {
            navigating = false
            return
        }
        holder.animate().alpha(0f).setDuration(FADE_DURATION_MS).withEndAction { removeUnderlay(holder) }.start()
    }

    /** Removes [holder] (by default the current underlay) and lets the next swipe start. */
    private fun removeUnderlay(holder: View? = underlay) {
        holder?.let { (it.parent as? ViewGroup)?.removeView(it) }
        if (holder === underlay) underlay = null
        navigating = false
    }

    private fun PointF.isInBars(): Boolean {
        val toolbar = toolbarLayoutRect()?.let { Rect(it) }
        // A bottom toolbar overlaps the system gesture area, so extend the touch area to cover it.
        if (toolbar != null && toolbarPosition == ToolbarPosition.BOTTOM) {
            activity.window.decorView.getWindowInsets()?.let { insets ->
                toolbar.top -= (insets.mandatorySystemGestureInsets().bottom - insets.bottom())
            }
        }
        val point = toPoint()
        return toolbar?.contains(point) == true || navBarLayoutRect()?.contains(point) == true
    }

    /**
     * Whether the swipe started inside the bottom system gesture area, where swiping up means "go home" to Android.
     * Upward gestures from there are left to the system.
     */
    private fun PointF.isInSystemGestureInset(): Boolean {
        val root = activity.window.decorView
        val bottomInset =
            ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.systemGestures())?.bottom ?: 0
        if (bottomInset <= 0) return false
        val screenBottom = IntArray(2).apply { root.getLocationOnScreen(this) }[1] + root.height
        return y >= screenBottom - bottomInset
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        /** How far the finger moves before the gesture's direction is decided. */
        const val DECIDE_DP = 12f

        /** Swipes within this many degrees of horizontal are back/forward; steeper upward swipes open the switcher. */
        const val SIDEWAYS_MAX_ANGLE = 20.0

        /** Where the finger would be this long after release is where the gesture is judged to have ended. */
        const val MOMENTUM_SECONDS = 0.15f

        /** How far the page underneath starts offset, as a fraction of its width. */
        const val PARALLAX = 0.3f

        const val BLOCKED_RESISTANCE = 0.12f
        const val SETTLE_DURATION_MS = 200L
        const val FINISH_DURATION_MS = 180L
        const val FADE_DURATION_MS = 150L
        const val PAINT_GRACE_MS = 120L
        const val POLL_MS = 50L
        const val NAVIGATION_TIMEOUT_MS = 1500L
    }
}

/** Opens the tabs tray from the browser or home screen. */
fun NavController.showTabsTrayFromToolbar(appStore: AppStore) {
    val current = currentDestination?.id
    // Avoid racing a navigation that is already under way, and leave the search screen alone.
    if (current != R.id.browserFragment && current != R.id.homeFragment) return
    if (appStore.state.searchState.isSearchActive) return

    Events.toolbarTabstraySwipe.record(NoExtras())
    nav(
        current,
        NavGraphDirections.actionGlobalTabManagementFragment(
            page =
                when (appStore.state.mode) {
                    BrowsingMode.Normal -> Page.NormalTabs
                    BrowsingMode.Private -> Page.PrivateTabs
                }
        ),
    )
}
