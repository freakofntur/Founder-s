/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar.gestures

import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.doOnEnd
import androidx.core.graphics.ColorUtils
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws [FoundrySwipeLimit] from where a swipe starts, as a pair of shaded eyebrows: a darker brow whose top edge is
 * the limit, over a lighter shade covering the area under it.
 *
 * The brow that reacts is the one on the side the destination comes from: moving the finger right brings in what lies
 * to the left (the previous page, or the previous tab), so the left brow reacts. Going back/forward fills that brow
 * with the brand colour like a loading bar, from its outer tail inwards, as far as the finger has travelled towards
 * the limit. In the tab switcher the brows take on the brand colour as the finger nears the limit: both while it
 * heads up over the middle (the tabs tray), shifting to just the one on the destination's side as it moves far
 * enough out to pick the neighbouring tab.
 *
 * @param awayIsUp Whether "away from the toolbar" is up the screen (bottom toolbar) rather than down.
 */
class FoundryBrowsGuide(
    private val activity: Activity,
    private val limit: FoundrySwipeLimit,
    private val awayIsUp: Boolean,
) {
    /** What the swipe has been recognised as, which decides how the brows react. */
    enum class Style { UNDECIDED, BACK_FORWARD, TABS }

    private val density = activity.resources.displayMetrics.density
    private val isNight =
        activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val shade = Color.parseColor(if (isNight) NIGHT_SHADE else DAY_SHADE)
    private val brand = Color.parseColor(BRAND_COLOR)

    private var view: BrowsView? = null
    private var animator: ValueAnimator? = null
    private var dismissing = false

    /** @param start The raw screen position where the swipe started. */
    fun show(start: PointF) {
        remove()
        val root = activity.window.decorView as? ViewGroup ?: return
        val brows = BrowsView(start)
        root.addView(brows, ViewGroup.LayoutParams(MATCH, MATCH))
        view = brows
        dismissing = false
        animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = APPEAR_MS
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener {
                    brows.drawn = it.animatedValue as Float
                    brows.invalidate()
                }
                start()
            }
    }

    /**
     * @param dx Sideways finger movement since the start, in pixels.
     * @param away Finger movement away from the toolbar's screen edge, in pixels.
     * @param style What the swipe has been recognised as.
     * @param canGoToSide Whether there is somewhere to go on the destination's side: a page to go back/forward to,
     *   or a neighbouring tab.
     * @param canGoUp Whether heading up over the middle leads somewhere (the tabs tray).
     */
    fun update(dx: Float, away: Float, style: Style, canGoToSide: Boolean, canGoUp: Boolean = false) {
        val brows = view ?: return
        if (dismissing) return
        val reach = limit.reach(dx, away)
        val toTheSide = limit.isToTheSide(dx)
        val crossed =
            reach >= 1f &&
                when (style) {
                    Style.UNDECIDED -> false
                    Style.BACK_FORWARD -> canGoToSide
                    Style.TABS -> if (toTheSide) canGoToSide else canGoUp
                }
        if (crossed != brows.crossed) {
            brows.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            brows.crossed = crossed
        }

        val destination = if (dx >= 0f) LEFT else RIGHT
        val other = 1 - destination
        brows.fillTarget.fill(0f)
        brows.glowTarget.fill(0f)
        brows.near.fill(0f)
        when (style) {
            Style.UNDECIDED -> Unit
            Style.BACK_FORWARD -> {
                brows.near[destination] = reach.coerceIn(0f, 1f)
                if (canGoToSide) brows.fillTarget[destination] = reach.coerceIn(0f, 1f)
            }
            Style.TABS -> {
                // 0 over the middle, 1 once the finger is far enough out to pick the neighbouring tab.
                val sideness = ((abs(dx) - limit.sideSplit + SIDE_BLEND_DP * density) / (SIDE_BLEND_DP * density))
                    .coerceIn(0f, 1f)
                val strength = ((reach - GLOW_FROM) / (1f - GLOW_FROM)).coerceIn(0f, 1f)
                val up = if (canGoUp) 1f - sideness else 0f
                val side = if (canGoToSide) sideness else 0f
                brows.glowTarget[destination] = strength * (side + up)
                brows.glowTarget[other] = strength * up
                brows.near[destination] = reach.coerceIn(0f, 1f)
                brows.near[other] = reach.coerceIn(0f, 1f) * (1f - sideness)
            }
        }
        brows.fade =
            if (style == Style.BACK_FORWARD && reach > 1f) {
                (1f - (reach - 1f) / FADE_BEYOND).coerceIn(0f, 1f)
            } else {
                1f
            }
        brows.invalidate()
    }

    fun dismiss(quick: Boolean = false) {
        val brows = view ?: return
        if (dismissing) return
        dismissing = true
        animator?.cancel()
        animator =
            ValueAnimator.ofFloat(brows.fade, 0f).apply {
                duration = if (quick) QUICK_DISMISS_MS else DISMISS_MS
                addUpdateListener {
                    brows.fade = it.animatedValue as Float
                    brows.invalidate()
                }
                doOnEnd { if (view === brows) remove() }
                start()
            }
    }

    private fun remove() {
        animator?.cancel()
        animator = null
        view?.let { (it.parent as? ViewGroup)?.removeView(it) }
        view = null
    }

    private inner class BrowsView(private val start: PointF) : View(activity) {
        var drawn = 0f
        var fade = 1f
        var crossed = false

        /** Per brow (index [RIGHT] or [LEFT]): how close the finger is to the limit, from 0 to 1. */
        val near = FloatArray(2)

        /** Per brow: how much of it, from its tail, is filled with the brand colour. */
        val fillTarget = FloatArray(2)

        /** Per brow: how strongly all of it takes on the brand colour. */
        val glowTarget = FloatArray(2)
        private val fill = FloatArray(2)
        private val glow = FloatArray(2)

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }
        private val location = IntArray(2)
        private val oval = RectF()
        private val area = Path()
        private val brow = Path()

        init {
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            if (fade <= 0f || drawn <= 0f) return
            val settling = step(fill, fillTarget) or step(glow, glowTarget)
            getLocationOnScreen(location)
            val cx = start.x - location[0]
            val cy = start.y - location[1]

            canvas.save()
            if (!awayIsUp) canvas.scale(1f, -1f, cx, cy)
            drawArea(canvas, cx, cy)
            drawBrow(canvas, cx, cy, RIGHT)
            drawBrow(canvas, cx, cy, LEFT)
            canvas.restore()

            if (settling) postInvalidateOnAnimation()
        }

        /** Moves each value part of the way to its target; returns whether any is still moving. */
        private fun step(values: FloatArray, targets: FloatArray): Boolean {
            var moving = false
            for (i in values.indices) {
                val delta = targets[i] - values[i]
                if (abs(delta) < SETTLED) {
                    values[i] = targets[i]
                } else {
                    values[i] += delta * EASE
                    moving = true
                }
            }
            return moving
        }

        /** The area under the limit: both brows' circles above the starting point's level. */
        private fun drawArea(canvas: Canvas, cx: Float, cy: Float) {
            val d = limit.eyeOffset
            val r = limit.eyeRadius
            val sweep = limit.eyeSweepDegrees
            area.reset()
            oval.set(cx + d - r, cy - r, cx + d + r, cy + r)
            area.arcTo(oval, 0f, -sweep, true)
            oval.set(cx - d - r, cy - r, cx - d + r, cy + r)
            area.arcTo(oval, 180f + sweep, -sweep)
            area.close()

            val alpha = fade * drawn * (AREA_ALPHA + AREA_NEAR_BOOST * maxOf(near[RIGHT], near[LEFT]))
            paint.shader =
                LinearGradient(
                    0f,
                    cy - r,
                    0f,
                    cy,
                    ColorUtils.setAlphaComponent(shade, (alpha * OPAQUE).toInt()),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
            canvas.drawPath(area, paint)
            paint.shader = null
        }

        /** One brow in the shade colour, with its glow and its filled part over it in the brand colour. */
        private fun drawBrow(canvas: Canvas, cx: Float, cy: Float, index: Int) {
            val side = if (index == RIGHT) 1 else -1
            val strength = fade * (BROW_ALPHA + BROW_NEAR_BOOST * near[index])

            buildBrow(cx, cy, side, from = 0f, to = drawn)
            soft.color = shade
            for (layer in SHADOW) {
                soft.strokeWidth = layer.widthDp * density
                soft.alpha = (layer.alpha * strength * OPAQUE).toInt().coerceIn(0, OPAQUE)
                canvas.drawPath(brow, soft)
            }
            paint.color = shade
            paint.alpha = (strength * OPAQUE).toInt().coerceIn(0, OPAQUE)
            canvas.drawPath(brow, paint)

            if (glow[index] > 0f) drawLit(canvas, glow[index])

            if (fill[index] > 0f) {
                buildBrow(cx, cy, side, from = 0f, to = fill[index].coerceAtMost(drawn))
                drawLit(canvas, 1f)
            }
        }

        /** Draws the current [brow] path in the brand colour, at the given strength. */
        private fun drawLit(canvas: Canvas, amount: Float) {
            soft.color = brand
            soft.strokeWidth = GLOW_DP * density
            soft.alpha = (GLOW_ALPHA * amount * fade * OPAQUE).toInt().coerceIn(0, OPAQUE)
            canvas.drawPath(brow, soft)
            paint.color = brand
            paint.alpha = (LIT_ALPHA * amount * fade * OPAQUE).toInt().coerceIn(0, OPAQUE)
            canvas.drawPath(brow, paint)
        }

        /**
         * The part of a brow between [from] and [to], where 0 is its outer tail and 1 is where it meets the other
         * brow. Its top edge lies on the limit; it is thickest in the middle and tapers to a thin tail.
         */
        private fun buildBrow(cx: Float, cy: Float, side: Int, from: Float, to: Float) {
            brow.reset()
            if (to <= from) return
            val d = limit.eyeOffset
            val r = limit.eyeRadius
            val sweep = Math.toRadians(limit.eyeSweepDegrees.toDouble())
            val thickness = BROW_DP * density
            for (i in 0..SAMPLES) {
                val u = from + (to - from) * i / SAMPLES
                val a = sweep * u
                val x = cx + side * (d + r * cos(a)).toFloat()
                val y = cy - (r * sin(a)).toFloat()
                if (i == 0) brow.moveTo(x, y) else brow.lineTo(x, y)
            }
            for (i in SAMPLES downTo 0) {
                val u = from + (to - from) * i / SAMPLES
                val a = sweep * u
                val inner = r - thickness * taper(u)
                brow.lineTo(cx + side * (d + inner * cos(a)).toFloat(), cy - (inner * sin(a)).toFloat())
            }
            brow.close()
        }

        private fun taper(u: Float) = (TAIL + (1 - TAIL) * sin(PI * u * PEAK_SPAN)).toFloat()
    }

    private class Layer(val widthDp: Float, val alpha: Float)

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val OPAQUE = 255
        const val BRAND_COLOR = "#2B50E0"
        const val DAY_SHADE = "#1A1D26"
        const val NIGHT_SHADE = "#D9DEEE"

        /** Index of the brow to the right of the starting point. */
        const val RIGHT = 0

        /** Index of the brow to the left of the starting point. */
        const val LEFT = 1

        const val SAMPLES = 40
        const val BROW_DP = 18f

        /** Brow thickness at the tail, relative to its thickest. */
        const val TAIL = 0.18

        /** How much of a half sine wave the brow's thickness follows, from tail to inner end. */
        const val PEAK_SPAN = 0.85

        const val BROW_ALPHA = 0.42f
        const val BROW_NEAR_BOOST = 0.3f
        const val LIT_ALPHA = 0.9f
        const val GLOW_DP = 8f
        const val GLOW_ALPHA = 0.25f
        const val AREA_ALPHA = 0.12f
        const val AREA_NEAR_BOOST = 0.06f

        /** In the tab switcher, how far towards the limit (as a fraction of it) the brows start taking on colour. */
        const val GLOW_FROM = 0.5f

        /** Sideways distance before the side split over which the colour shifts from both brows to one. */
        const val SIDE_BLEND_DP = 40f

        /** Soft edge around the brows, like a shadow: wide faint strokes. */
        val SHADOW = listOf(Layer(14f, 0.08f), Layer(8f, 0.14f), Layer(3f, 0.3f))

        /** How far past the limit, relative to it, the brows take to fade away while going back/forward. */
        const val FADE_BEYOND = 0.5f

        /** Per frame, how much of the remaining way a brow's fill or colour moves. */
        const val EASE = 0.35f
        const val SETTLED = 0.005f

        const val APPEAR_MS = 220L
        const val DISMISS_MS = 200L
        const val QUICK_DISMISS_MS = 120L
    }
}
