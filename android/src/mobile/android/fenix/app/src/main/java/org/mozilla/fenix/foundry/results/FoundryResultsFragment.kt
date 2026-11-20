/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.foundry.results

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.navigation.fragment.findNavController
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView
import mozilla.components.support.ktx.kotlin.isUrl
import org.mozilla.fenix.R
import org.mozilla.fenix.e2e.SystemInsetsPaddedFragment
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.theme.FirefoxTheme

/**
 * Foundry's Results screen: one search shown by several services at once, as live pages ranked by [FoundryPriority],
 * like a tab group made for the query. Each page holds three cards: priority 1 in the big square at the bottom, 2 and
 * 3 as tall cards above it (left, then right). Swiping left turns one page at a time to the next three, and the next
 * page peeks in at the right edge. Tapping a card's Open button opens its current page as a normal tab, and
 * this screen stays on the back stack with every card as it was.
 *
 * Each card's page lives as long as this fragment (not just its view), so coming back from a full-screen card does
 * not reload anything. A card loads only once its page is landed on: the peeking page shows placeholders, so a search
 * costs three pages, not every service.
 */
class FoundryResultsFragment : Fragment(), SystemInsetsPaddedFragment {
    private val sessions = mutableMapOf<String, EngineSession>()
    private val currentUrls = mutableMapOf<String, String>()
    private val loaded = mutableStateListOf<String>()
    private var query by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        query =
            savedInstanceState?.getString(FoundryResults.ARG_QUERY)
                ?: arguments?.getString(FoundryResults.ARG_QUERY).orEmpty()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(FoundryResults.ARG_QUERY, query)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        content {
            FirefoxTheme {
                ResultsScreen(
                    query = query,
                    onSubmit = ::submit,
                    card = { service, priority, isLanded, modifier ->
                        if (isLanded && service.id !in loaded) loaded.add(service.id)
                        ServiceCard(
                            service = service,
                            priority = priority,
                            modifier = modifier,
                            onOpen = { openFullScreen(service) },
                        ) {
                            if (service.id !in loaded) {
                                CardPlaceholder(service)
                                return@ServiceCard
                            }
                            key(service.id) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { context ->
                                        val view = requireComponents.core.engine.createView(context)
                                        view.render(sessionFor(service))
                                        view.asView()
                                    },
                                    onRelease = { view -> (view as? EngineView)?.release() },
                                )
                            }
                        }
                    },
                )
            }
        }

    override fun onDestroy() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        super.onDestroy()
    }

    private fun sessionFor(service: FoundryService): EngineSession =
        sessions.getOrPut(service.id) {
            requireComponents.core.engine.createSession().also { session ->
                session.register(
                    object : EngineSession.Observer {
                        override fun onLocationChange(url: String, hasUserGesture: Boolean) {
                            currentUrls[service.id] = url
                        }
                    },
                )
                session.loadUrl(service.urlFor(query), flags = CARD_LOAD_FLAGS)
            }
        }

    /** A new query reloads every card that has been created; an address opens as a normal tab instead. */
    private fun submit(text: String) {
        val input = text.trim()
        if (input.isEmpty()) return
        if (input.isUrl()) {
            requireComponents.useCases.fenixBrowserUseCases.loadUrlOrSearch(searchTermOrURL = input, newTab = true)
            findNavController().navigate(R.id.browserFragment)
            return
        }
        query = input
        for ((id, session) in sessions) {
            val service = FoundryServices.all.firstOrNull { it.id == id } ?: continue
            currentUrls.remove(id)
            session.loadUrl(service.urlFor(input), flags = CARD_LOAD_FLAGS)
        }
    }

    private fun openFullScreen(service: FoundryService) {
        val url = currentUrls[service.id] ?: service.urlFor(query)
        requireComponents.useCases.tabsUseCases.addTab(url = url, selectTab = true)
        findNavController().navigate(R.id.browserFragment)
    }
}

/**
 * Cards are not tabs, so the "open in the YouTube app?" check (app links) has nowhere to ask and would leave the card
 * blank: load their pages the way "Stay in Foundry" does, without that check.
 */
private val CARD_LOAD_FLAGS =
    EngineSession.LoadUrlFlags.select(EngineSession.LoadUrlFlags.LOAD_FLAGS_BYPASS_LOAD_URI_DELEGATE)

/** Cards per page: priority 1 in the big square at the bottom, 2 and 3 side by side above it. */
private const val CARDS_PER_PAGE = 3

/** How far a drag must go, as a share of the page width, before releasing it turns the page. */
private const val PAGE_TURN_THRESHOLD = 0.5f

/** The bottom card is square, but never taller than this share of the height (so landscape keeps an upper row). */
private const val MAX_SQUARE_HEIGHT_FRACTION = 0.6f

@Composable
private fun ResultsScreen(
    query: String,
    onSubmit: (String) -> Unit,
    card: @Composable (service: FoundryService, priority: Int, isLanded: Boolean, modifier: Modifier) -> Unit,
) {
    val ranked = remember(query) { FoundryPriority.rank(query) }
    val pageCount = (ranked.size + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE
    val pagerState = rememberPagerState { pageCount }
    LaunchedEffect(query) { pagerState.scrollToPage(0) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 28.dp, top = 8.dp),
            pageSpacing = 8.dp,
            beyondViewportPageCount = 1,
            flingBehavior =
                PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                    snapPositionalThreshold = PAGE_TURN_THRESHOLD,
                ),
        ) { page ->
            val isLanded = pagerState.settledPage == page
            val first = page * CARDS_PER_PAGE

            @Composable
            fun slot(offset: Int, modifier: Modifier) {
                val service = ranked.getOrNull(first + offset)
                if (service == null) {
                    Spacer(modifier = modifier)
                } else {
                    card(service, first + offset + 1, isLanded, modifier)
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val square = minOf(maxWidth, maxHeight * MAX_SQUARE_HEIGHT_FRACTION)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        slot(1, Modifier.weight(1f).fillMaxHeight())
                        slot(2, Modifier.weight(1f).fillMaxHeight())
                    }
                    slot(0, Modifier.fillMaxWidth().height(square))
                }
            }
        }
        SearchBar(query = query, onSubmit = onSubmit)
    }
}

@Composable
private fun ServiceCard(
    service: FoundryService,
    priority: Int,
    modifier: Modifier,
    onOpen: () -> Unit,
    page: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = service.badge, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$priority · ${service.name}",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(text = "Open")
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) { page() }
        }
    }
}

/** What a card shows until it is landed on: its badge and name, at no page-load cost. */
@Composable
private fun CardPlaceholder(service: FoundryService) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = service.badge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = service.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchBar(query: String, onSubmit: (String) -> Unit) {
    var text by remember(query) { mutableStateOf(query) }
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(30.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        onSubmit(text)
                    },
                ),
        )
    }
}
