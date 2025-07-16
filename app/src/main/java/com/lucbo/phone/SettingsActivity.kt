package com.lucbo.phone

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDelegate
import android.content.SharedPreferences

class SettingsActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 2024
    private var pendingPermission: String? = null

    // Liste complète des permissions nécessaires
    private val allPermissions = listOf(
        Triple(android.Manifest.permission.CAMERA, "Caméra", android.R.drawable.ic_menu_camera),
        Triple(android.Manifest.permission.RECORD_AUDIO, "Microphone", android.R.drawable.ic_btn_speak_now),
        Triple(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, "Écriture stockage", android.R.drawable.ic_menu_save),
        Triple(android.Manifest.permission.READ_EXTERNAL_STORAGE, "Lecture stockage", android.R.drawable.ic_menu_view),
        Triple(android.Manifest.permission.READ_MEDIA_IMAGES, "Lecture images", android.R.drawable.ic_menu_gallery),
        Triple(android.Manifest.permission.READ_MEDIA_VIDEO, "Lecture vidéos", android.R.drawable.ic_menu_slideshow),
        Triple(android.Manifest.permission.FOREGROUND_SERVICE, "Service premier plan", android.R.drawable.ic_lock_idle_alarm)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Appliquer le mode sombre si activé
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val darkMode = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        val scroll = ScrollView(this)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)
        layout.setBackgroundColor(Color.parseColor("#F5F6FA"))

        val title = TextView(this)
        title.text = "Paramètres de l'application"
        title.textSize = 26f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.parseColor("#22223B"))
        title.setPadding(0, 0, 0, 40)
        layout.addView(title)

        allPermissions.forEach { (perm, label, iconRes) ->
            // Adapter la logique selon la version Android et la permission
            val status = when (perm) {
                android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
                    else true // Pas concerné avant TIRAMISU
                android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
                    else true // Plus utilisé après TIRAMISU
                android.Manifest.permission.FOREGROUND_SERVICE ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
                    else true // Pas concerné avant P
                else -> ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
            }

            val card = LinearLayout(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.setPadding(36, 36, 36, 36)
            card.setBackgroundColor(Color.WHITE)
            card.elevation = 8f
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 36)
            card.layoutParams = params
            card.gravity = Gravity.CENTER_VERTICAL
            card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame)

            val icon = ImageView(this)
            icon.setImageResource(iconRes)
            val iconParams = LinearLayout.LayoutParams(90, 90)
            iconParams.setMargins(0, 0, 36, 0)
            icon.layoutParams = iconParams
            card.addView(icon)

            val textBlock = LinearLayout(this)
            textBlock.orientation = LinearLayout.VERTICAL
            textBlock.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val labelView = TextView(this)
            labelView.text = label
            labelView.textSize = 20f
            labelView.setTypeface(null, Typeface.BOLD)
            labelView.setTextColor(Color.parseColor("#22223B"))
            textBlock.addView(labelView)

            val statusView = TextView(this)
            statusView.text = if (status) "Autorisé" else "Refusé"
            statusView.textSize = 16f
            statusView.setTypeface(null, Typeface.BOLD)
            statusView.setPadding(0, 10, 0, 0)
            statusView.setTextColor(if (status) Color.parseColor("#388E3C") else Color.parseColor("#D32F2F"))
            textBlock.addView(statusView)

            card.addView(textBlock)

            if (!status && perm != android.Manifest.permission.FOREGROUND_SERVICE) {
                val btn = Button(this)
                btn.text = "Autoriser"
                btn.textSize = 15f
                btn.setPadding(32, 0, 32, 0)
                btn.setBackgroundColor(Color.parseColor("#4F8EF7"))
                btn.setTextColor(Color.WHITE)
                btn.setOnClickListener {
                    if (perm == android.Manifest.permission.READ_MEDIA_IMAGES || perm == android.Manifest.permission.READ_MEDIA_VIDEO) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ActivityCompat.requestPermissions(this, arrayOf(
                                android.Manifest.permission.READ_MEDIA_IMAGES,
                                android.Manifest.permission.READ_MEDIA_VIDEO
                            ), PERMISSION_REQUEST_CODE)
                        }
                    } else if (perm == android.Manifest.permission.READ_EXTERNAL_STORAGE || perm == android.Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            ActivityCompat.requestPermissions(this, arrayOf(
                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ), PERMISSION_REQUEST_CODE)
                        }
                    } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                        ActivityCompat.requestPermissions(this, arrayOf(perm), PERMISSION_REQUEST_CODE)
                    } else {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:" + packageName)
                        startActivity(intent)
                        Toast.makeText(this, "Pour accorder la permission, allez dans les paramètres de l'application.", Toast.LENGTH_LONG).show()
                    }
                }
                card.addView(btn)
            }
            layout.addView(card)
        }

        scroll.addView(layout)

        // Section "À propos et options"
        val aboutCard = LinearLayout(this)
        aboutCard.orientation = LinearLayout.VERTICAL
        aboutCard.setPadding(36, 36, 36, 36)
        aboutCard.setBackgroundColor(Color.WHITE)
        aboutCard.elevation = 8f
        val aboutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        aboutParams.setMargins(0, 0, 0, 36)
        aboutCard.layoutParams = aboutParams

        // Version
        val versionView = TextView(this)
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "1.0.0" }
        versionView.text = "Version : $versionName"
        versionView.textSize = 16f
        versionView.setTextColor(Color.parseColor("#22223B"))
        aboutCard.addView(versionView)

        // Politique de confidentialité
        val btnPrivacy = Button(this)
        btnPrivacy.text = "Politique de confidentialité"
        btnPrivacy.setBackgroundColor(Color.parseColor("#4F8EF7"))
        btnPrivacy.setTextColor(Color.WHITE)
        btnPrivacy.setOnClickListener {
            val url = "https://www.example.com/privacy" // Remplace par ton vrai lien
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
        val privacyParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        privacyParams.setMargins(0, 24, 0, 0)
        btnPrivacy.layoutParams = privacyParams
        aboutCard.addView(btnPrivacy)

        // Contact support
        val btnSupport = Button(this)
        btnSupport.text = "Contact support"
        btnSupport.setBackgroundColor(Color.parseColor("#4F8EF7"))
        btnSupport.setTextColor(Color.WHITE)
        btnSupport.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:support@example.com") // Remplace par ton email
            intent.putExtra(Intent.EXTRA_SUBJECT, "Support - Phone App")
            startActivity(intent)
        }
        val supportParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        supportParams.setMargins(0, 16, 0, 0)
        btnSupport.layoutParams = supportParams
        aboutCard.addView(btnSupport)

        // Switch thème sombre
        val themeLayout = LinearLayout(this)
        themeLayout.orientation = LinearLayout.HORIZONTAL
        themeLayout.gravity = Gravity.CENTER_VERTICAL
        themeLayout.setPadding(0, 24, 0, 0)
        val themeLabel = TextView(this)
        themeLabel.text = "Mode sombre"
        themeLabel.textSize = 16f
        themeLabel.setTextColor(Color.parseColor("#22223B"))
        val themeSwitch = android.widget.Switch(this)
        themeSwitch.isChecked = darkMode
        themeSwitch.setPadding(32, 0, 0, 0)
        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        themeLayout.addView(themeLabel)
        themeLayout.addView(themeSwitch)
        aboutCard.addView(themeLayout)

        // Place the scroll in a parent layout with the Retour button at the bottom
        val parent = LinearLayout(this)
        parent.orientation = LinearLayout.VERTICAL
        parent.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        parent.setBackgroundColor(Color.parseColor("#F5F6FA"))

        val scrollParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        scroll.layoutParams = scrollParams
        parent.addView(scroll)

        parent.addView(aboutCard, parent.childCount - 1)

        val btnRetour = Button(this)
        btnRetour.text = "Retour"
        btnRetour.textSize = 17f
        btnRetour.setBackgroundColor(Color.parseColor("#22223B"))
        btnRetour.setTextColor(Color.WHITE)
        btnRetour.setOnClickListener { finish() }
        val retourParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        retourParams.setMargins(0, 40, 0, 40)
        btnRetour.layoutParams = retourParams
        parent.addView(btnRetour)

        setContentView(parent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permission accordée", Toast.LENGTH_SHORT).show()
                recreate()
            } else {
                Toast.makeText(this, "Permission refusée", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 