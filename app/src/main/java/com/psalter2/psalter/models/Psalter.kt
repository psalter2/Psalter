package com.psalter2.psalter.models

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.net.toUri
import com.psalter2.psalter.helpers.DownloadHelper
import java.io.File

/**
 * Created by Jonathan on 3/27/2017.
 */

data class Psalter (
        var id: Int = 0,
        var number: Int = 0,
        var psalm: Int = 0,
        var title: String = "",
        var lyrics: String = "",
        var numverses: Int = 0,
        var numVersesInsideStaff: Int = 0,
        var heading: String = "",
        var audioPath: String = "",
        var scorePath: String = "",
        var isFavorite: Boolean = false
) {
    private val passage get() = if (psalm == 0) "Matthew+6:9-13" else "Psalm+$psalm"
    val biblePassage get() = if (psalm == 0) "Lords Prayer" else "Psalm $psalm"
    val bibleLink get() = "<a href=https://www.biblegateway.com/passage?search=$passage&version=KJV>$biblePassage</a>"

    private var _audio: Uri? = null
    val audio get() = _audio
    suspend fun loadAudio(downloader: DownloadHelper): Uri? {
        if(_audio != null) return _audio
        val file = getFile(downloader, audioPath) ?: return null
        _audio = file.path.toUri()
        return _audio
    }

    private var _score: BitmapDrawable? = null
    val score get() = _score
    suspend fun loadScore(downloader: DownloadHelper): BitmapDrawable? {
        if(_score != null) return _score
        val file = getFile(downloader, scorePath) ?: return null
        _score = Drawable.createFromPath(file.path) as BitmapDrawable?
        return _score
    }

    private suspend fun getFile(downloader: DownloadHelper, path: String): File? {
        return try{
            // try getting from internal storage
            var file: File? = File(downloader.saveDir, path)
            // try downloading
            if(file?.exists() == false) file = downloader.downloadFile(path)
            if(file?.exists() == true) file else null
        } catch (ex: Exception) { null }
    }
}
