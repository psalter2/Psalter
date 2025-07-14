package com.psalter2.psalter.infrastructure

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.psalter2.psalter.models.LogEvent
import com.psalter2.psalter.models.Psalter
import com.psalter2.psalter.models.SearchMode

object Logger {
    private const val TAG = "PsalterLog"
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    }

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun e(message: String, ex: Throwable?) {
        Log.e(TAG, message, ex)
        if (ex != null) {
            FirebaseCrashlytics.getInstance().log(message)
            FirebaseCrashlytics.getInstance().recordException(ex)
        }
    }

    fun changeScore(scoreVisible: Boolean) {
        val params = mapOf("ScoreVisible" to scoreVisible.toString())
        event(LogEvent.ChangeScore, params)
    }

    fun changeTheme(nightMode: Boolean) {
        val params = mapOf("NightMode" to nightMode.toString())
        event(LogEvent.ChangeTheme, params)
    }

    fun playbackStarted(numberTitle: String, shuffling: Boolean) {
        val params = mapOf("Number" to numberTitle, "Shuffling" to shuffling.toString())
        event(LogEvent.PlayPsalter, params)
    }

    fun skipToNext(skippedPsalter: Psalter?){
        val params = mapOf("SkippedPsalter" to (skippedPsalter?.title ?: "null"))
        event(LogEvent.SkipToNext, params)
    }

    fun searchPsalter(number: Int){
        event(LogEvent.SearchPsalter, mapOf("Number" to number.toString()))
    }
    fun searchPsalm(psalm: Int, psalterChosen: Int? = null){
        val params = mutableMapOf("Psalm" to psalm.toString())
        if(psalterChosen != null) params["PsalterChosen"] = psalterChosen.toString()
        event(LogEvent.SearchPsalm, params)
    }
    fun searchLyrics(query: String, psalterChosen: Int? = null){
        val params = mutableMapOf("Query" to query)
        if(psalterChosen != null) params["PsalterChosen"] = psalterChosen.toString()
        event(LogEvent.SearchLyrics, params)
    }
    fun searchEvent(searchMode: SearchMode, query: String, psalterChosen: Int? = null){
        when (searchMode){
            SearchMode.Lyrics -> searchLyrics(query, psalterChosen)
            SearchMode.Psalter -> searchPsalter(query.toInt())
            SearchMode.Psalm -> searchPsalm(query.toInt(), psalterChosen)
            SearchMode.Favorites -> event(LogEvent.SearchFavorites, mutableMapOf("PsalterChosen" to psalterChosen.toString()))
        }
    }
    fun ratePromptAttempt(timesShown: Int){
        val params = mutableMapOf("TimesShown" to timesShown.toString())
        event(LogEvent.RateFlow, params)
    }

    fun event(event: LogEvent, params: Map<String, String>? = null) {
        if (params != null) {
            val bundle = Bundle()
            params.forEach({
                bundle.putString(it.key, it.value)
            })
            firebaseAnalytics?.logEvent(event.toString(), bundle)
        }
    }
}
