package com.example.deiakwaternetwork

import NodeRepository
import android.Manifest
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.CancellationTokenSource
import android.app.ActionBar
import com.example.deiakwaternetwork.data.AuthRepository
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.example.deiakwaternetwork.data.UserRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Base64
import android.view.View
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONObject
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import com.example.deiakwaternetwork.model.Node
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var settingsClient: SettingsClient
    private lateinit var locationRequest: LocationRequest
    private var cancellationTokenSource = CancellationTokenSource()

    private lateinit var authRepository: AuthRepository

    // Constant for the permission request code
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val REQUEST_CHECK_SETTINGS = 2

    private lateinit var userRepository: UserRepository
    private lateinit var nodeRepository: NodeRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authRepository = AuthRepository(this) // Initialize authRepository

        userRepository = UserRepository(this)

        nodeRepository = NodeRepository(this)

        // Check if the user is logged in
        if (authRepository.isLoggedIn()) {
            // User is logged in, proceed with MainActivity setup
            supportActionBar?.apply {
                displayOptions = androidx.appcompat.app.ActionBar.DISPLAY_SHOW_CUSTOM
                setCustomView(R.layout.logo_bar)
            }

            val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
            mapFragment.getMapAsync(this)

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            settingsClient = LocationServices.getSettingsClient(this)

            // Create a location request
            locationRequest = LocationRequest.create().apply {
                interval = 10000 // Update interval in milliseconds
                fastestInterval = 5000 // Fastest update interval in milliseconds
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            // ... (The rest of your existing MainActivity setup code) ...

        } else {
            // User is not logged in, redirect to LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
            finish() // Finish MainActivity to prevent going back
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val isAdmin = authRepository.getUserRole() == "admin"
        menu?.findItem(R.id.action_all_users)?.isVisible = isAdmin
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_user_details -> {
                // Show user details
                showUserDetails()
                true
            }
            R.id.action_all_users -> {
                // Show all users (admin only)
                showAllUsers()
                true
            }
            R.id.action_logout -> {
                // Handle logout
                authRepository.logout()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showUserDetails() {
        lifecycleScope.launch {
            try {
                val userId = getUserIdFromToken()

                if (userId != null) {
                    val user = userRepository.getUser(userId)
                    if (user != null) {
                        // Create and show the AlertDialog
                        val dialog = AlertDialog.Builder(this@MainActivity)
                            .setTitle("User Details")
                            .setMessage(
                                "Name: ${user.name}\n" +
                                        "Email: ${user.email}\n" +
                                        "Role: ${user.role}"
                            )
                            .setPositiveButton("OK") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .create()

                        dialog.show()
                    } else {
                        // Handle the case where user data is null
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to fetch user details",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    // Handle the case where userId is null
                    Toast.makeText(this@MainActivity, "User ID not found", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                // Handle exceptions
                Log.e("MainActivity", "Error fetching user details: ${e.message}")
                Toast.makeText(
                    this@MainActivity,
                    "Failed to fetch user details",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showAllUsers() {
        lifecycleScope.launch {
            try {
                val users = userRepository.getAllUsers()
                if (users != null) {
                    // Format user details with email and role, and number them
                    val formattedUserList = users.mapIndexed { index, user ->
                        "${index + 1}. Email: ${user.email}, Role: ${user.role}"
                    }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("All Users")
                        .setItems(formattedUserList.toTypedArray()) { dialog, which ->
                            // Handle user selection if needed (e.g., show details or delete)
                            // ...
                        }
                        .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                        .show()
                } else {
                    // Handle the case where fetching users failed
                    Toast.makeText(this@MainActivity, "Failed to fetch users", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Handle exceptions (e.g., network errors)
                Log.e("MainActivity", "Error fetching users: ${e.message}")
                Toast.makeText(this@MainActivity, "Failed to fetch users", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun getUserIdFromToken(): String? {
        val token = authRepository.getToken()
        return if (token != null) {
            try {
                val parts = token.split(".")
                val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
                val jsonObject = JSONObject(payload)
                jsonObject.getString("id") // Access the "id" claim
            } catch (e: Exception) {
                Log.e("MainActivity", "Error decoding token: ${e.message}")
                null
            }
        } else {
            null
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val fabAddNode = findViewById<FloatingActionButton>(R.id.fabAddNode)
        /*closed for now to test
                // Set initial map position to Corfu Island
                val corfuBounds = LatLngBounds(
                    LatLng(39.45, 19.7), // Southwest corner of Corfu Island
                    LatLng(39.8, 20.1)  // Northeast corner of Corfu Island
                )
                // Set initial map position to Corfu Island AND lock the camera to those bounds
                val cameraUpdate = CameraUpdateFactory.newLatLngBounds(corfuBounds, 0)
                mMap.moveCamera(cameraUpdate)

                // Constrain map camera target to Corfu Island (allow zooming)
                mMap.setOnCameraMoveListener {
                    // Get current camera position
                    val currentCameraPosition = mMap.cameraPosition

                    // Check if the camera target is within Corfu bounds
                    if (!corfuBounds.contains(currentCameraPosition.target)) {
                        // If the target is outside the bounds, move it back inside
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(corfuBounds, 0))
                    }
                }
        */
        mMap.setOnMyLocationButtonClickListener {
            checkLocationSettingsAndGetCurrentLocation()
            true
        }

        if (::nodeRepository.isInitialized) { // Check if nodeRepository is initialized
            lifecycleScope.launch {
                val nodes = nodeRepository.getNodes()
                nodes?.forEach { node ->
                    addMarkerToMap(node)
                }
            }
        } else {
            // Handle the case where nodeRepository is not initialized
            Log.e("MainActivity", "nodeRepository is not initialized")
            Toast.makeText(this, "Error initializing map", Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            val nodes = nodeRepository.getNodes()
            nodes?.forEach { node ->
                addMarkerToMap(node)
            }
        }

        if (authRepository.getUserRole() == "admin") {
            fabAddNode.visibility = View.VISIBLE
        }

        fabAddNode.setOnClickListener {
            Toast.makeText(this, "Click on the map to add a node", Toast.LENGTH_SHORT).show()
            mMap.setOnMapClickListener { latLng ->
                showNodeCreationDialog(latLng)
                mMap.setOnMapClickListener(null)
            }
        }

        // GPS Location (with permission check)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request location permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        mMap.isMyLocationEnabled = true
    }

    private fun showNodeCreationDialog(latLng: LatLng) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_node_creation, null)

        // Get references to EditText fields and Spinner
        val etNodeName = dialogView.findViewById<EditText>(R.id.etNodeName)
        val spinnerNodeType = dialogView.findViewById<Spinner>(R.id.spinnerNodeType)
        val etNodeCapacity = dialogView.findViewById<EditText>(R.id.etNodeCapacity)
        val spinnerNodeStatus = dialogView.findViewById<Spinner>(R.id.spinnerNodeStatus) // Use a Spinner for status
        val etNodeDescription = dialogView.findViewById<EditText>(R.id.etNodeDescription)

        // Set up Spinner adapter for node types
        val nodeTypes = arrayOf("Κλειδί", "Πυροσβεστικός Κρουνός", "Ταφ", "Γωνία", "Κολεκτέρ", "Παροχή")
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, nodeTypes)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNodeType.adapter = typeAdapter

        // Set up Spinner adapter for node status
        val nodeStatuses = arrayOf("active", "maintenance", "inactive") // Allowed status values
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, nodeStatuses)
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNodeStatus.adapter = statusAdapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Create New Node")
            .setPositiveButton("Create") { dialog, _ ->
                val name = etNodeName.text.toString()
                val type = spinnerNodeType.selectedItem.toString()
                val capacity = etNodeCapacity.text.toString().toIntOrNull()
                val status = spinnerNodeStatus.selectedItem.toString() // Get value from status Spinner
                val description = etNodeDescription.text.toString()

                val newNode = Node(
                    name = name,
                    type = type,
                    location = com.example.deiakwaternetwork.model.Location(latLng.latitude, latLng.longitude),
                    capacity = capacity,
                    status = status,
                    description = description,
                    createdAt = "",
                    updatedAt = ""
                )
                createNodeAndAddMarker(newNode)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun createNodeAndAddMarker(node: Node) {
        lifecycleScope.launch {
            val createdNode = nodeRepository.createNode(node)  // Call the API using your repository
            if (createdNode != null) {
                // Add marker to the map
                addMarkerToMap(createdNode)
                // Optionally, you can show a success message
                Toast.makeText(this@MainActivity, "Node created successfully", Toast.LENGTH_SHORT).show()
            } else {
                // Handle error (e.g., show a Toast)
                Toast.makeText(this@MainActivity, "Failed to create node", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addMarkerToMap(node: Node) {
        val latLng = LatLng(node.location.latitude, node.location.longitude)
        val markerIcon = when (node.type) {
            "Κλειδί" -> BitmapDescriptorFactory.fromResource(R.drawable.kleidi_icon)
            "Πρυσβεστικός Κρουνός" -> BitmapDescriptorFactory.fromResource(R.drawable.krounos_icon)
            "Ταφ" -> BitmapDescriptorFactory.fromResource(R.drawable.taf_icon) // Example
            "Γωνία" -> BitmapDescriptorFactory.fromResource(R.drawable.gonia_icon) // Example
            "Κολεκτέρ" -> BitmapDescriptorFactory.fromResource(R.drawable.kolekter_icon) // Example
            "Παροχή" -> BitmapDescriptorFactory.fromResource(R.drawable.paroxi_icon) // Example
            else -> BitmapDescriptorFactory.defaultMarker() // Default icon if type not found
        }

        mMap.addMarker(
            MarkerOptions()
            .position(latLng)
            .title(node.name)
            .icon(markerIcon)
        )
    }
    // Handle the permission request response
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, enable location (with check)
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    mMap.isMyLocationEnabled = true
                }
            } else {
                // Permission denied, show a message
                showLocationPermissionDeniedDialog()
            }
        }
    }

    // Function to show a dialog when location permission is denied
    private fun showLocationPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Denied")
            .setMessage("This app needs location permission to show your current location on the map. Please grant the permission in the app settings.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                // You can optionally open app settings here
            }
            .show()
    }

    private fun checkLocationSettingsAndGetCurrentLocation() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // Location settings are satisfied, get the current location
            getCurrentLocation()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, show a dialog to enable location
                try {
                    // Show the dialog by calling startResolutionForResult()
                    exception.startResolutionForResult(
                        this@MainActivity,
                        REQUEST_CHECK_SETTINGS
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error
                    Log.e("LocationService", "Error getting location settings resolution: ${sendEx.message}")
                }
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request location permission if not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        fusedLocationClient.getCurrentLocation(
            LocationRequest.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        )
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                } ?: run {
                    // Handle the case where the location is null
                    Log.e("LocationService", "Location is null")
                }
            }
            .addOnFailureListener { exception: Exception ->
                // Handle any errors that occur while getting the location
                Log.e("LocationService", "Error getting current location: ${exception.message}")
            }
    }


    override fun onStop() {
        super.onStop()
        // Cancels location request (if in flight)
        cancellationTokenSource.cancel()
    }
}
