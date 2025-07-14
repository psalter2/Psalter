package com.psalter2.psalter.infrastructure

import android.content.Context
import android.net.Uri

interface IPlayer {
    fun start()
    fun stop()
    fun pause()
    fun isPlaying(): Boolean

    fun duck()
    fun unduck()

    fun setDataSource(context: Context, uri: Uri)
    fun prepare()

    fun onComplete(listener: () -> Unit)
    fun onError(listener: (what: Int, extra: Int) -> Unit)

    fun release()
    fun reset()
}