package uk.tojoco.gpstracker

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import uk.tojoco.gpstracker.data.AppDatabase
import uk.tojoco.gpstracker.data.LocationEntity
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationText: TextView

    private var lastSavedTime: Long = 0
    private lateinit var db: AppDatabase
    private val saveIntervalMillis = 1 * 30 * 1000L // 30 seconds

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationText = TextView(this)
        setContentView(locationText)

        db = AppDatabase.getDatabase(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                startLocationUpdates()
            } else {
                locationText.text = "Location permission denied"
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location: Location in result.locations) {
                    val text = "Lat: ${location.latitude}, Lng: ${location.longitude}"
                    locationText.text = text

                    val now = System.currentTimeMillis()
                    if (now - lastSavedTime >= saveIntervalMillis) {
                        lastSavedTime = now
                        scope.launch {
                            db.locationDao().insert(
                                LocationEntity(
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    timestamp = now
                                )
                            )
                        }
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        scope.cancel()
    }
}
