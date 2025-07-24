package com.psalter2.psalter.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.psalter2.psalter.R

class StorageHelper(private val context: Context) {
    private val sPref: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var nightMode
        get() = getBoolean(R.string.pref_nightmode)
        set(b) = setBoolean(R.string.pref_nightmode, b)

    var recentNumbers
        get() = getStringList(R.string.action_recents)
        set(n) = setStringList(R.string.action_recents, n)

    fun addRecentNumber(number: Int) {
        val numberStr = number.toString()
        val numbers = buildList {
            add(numberStr)
            addAll(recentNumbers.filter { it != numberStr && it.isNotBlank() && it != "," })
        }.take(25).toCollection(ArrayList())
        recentNumbers = numbers
    }

    var scoreShown
        get() = getBoolean(R.string.pref_showScore)
        set(b) = setBoolean(R.string.pref_showScore, b)

    var pageIndex
        get() = getInt(R.string.pref_lastindex)
        set(i) = setInt(R.string.pref_lastindex, i)

    var fabLongPressCount
        get() = getInt(R.string.pref_fabLongPressCount)
        set(i) = setInt(R.string.pref_fabLongPressCount, i)

    var launchCount
        get() = getInt(R.string.pref_launchCount)
        set(i) = setInt(R.string.pref_launchCount, i)

    var lastRatePromptTime
        get() = getLong(R.string.pref_lastRatePromptShownTime)
        set(l) = setLong(R.string.pref_lastRatePromptShownTime, l)

    var ratePromptCount
        get() = getInt(R.string.pref_ratePromptCount)
        set(i) = setInt(R.string.pref_ratePromptCount, i)

    var allMediaDownloaded
        get() = getBoolean(R.string.pref_enableOffline)
        set(b) = setBoolean(R.string.pref_enableOffline, b)

    var textScale
        get() = getFloat(R.string.pref_textScale, 1f)
        set(i) = setFloat(R.string.pref_textScale, i)

    fun getBoolean(@StringRes id: Int): Boolean {
        return sPref.getBoolean(context.getString(id), false)
    }
    fun setBoolean(@StringRes id: Int, b: Boolean) {
        sPref.edit { putBoolean(context.getString(id), b) }
    }

    fun getInt(@StringRes id: Int, default: Int = 0): Int {
        return sPref.getInt(context.getString(id), default)
    }
    fun setInt(@StringRes id: Int, i: Int) {
        sPref.edit { putInt(context.getString(id), i) }
    }

    fun getFloat(@StringRes id: Int, default: Float = 0f): Float {
        return sPref.getFloat(context.getString(id), default)
    }
    fun setFloat(@StringRes id: Int, i: Float) {
        sPref.edit { putFloat(context.getString(id), i) }
    }

    fun getLong(@StringRes id: Int): Long {
        return sPref.getLong(context.getString(id), 0)
    }
    fun setLong(@StringRes id: Int, l: Long) {
        sPref.edit { putLong(context.getString(id), l) }
    }

    fun getStringList(@StringRes id: Int) : ArrayList<String> {
        val list = sPref.getString(context.getString(id),"")?.split(',')
        return if (list == null) arrayListOf() else ArrayList(list)
    }
    fun setStringList(@StringRes id: Int, l: ArrayList<String>) {
        sPref.edit { putString(context.getString(id),l.joinToString(",")) }
    }
}
