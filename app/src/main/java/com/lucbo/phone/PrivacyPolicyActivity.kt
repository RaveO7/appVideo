package com.lucbo.phone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

class PrivacyPolicyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        val acceptButton = findViewById<Button>(R.id.button_accept)
        acceptButton.setOnClickListener {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("privacy_policy_accepted", true).apply()
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        val declineButton = findViewById<Button>(R.id.button_decline)
        declineButton.setOnClickListener {
            // Animation rouge sur le bouton
            val colorFrom = declineButton.solidColor
            val colorTo = ContextCompat.getColor(this, android.R.color.holo_red_dark)
            val animator = ObjectAnimator.ofObject(
                declineButton,
                "backgroundColor",
                ArgbEvaluator(),
                declineButton.solidColor,
                colorTo,
                declineButton.solidColor
            )
            animator.duration = 350
            animator.repeatCount = 1
            animator.repeatMode = ObjectAnimator.REVERSE
            animator.start()
            // Fermer l'application après l'animation
            Handler(Looper.getMainLooper()).postDelayed({
                finishAffinity()
            }, 400)
        }

        val scrollPolicy = findViewById<ScrollView>(R.id.scroll_policy)
        val welcomeText = findViewById<TextView>(R.id.text_welcome)
        val welcome = welcomeText.text.toString()
        val keyword = "ci-dessous"
        val start = welcome.indexOf(keyword)
        if (start >= 0) {
            val end = start + keyword.length
            val spannable = SpannableString(welcome)
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    if (scrollPolicy.visibility == View.GONE) {
                        scrollPolicy.visibility = View.VISIBLE
                    } else {
                        scrollPolicy.visibility = View.GONE
                    }
                }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    // Pas de soulignement, couleur par défaut
                    ds.isUnderlineText = false
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            welcomeText.text = spannable
            welcomeText.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    companion object {
        fun isPolicyAccepted(context: Context): Boolean {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("privacy_policy_accepted", false)
        }
    }
} 