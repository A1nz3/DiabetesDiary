package com.example.diabetesdiary

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnRequestPermission: Button
    private lateinit var backButton: ImageButton
    private lateinit var btnOpenSettings: Button

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            tvStatus.text = "Pozwolenie na powiadomienia zostało udzielone."
            btnRequestPermission.isEnabled = false
        } else {
            tvStatus.text = "Pozwolenie na powiadomienia nie zostało udzielone."
            btnRequestPermission.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        tvStatus = findViewById(R.id.tvPermissionStatus)
        btnRequestPermission = findViewById(R.id.btnRequestPermission)
        backButton = findViewById(R.id.backButton)

        backButton.setOnClickListener {
            finish()
        }

        updateUI()

        btnRequestPermission.setOnClickListener {
            requestNotificationPermission()
        }

        btnOpenSettings = findViewById(R.id.btnOpenSettings)

        btnOpenSettings.setOnClickListener {
            openAppSettings()
        }

    }

    private fun openAppSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }


    private fun updateUI() {
        if (isNotificationPermissionGranted()) {
            tvStatus.text = "Pozwolenie na powiadomienia jest już udzielone."
            btnRequestPermission.isEnabled = false
        } else {
            tvStatus.text = "Pozwolenie na powiadomienia nie zostało udzielone."
            btnRequestPermission.isEnabled = true
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isNotificationPermissionGranted()) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
