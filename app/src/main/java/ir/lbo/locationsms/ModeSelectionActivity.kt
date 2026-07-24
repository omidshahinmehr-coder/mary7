package ir.lbo.locationsms

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import android.widget.Button

class ModeSelectionActivity : LockProtectedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_selection)

        findViewById<Button>(R.id.trackerModeButton).setOnClickListener {
            startActivity(Intent(this, TrackerActivity::class.java))
        }

        findViewById<Button>(R.id.viewerModeButton).setOnClickListener {
            startActivity(Intent(this, ViewerActivity::class.java))
        }

        findViewById<Button>(R.id.securitySettingsButton).setOnClickListener {
            startActivity(Intent(this, SecuritySettingsActivity::class.java))
        }

        findViewById<ImageButton>(R.id.languageButton).setOnClickListener {
            showLanguagePicker()
        }
    }

    /**
     * A simple 3-way choice: follow the system language, or pin the app to
     * Persian/English regardless of the device's own language. Selecting
     * an option calls AppCompatDelegate.setApplicationLocales(), which
     * AppCompat persists automatically and re-applies on every future
     * launch — no manual storage or per-Activity locale code needed.
     */
    private fun showLanguagePicker() {
        val options = arrayOf(
            getString(R.string.language_option_system),
            getString(R.string.language_option_persian),
            getString(R.string.language_option_english)
        )

        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val currentIndex = when {
            currentTags.startsWith("fa") -> 1
            currentTags.startsWith("en") -> 2
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.language_dialog_title)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val localeList = when (which) {
                    1 -> LocaleListCompat.forLanguageTags("fa")
                    2 -> LocaleListCompat.forLanguageTags("en")
                    else -> LocaleListCompat.getEmptyLocaleList()
                }
                AppCompatDelegate.setApplicationLocales(localeList)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
