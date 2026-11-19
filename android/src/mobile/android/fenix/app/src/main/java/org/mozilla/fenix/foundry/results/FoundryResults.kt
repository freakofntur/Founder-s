/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.foundry.results

import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import mozilla.components.support.ktx.kotlin.isUrl
import org.mozilla.fenix.R

/**
 * Entry point for Foundry's Results screen ("service tabs"): a search typed in the address bar opens
 * [FoundryResultsFragment] instead of a single search engine page.
 */
object FoundryResults {
    const val ARG_QUERY = "query"

    /**
     * Opens the Results screen for [text] if it is a search (not a web address) outside private browsing.
     *
     * @return Whether the Results screen was opened; if not, the caller loads [text] as usual.
     */
    fun openIfSearch(navController: NavController, text: String, private: Boolean): Boolean {
        val query = text.trim()
        if (private || query.isEmpty() || query.isUrl()) return false
        navController.navigate(
            R.id.foundryResultsFragment,
            bundleOf(ARG_QUERY to query),
            NavOptions.Builder().setPopUpTo(R.id.homeFragment, false).build(),
        )
        return true
    }
}
