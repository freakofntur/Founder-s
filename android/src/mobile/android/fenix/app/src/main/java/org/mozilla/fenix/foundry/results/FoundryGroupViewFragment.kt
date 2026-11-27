/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.foundry.results

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.BackEventCompat
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import androidx.core.view.children
import androidx.fragment.app.DialogFragment
import androidx.fragment.compose.content
import androidx.navigation.fragment.findNavController
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.ktx.kotlin.isUrl
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.geckoview.GeckoView

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
 * tapping outside the panel (or Back) closes it. The panel grows in when it opens and shrinks away when it closes.
 *
 * Every group opens here, not only searches: a normal tab group's tabs are laid out by [FoundryRows.forTabs].
 */
class FoundryGroupViewFragment : DialogFragment() {
    private val groupId: String by lazy { arguments?.getString(FoundryResults.ARG_GROUP_ID).orEmpty() }

    /**
     * Whether windows may show their pages. A page can only be shown by one view at a time, and the browser screen we
     * may be coming back from lets go of its page a moment after this screen appears, so pages wait for that.
     */
    private var pagesAllowed by mutableStateOf(false)

    /** The page views on screen, so [openPage] can hand their pages over without removing views mid-frame. */
    private val liveViews = mutableMapOf<EngineView, String>()

    /**
     * While the panel closes (or follows a back swipe), each live page is swapped for a still picture of itself
     * ([pictures], by tab id): redrawing live pages every frame of the shrink was too slow, so the panel stayed still
     * and then vanished instead of animating.
     */
    private var frozen by mutableStateOf(false)
    private val pictures = mutableStateMapOf<String, ImageBitmap>()

    /** How far a back swipe has gone (0 to 1); the panel shrinks with it, like Android's predictive back. */
    private var backDrag by mutableFloatStateOf(0f)

    /** Set when the panel should close: [Overlay] plays the closing animation, then dismisses. */
    private var closing by mutableStateOf(false)

    /** A full-screen, see-through window: [Overlay] draws the dimmed surround and the panel itself. */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        ComponentDialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar).apply {
            onBackPressedDispatcher.addCallback(
                this@FoundryGroupViewFragment,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackStarted(backEvent: BackEventCompat) = freeze()

                    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                        backDrag = backEvent.progress
                    }

                    override fun handleOnBackCancelled() {
                        backDrag = 0f
                        unfreeze()
                    }

                    override fun handleOnBackPressed() = close()
                },
            )
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
                val context = LocalContext.current
                val searchGroups by FoundrySearchGroups.groups(context).collectAsState()
                val search = searchGroups?.get(groupId)
                val tabs by produceState<FoundryGroupTabs?>(initialValue = null, groupId) {
                    FoundrySearchGroups.tabsOf(context, groupId).collect { value = it }
                }
                Overlay(
                    closing = closing,
                    backDrag = backDrag,
                    onOutside = ::close,
                    onClosed = ::dismissAllowingStateLoss,
                ) {
                    val plain = tabs?.tabs
                    when {
                        search != null ->
                            GroupView(
                                groupId = groupId,
                                rows = search.rows(tabs),
                                opening = FoundryRows.OPENING_INDEX,
                                openAtStart = search.hasNoSources,
                                query = search.query,
                                onSubmit = ::submit,
                                window = { slot, window, isShown, modifier ->
                                    Window(slot, window, isShown, search.query, modifier)
                                },
                            )
                        searchGroups != null && plain != null ->
                            if (plain.isEmpty()) {
                                GoneMessage()
                            } else {
                                GroupView(
                                    groupId = groupId,
                                    rows = FoundryRows.forTabs(plain),
                                    opening = 0,
                                    openAtStart = false,
                                    query = "",
                                    onSubmit = ::submit,
                                    window = { slot, window, isShown, modifier ->
                                        Window(slot, window, isShown, "", modifier)
                                    },
                                )
                            }
                    }
                }
            }
        }
    }

    @Composable
    private fun Window(slot: String, window: FoundryWindow?, isShown: Boolean, query: String, modifier: Modifier) {
        WindowCard(
            slot = slot,
            window = window,
            query = query,
            modifier = modifier,
            onOpen = { tabId -> openPage(tabId) },
            onAsk = { w -> FoundrySearchGroups.ensureTab(requireContext(), groupId, w) },
        ) { w ->
            WindowPage(
                groupId = groupId,
                window = w,
                show = isShown && pagesAllowed,
                picture = if (frozen) w.tabId?.let { pictures[it] } else null,
                liveViews = liveViews,
            )
        }
    }

    /** Closes the panel with its animation (tap outside, Back). */
    private fun close() {
        freeze()
        closing = true
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
        liveViews.forEach { (view, id) -> pageBitmap(view)?.let { saveThumbnail(requireContext(), id, it) } }
        liveViews.keys.toList().forEach { it.release() }
        liveViews.clear()
        requireComponents.useCases.tabsUseCases.selectTab(tabId)
        FoundryResults.pageOpenedFromGroup(tabId, groupId)
        findNavController().navigate(R.id.browserFragment)
    }

    /** Swaps every live page for a picture of itself (see [frozen]). */
    private fun freeze() {
        if (frozen) return
        liveViews.forEach { (view, tabId) ->
            pageBitmap(view)?.let {
                pictures[tabId] = it.asImageBitmap()
                saveThumbnail(requireContext(), tabId, it)
            }
            view.asView().visibility = View.INVISIBLE
        }
        frozen = true
    }

    /** Shows the live pages again after a back swipe was cancelled. */
    private fun unfreeze() {
        if (!frozen || closing) return
        liveViews.keys.forEach { it.asView().visibility = View.VISIBLE }
        frozen = false
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

private val GAP = 10.dp

/** How much of the next window shows at each edge of a row, so you can see there is more both ways. */
private val PEEK = 22.dp

/** Space from the panel's edge to a row's main windows: the peeking window plus a gap. */
private val EDGE = PEEK + GAP
private val CARD_SHAPE = RoundedCornerShape(16.dp)

@Composable
private fun GroupView(
    groupId: String,
    rows: FoundryRows,
    opening: Int,
    openAtStart: Boolean,
    query: String,
    onSubmit: (String) -> Unit,
    window: @Composable (slot: String, window: FoundryWindow?, isShown: Boolean, modifier: Modifier) -> Unit,
) {
    // Coming back to a group (Back from one of its Pages) opens the rows where you left them.
    val saved = remember(groupId) { rowPositions[groupId] }
    val upper = rememberPagerState(initialPage = saved?.first ?: opening) { rows.upper.size }
    val lower = rememberPagerState(initialPage = saved?.second ?: opening) { rows.lower.size }
    val upperMoved = KeepOpeningUntilMoved(upper, opening, rows.upper.size, alreadyMoved = saved != null)
    val lowerMoved = KeepOpeningUntilMoved(lower, opening, rows.lower.size, alreadyMoved = saved != null)
    LaunchedEffect(upper, lower) {
        snapshotFlow { Triple(upper.settledPage, lower.settledPage, upperMoved.value || lowerMoved.value) }
            .collect { (u, l, moved) -> if (moved) rowPositions[groupId] = u to l }
    }

    // A search whose answer cited nothing: open on Google's own windows instead of the empty source windows.
    LaunchedEffect(openAtStart) {
        if (openAtStart && saved == null) {
            if (upper.currentPage == opening) upper.animateScrollToPage(0)
            if (lower.currentPage == opening) lower.animateScrollToPage(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val upperWidth = (maxWidth - EDGE * 2 - GAP) / 2
            val lowerHeight = minOf((maxWidth - EDGE * 2) * LOWER_ASPECT, maxHeight * MAX_LOWER_HEIGHT_FRACTION)
            Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(GAP)) {
                val upperShown by mostlyVisible(upper)
                WindowRow(
                    state = upper,
                    pageSize = PageSize.Fixed(upperWidth),
                    snap = false,
                    modifier = Modifier.weight(1f),
                ) { i ->
                    window("U$i", rows.upper[i], i in upperShown, Modifier.fillMaxSize())
                }
                if (rows.lower.isNotEmpty()) {
                    WindowRow(
                        state = lower,
                        pageSize = PageSize.Fill,
                        snap = true,
                        modifier = Modifier.height(lowerHeight),
                    ) { i ->
                        window("L$i", rows.lower[i], i == lower.settledPage, Modifier.fillMaxSize())
                    }
                }
            }
        }
        SearchBar(query = query, onSubmit = onSubmit)
    }
}

/**
 * Keeps a row on its [opening] window while its windows change underneath, until the person moves the row.
 *
 * A group opens before Google's AI answer has been read: the row may hold one window at first (so the pager clamps to
 * window 0), and sources inserted later shift the windows by index. Without this the row opened on U0, or on U2 and U3,
 * instead of U1 and U2.
 *
 * @param alreadyMoved The row reopens where the person left it, so it must not be pulled back to [opening].
 * @return Whether the person has moved the row.
 */
@Composable
private fun KeepOpeningUntilMoved(state: PagerState, opening: Int, size: Int, alreadyMoved: Boolean): State<Boolean> {
    val moved = remember(state) { mutableStateOf(alreadyMoved) }
    var aligning by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling -> if (scrolling && !aligning) moved.value = true }
    }
    LaunchedEffect(state, size) {
        if (moved.value || size <= opening) return@LaunchedEffect
        val target = opening.coerceAtMost(size - 1)
        if (state.currentPage != target || state.currentPageOffsetFraction != 0f) {
            aligning = true
            try {
                state.scrollToPage(target)
            } finally {
                aligning = false
            }
        }
    }
    return moved
}

/** Where each group's rows were last left (upper, lower), for this run of the app. */
private val rowPositions = HashMap<String, Pair<Int, Int>>()

/**
 * The windows of a free-scrolling row that are more than half on screen. Only these load their pages, so the windows
 * peeking in at the edges stay placeholders (a search only loads what you look at).
 */
@Composable
private fun mostlyVisible(state: PagerState) =
    remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            info.visiblePagesInfo
                .filter { page ->
                    val start = maxOf(page.offset, info.viewportStartOffset)
                    val end = minOf(page.offset + info.pageSize, info.viewportEndOffset)
                    (end - start) * 2 > info.pageSize
                }
                .map { it.index }
                .toSet()
        }
    }

/**
 * One row of windows. Every sideways drag anywhere on the row moves the row (owner's rule: inside a window the page
 * only scrolls up and down), see [sidewaysSwipes]. The windows beyond each edge peek in by [PEEK] on both sides.
 */
@Composable
private fun WindowRow(
    state: PagerState,
    pageSize: PageSize,
    snap: Boolean,
    modifier: Modifier,
    page: @Composable (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val decay = rememberSplineBasedDecay<Float>()
    val rowDrag = remember(state) { FoundryRowDrag(state, scope, snap, FLING_DP_PER_S * density, decay) }
    CompositionLocalProvider(LocalRowDrag provides rowDrag) {
        HorizontalPager(
            state = state,
            modifier = modifier.fillMaxWidth().sidewaysSwipes(rowDrag),
            contentPadding = PaddingValues(horizontal = EDGE),
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
 * The panel grows in and the dim fades in when it opens; when [closing] turns true they play backwards, then
 * [onClosed] runs.
 */
@Composable
private fun Overlay(
    closing: Boolean,
    backDrag: Float,
    onOutside: () -> Unit,
    onClosed: () -> Unit,
    panel: @Composable () -> Unit,
) {
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { opened = true }
    val progress by animateFloatAsState(
        targetValue = if (opened && !closing) 1f else 0f,
        animationSpec = tween(durationMillis = PANEL_ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "panel",
        finishedListener = { if (closing) onClosed() },
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(SCRIM.copy(alpha = SCRIM.alpha * progress))
                .clickable(interactionSource = null, indication = null, onClick = onOutside)
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding()
                .padding(start = PANEL_MARGIN, end = PANEL_MARGIN, top = PANEL_TOP_MARGIN, bottom = PANEL_MARGIN),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale =
                            (PANEL_START_SCALE + (1f - PANEL_START_SCALE) * progress) * (1f - BACK_SHRINK * backDrag)
                        scaleX = scale
                        scaleY = scale
                        alpha = progress
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
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

/** How long the panel takes to open or close. */
private const val PANEL_ANIMATION_MS = 260

/** How much smaller the panel gets at the end of a back swipe, before it closes. */
private const val BACK_SHRINK = 0.1f

/** The panel's size when it starts opening or finishes closing, as a share of its full size. */
private const val PANEL_START_SCALE = 0.86f

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
private fun WindowPage(
    groupId: String,
    window: FoundryWindow,
    show: Boolean,
    picture: ImageBitmap?,
    liveViews: MutableMap<EngineView, String>,
) {
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
    val liveId = id ?: return
    var liveView by remember(current) { mutableStateOf<EngineView?>(null) }
    // Overview's card for a group shows its tabs' thumbnails, but the browser only takes them of the selected tab:
    // take one of each window a moment after its page has loaded.
    LaunchedEffect(liveId, liveView) {
        val view = liveView ?: return@LaunchedEffect
        context.components.core.store
            .flow()
            .map { it.findTab(liveId)?.content?.loading }
            .distinctUntilChanged()
            .collectLatest { loading ->
                if (loading == false) {
                    delay(THUMBNAIL_DELAY_MS)
                    pageBitmap(view)?.let { saveThumbnail(context, liveId, it) }
                }
            }
    }
    val rowDrag = LocalRowDrag.current
    key(current) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                val view = context.components.core.engine.createView(viewContext)
                // Draw through a TextureView, not Gecko's default SurfaceView: a SurfaceView ignores the panel's
                // clipping and fade, so pages drew outside the panel's edge and stayed solid while it closed.
                (view.asView() as? ViewGroup)?.children?.filterIsInstance<GeckoView>()?.firstOrNull()
                    ?.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                try {
                    view.render(current)
                } catch (e: IllegalStateException) {
                    // Still shown by another view; it shows here the next time this window is shown.
                }
                liveViews[view] = liveId
                liveView = view
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
    if (picture != null) {
        Image(
            bitmap = picture,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
    }
}

/** How long after a window's page has loaded before its thumbnail is taken, so it has drawn. */
private const val THUMBNAIL_DELAY_MS = 1_500L

/** Stores [bitmap] as [tabId]'s thumbnail, as the browser does for the selected tab, so Overview can show it. */
private fun saveThumbnail(context: Context, tabId: String, bitmap: Bitmap) {
    context.components.core.store.dispatch(ContentAction.UpdateThumbnailAction(tabId, bitmap))
}

/** A still picture of what a live page shows now, read from the TextureView it draws into; null if it has none yet. */
internal fun pageBitmap(view: EngineView): Bitmap? {
    fun find(v: View): TextureView? =
        v as? TextureView ?: (v as? ViewGroup)?.children?.firstNotNullOfOrNull { find(it) }
    return find(view.asView())?.takeIf { it.isAvailable }?.bitmap
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
        Text(text = "This group has no open tabs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    siteName(host(window.url)).take(1).uppercase(),
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
        }
    val icon = rememberTabIcon(window?.tabId)
    Box(modifier = Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(size / 6))
        } else {
            Text(text = glyph, color = foreground, maxLines = 1, fontSize = (size.value * 0.42f).sp)
        }
    }
}

/** The site icon of a window's tab once the page has given one, so plain tabs show their site, not a letter. */
@Composable
private fun rememberTabIcon(tabId: String?): ImageBitmap? {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, tabId) {
        if (tabId == null) return@produceState
        context.components.core.store
            .flow()
            .map { it.findTab(tabId)?.content?.icon }
            .distinctUntilChanged()
            .collect { bitmap -> value = bitmap?.asImageBitmap() }
    }
    return icon
}

/**
 * The part of a host people recognise: "in.trip.com" gives "trip", "www.bbc.co.uk" gives "bbc". Used for a window's
 * letter while its site icon hasn't loaded.
 */
private fun siteName(host: String): String {
    val labels = host.split('.').filter { it.isNotEmpty() }
    if (labels.size < 2) return host
    val secondLevel = labels[labels.size - 2]
    return if (secondLevel in SECOND_LEVEL_SUFFIXES && labels.size >= 3) labels[labels.size - 3] else secondLevel
}

private val SECOND_LEVEL_SUFFIXES = setOf("co", "com", "org", "net", "ac", "gov", "edu")

private val CLAUDE_ORANGE = Color(0xFFD97757)
private val VIDEO_RED = Color(0xFFE62117)

private fun label(window: FoundryWindow): String =
    when (window.role) {
        FoundryWindow.Role.AI_MODE -> "Google AI answer"
        FoundryWindow.Role.GOOGLE -> "Google results"
        FoundryWindow.Role.CLAUDE, FoundryWindow.Role.CHATGPT -> window.title
        FoundryWindow.Role.SOURCE -> host(window.url)
        FoundryWindow.Role.TAB -> window.title.ifBlank { host(window.url) }
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
