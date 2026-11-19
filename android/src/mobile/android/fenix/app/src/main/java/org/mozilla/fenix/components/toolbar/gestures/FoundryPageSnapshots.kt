/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar.gestures

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.EngineView
import mozilla.components.concept.engine.utils.ABOUT_HOME_URL
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.fenix.R
import org.mozilla.fenix.theme.ThemeManager
import com.google.android.material.R as materialR

/**
 * In-memory snapshots of pages, one per tab history entry, so gestures can show a page before it is rendered: the
 * previous page revealed under a back swipe, or a tab's card in the tab switcher.
 */
object FoundryPageSnapshots {
    private class Entry(val url: String, val bitmap: Bitmap)

    private const val MAX_BYTES = 48 * 1024 * 1024
    private const val SCALE = 0.5f

    private val cache =
        object : LruCache<String, Entry>(MAX_BYTES) {
            override fun sizeOf(key: String, value: Entry) = value.bitmap.byteCount
        }

    private fun key(tabId: String, index: Int) = "$tabId#$index"

    /** The snapshot of the history entry `offset` steps from the current one (-1 = back, 1 = forward). */
    fun get(tab: TabSessionState, offset: Int = 0): Bitmap? {
        val history = tab.content.history
        val index = history.currentIndex + offset
        val item = history.items.getOrNull(index)
        // Going back/forward to an entry that doesn't exist shows nothing, even if an old snapshot is still cached
        // under that index. The current entry is allowed through: restored tabs may not have their history yet.
        if (item == null && offset != 0) return null
        val entry = cache.get(key(tab.id, index)) ?: return null
        return entry.bitmap.takeIf { item == null || entry.url == item.uri }
    }

    /**
     * Captures what [engineView] currently shows as the snapshot of [tab]'s current history entry. Does nothing (and
     * reports null) when the page is not on screen, e.g. while switching to the home screen, where capturing would
     * fail on a zero-sized surface.
     */
    fun capture(engineView: EngineView, tab: TabSessionState, onCaptured: (Bitmap?) -> Unit = {}) {
        val view = engineView.asView()
        if (tab.content.url == ABOUT_HOME_URL || !view.isAttachedToWindow || !view.isShown ||
            view.width <= 0 || view.height <= 0
        ) {
            onCaptured(null)
            return
        }
        val index = tab.content.history.currentIndex
        val url = tab.content.url
        try {
            engineView.captureThumbnail { bitmap ->
                val usable = bitmap?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
                if (usable != null) {
                    runCatching { usable.scale((usable.width * SCALE).toInt(), (usable.height * SCALE).toInt()) }
                        .onSuccess { small -> cache.put(key(tab.id, index), Entry(url, small)) }
                }
                onCaptured(usable)
            }
        } catch (e: RuntimeException) {
            Logger.warn("Could not snapshot the page", e)
            onCaptured(null)
        }
    }

    /** What a page with no snapshot looks like: the home screen's background with the Foundry logo. */
    fun placeholder(context: Context): View {
        val density = context.resources.displayMetrics.density
        val background = ContextCompat.getColor(context, ThemeManager.resolveAttribute(materialR.attr.colorSurface, context))
        val logo =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(
                    ImageView(context).apply { setImageResource(R.drawable.foundry_logo) },
                    LinearLayout.LayoutParams((LOGO_DP * density).toInt(), (LOGO_DP * density).toInt()),
                )
                addView(
                    ImageView(context).apply { setImageResource(R.drawable.foundry_wordmark_text) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (WORDMARK_DP * density).toInt())
                        .apply { topMargin = (WORDMARK_GAP_DP * density).toInt() },
                )
            }
        return FrameLayout(context).apply {
            setBackgroundColor(background)
            addView(
                logo,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }
    }

    private const val LOGO_DP = 64f
    private const val WORDMARK_DP = 28f
    private const val WORDMARK_GAP_DP = 12f
}
