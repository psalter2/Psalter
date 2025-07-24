package com.psalter2.psalter.ui.adaptors

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import com.psalter2.psalter.R
import com.psalter2.psalter.dp
import com.psalter2.psalter.helpers.DownloadHelper
import com.psalter2.psalter.helpers.StorageHelper
import com.psalter2.psalter.hide
import com.psalter2.psalter.infrastructure.Logger
import com.psalter2.psalter.infrastructure.PsalterDb
import com.psalter2.psalter.invertColors
import com.psalter2.psalter.models.Psalter
import com.psalter2.psalter.show
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Jonathan on 3/27/2017.
 */

class PsalterPagerAdapter(private val context: Context,
                          private val scope: CoroutineScope,
                          private val psalterDb: PsalterDb,
                          private val downloader: DownloadHelper,
                          private val storage: StorageHelper
) : androidx.viewpager.widget.PagerAdapter() {

    private val views = ConcurrentHashMap<Int, View>()
    private var _bottomInsets : Int? = 0
    var bottomInsets: Int?
        get() = _bottomInsets
        set(value) { _bottomInsets = value }

    fun getView(i: Int): View? {
        return views[i]
    }

    override fun instantiateItem(collection: ViewGroup, position: Int): Any {
        try {
            val psalter = psalterDb.getIndex(position)!!
            Logger.d("Viewpager building page for psalter ${psalter.title}")
            val inflater = LayoutInflater.from(context)
            val layout = inflater.inflate(R.layout.psalter_layout, collection, false) as ViewGroup

            scope.launch { setLayoutData(psalter, layout) }

            views[position] = layout
            collection.addView(layout)

            return layout
        }
        catch (ex: Exception) {
            Logger.e("Error in PsalterPagerAdapter.instantiateItem", ex)
            return collection
        }
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        if (item is View) {
            container.removeView(item)
            views.remove(position)
        }
    }

    override fun getCount(): Int {
        return psalterDb.getCount()
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return psalterDb.getIndex(position)?.title ?: "?"
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    private suspend fun setLayoutData(psalter: Psalter, layout: View) {
        layout.findViewById<View>(R.id.linearLayoutPsalter).setPadding(10.dp, 0, 10.dp, (bottomInsets ?: 0) + 140.dp)
        val tvPagerHeading : TextView = layout.findViewById(R.id.tvPagerHeading)
        tvPagerHeading.textSize = 18 * storage.textScale
        tvPagerHeading.text = psalter.heading
        val tvPagerLyrics : TextView = layout.findViewById(R.id.tvPagerLyrics)
        tvPagerLyrics.textSize = 16 * storage.textScale
        val tvPagerPsalm : TextView = layout.findViewById(R.id.tvPagerPsalm)
        tvPagerPsalm.textSize = 18 * storage.textScale
        tvPagerPsalm.text = HtmlCompat.fromHtml(psalter.bibleLink, HtmlCompat.FROM_HTML_MODE_LEGACY)
        tvPagerPsalm.movementMethod = LinkMovementMethod.getInstance()
        // load audio in bg. viewpager never needs audio, but user could request it at tap of a button
        scope.launch { psalter.loadAudio(downloader) }
        var text = psalter.lyrics
        if (storage.scoreShown) {
            val scoreProgress : View = layout.findViewById(R.id.scoreProgress)
            scoreProgress.show()
            val score = psalter.loadScore(downloader)
            scoreProgress.hide()
            if (score != null) {
                if (storage.nightMode) score.invertColors()
                layout.findViewById<ImageView>(R.id.imgScore).setImageDrawable(score)

                val lyricStartIndex = psalter.lyrics.indexOf((psalter.numVersesInsideStaff + 1).toString() + ". ")
                text = if (lyricStartIndex < 0) "" else psalter.lyrics.substring(lyricStartIndex)
            }
        } else {
            layout.findViewById<ImageView>(R.id.imgScore).setImageDrawable(null)
            // load score in bg. viewpager doesn't need it *now*, but could at the tap of a button
            scope.launch { psalter.loadScore(downloader) }
        }
        tvPagerLyrics.text = text
    }

    fun updateViews() {
        for((i, view) in views) {
            scope.launch { setLayoutData(psalterDb.getIndex(i)!!, view) }
        }
    }

}
