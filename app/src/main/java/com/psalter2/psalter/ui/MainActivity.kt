package com.psalter2.psalter.ui

import android.Manifest
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.InputType
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import com.psalter2.psalter.R
import com.psalter2.psalter.databinding.ActivityMainBinding
import com.psalter2.psalter.dp
import com.psalter2.psalter.helpers.DownloadHelper
import com.psalter2.psalter.helpers.InstantHelper
import com.psalter2.psalter.helpers.IntentHelper
import com.psalter2.psalter.helpers.RateHelper
import com.psalter2.psalter.helpers.StorageHelper
import com.psalter2.psalter.helpers.TutorialHelper
import com.psalter2.psalter.hide
import com.psalter2.psalter.infrastructure.Logger
import com.psalter2.psalter.infrastructure.MediaService
import com.psalter2.psalter.infrastructure.MediaServiceBinder
import com.psalter2.psalter.infrastructure.PsalterDb
import com.psalter2.psalter.models.LogEvent
import com.psalter2.psalter.models.MediaServiceCallbacks
import com.psalter2.psalter.models.Psalter
import com.psalter2.psalter.models.SearchMode
import com.psalter2.psalter.recreateSafe
import com.psalter2.psalter.show
import com.psalter2.psalter.ui.adaptors.PsalterPagerAdapter
import com.psalter2.psalter.ui.adaptors.PsalterSearchAdapter
import com.psalter2.psalter.updateMargin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope(), LifecycleOwner {
    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: StorageHelper
    private lateinit var rateHelper: RateHelper
    private lateinit var instant: InstantHelper
    private lateinit var psalterDb: PsalterDb
    private lateinit var tutorials: TutorialHelper
    private lateinit var searchMenuItem: MenuItem
    private lateinit var searchView: SearchView
    private lateinit var downloader: DownloadHelper
    private var mediaService: MediaServiceBinder? = null
    private var menu: Menu? = null
    private val selectedPsalter get() = psalterDb.getIndex(binding.viewPager.currentItem)

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.init(this)
        storage = StorageHelper(this)
        downloader = DownloadHelper(this, storage)
        rateHelper = RateHelper(this, storage)
        tutorials = TutorialHelper(this)
        instant = InstantHelper(this)
        psalterDb = PsalterDb(this, this, downloader)

        lifecycle.addObserver(psalterDb)

        // must be done before super(), or onCreate() will be called twice and tutorials won't work
        if (Build.VERSION.SDK_INT < 31) {
            AppCompatDelegate.setDefaultNightMode(if(storage.nightMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            var shouldExit = false
            override fun handleOnBackPressed() {
                if (binding.lvSearchResults.isShown) {
                    collapseSearchView()
                } else {
                    if (shouldExit) finish()
                    else {
                        shouldExit = true
                        snack("Repeat action to leave app", 1200)
                        Handler(Looper.getMainLooper()).postDelayed({ shouldExit = false }, 1200)
                    }
                }
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.statusBarScrim.updateLayoutParams { height = systemBars.top }
            binding.toolbar.updateMargin(top = systemBars.top)

            binding.lvSearchResults.setPadding(0, 0, 0, systemBars.bottom)

            (binding.viewPager.adapter as PsalterPagerAdapter).bottomInsets = systemBars.bottom

            binding.fab.updateMargin(bottom = systemBars.bottom + 16.dp)
            binding.fabToggleScore.updateMargin(bottom = systemBars.bottom + 88.dp)
            binding.fabToggleFavorite.updateMargin(bottom = systemBars.bottom + 69.2548339959.dp)

            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            binding.tableButtons.updateMargin(bottom = if (ime.bottom > 0) ime.bottom else systemBars.bottom)

            insets
        }

        storage.launchCount++
        // instant.transferInstantAppData() doesn't work anyways

        tutorials.showScoreTutorial(binding.fabToggleScore)
        tutorials.showShuffleTutorial(binding.fab)
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this@MainActivity, MediaService::class.java), mConnection, BIND_AUTO_CREATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                snack("Need permission for showing media in notification.", Snackbar.LENGTH_INDEFINITE, "Allow", onClick =
                    { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101) })
    }

    override fun onStop() {
        mediaService?.unregisterCallbacks()
        unbindService(mConnection)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        menuInflater.inflate(R.menu.menu_options, menu)
        menu.findItem(R.id.action_nightMode).isChecked = storage.nightMode
        initSearchView(menu)
        searchMode = SearchMode.Psalter
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_downloadAll)?.isVisible = !storage.allMediaDownloaded && !instant.isInstantApp
        if (storage.fabLongPressCount > 7) menu?.findItem(R.id.action_shuffle)?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressedDispatcher.onBackPressed()
            R.id.action_random -> goToRandom()
            R.id.action_history -> showRecents()
            R.id.action_favorites -> showFavorites()
            R.id.action_shuffle -> shuffle(true)
            R.id.action_downloadAll -> queueDownloads()
            R.id.action_fontSize_small -> setFontScale(.875f)
            R.id.action_fontSize_normal -> setFontScale(1f)
            R.id.action_fontSize_big -> setFontScale(1.125f)
            R.id.action_fontSize_huge -> setFontScale(1.25f)
            R.id.action_nightMode -> toggleNightMode()
            R.id.action_sendFeedback -> startActivity(IntentHelper.FeedbackIntent)
            else -> return false
        }
        return true
    }

    private fun setFontScale(scale: Float) {
        val adapter = binding.viewPager.adapter as PsalterPagerAdapter
        storage.textScale = scale
        adapter.updateViews()
        updateViewPagerTitleTextSize()
    }

    private fun queueDownloads(){
        downloader.offlinePrompt(psalterDb, this) { msg -> snack(msg) }
    }

    private fun goToRandom() {
        if (mediaService?.isShuffling == true && mediaService?.isPlaying == true) {
            Logger.skipToNext(selectedPsalter)
            mediaService?.skipToNext()
        } else {
            Logger.event(LogEvent.GoToRandom)
            val next = psalterDb.getRandom()
            binding.viewPager.setCurrentItem(next.id, true)
            storage.addRecentNumber(next.number)
        }
        rateHelper.showRateDialogIfAppropriate()
    }

    private fun toggleNightMode() {
        storage.nightMode = !storage.nightMode
        Logger.changeTheme(storage.nightMode)
        if (Build.VERSION.SDK_INT >= 31) {
            val uiModeManager = getSystemService(UiModeManager::class.java)
            uiModeManager.setApplicationNightMode(if(storage.nightMode) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)
        } else {
            recreateSafe()
        }
    }

    private fun performPsalterSearch(psalterNumber: Int){
        if (psalterNumber in 1..434) {
            collapseSearchView()
            goToPsalter(psalterNumber)
            Logger.searchPsalter(psalterNumber)
        }
        else snack("Pick a number between 1 and 434")
    }
    private fun performLyricSearch(query: String, logEvent: Boolean) {
        showSearchResultsScreen()
        (binding.lvSearchResults.adapter as PsalterSearchAdapter).queryPsalter(query)
        binding.lvSearchResults.setSelectionAfterHeaderView()
        if(logEvent) Logger.searchLyrics(query)
    }
    private fun performPsalmSearch(psalm: Int) {
        if (psalm in 1..150) {
            showSearchResultsScreen()
            (binding.lvSearchResults.adapter as PsalterSearchAdapter).getAllFromPsalm(psalm)
            binding.lvSearchResults.setSelectionAfterHeaderView()
            Logger.searchPsalm(psalm)
        }
        else snack("Pick a number between 1 and 150")
    }

    private fun showRecents(): Boolean {
        searchMode = SearchMode.Recents
        menu?.hideAll()
        showSearchResultsScreen("Recents")

        (binding.lvSearchResults.adapter as PsalterSearchAdapter).showRecents()
        binding.lvSearchResults.setSelectionAfterHeaderView()
        return true
    }

    private fun showFavorites(): Boolean {
        if(!psalterDb.getFavorites().any()) {
            tutorials.showAddToFavoritesTutorial(binding.fabToggleFavorite)
            return false
        }

        searchMode = SearchMode.Favorites
        menu?.hideAll()
        showSearchResultsScreen("Favorites")

        (binding.lvSearchResults.adapter as PsalterSearchAdapter).showFavorites()
        binding.lvSearchResults.setSelectionAfterHeaderView()
        return true
    }

    private fun showSearchButtons() {
        binding.tableButtons.show()
        hideFabs()
    }
    private fun hideSearchButtons() {
        binding.tableButtons.hide()
        showFabs()
    }

    private fun showFabs(){
        binding.fabToggleScore.show()
        binding.fabToggleFavorite.show()
        binding.fab.show()
    }
    private fun  hideFabs(){
        binding.fabToggleScore.hide()
        binding.fabToggleFavorite.hide()
        binding.fab.hide()
    }

    private fun showSearchResultsScreen(title: CharSequence? = "Psalter") {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.title = title
        binding.lvSearchResults.show()
        binding.viewPager.hide()
        hideFabs()
    }
    private fun hideSearchResultsScreen() {
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setHomeButtonEnabled(false)
        supportActionBar?.title = "Psalter"
        binding.lvSearchResults.hide()
        binding.viewPager.show()
        showFabs()
    }

    private fun collapseSearchView() {
        if (searchMenuItem.isActionViewExpanded) searchMenuItem.collapseActionView()
        hideSearchResultsScreen()
        invalidateOptionsMenu() // rebuild menu
    }

    private fun goToId(id: Int){
        binding.viewPager.setCurrentItem(id, true) //viewpager goes by index
    }
    private fun goToPsalter(psalterNumber: Int) {
        val psalter = psalterDb.getPsalter(psalterNumber)!!
        goToId(psalter.id)
        storage.addRecentNumber(psalterNumber)
    }

    private fun toggleScore() {
        val adapter = binding.viewPager.adapter as PsalterPagerAdapter
        storage.scoreShown = !storage.scoreShown
        storage.addRecentNumber(selectedPsalter!!.number)
        adapter.updateViews()
        binding.fabToggleScore.isSelected = storage.scoreShown
        Logger.changeScore(storage.scoreShown)
    }

    private fun toggleFavorite() {
        psalterDb.toggleFavorite(selectedPsalter!!)
        storage.addRecentNumber(selectedPsalter!!.number)
        binding.fabToggleFavorite.isSelected = !binding.fabToggleFavorite.isSelected
        tutorials.showViewFavoritesTutorial(binding.toolbar)
    }

    private fun togglePlay() {
        val psalter = selectedPsalter!!
        storage.addRecentNumber(psalter.number)
        if (mediaService?.isPlaying == true) {
            mediaService?.stop()
            rateHelper.showRateDialogIfAppropriate()
        } else {
            mediaService?.play(psalter, false)
            mediaService?.startService(this)
        }
    }

    private fun shuffle(showLongPressTutorial: Boolean = false): Boolean {
        if(showLongPressTutorial) tutorials.showShuffleReminderTutorial(binding.fab)
        else storage.fabLongPressCount++

        mediaService?.play(selectedPsalter!!, true)
        mediaService?.startService(this)
        rateHelper.showRateDialogIfAppropriate()
        return true
    }

    private fun onItemClick(view: View) {
        try {
            val tvNumber = (view.tag as PsalterSearchAdapter.ViewHolder).tvNumber
            val tvId = (view.tag as PsalterSearchAdapter.ViewHolder).tvId
            val num = Integer.parseInt(tvNumber.text.toString())
            val id = Integer.parseInt(tvId.text.toString())

            //log event before collapsing searchview, so we can log the query text
            Logger.searchEvent(searchMode, searchView.query.toString(), num)

            collapseSearchView()
            goToId(id)
            if (searchMode != SearchMode.Recents) storage.addRecentNumber(num)
            rateHelper.showRateDialogIfAppropriate()
        }
        catch (ex: Exception) {
            Logger.e("Error in MainActivity.onItemClick", ex)
        }
    }

    private fun onPageSelected(index: Int, view: View?) {
        Logger.d("Page selected: $index")
        storage.pageIndex = index
        binding.fabToggleFavorite.isSelected = selectedPsalter?.isFavorite ?: false
        if (mediaService?.isPlaying == true && mediaService?.currentMediaId != index) { // if we're playing audio of a different #, stop it
            mediaService?.stop()
        }
    }

    private var mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            Logger.d("MediaService bound")
            mediaService = iBinder as MediaServiceBinder
            mediaService?.registerCallbacks(callback)
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            // this method IS NOT CALLED on unbindService(), only when service crashes or something. So it's pretty useless and misleading
        }
    }

    private var callback = object : MediaServiceCallbacks() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            binding.fab.isSelected = mediaService?.isPlaying ?: false
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if (mediaService?.isPlaying == true) binding.viewPager.currentItem = metadata!!.description.mediaId!!.toInt()
        }

        override fun onAudioUnavailable(psalter: Psalter) {
            snack("Audio unavailable for ${psalter.title}")
        }

        override fun onBeginShuffling() {
            snack("Shuffling", Snackbar.LENGTH_SHORT, "Skip") {
                mediaService?.skipToNext()
            }
        }
    }

    private lateinit var _searchMode: SearchMode
    private var searchMode
        get() = _searchMode
        set(mode) {
            _searchMode = mode
            searchView.setQuery("", false)
            when(mode) {
                SearchMode.Psalter -> {
                    searchView.inputType = InputType.TYPE_CLASS_NUMBER
                    searchView.queryHint = "Enter Psalter number (1 - 434)"
                    binding.searchBtnPsalter.deselect(binding.searchBtnPsalm, binding.searchBtnLyrics)
                }
                SearchMode.Lyrics, SearchMode.Favorites, SearchMode.Recents -> {
                    searchView.inputType = InputType.TYPE_CLASS_TEXT
                    searchView.queryHint = "Enter search query"
                    binding.searchBtnLyrics.deselect(binding.searchBtnPsalter, binding.searchBtnPsalm)
                }
                SearchMode.Psalm -> {
                    searchView.inputType = InputType.TYPE_CLASS_NUMBER
                    searchView.queryHint = "Enter Psalm (1 - 150)"
                    binding.searchBtnPsalm.deselect(binding.searchBtnPsalter, binding.searchBtnLyrics)
                }
            }
        }

    fun updateSearchMode(mode : SearchMode) {
        searchMode = mode
        searchView.post {
            searchView.requestFocus()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                searchView.windowInsetsController?.show(WindowInsets.Type.ime())
            } else {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchView, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun initViews() {
        setSupportActionBar(binding.toolbar)

        binding.fab.setOnClickListener { togglePlay() }
        binding.fab.setOnLongClickListener { shuffle() }

        binding.fabToggleScore.setOnClickListener { toggleScore() }
        binding.fabToggleScore.isSelected = storage.scoreShown

        binding.fabToggleFavorite.setOnClickListener { toggleFavorite() }
        binding.fabToggleFavorite.isSelected = psalterDb.getIndex(storage.pageIndex)?.isFavorite ?: false

        binding.searchBtnPsalter.setOnClickListener { updateSearchMode(SearchMode.Psalter) }
        binding.searchBtnLyrics.setOnClickListener { updateSearchMode(SearchMode.Lyrics) }
        binding.searchBtnPsalm.setOnClickListener { updateSearchMode(SearchMode.Psalm) }

        val viewPagerAdapter = PsalterPagerAdapter(this, this, psalterDb, downloader, storage)
        binding.viewPager.adapter = viewPagerAdapter
        binding.viewPager.currentItem = storage.pageIndex
        updateViewPagerTitleTextSize()
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                this@MainActivity.onPageSelected(position, viewPagerAdapter.getView(position))
            }
        })

        binding.lvSearchResults.adapter = PsalterSearchAdapter(this, psalterDb, storage)
        binding.lvSearchResults.setOnItemClickListener { _, view, _, _ -> onItemClick(view) }
    }
    private fun updateViewPagerTitleTextSize(){
        binding.viewPagerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18 * storage.textScale)
    }
    private fun initSearchView(menu: Menu){
        searchMenuItem = menu.findItem(R.id.action_search)
        searchView = searchMenuItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                when (searchMode) {
                    SearchMode.Psalter -> performPsalterSearch(query?.toIntOrNull() ?: 0)
                    SearchMode.Lyrics, SearchMode.Favorites, SearchMode.Recents -> performLyricSearch(query ?: "", true)
                    SearchMode.Psalm -> performPsalmSearch(query?.toIntOrNull() ?: 0)
                }
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(query: String?): Boolean {
                if (searchMode == SearchMode.Lyrics && query != null && query.length > 1) {
                    performLyricSearch(query, false)
                    return true
                }
                return false
            }
        })

        searchMenuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                showSearchButtons()
                menu.hideAll(searchMenuItem)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                hideSearchButtons()
                invalidateOptionsMenu()
                return true
            }
        })
    }

    /** [Snackbar.make] */
    private fun snack(msg: String, len: Int = Snackbar.LENGTH_LONG){
        Snackbar.make(binding.mainCoordinatorLayout, msg, len).show()
    }
    /** [Snackbar.make] */
    private fun snack(msg: String, len: Int, action: String, onClick: () -> Unit){
        Snackbar.make(binding.mainCoordinatorLayout, msg, len)
                .setAction(action) { onClick() }.show()
    }

    private fun AppCompatButton.deselect(vararg deselect: AppCompatButton){
        this.isSelected = true
        deselect.forEach { btn -> btn.isSelected = false }
    }

    private fun Menu.hideAll(except: MenuItem? = null) {
        for (i in 0 until this.size) {
            val item = this[i]
            if (item != except) item.isVisible = false
        }
    }
}
