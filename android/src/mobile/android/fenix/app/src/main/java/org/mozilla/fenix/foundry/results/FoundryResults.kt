/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.foundry.results

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import mozilla.components.support.ktx.kotlin.isUrl
import org.mozilla.fenix.R

/**
 * Entry points for Foundry's search results: a search typed in the address bar becomes a search group, shown in the
 * Group view ([FoundryGroupViewFragment], a panel over Overview) instead of a single search engine page. Tapping a
 * search group in Overview (the tabs view) opens it there too.
 */
object FoundryResults {
    const val ARG_GROUP_ID = "groupId"

    /**
     * Starts a search group for [text] and opens it in the Group view, if [text] is a search (not a web address)
     * outside private browsing.
     *
     * @return Whether the Group view was opened; if not, the caller loads [text] as usual.
     */
    fun openIfSearch(navController: NavController, text: String, private: Boolean): Boolean {
        val query = text.trim()
        if (private || query.isEmpty() || query.isUrl()) return false
        showGroup(navController, FoundrySearchGroups.start(navController.context, query))
        return true
    }

    /**
     * Opens [groupId] in the Group view. Every group opens here, search groups and normal tab groups alike, so there is
     * one kind of group (owner, 2026-11-25).
     *
     * @return Whether it was opened (always, today; patch 0008 falls back to Fenix's own view otherwise).
     */
    fun openGroup(navController: NavController, groupId: String): Boolean {
        showGroup(navController, groupId)
        return true
    }

    /** Shows the Group view as a panel over Overview, opening Overview under it first if it isn't open. */
    private fun showGroup(navController: NavController, groupId: String) {
        val overviewOpen = runCatching { navController.getBackStackEntry(R.id.tabManagementFragment) }.isSuccess
        if (!overviewOpen) {
            navController.navigate(
                R.id.tabManagementFragment,
                null,
                NavOptions.Builder().setPopUpTo(R.id.homeFragment, false).build(),
            )
        }
        navController.navigate(
            R.id.foundryGroupViewFragment,
            Bundle().apply { putString(ARG_GROUP_ID, groupId) },
            NavOptions.Builder().setPopUpTo(R.id.foundryGroupViewFragment, true).build(),
        )
    }
}
