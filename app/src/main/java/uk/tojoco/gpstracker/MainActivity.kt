package uk.tojoco.gpstracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import uk.tojoco.gpstracker.databinding.ActivityMainBinding
import uk.tojoco.gpstracker.service.TrackingService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Tracks a simple local flag for whether we've requested start/stop.
    // You may want to replace this with a more robust check (e.g. binding to the Service or checking Service state).
    private var isTracking = false

    // A place to store an action that should run once permissions are granted
    private var pendingPermissionAction: (() -> Unit)? = null

    // Registered launcher for multiple permissions
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val fgsGranted = if (Build.VERSION.SDK_INT >= 34) {
                permissions[Manifest.permission.FOREGROUND_SERVICE_LOCATION] ?: false
            } else {
                // on older API levels the foreground-service-location permission does not exist
                true
            }

            if (fineGranted && fgsGranted) {
                // Run the pending action (if any)
                pendingPermissionAction?.invoke()
            } else {
                Toast.makeText(
                    this,
                    "Permissions denied — cannot start location tracking.",
                    Toast.LENGTH_LONG
                ).show()
            }
            pendingPermissionAction = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup nav controller + bottom nav
        val navController = findNavController(R.id.nav_host_fragment)

        // NOTE: Use the viewBinding property generated from your XML id.
        // If your BottomNavigationView id is @+id/bottom_navigation then the property is binding.bottomNavigation
        binding.bottomNavigation.setupWithNavController(navController)

        // Optional: If you want to start tracking automatically if permissions already exist:
        // if (hasTrackingPermissions()) { /* maybe startTrackingFromUI() or show UI state */ }
    }

    /**
     * Request tracking-related permissions if needed, then run [action].
     * The action will be invoked immediately if permissions are already granted.
     */
    fun ensureTrackingPermissionsAndRun(action: () -> Unit) {
        if (hasTrackingPermissions()) {
            action()
            return
        }

        // store action to run after user responds to permission dialog
        pendingPermissionAction = action

        val toRequest = mutableListOf<String>()
        toRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 34) {
            // new permission on API 34+ for foreground-service location starts
            toRequest.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        requestPermissionsLauncher.launch(toRequest.toTypedArray())
    }

    /** Returns true if the required permissions are already granted. */
    private fun hasTrackingPermissions(): Boolean {
        val fineOk = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val fgsOk = if (Build.VERSION.SDK_INT >= 34) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return fineOk && fgsOk
    }

    /**
     * Public method Fragments / UI can call to start location tracking (foreground service + optional activity updates).
     * Will request permissions if needed.
     */
    fun startTrackingFromUI() {
        ensureTrackingPermissionsAndRun {
            try {
                val intent = Intent(this, TrackingService::class.java)
                // startForegroundService is required for starting FGS on Android O+
                startForegroundService(intent)
                isTracking = true
                Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // defensive fallback; most issues should be prevented by permissions check above
                Toast.makeText(this, "Failed to start tracking: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    /**
     * Public method to stop the tracking service. Fragments / UI should call this to stop tracking.
     */
    fun stopTrackingFromUI() {
        val intent = Intent(this, TrackingService::class.java)
        val stopped = stopService(intent)
        isTracking = false
        if (stopped) {
            Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show()
        }
    }

    /** Convenience: expose whether the app believes it's tracking. */
    fun isTracking(): Boolean = isTracking

    override fun onDestroy() {
        super.onDestroy()
        // clear pending action if activity dies
        pendingPermissionAction = null
    }
}
