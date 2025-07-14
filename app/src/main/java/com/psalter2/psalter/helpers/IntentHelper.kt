package com.psalter2.psalter.helpers

import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.psalter2.psalter.BuildConfig

object IntentHelper {
    val RateIntent get() = Intent(Intent.ACTION_VIEW,
        "https://play.google.com/store/apps/details?id=com.psalter2.psalter".toUri())
    val FeedbackIntent: Intent get() {
        var body = "\n\n\n"
        body += "---------------------------\n"
        body += "App version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
        body += "Android version: " + Build.VERSION.RELEASE

        return Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf("psalter2app@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Psalter App")
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }
}