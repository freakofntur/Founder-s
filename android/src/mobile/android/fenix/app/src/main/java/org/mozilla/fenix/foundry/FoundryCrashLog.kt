/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.foundry

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.StrictMode
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.fenix.BuildConfig
import java.io.File

/**
 * Keeps the details of the last crash, so they can be shown and copied the next time Foundry starts: test builds
 * otherwise close without any notice.
 *
 * Nothing here touches the disk on the main thread during startup, where debug builds' StrictMode kills the app for
 * it (even [Context.getFilesDir] reads the disk).
 */
object FoundryCrashLog {
    private const val FILE_NAME = "foundry-last-crash.txt"
    private const val TEXT_SP = 11f
    private const val PADDING_DP = 20

    private val logger = Logger("FoundryCrashLog")

    /** Records uncaught exceptions to a file, then passes them on to the handler that was already installed. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(appContext, thread, throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    @Suppress("TooGenericExceptionCaught") // Recording must never stop the crash from being handled.
    private fun record(context: Context, thread: Thread, throwable: Throwable) {
        // The app is going down anyway; StrictMode must not get in the way of writing the report.
        val policy = StrictMode.allowThreadDiskWrites()
        try {
            File(context.filesDir, FILE_NAME).writeText(
                "Foundry ${BuildConfig.VERSION_NAME} on ${Build.MANUFACTURER} ${Build.MODEL}, " +
                    "Android ${Build.VERSION.RELEASE}\nThread: ${thread.name}\n\n${throwable.stackTraceToString()}",
            )
        } catch (e: Exception) {
            logger.error("Could not record the crash", e)
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
    }

    /** If Foundry crashed last time, shows what happened, with a Copy button. The file is read in the background. */
    fun showIfCrashed(activity: AppCompatActivity) {
        val appContext = activity.applicationContext
        activity.lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) { takeReport(appContext) } ?: return@launch
            if (!activity.isFinishing && !activity.isDestroyed) show(activity, report)
        }
    }

    @Suppress("TooGenericExceptionCaught") // A broken report file must not stop Foundry from starting.
    private fun takeReport(context: Context): String? =
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) file.readText().also { file.delete() } else null
        } catch (e: Exception) {
            logger.error("Could not read the last crash", e)
            null
        }

    private fun show(activity: AppCompatActivity, report: String) {
        val padding = (PADDING_DP * activity.resources.displayMetrics.density).toInt()
        val text =
            TextView(activity).apply {
                setText(report)
                setTextIsSelectable(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
                setPadding(padding, padding, padding, padding)
            }
        AlertDialog.Builder(activity)
            .setTitle("Foundry closed unexpectedly")
            .setView(ScrollView(activity).apply { addView(text) })
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = activity.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("Foundry crash", report))
                Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
