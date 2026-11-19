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
 * @param row Which row of the Results screen the card belongs to.
 * @param urlFor Builds the page to load for a query (already trimmed, not encoded).
 */
data class FoundryService(
    val id: String,
    val name: String,
    val badge: String,
    val row: Row,
    val urlFor: (String) -> String,
) {
    /** The two independent rows of the Results screen, each ranked on its own. */
    enum class Row { UPPER, LOWER }
}

/** The services on the Results screen, in their default order (the future ranking will reorder each row). */
object FoundryServices {
    private fun encode(query: String): String = Uri.encode(query)

    val all: List<FoundryService> =
        listOf(
            FoundryService("clips", "Clips", "▷", FoundryService.Row.UPPER) {
                "https://m.youtube.com/results?search_query=${encode(it)}"
            },
            FoundryService("info", "Info", "G", FoundryService.Row.UPPER) {
                "https://www.google.com/search?q=${encode(it)}"
            },
            FoundryService("images", "Images", "▣", FoundryService.Row.UPPER) {
                "https://www.google.com/search?udm=2&q=${encode(it)}"
            },
            FoundryService("claude", "Claude", "C", FoundryService.Row.LOWER) {
                "https://claude.ai/new?q=${encode(it)}"
            },
            FoundryService("chatgpt", "ChatGPT", "◎", FoundryService.Row.LOWER) {
                "https://chatgpt.com/?q=${encode(it)}"
            },
            FoundryService("gemini", "Gemini", "✦", FoundryService.Row.LOWER) {
                "https://gemini.google.com/app?q=${encode(it)}"
            },
            FoundryService("grok", "Grok", "X", FoundryService.Row.LOWER) {
                "https://grok.com/?q=${encode(it)}"
            },
        )

    fun row(row: FoundryService.Row): List<FoundryService> = all.filter { it.row == row }
}
