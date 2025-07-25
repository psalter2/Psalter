package com.psalter2.psalter.helpers

import android.app.Activity
import androidx.annotation.StringRes
import android.view.View
import androidx.appcompat.widget.Toolbar

import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.psalter2.psalter.R

/**
 * Created by Jonathan on 6/22/2018.
 */

class TutorialHelper(private val context: Activity) : TapTargetSequence.Listener {
    private var targetSequence: TapTargetSequence? = null
    private var storage = StorageHelper(context)

    private fun getTargetSequence(): TapTargetSequence {
        return TapTargetSequence(context)
                .listener(this)
                .continueOnCancel(true)
                .considerOuterCircleCanceled(true)
    }

    private fun showTutorial(view: View?, @StringRes prefTutorialShown: Int, @StringRes title: Int, @StringRes description: Int?, tint: Boolean = false) {
        val shown = storage.getBoolean(prefTutorialShown)
        if (!shown && view != null) {
            if (targetSequence == null) targetSequence = getTargetSequence()
            targetSequence!!.target(getTapTarget(view, title, description)
                    .tintTarget(tint)
                    .outerCircleColor(R.color.colorAccent)
                    .textColor(android.R.color.white))

            targetSequence!!.start()
            storage.setBoolean(prefTutorialShown, true)
        }
    }

    private fun getTapTarget(view: View?, @StringRes rTitle: Int, @StringRes rDescription: Int?): TapTarget {
        val title = context.getString(rTitle)
        val description = if (rDescription == null) "" else context.getString(rDescription)
        return when(view){
            is Toolbar -> TapTarget.forToolbarOverflow(view, title, description)
            else -> TapTarget.forView(view, title, description)
        }
    }

    fun showShuffleTutorial(fab: View) {
        showTutorial(fab,
                R.string.pref_tutorialshown_fablongpress,
                R.string.tutorial_fab_title,
                R.string.tutorial_fab_description)
    }

    fun showShuffleReminderTutorial(fab: View) {
        showTutorial(fab, R.string.pref_tutorialshown_fabreminder,
                R.string.tutorial_fabreminder_title,
                R.string.tutorial_fabreminder_description)
    }

    fun showScoreTutorial(view: View) {
        showTutorial(view,
                R.string.pref_tutorialshown_showscore,
                R.string.tutorial_showscore_title,
                null)
    }

    fun showAddToFavoritesTutorial(view: View){
        // should show regardless of whether or not it was shown before
        storage.setBoolean(R.string.pref_tutorialShown_favorites, false)
        showTutorial(view,
            R.string.pref_tutorialShown_favorites,
            R.string.tutorial_addToFavorites_title,
            R.string.tutorial_addToFavorites_description)
    }

    fun showViewFavoritesTutorial(toolbar: Toolbar){
        showTutorial(toolbar,
            R.string.pref_tutorialShown_favorites,
            R.string.tutorial_viewFavorites_title,
            R.string.tutorial_viewFavorites_description,
            true)
    }

    override fun onSequenceFinish() {
        targetSequence = null
    }

    override fun onSequenceStep(lastTarget: TapTarget, targetClicked: Boolean) {}

    override fun onSequenceCanceled(lastTarget: TapTarget) {}
}
