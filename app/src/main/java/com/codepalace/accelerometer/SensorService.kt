package com.codepalace.accelerometer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var linearAcceleration: Sensor? = null
    private var gyroscope: Sensor? = null

    private val handler = Handler(Looper.getMainLooper())
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private var currentLatitude = 0.0
    private var currentLongitude = 0.0

    private var lastAlertTime = 0L
    private val alertCooldownMs = 5 * 60 * 1000L // 5 minutos

    private val buffer = ArrayList<Map<String, Any>>()

    private val pontosFixos = listOf(
        LatLng(-23.638725296201766, -46.546012666577845),
        LatLng(-23.618493786022565, -46.57880951067621),
        LatLng(-23.66420322, -46.50678892)
    )

    private var lastLinear = FloatArray(3)
    private var lastGyro = FloatArray(3)

    private val uploadRunnable = object : Runnable {
        override fun run() {
            uploadBufferToFirebase()
            handler.postDelayed(this, BLOCK_DURATION_MS)
        }
    }

    companion object {
        const val CHANNEL_ID = "sensor_service_channel"
        const val BLOCK_DURATION_MS = 2 * 60 * 1000L // 2 minutos
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildForegroundNotification())

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        salvarPontosFixos()
        startLocationUpdates()

        linearAcceleration?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        handler.postDelayed(uploadRunnable, BLOCK_DURATION_MS)

        Toast.makeText(
            this,
            "📡 Coleta contínua iniciada (Acelerômetro + Giroscópio)",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> lastLinear = event.values.clone()
            Sensor.TYPE_GYROSCOPE -> lastGyro = event.values.clone()
        }

        val timestamp = sdf.format(Date())

        val reading = mapOf(
            "timestamp" to timestamp,
            "latitude" to currentLatitude,
            "longitude" to currentLongitude,
            "x" to lastLinear[0],
            "y" to lastLinear[1],
            "z" to lastLinear[2],
            "gyroX" to lastGyro[0],
            "gyroY" to lastGyro[1],
            "gyroZ" to lastGyro[2]
        )

        buffer.add(reading)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("SensorService", "Permissão de localização não concedida")
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        ).build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                currentLatitude = location.latitude
                currentLongitude = location.longitude
                checkProximityAlert()
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun checkProximityAlert() {
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < alertCooldownMs) return
        if (currentLatitude == 0.0 && currentLongitude == 0.0) return

        pontosFixos.forEach { ponto ->
            val dist = calculateDistance(
                currentLatitude,
                currentLongitude,
                ponto.latitude,
                ponto.longitude
            )
            if (dist < 500) {
                lastAlertTime = now
                sendProximityNotification()
                return
            }
        }
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double, lat2: Double, lon2: Double
    ): Float {
        val a = android.location.Location("").apply { latitude = lat1; longitude = lon1 }
        val b = android.location.Location("").apply { latitude = lat2; longitude = lon2 }
        return a.distanceTo(b)
    }

    private fun sendProximityNotification() {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠ Atenção!")
            .setContentText("Buraco detectado dentro de um raio de 500 metros!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(2222, notif)
    }

    private fun uploadBufferToFirebase() {
        if (buffer.isEmpty()) return

        val mainDb = FirebaseDatabase.getInstance().getReference("sensor_data_blocks")
        val blockId = mainDb.push().key ?: UUID.randomUUID().toString()

        val dataBlock = mapOf(
            "block_id" to blockId,
            "samples" to buffer.toList()
        )

        mainDb.child(blockId).setValue(dataBlock)
            .addOnSuccessListener { Log.d("SensorService", "Block salvo: $blockId") }
            .addOnFailureListener { e -> Log.e("SensorService", "Erro ao salvar block", e) }

        buffer.clear()
    }


    private fun salvarPontosFixos() {
        val db = FirebaseDatabase.getInstance().getReference("pontos_fixos")
        db.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val pontos = listOf(
                    mapOf("latitude" to -23.638725296201766, "longitude" to -46.546012666577845, "nome" to "Buraco 1"),
                    mapOf("latitude" to -23.618493786022565, "longitude" to -46.57880951067621, "nome" to "Buraco 2"),
                    mapOf("latitude" to -23.66420322, "longitude" to -46.50678892, "nome" to "Buraco 3")
                )
                pontos.forEachIndexed { index, ponto ->
                    db.child((index + 1).toString()).setValue(ponto)
                        .addOnSuccessListener { Log.d("SensorService", "Ponto fixo salvo: ${index+1}") }
                        .addOnFailureListener { e -> Log.e("SensorService", "Erro ao salvar ponto fixo", e) }
                }
            }
        }.addOnFailureListener { e ->
            Log.e("SensorService", "Erro ao verificar pontos fixos", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Coleta de Dados Contínua",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Serviço ativo coletando sensores + GPS" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📊 Coleta de Dados Ativa")
            .setContentText("Monitorando sensores e localização…")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(uploadRunnable)
        uploadBufferToFirebase()
        Toast.makeText(this, "🛑 Coleta encerrada", Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?) = null
}
