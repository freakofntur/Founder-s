/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.foundry.results

import android.net.Uri

/**
 * A service shown as a card on the Results screen. Each card loads the service's real web page for the query, so the
 * user's existing logins work and no API is involved.
 *
 * @param id Stable identifier, used to keep a card's page across recompositions.
 * @param name Shown on the card's header.
 * @param badge One or two characters shown as the card's icon.
 * @param urlFor Builds the page to load for a query (already trimmed, not encoded).
 */
data class FoundryService(
    val id: String,
    val name: String,
    val badge: String,
    val urlFor: (String) -> String,
)

/** The services on the Results screen. Their order on screen comes from [FoundryPriority]. */
object FoundryServices {
    private fun encode(query: String): String = Uri.encode(query)

    val all: List<FoundryService> =
        listOf(
            FoundryService("aimode", "AI Mode", "G✦") { "https://www.google.com/search?udm=50&q=${encode(it)}" },
            FoundryService("info", "Google", "G") { "https://www.google.com/search?q=${encode(it)}" },
            FoundryService("clips", "Clips", "▷") { "https://m.youtube.com/results?search_query=${encode(it)}" },
            FoundryService("images", "Images", "▣") { "https://www.google.com/search?udm=2&q=${encode(it)}" },
            FoundryService("claude", "Claude", "C") { "https://claude.ai/new?q=${encode(it)}" },
            FoundryService("chatgpt", "ChatGPT", "◎") { "https://chatgpt.com/?q=${encode(it)}" },
            FoundryService("gemini", "Gemini", "✦") { "https://gemini.google.com/app?q=${encode(it)}" },
            FoundryService("grok", "Grok", "X") { "https://grok.com/?q=${encode(it)}" },
        )
}

/**
 * Orders the services for a query, best first: priority 1 fills the big bottom card, 2 and 3 the upper cards, and so
 * on page by page. This is a placeholder for the planned on-device model ("Result priority"): it guesses the context
 * from words in the query.
 */
object FoundryPriority {
    private val videoWords = setOf("video", "videos", "watch", "trailer", "song", "music", "tutorial", "clip", "vlog")
    private val imageWords = setOf("image", "images", "photo", "photos", "picture", "pictures", "wallpaper", "logo")
    private val factWords =
        setOf("weather", "price", "near", "hours", "open", "score", "time", "map", "directions", "buy", "news")
    private val questionWords =
        setOf("why", "how", "what", "explain", "compare", "should", "which", "difference", "vs", "best", "can", "is")

    /** Default scores, used when the query gives no stronger hint. Higher comes first. */
    private val base =
        mapOf(
            "aimode" to 40,
            "info" to 35,
            "claude" to 30,
            "clips" to 25,
            "chatgpt" to 20,
            "images" to 15,
            "gemini" to 10,
            "grok" to 5,
        )

    fun rank(query: String): List<FoundryService> {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
        val score = base.toMutableMap()
        fun boost(id: String, by: Int) = score.merge(id, by, Int::plus)

        if (words.any { it in videoWords }) boost("clips", 100)
        if (words.any { it in imageWords }) boost("images", 100)
        if (words.any { it in factWords }) boost("info", 60)
        if (words.firstOrNull() in questionWords || query.trimEnd().endsWith("?") || words.size >= LONG_QUERY_WORDS) {
            boost("aimode", 50)
            boost("claude", 45)
            boost("chatgpt", 40)
            boost("gemini", 35)
        }
        return FoundryServices.all.sortedByDescending { score[it.id] ?: 0 }
    }

    /** Queries this long read as questions even without a question word. */
    private const val LONG_QUERY_WORDS = 6
}
