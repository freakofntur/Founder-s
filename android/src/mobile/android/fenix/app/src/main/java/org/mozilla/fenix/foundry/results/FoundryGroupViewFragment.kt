/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.foundry.results

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.compose.content
import androidx.navigation.fragment.findNavController
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.selector.findTab
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.ktx.kotlin.isUrl
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.theme.FirefoxTheme

/**
 * The Group view: one search group laid out in two rows of live windows, named U0, U1, … (the upper row of tall
 * windows) and L0, L1, … (the lower row of landscape windows). The three levels are Overview (all tabs and groups,
 * Fenix's tabs tray), the Group view (this), and a Page (one tab full screen). See `docs/design/service-tabs.md`.
 *
 * Each row scrolls on its own and opens on window 1, so U1, U2 and L1 (the sources behind Google's AI answer) are what
 * you see first; U0 (Google's results) and L0 (the AI answer) are one swipe back. Every window is a real tab in the
 * group, created the first time the window is shown, so the group shows in Overview and "Open" simply switches to
 * that tab as a Page. The AI windows (Claude, ChatGPT) only ask when you tap Ask.
 *
 * Like Chrome's tab groups, the Group view is a panel over Overview: Overview stays visible, dimmed, around it, and
 * tapping outside the panel (or Back) closes it.
 */
class FoundryGroupViewFragment : DialogFragment() {
    private val groupId: String by lazy { arguments?.getString(FoundryResults.ARG_GROUP_ID).orEmpty() }

    /**
     * Whether windows may show their pages. A page can only be shown by one view at a time, and the browser screen we
     * may be coming back from lets go of its page a moment after this screen appears, so pages wait for that.
     */
    private var pagesAllowed by mutableStateOf(false)

    /** The page views on screen, so [openPage] can hand their pages over without removing views mid-frame. */
    private val liveViews = mutableSetOf<EngineView>()

    /** A full-screen, see-through window: [Overlay] draws the dimmed surround and the panel itself. */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar).apply {
            window?.apply {
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                @Suppress("DEPRECATION")
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                WindowCompat.setDecorFitsSystemWindows(this, false)
            }
        }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        pagesAllowed = false
        return content {
            FirefoxTheme {
                val groups by FoundrySearchGroups.groups(requireContext()).collectAsState()
                val group = groups?.get(groupId)
                Overlay(onOutside = ::dismiss) {
                    if (group == null) {
                        if (groups != null) GoneMessage()
                        return@Overlay
                    }
                    GroupView(
                        group = group,
                        onSubmit = ::submit,
                        window = { slot, window, isShown, modifier ->
                            WindowCard(
                                slot = slot,
                                window = window,
                                query = group.query,
                                modifier = modifier,
                                onOpen = { tabId -> openPage(tabId) },
                                onAsk = { w -> FoundrySearchGroups.ensureTab(requireContext(), groupId, w) },
                            ) { w ->
                                WindowPage(
                                    groupId = groupId,
                                    window = w,
                                    show = isShown && pagesAllowed,
                                    liveViews = liveViews,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        view?.postDelayed({ pagesAllowed = true }, PAGE_HANDOVER_DELAY_MS)
    }

    /** A new search starts a new group; an address opens as a normal tab. */
    private fun submit(text: String) {
        val input = text.trim()
        if (input.isEmpty()) return
        if (input.isUrl()) {
            requireComponents.useCases.fenixBrowserUseCases.loadUrlOrSearch(searchTermOrURL = input, newTab = true)
            findNavController().navigate(R.id.browserFragment)
            return
        }
        FoundryResults.openIfSearch(findNavController(), input, private = false)
    }

    /** Lets go of every page first, so the browser screen can show this tab's page. */
    private fun openPage(tabId: String) {
        liveViews.toList().forEach { it.release() }
        liveViews.clear()
        requireComponents.useCases.tabsUseCases.selectTab(tabId)
        findNavController().navigate(R.id.browserFragment)
    }

    private companion object {
        /** How long after this screen appears before its windows take their pages (see [pagesAllowed]). */
        const val PAGE_HANDOVER_DELAY_MS = 250L
    }
}

/** A release faster than this (dp per second) moves the row on even after a short drag. */
private const val FLING_DP_PER_S = 400f

/** Lower-row windows are landscape, but never taller than this share of the space, so the upper row stays tall. */
private const val MAX_LOWER_HEIGHT_FRACTION = 0.42f

/** Height over width of a lower-row window: landscape, close to a video's shape. */
private const val LOWER_ASPECT = 0.75f

private val SIDE_PADDING = 10.dp
private val GAP = 10.dp
private val CARD_SHAPE = RoundedCornerShape(16.dp)

@Composable
private fun GroupView(
    group: FoundrySearchGroup,
    onSubmit: (String) -> Unit,
    window: @Composable (slot: String, window: FoundryWindow?, isShown: Boolean, modifier: Modifier) -> Unit,
) {
    val rows = group.rows()
    val upper = rememberPagerState(initialPage = FoundryRows.OPENING_INDEX) { rows.upper.size }
    val lower = rememberPagerState(initialPage = FoundryRows.OPENING_INDEX) { rows.lower.size }

    // Nothing was cited: open on Google's own windows instead of the AI windows.
    LaunchedEffect(group.hasNoSources) {
        if (group.hasNoSources) {
            if (upper.currentPage == FoundryRows.OPENING_INDEX) upper.animateScrollToPage(0)
            if (lower.currentPage == FoundryRows.OPENING_INDEX) lower.animateScrollToPage(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val upperWidth = (maxWidth - SIDE_PADDING * 2 - GAP) / 2
            val lowerHeight = minOf((maxWidth - SIDE_PADDING * 2) * LOWER_ASPECT, maxHeight * MAX_LOWER_HEIGHT_FRACTION)
            Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(GAP)) {
                WindowRow(state = upper, pageSize = PageSize.Fixed(upperWidth), modifier = Modifier.weight(1f)) { i ->
                    val isShown = i == upper.settledPage || i == upper.settledPage + 1
                    window("U$i", rows.upper[i], isShown, Modifier.fillMaxSize())
                }
                WindowRow(
                    state = lower,
                    pageSize = PageSize.Fill,
                    modifier = Modifier.height(lowerHeight),
                    peek = 28.dp,
                ) { i ->
                    window("L$i", rows.lower[i], i == lower.settledPage, Modifier.fillMaxSize())
                }
            }
        }
        SearchBar(query = group.query, onSubmit = onSubmit)
    }
}

/**
 * One row of windows. Every sideways drag anywhere on the row moves the row by one window (owner's rule: inside a
 * window the page only scrolls up and down), see [sidewaysSwipes].
 */
@Composable
private fun WindowRow(
    state: PagerState,
    pageSize: PageSize,
    modifier: Modifier,
    peek: Dp = 0.dp,
    page: @Composable (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val rowDrag = remember(state) { FoundryRowDrag(state, scope, FLING_DP_PER_S * density) }
    CompositionLocalProvider(LocalRowDrag provides rowDrag) {
        HorizontalPager(
            state = state,
            modifier = modifier.fillMaxWidth().sidewaysSwipes(rowDrag),
            contentPadding = PaddingValues(start = SIDE_PADDING, end = SIDE_PADDING + peek),
            pageSize = pageSize,
            pageSpacing = GAP,
            beyondViewportPageCount = 1,
            userScrollEnabled = false,
        ) { i -> page(i) }
    }
}

/** The row a window sits in, so its page frame ([SidewaysSwipeFrame]) can move it. */
private val LocalRowDrag = compositionLocalOf<FoundryRowDrag?> { null }

/**
 * Moves the row by sideways drags on the parts Compose draws (headers, ask cards, placeholders). Live pages are
 * native views whose touches Compose never sees; [SidewaysSwipeFrame] does the same job for them.
 */
private fun Modifier.sidewaysSwipes(rowDrag: FoundryRowDrag): Modifier =
    pointerInput(rowDrag) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var total = Offset.Zero
            var sideways = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.positionChange()
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!sideways) {
                    total += delta
                    val ratio = FoundryRowDrag.SIDEWAYS_RATIO
                    if (abs(total.x) > viewConfiguration.touchSlop && abs(total.x) > abs(total.y) * ratio) {
                        sideways = true
                        rowDrag.begin()
                    } else if (abs(total.y) > viewConfiguration.touchSlop) {
                        return@awaitEachGesture
                    }
                }
                if (sideways) {
                    rowDrag.drag(delta.x)
                    event.changes.forEach { it.consume() }
                }
            }
            if (sideways) rowDrag.end(tracker.calculateVelocity().x)
        }
    }

/**
 * The Group view's frame: Overview shows through, dimmed, around a panel. Tapping the dimmed part calls [onOutside].
 */
@Composable
private fun Overlay(onOutside: () -> Unit, panel: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(SCRIM)
                .clickable(interactionSource = null, indication = null, onClick = onOutside)
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding()
                .padding(start = PANEL_MARGIN, end = PANEL_MARGIN, top = PANEL_TOP_MARGIN, bottom = PANEL_MARGIN),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Taps inside the panel that nothing else takes must not close it.
                    .clickable(interactionSource = null, indication = null, onClick = {}),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 8.dp,
        ) {
            Box(modifier = Modifier.padding(top = 8.dp)) { panel() }
        }
    }
}

/** How much of Overview shows around the Group view's panel: enough to see it and tap it, little enough to keep room. */
private val PANEL_MARGIN = 12.dp

/** The dimmed strip above the panel, the easiest place to tap to close it. */
private val PANEL_TOP_MARGIN = 40.dp

/** Dims Overview behind the panel. */
private val SCRIM = Color(0x99000000)

/** A window's frame: its slot name, what it holds and an Open button, around its page (or ask card). */
@Composable
private fun WindowCard(
    slot: String,
    window: FoundryWindow?,
    query: String,
    modifier: Modifier,
    onOpen: (tabId: String) -> Unit,
    onAsk: (FoundryWindow) -> Unit,
    page: @Composable (FoundryWindow) -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CARD_SHAPE,
        color = MaterialTheme.colorScheme.surfaceBright,
        shadowElevation = 4.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Badge(window = window, size = 24.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$slot · ${window?.let(::label) ?: "Reading Google's answer…"}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val tabId = window?.tabId
                if (tabId != null) {
                    TextButton(onClick = { onOpen(tabId) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(text = "Open")
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface),
            ) {
                when {
                    window == null -> Placeholder(window = null, text = "Reading Google's AI answer…")
                    window.isAskWindow && window.tabId == null -> AskCard(window = window, query = query, onAsk = onAsk)
                    else -> page(window)
                }
            }
        }
    }
}

/**
 * A window's live page. It starts the first time the window is shown and then stays while the row keeps the window
 * composed: removing a page view in the middle of a swipe crashes Compose, and keeping it avoids reloading.
 */
@Composable
private fun WindowPage(groupId: String, window: FoundryWindow, show: Boolean, liveViews: MutableSet<EngineView>) {
    val context = LocalContext.current
    var tabId by remember(window.key) { mutableStateOf<String?>(null) }
    LaunchedEffect(window.key, show) {
        if (show && tabId == null) tabId = FoundrySearchGroups.ensureTab(context, groupId, window)
    }
    val id = tabId
    val session by produceState<EngineSession?>(initialValue = null, id) {
        if (id == null) return@produceState
        context.components.core.store
            .flow()
            .map { it.findTab(id)?.engineState?.engineSession }
            .distinctUntilChanged()
            .collect { value = it }
    }
    val current = session
    if (current == null) {
        Placeholder(window = window, text = label(window))
        return
    }
    val rowDrag = LocalRowDrag.current
    key(current) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                val view = context.components.core.engine.createView(viewContext)
                try {
                    view.render(current)
                } catch (e: IllegalStateException) {
                    // Still shown by another view; it shows here the next time this window is shown.
                }
                liveViews += view
                SidewaysSwipeFrame(viewContext, rowDrag).apply { addView(view.asView()) }
            },
            update = { frame -> frame.rowDrag = rowDrag },
            onRelease = { frame ->
                (frame.getChildAt(0) as? EngineView)?.let {
                    liveViews -= it
                    it.release()
                }
            },
        )
    }
}

/** An AI window before it is asked: what you searched, the AI's symbol, and an Ask button. */
@Composable
private fun AskCard(window: FoundryWindow, query: String, onAsk: (FoundryWindow) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Badge(window = window, size = 56.dp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "“$query”",
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { onAsk(window) }) { Text(text = "Ask ${window.title}") }
    }
}

@Composable
private fun Placeholder(window: FoundryWindow?, text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Badge(window = window, size = 48.dp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GoneMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "This search group was closed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A window's symbol: the AI's colours for Claude and ChatGPT, a play sign for videos, else the site's initial. */
@Composable
private fun Badge(window: FoundryWindow?, size: Dp) {
    val colors = MaterialTheme.colorScheme
    val (glyph, background, foreground) =
        when {
            window == null -> Triple("…", colors.surfaceVariant, colors.onSurfaceVariant)
            window.role == FoundryWindow.Role.CLAUDE -> Triple("✳", CLAUDE_ORANGE, Color.White)
            window.role == FoundryWindow.Role.CHATGPT -> Triple("⊛", Color.Black, Color.White)
            window.role == FoundryWindow.Role.AI_MODE -> Triple("G✦", colors.primary, colors.onPrimary)
            window.role == FoundryWindow.Role.GOOGLE -> Triple("G", colors.primary, colors.onPrimary)
            window.kind == FoundrySource.Kind.VIDEO -> Triple("▶", VIDEO_RED, Color.White)
            window.kind == FoundrySource.Kind.IMAGE -> Triple("▣", colors.tertiary, colors.onTertiary)
            else ->
                Triple(
                    host(window.url).take(1).uppercase(),
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
        }
    Box(modifier = Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Text(text = glyph, color = foreground, maxLines = 1, fontSize = (size.value * 0.42f).sp)
    }
}

private val CLAUDE_ORANGE = Color(0xFFD97757)
private val VIDEO_RED = Color(0xFFE62117)

private fun label(window: FoundryWindow): String =
    when (window.role) {
        FoundryWindow.Role.AI_MODE -> "Google AI answer"
        FoundryWindow.Role.GOOGLE -> "Google results"
        FoundryWindow.Role.CLAUDE, FoundryWindow.Role.CHATGPT -> window.title
        FoundryWindow.Role.SOURCE -> host(window.url)
    }

private fun host(url: String): String = Uri.parse(url).host.orEmpty().removePrefix("www.").removePrefix("m.")

@Composable
private fun SearchBar(query: String, onSubmit: (String) -> Unit) {
    var text by remember(query) { mutableStateOf(TextFieldValue(query)) }
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    // Tapping the box selects the current search, so typing starts a new one instead of adding to it.
    LaunchedEffect(focused) {
        if (focused) {
            delay(SELECT_ALL_DELAY_MS)
            text = text.copy(selection = TextRange(0, text.text.length))
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            singleLine = true,
            shape = RoundedCornerShape(30.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        onSubmit(text.text)
                    },
                ),
        )
    }
}

/** Lets the tap that focused the search box place its cursor first, so selecting everything isn't undone. */
private const val SELECT_ALL_DELAY_MS = 50L
