package com.codepalace.accelerometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton

class WelcomeActivity : AppCompatActivity() {

    private var isServiceRunning = false
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val btnAcelerometro = findViewById<Button>(R.id.btnAcelerometro)
        val btnMapa = findViewById<Button>(R.id.btnMapa)
        val btnIniciar = findViewById<Button>(R.id.btnIniciar)
        val btnParar = findViewById<Button>(R.id.btnParar)
        val fabSite = findViewById<FloatingActionButton>(R.id.fabSite)
        val fabSuporte = findViewById<FloatingActionButton>(R.id.fabSuporte)

        btnAcelerometro.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        btnMapa.setOnClickListener {
            startActivity(Intent(this, MapsActivity::class.java))
        }

        btnIniciar.setOnClickListener {
            if (!isServiceRunning) {
                checkLocationPermission()
            } else {
                Toast.makeText(this, "O sistema já está em execução!", Toast.LENGTH_SHORT).show()
            }
        }

        btnParar.setOnClickListener {
            if (isServiceRunning) {
                stopSensorService()
            } else {
                Toast.makeText(this, "O sistema já está parado!", Toast.LENGTH_SHORT).show()
            }
        }

        fabSite.setOnClickListener {
            val siteUrl = "https://html-alura-five.vercel.app/"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(siteUrl))
            startActivity(intent)
        }

        fabSuporte.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:suporte@seusite.com")
                putExtra(Intent.EXTRA_SUBJECT, "Suporte - App Detector de Buracos")
            }

            try {
                startActivity(emailIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Nenhum app de e-mail encontrado!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startSensorService()
        }
    }

    private fun startSensorService() {
        val serviceIntent = Intent(this, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        isServiceRunning = true
        Toast.makeText(this, "🚀 Sistema de detecção iniciado!", Toast.LENGTH_SHORT).show()
    }

    private fun stopSensorService() {
        val serviceIntent = Intent(this, SensorService::class.java)
        stopService(serviceIntent)
        isServiceRunning = false
        Toast.makeText(this, "🛑 Sistema de detecção parado!", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSensorService()
            } else {
                Toast.makeText(this, "Permissão de localização é necessária", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
