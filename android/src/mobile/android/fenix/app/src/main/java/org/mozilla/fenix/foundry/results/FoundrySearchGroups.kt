/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.foundry.results

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.concept.engine.EngineSession
import mozilla.components.feature.tabgroups.storage.data.TabGroup
import mozilla.components.lib.state.ext.flow
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.fenix.ext.components
import java.io.File
import java.util.UUID

/**
 * One window of a search group. Its tab is created the first time the window is shown (or, for an AI, asked), so a
 * search only loads what you look at.
 *
 * @param key Stable within a group: "aimode", "google", "claude", "chatgpt", or "src:" plus the source's address.
 * @param tabId The tab showing this window, or null until it is first shown.
 */
data class FoundryWindow(
    val key: String,
    val role: Role,
    val url: String,
    val title: String,
    val kind: FoundrySource.Kind = FoundrySource.Kind.PAGE,
    val tabId: String? = null,
) {
    /** What the window holds; [FoundrySearchGroup.rows] places windows by role. [TAB] is a tab of a normal group. */
    enum class Role { AI_MODE, GOOGLE, SOURCE, CLAUDE, CHATGPT, TAB }

    /** Whether this is an AI that is only asked when the user says so. */
    val isAskWindow: Boolean
        get() = role == Role.CLAUDE || role == Role.CHATGPT
}

/**
 * The two rows of a search group's view. A null entry is a window still waiting for Google's AI answer to be read.
 *
 * @param upper U0, U1, …: tall windows. U0 is Google's results, then the pages the AI answer cites.
 * @param lower L0, L1, …: landscape windows. L0 is the AI answer, then its main source (or the videos and images it
 *   cites), then the AI ask windows.
 */
data class FoundryRows(val upper: List<FoundryWindow?>, val lower: List<FoundryWindow?>) {
    companion object {
        /** The window a search group's rows open on: U1/U2 and L1, with U0 and L0 (Google's own) one swipe back. */
        const val OPENING_INDEX = 1

        /**
         * Lays out a normal tab group (no search, no AI answer) the same way a search group lays out its sources:
         * pages in the upper row and videos and images in the lower row, each in the group's tab order. With no
         * videos or images there is no lower row and the pages get the whole height.
         */
        fun forTabs(tabs: List<FoundryWindow>): FoundryRows {
            val media = tabs.filter { it.kind != FoundrySource.Kind.PAGE }
            val pages = tabs.filter { it.kind == FoundrySource.Kind.PAGE }
            return if (pages.isEmpty()) FoundryRows(upper = media, lower = emptyList()) else FoundryRows(pages, media)
        }
    }
}

/**
 * A search shown as a tab group: Fenix's own tab group (so it appears in the tabs view like any other group) plus
 * what Foundry needs to lay it out.
 *
 * @param id Also the Fenix tab group's id.
 * @param sourcesRead Whether Google's AI answer has been read (or reading gave up).
 */
data class FoundrySearchGroup(
    val id: String,
    val query: String,
    val windows: List<FoundryWindow>,
    val sourcesRead: Boolean,
    val created: Long,
) {
    /**
     * Places the windows in the U and L rows (see `docs/design/service-tabs.md`, "What goes where").
     *
     * The browser's tab group decides what is in the group ([live]): a window whose tab was closed or moved to another
     * group leaves the rows (an AI window goes back to its Ask card instead), and tabs added to the group in Overview
     * join the rows after the search's own windows, pages above and videos and images below.
     */
    fun rows(live: FoundryGroupTabs? = null): FoundryRows {
        val placed = windows.mapNotNullTo(HashSet()) { it.tabId }
        val kept =
            windows.mapNotNull { w ->
                when {
                    live == null || w.tabId == null || live.holds(w.tabId) -> w
                    w.isAskWindow -> w.copy(tabId = null)
                    else -> null
                }
            }
        fun window(role: FoundryWindow.Role) = kept.firstOrNull { it.role == role }
        val ai = listOfNotNull(window(FoundryWindow.Role.AI_MODE))
        val google = listOfNotNull(window(FoundryWindow.Role.GOOGLE))
        val asks = listOfNotNull(window(FoundryWindow.Role.CLAUDE), window(FoundryWindow.Role.CHATGPT))
        val added = live?.tabs.orEmpty().filter { it.tabId !in placed }
        val addedPages = added.filter { it.kind == FoundrySource.Kind.PAGE }
        val addedMedia = added.filter { it.kind != FoundrySource.Kind.PAGE }
        if (!sourcesRead) {
            return FoundryRows(upper = google + listOf(null, null) + addedPages, lower = ai + null + asks + addedMedia)
        }

        val sources = kept.filter { it.role == FoundryWindow.Role.SOURCE }
        val media = sources.filter { it.kind != FoundrySource.Kind.PAGE }
        val pages = sources.filter { it.kind == FoundrySource.Kind.PAGE }
        val lowerSources = media.ifEmpty { pages.take(1) }
        val upperSources = if (media.isEmpty()) pages.drop(1) else pages
        return FoundryRows(
            upper = google + upperSources + addedPages,
            lower = ai + lowerSources + asks + addedMedia,
        )
    }

    /** Whether the answer was read and cited nothing, so the view should open on Google's own windows instead. */
    val hasNoSources: Boolean
        get() = sourcesRead && windows.none { it.role == FoundryWindow.Role.SOURCE }
}

/**
 * What the browser's own tab group holds right now, for laying out a search group by it.
 *
 * @param tabs The group's open tabs as windows, in tab order.
 * @param open Every open tab's id.
 * @param elsewhere Ids of tabs that belong to another group.
 * @param restored Whether the browser has restored its tabs; before that no tab counts as closed.
 */
data class FoundryGroupTabs(
    val tabs: List<FoundryWindow>,
    val open: Set<String>,
    val elsewhere: Set<String>,
    val restored: Boolean,
) {
    /** Whether [tabId] is still open and still in this group. */
    fun holds(tabId: String): Boolean = !restored || (tabId in open && tabId !in elsewhere)
}

/**
 * Creates search groups and remembers their layout. Fenix stores the group and its tabs; this keeps which tab is
 * which window, in a small JSON file, so the group view survives an app restart.
 */
object FoundrySearchGroups {
    private const val FILE_NAME = "foundry-search-groups.json"

    /** The most groups remembered; older ones fall back to Fenix's plain group view. */
    private const val MAX_GROUPS = 50

    /** The most cited sources kept per search. */
    private const val MAX_SOURCES = 10

    /** How long to wait for Google's AI answer before showing the group without sources. */
    private const val GIVE_UP_MS = 45_000L

    /** How long to wait for a new tab's page session. */
    private const val SESSION_TIMEOUT_MS = 10_000L

    private const val GROUP_THEME = "Blue"

    /** How long an emptied search group waits before it is deleted: a little longer than the "Undo" message shows. */
    private const val UNDO_GRACE_MS = 8_000L

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val state = MutableStateFlow<Map<String, FoundrySearchGroup>?>(null)

    /** Every remembered group by id, once loaded. */
    fun groups(context: Context): StateFlow<Map<String, FoundrySearchGroup>?> {
        load(context)
        return state
    }

    /**
     * Starts a search group for [query]: remembers it, opens AI Mode in a background tab once the AI Mode reader is
     * ready, and fills in the sources when the answer has been read.
     *
     * @return The new group's id.
     */
    @MainThread
    fun start(context: Context, query: String): String {
        val app = context.applicationContext
        val id = UUID.randomUUID().toString()
        val group =
            FoundrySearchGroup(
                id = id,
                query = query,
                windows =
                    listOf(
                        FoundryWindow("aimode", FoundryWindow.Role.AI_MODE, aiModeUrl(query), "AI Mode"),
                        FoundryWindow("google", FoundryWindow.Role.GOOGLE, googleUrl(query), "Google"),
                        FoundryWindow("claude", FoundryWindow.Role.CLAUDE, claudeUrl(query), "Claude"),
                        FoundryWindow("chatgpt", FoundryWindow.Role.CHATGPT, chatGptUrl(query), "ChatGPT"),
                    ),
                sourcesRead = false,
                created = System.currentTimeMillis(),
            )
        put(app, group)

        val engine = app.components.core.engine
        FoundryAiMode.withExtension(engine) {
            val tabId = openTab(app, id, group.windows.first())
            scope.launch {
                val session = awaitSession(app, tabId) ?: return@launch
                FoundryAiMode.watch(engine, session) { sources -> addSources(app, id, sources) }
            }
        }
        main.postDelayed({ addSources(app, id, emptyList()) }, GIVE_UP_MS)
        return id
    }

    /**
     * Makes sure [window] has a tab with a live page, creating the tab (and adding it to the group) the first time.
     *
     * @return The tab's id.
     */
    @MainThread
    fun ensureTab(context: Context, groupId: String, window: FoundryWindow): String {
        val store = context.components.core.store
        val tab = window.tabId?.let { store.state.findTab(it) }
        if (tab != null) {
            if (tab.engineState.engineSession == null) {
                store.dispatch(EngineAction.CreateEngineSessionAction(tab.id))
            }
            return tab.id
        }
        return openTab(context, groupId, window)
    }

    /**
     * What the browser's tab group [groupId] holds, kept up to date as tabs are added, closed, moved or change page.
     * Empty [FoundryGroupTabs.tabs] means the group is gone or has no open tabs.
     */
    @MainThread
    fun tabsOf(context: Context, groupId: String): Flow<FoundryGroupTabs> {
        val components = context.components
        return combine(components.core.tabGroupRepository.tabGroupDataFlow, components.core.store.flow()) { data, state ->
            val ids = data.tabGroups.firstOrNull { it.id == groupId }?.tabIds?.toSet().orEmpty()
            FoundryGroupTabs(
                tabs =
                    state.tabs
                        .filter { it.id in ids }
                        .map { tab ->
                            FoundryWindow(
                                key = "tab:${tab.id}",
                                role = FoundryWindow.Role.TAB,
                                url = tab.content.url,
                                title = tab.content.title,
                                kind = FoundrySource.kindOf(tab.content.url),
                                tabId = tab.id,
                            )
                        },
                open = state.tabs.mapTo(HashSet()) { it.id },
                elsewhere = data.tabGroupAssignments.filterValues { it != groupId }.keys,
                restored = state.restoreComplete,
            )
        }.distinctUntilChanged()
    }

    /**
     * Reopens [groupId] if Overview's "close group" had put it away, as the browser's own group view does, and starts
     * [cleanUpEmptyGroups].
     */
    @MainThread
    fun reopen(context: Context, groupId: String) {
        val app = context.applicationContext
        cleanUpEmptyGroups(app)
        scope.launch(io) {
            val repository = app.components.core.tabGroupRepository
            if (repository.tabGroupDataFlow.first().tabGroups.any { it.id == groupId && it.closed }) {
                repository.openTabGroup(groupId)
            }
        }
    }

    /**
     * Deletes search groups whose tabs have all been closed, so Overview doesn't keep listing them with "0 tabs". It
     * waits [UNDO_GRACE_MS] first, because closing a tab can be undone and the tab should come back to its group. Runs
     * for the life of the app once started.
     */
    @MainThread
    fun cleanUpEmptyGroups(context: Context) {
        if (cleaning) return
        cleaning = true
        val app = context.applicationContext
        val components = app.components
        scope.launch {
            components.core.store.flow().first { it.restoreComplete }
            combine(components.core.tabGroupRepository.tabGroupDataFlow, components.core.store.flow(), state) {
                    data,
                    browser,
                    searches,
                ->
                val open = browser.tabs.mapTo(HashSet()) { it.id }
                data.tabGroups
                    .filter { g -> !g.closed && searches?.containsKey(g.id) == true && g.tabIds.none { it in open } }
                    .map { it.id }
                    .toSet()
            }
                .distinctUntilChanged()
                .collectLatest { empty ->
                    if (empty.isEmpty()) return@collectLatest
                    delay(UNDO_GRACE_MS)
                    withContext(io) { components.core.tabGroupRepository.deleteTabGroupsById(empty.toList()) }
                    forget(app, empty)
                }
        }
    }

    private var cleaning = false

    /** Waits for the page session of [tabId], or null if it doesn't come. */
    suspend fun awaitSession(context: Context, tabId: String): EngineSession? =
        withTimeoutOrNull(SESSION_TIMEOUT_MS) {
            context.components.core.store
                .flow()
                .mapNotNull { it.findTab(tabId)?.engineState?.engineSession }
                .first()
        }

    private fun openTab(context: Context, groupId: String, window: FoundryWindow): String {
        val components = context.components
        val tabId =
            components.useCases.tabsUseCases.addTab(
                url = window.url,
                selectTab = false,
                startLoading = true,
                flags = CARD_LOAD_FLAGS,
                title = window.title,
            )
        val group = update(context, groupId) { g ->
            g.copy(windows = g.windows.map { if (it.key == window.key) it.copy(tabId = tabId) else it })
        } ?: return tabId
        val repository = components.core.tabGroupRepository
        val isFirstTab = group.windows.count { it.tabId != null } == 1
        scope.launch(io) {
            if (isFirstTab) {
                repository.createTabGroupWithTabs(
                    TabGroup(
                        id = groupId,
                        title = group.query,
                        theme = GROUP_THEME,
                        lastModified = System.currentTimeMillis(),
                        tabIds = listOf(tabId),
                    ),
                )
            } else {
                repository.addTabsToTabGroup(groupId, listOf(tabId))
            }
        }
        return tabId
    }

    /** Adds newly cited sources after the ones already placed, so windows never move under the user. */
    @MainThread
    private fun addSources(context: Context, groupId: String, sources: List<FoundrySource>) {
        update(context, groupId) { group ->
            val known = group.windows.mapTo(HashSet()) { it.key }
            val existing = group.windows.count { it.role == FoundryWindow.Role.SOURCE }
            val added =
                sources
                    .map { FoundryWindow("src:${it.url}", FoundryWindow.Role.SOURCE, it.url, it.title, it.kind) }
                    .filter { it.key !in known }
                    .distinctBy { it.key }
                    .take((MAX_SOURCES - existing).coerceAtLeast(0))
            if (added.isEmpty() && group.sourcesRead) return@update group
            group.copy(windows = group.windows + added, sourcesRead = true)
        }
    }

    private fun forget(context: Context, ids: Set<String>) {
        val all = load(context)
        if (ids.none { it in all }) return
        val left = all - ids
        state.value = left
        save(context, left)
    }

    private fun put(context: Context, group: FoundrySearchGroup) {
        val all = load(context) + (group.id to group)
        val trimmed = all.values.sortedByDescending { it.created }.take(MAX_GROUPS).associateBy { it.id }
        state.value = trimmed
        save(context, trimmed)
    }

    private fun update(
        context: Context,
        groupId: String,
        transform: (FoundrySearchGroup) -> FoundrySearchGroup,
    ): FoundrySearchGroup? {
        val all = load(context)
        val old = all[groupId] ?: return null
        val new = transform(old)
        if (new != old) {
            val updated = all + (groupId to new)
            state.value = updated
            save(context, updated)
        }
        return new
    }

    /** Reads the file once. It is small, and this runs after startup, so a short main-thread read is acceptable. */
    private fun load(context: Context): Map<String, FoundrySearchGroup> {
        state.value?.let { return it }
        val policy = StrictMode.allowThreadDiskReads()
        val loaded =
            try {
                val file = File(context.filesDir, FILE_NAME)
                if (file.exists()) decode(file.readText()) else emptyMap()
            } catch (e: Exception) {
                emptyMap()
            } finally {
                StrictMode.setThreadPolicy(policy)
            }
        state.value = loaded
        return loaded
    }

    private fun save(context: Context, groups: Map<String, FoundrySearchGroup>) {
        val text = encode(groups.values)
        val dir = context.applicationContext
        scope.launch(io) { File(dir.filesDir, FILE_NAME).writeText(text) }
    }

    private fun encode(groups: Collection<FoundrySearchGroup>): String =
        JSONArray(
            groups.map { g ->
                JSONObject()
                    .put("id", g.id)
                    .put("query", g.query)
                    .put("sourcesRead", g.sourcesRead)
                    .put("created", g.created)
                    .put(
                        "windows",
                        JSONArray(
                            g.windows.map { w ->
                                JSONObject()
                                    .put("key", w.key)
                                    .put("role", w.role.name)
                                    .put("url", w.url)
                                    .put("title", w.title)
                                    .put("kind", w.kind.name)
                                    .put("tabId", w.tabId ?: JSONObject.NULL)
                            },
                        ),
                    )
            },
        ).toString()

    private fun decode(text: String): Map<String, FoundrySearchGroup> {
        val array = JSONArray(text)
        return (0 until array.length()).map { i ->
            val g = array.getJSONObject(i)
            val windows = g.getJSONArray("windows")
            FoundrySearchGroup(
                id = g.getString("id"),
                query = g.getString("query"),
                sourcesRead = g.getBoolean("sourcesRead"),
                created = g.getLong("created"),
                windows =
                    (0 until windows.length()).map { j ->
                        val w = windows.getJSONObject(j)
                        FoundryWindow(
                            key = w.getString("key"),
                            role = FoundryWindow.Role.valueOf(w.getString("role")),
                            url = w.getString("url"),
                            title = w.getString("title"),
                            kind = FoundrySource.Kind.valueOf(w.getString("kind")),
                            tabId = if (w.isNull("tabId")) null else w.getString("tabId"),
                        )
                    },
            )
        }.associateBy { it.id }
    }

    private fun enc(query: String) = Uri.encode(query)

    private fun aiModeUrl(query: String) = "https://www.google.com/search?udm=50&q=${enc(query)}"

    private fun googleUrl(query: String) = "https://www.google.com/search?q=${enc(query)}"

    private fun claudeUrl(query: String) = "https://claude.ai/new?q=${enc(query)}"

    private fun chatGptUrl(query: String) = "https://chatgpt.com/?q=${enc(query)}"
}

/**
 * Group windows are not the selected tab, so the "open in the YouTube app?" check (app links) has nowhere to ask and
 * would leave the window blank: load their pages the way "Stay in Foundry" does, without that check.
 */
internal val CARD_LOAD_FLAGS: EngineSession.LoadUrlFlags =
    EngineSession.LoadUrlFlags.select(EngineSession.LoadUrlFlags.LOAD_FLAGS_BYPASS_LOAD_URI_DELEGATE)
