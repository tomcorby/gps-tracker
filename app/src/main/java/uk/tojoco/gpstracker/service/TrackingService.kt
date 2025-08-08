package uk.tojoco.gpstracker.service

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import androidx.core.app.NotificationCompat
import uk.tojoco.gpstracker.MainActivity
import uk.tojoco.gpstracker.R
import uk.tojoco.gpstracker.data.AppDatabase
import uk.tojoco.gpstracker.data.LocationEntity
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class TrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var db: AppDatabase
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        startForegroundService()
        startLocationUpdates()
    }

    private fun startForegroundService() {
        val notificationChannelId = "gps_tracker_channel"
        val channel = NotificationChannel(
            notificationChannelId,
            "GPS Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("GPS Tracking Active")
            .setContentText("Tracking your location in background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.create().apply {
            interval = 5 * 60 * 1000L  // every 5 minutes
            fastestInterval = 60 * 1000L
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    saveLocation(location)
                }
            }
        }

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                request, locationCallback, mainLooper
            )
        } else {
            // Could log this, or stop the service if desired
            stopSelf() // Optional: stop if permission isn't granted
        }
    }

    private fun saveLocation(location: Location) {
        val entity = LocationEntity(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = System.currentTimeMillis()
        )
        scope.launch {
            db.locationDao().insert(entity)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}