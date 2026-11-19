/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar.gestures

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The line a toolbar swipe has to cross to do anything: two arcs over the starting point, shaped like a pair of
 * eyebrows. Released under them, the swipe does nothing; released past them, it goes back/forward, to the
 * neighbouring tab or to the tabs tray. [FoundryBrowsGuide] draws the brows with their top edge on this line, so what
 * is shown is the limit.
 *
 * Each brow follows the upper half of a circle beside the starting point; together they reach [SIDE_REACH_DP]
 * sideways and dip to [UP_REACH_DP] straight up, between the brows.
 */
class FoundrySwipeLimit(density: Float) {
    private val side = SIDE_REACH_DP * density
    private val up = UP_REACH_DP * density

    /** Distance from the starting point to the centre of each brow's circle. */
    val eyeOffset = (side * side - up * up) / (2 * side)

    /** Radius of each brow's top edge. */
    val eyeRadius = side - eyeOffset

    /** Angle, seen from a brow's centre, from its outer tail to where the two brows meet. */
    val eyeSweepDegrees = (180.0 - Math.toDegrees(atan2(up.toDouble(), eyeOffset.toDouble()))).toFloat()

    /** Sideways distance, in pixels, past which a swipe over the limit counts as going to one side. */
    val sideSplit = SIDE_SPLIT_DP * density

    /**
     * How far a point is towards the limit along its direction: below 1 is under the brows, 1 or more has crossed.
     *
     * @param dx Sideways distance from the starting point, in pixels.
     * @param away Distance away from the toolbar's screen edge, in pixels.
     */
    fun reach(dx: Float, away: Float): Float {
        val distance = hypot(dx, away)
        if (distance == 0f) return 0f
        val c = abs(dx) / distance
        val d = eyeOffset
        val limit = d * c + sqrt(d * d * c * c - d * d + eyeRadius * eyeRadius)
        return distance / limit
    }

    fun isCrossed(dx: Float, away: Float) = reach(dx, away) >= 1f

    /** Whether a point past the limit went out to one side (rather than up over the middle). */
    fun isToTheSide(dx: Float) = abs(dx) >= sideSplit

    private companion object {
        const val SIDE_REACH_DP = 176f
        const val UP_REACH_DP = 94f
        const val SIDE_SPLIT_DP = 64f
    }
}
