/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.foundry.results

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.WebExtension
import org.json.JSONObject

/**
 * A page that Google's AI answer cites, in the order the answer cites it.
 *
 * @param kind Videos and images go in the lower (landscape) row; pages fill the upper row.
 */
data class FoundrySource(val url: String, val title: String, val kind: Kind) {
    /** What a source is, read from its address. */
    enum class Kind { PAGE, VIDEO, IMAGE }
}

/**
 * Reads Google AI Mode's answer in the background: Foundry's built-in web extension runs a content script on AI Mode
 * pages (`assets/extensions/foundry/aimode.js`) that sends the cited links to the app, and this hands them to whoever
 * watches that page's session.
 */
object FoundryAiMode {
    private const val EXTENSION_ID = "foundry-results@foundry.browser"
    private const val EXTENSION_URL = "resource://android/assets/extensions/foundry/"

    /** The "application" name the content script sends to; it must match `aimode.js`. */
    private const val APP_NAME = "foundry"
    private const val LOG_TAG = "FoundryAiMode"

    private val main = Handler(Looper.getMainLooper())
    private var extension: WebExtension? = null
    private var installing = false
    private val waiting = mutableListOf<(WebExtension) -> Unit>()

    /**
     * Runs [block] once Foundry's extension is installed. Built-in extensions are registered again on every app start,
     * so the first search after a start waits for that (a few hundred ms) before loading AI Mode: a page loaded
     * earlier would not get the content script.
     */
    @MainThread
    fun withExtension(engine: Engine, block: (WebExtension) -> Unit) {
        extension?.let {
            block(it)
            return
        }
        waiting += block
        if (installing) return
        installing = true
        engine.installBuiltInWebExtension(
            EXTENSION_ID,
            EXTENSION_URL,
            onSuccess = { installed ->
                main.post {
                    extension = installed
                    installing = false
                    waiting.toList().forEach { it(installed) }
                    waiting.clear()
                }
            },
            onError = { throwable ->
                Log.e(LOG_TAG, "Couldn't install the AI Mode reader", throwable)
                main.post { installing = false }
            },
        )
    }

    /** Calls [onSources] on the main thread each time the AI Mode page in [session] reports its cited sources. */
    @MainThread
    fun watch(engine: Engine, session: EngineSession, onSources: (List<FoundrySource>) -> Unit) {
        withExtension(engine) { installed ->
            installed.registerContentMessageHandler(
                session,
                APP_NAME,
                object : MessageHandler {
                    override fun onMessage(message: Any, source: EngineSession?): Any? {
                        val sources = parse(message) ?: return null
                        main.post { onSources(sources) }
                        return null
                    }
                },
            )
        }
    }

    private fun parse(message: Any): List<FoundrySource>? {
        val json = message as? JSONObject ?: return null
        if (json.optString("type") != "aimode-sources") return null
        val list = json.optJSONArray("sources") ?: return null
        return (0 until list.length()).mapNotNull { i ->
            val item = list.optJSONObject(i) ?: return@mapNotNull null
            val url = item.optString("url").takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val kind =
                when (item.optString("kind")) {
                    "video" -> FoundrySource.Kind.VIDEO
                    "image" -> FoundrySource.Kind.IMAGE
                    else -> FoundrySource.Kind.PAGE
                }
            FoundrySource(url = url, title = item.optString("title"), kind = kind)
        }.also { Log.i(LOG_TAG, "AI Mode cited ${it.size}: ${it.joinToString { s -> "${s.kind} ${s.url}" }}") }
    }
}
