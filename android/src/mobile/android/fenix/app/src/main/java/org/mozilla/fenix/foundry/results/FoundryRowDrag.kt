/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.foundry.results

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Moves one row of the Group view by a sideways drag, one window per swipe at most: past half a window, or a clear
 * flick. Used both by the row itself and by [SidewaysSwipeFrame] around each live page.
 *
 * @param flingPxPerS A release faster than this moves the row on even after a short drag.
 */
class FoundryRowDrag(
    private val state: PagerState,
    private val scope: CoroutineScope,
    private val flingPxPerS: Float,
) {
    private var start = 0

    /** A sideways drag started. */
    fun begin() {
        start = state.currentPage
    }

    /** The finger moved [dx] pixels sideways. */
    fun drag(dx: Float) {
        state.dispatchRawDelta(-dx)
    }

    /** The finger lifted, moving sideways at [velocity] pixels per second. */
    fun end(velocity: Float) {
        val moved = state.currentPage + state.currentPageOffsetFraction - start
        val target =
            when {
                velocity < -flingPxPerS || moved > TURN_THRESHOLD -> start + 1
                velocity > flingPxPerS || moved < -TURN_THRESHOLD -> start - 1
                else -> start
            }
        scope.launch { state.animateScrollToPage(target.coerceIn(0, state.pageCount - 1)) }
    }

    companion object {
        /** How far a drag must go, as a share of a window's width, before releasing it moves the row on. */
        const val TURN_THRESHOLD = 0.5f

        /** A drag counts as sideways once it is this much more horizontal than vertical. */
        const val SIDEWAYS_RATIO = 1.2f
    }
}

/**
 * Holds a window's live page. Touches on a page view go straight to it, so Compose never sees them; this frame
 * watches them first and, once a drag is clearly sideways, cancels the page's touch and moves the row instead. The
 * page only ever gets up and down scrolling and taps (owner's rule).
 */
@SuppressLint("ViewConstructor")
class SidewaysSwipeFrame(context: Context, var rowDrag: FoundryRowDrag?) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var decided = false
    private var sideways = false
    private var tracker: VelocityTracker? = null

    @Suppress("ReturnCount")
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val drag = rowDrag ?: return super.dispatchTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                decided = false
                sideways = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain().apply { addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(event)
                if (!decided) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) > slop && abs(dx) > abs(dy) * FoundryRowDrag.SIDEWAYS_RATIO) {
                        decided = true
                        sideways = true
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        drag.begin()
                        lastX = event.x
                        return true
                    } else if (abs(dy) > slop) {
                        decided = true
                    }
                }
                if (sideways) {
                    drag.drag(event.x - lastX)
                    lastX = event.x
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasSideways = sideways
                sideways = false
                if (wasSideways) {
                    tracker?.addMovement(event)
                    tracker?.computeCurrentVelocity(MS_PER_S)
                    drag.end(tracker?.xVelocity ?: 0f)
                }
                tracker?.recycle()
                tracker = null
                if (wasSideways) return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private companion object {
        const val MS_PER_S = 1000
    }
}
