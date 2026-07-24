package ir.lbo.locationsms

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * A short, animated "Bloodline" title screen shown before the login screen.
 * Purely presentational — it always hands off to LoginActivity and never
 * becomes part of the back stack the user can return to.
 */
class SplashActivity : AppCompatActivity() {

    private var pulseAnimator: ObjectAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val rootContainer = findViewById<View>(R.id.splashContainer)
        val titleText = findViewById<TextView>(R.id.splashTitleText)
        val dividerView = findViewById<View>(R.id.splashDivider)
        val subtitleText = findViewById<TextView>(R.id.splashSubtitleText)

        // Soft neon-blue glow behind the letters.
        titleText.setShadowLayer(28f, 0f, 0f, Color.parseColor("#B300B3FF"))

        titleText.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(700)
            .setStartDelay(150)
            .setInterpolator(OvershootInterpolator(1.4f))
            .withEndAction { startPulse(titleText) }
            .start()

        dividerView.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(650)
            .start()
        ObjectAnimator.ofFloat(dividerView, "scaleX", 0f, 1f).apply {
            duration = 500
            startDelay = 650
            interpolator = OvershootInterpolator(2f)
            start()
        }

        subtitleText.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(450)
            .setStartDelay(950)
            .start()

        handler.postDelayed({
            pulseAnimator?.cancel()
            rootContainer.animate()
                .alpha(0f)
                .setDuration(320)
                .withEndAction { goToLogin() }
                .start()
        }, 2400)
    }

    private fun startPulse(view: TextView) {
        pulseAnimator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.82f).apply {
            duration = 900
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun goToLogin() {
        if (navigated) return
        navigated = true
        startActivity(Intent(this, LoginActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        pulseAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
