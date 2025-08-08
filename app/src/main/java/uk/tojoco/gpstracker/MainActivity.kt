package uk.tojoco.gpstracker

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import uk.tojoco.gpstracker.data.AppDatabase
import uk.tojoco.gpstracker.data.LocationEntity
import uk.tojoco.gpstracker.databinding.ActivityMainBinding
import uk.tojoco.gpstracker.ui.LocationAdapter
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private lateinit var adapter: LocationAdapter

    private val saveInterval = 5 * 60 * 1000L
    private var lastSaved: Long = 0

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        adapter = LocationAdapter(listOf())
        binding.locationList.layoutManager = LinearLayoutManager(this)
        binding.locationList.adapter = adapter

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) startLocationUpdates()
            else binding.locationText.text = "Location permission denied"
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            startLocationUpdates()
        }

        loadSavedLocations()
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    val lat = location.latitude
                    val lng = location.longitude
                    binding.locationText.text = "Lat: $lat, Lng: $lng"

                    val now = System.currentTimeMillis()
                    if (now - lastSaved >= saveInterval) {
                        lastSaved = now
                        scope.launch(Dispatchers.IO) {
                            db.locationDao().insert(LocationEntity(latitude = lat, longitude = lng, timestamp = now))
                            withContext(Dispatchers.Main) {
                                loadSavedLocations()
                            }
                        }
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        }
    }

    private fun loadSavedLocations() {
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                db.locationDao().getAll()
            }
            adapter = LocationAdapter(data)
            binding.locationList.adapter = adapter
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        scope.cancel()
    }
}
