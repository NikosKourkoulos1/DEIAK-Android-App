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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Button
import android.widget.TextView
import com.example.deiakwaternetwork.data.APIService
import com.example.deiakwaternetwork.data.RetrofitClient
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.Marker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private var nodes: List<Node>? = null
    private val nodesMap: MutableMap<Marker, Node> = mutableMapOf()
    private lateinit var apiService: APIService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiService = RetrofitClient.getApiService(this)
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
            nodes = nodeRepository.getNodes()
            nodes?.forEach { node ->
                val location = LatLng(node.location.latitude, node.location.longitude)
                val marker = mMap.addMarker(
                    MarkerOptions()
                        .position(location)
                        .title(node.name)
                        .icon(getMarkerIconFromType(node.type))
                )

                // Add marker and node to nodesMap only if _id is not null
                if (!node._id.isNullOrEmpty()) {
                    marker?.let { nodesMap[it] = node }
                }
            }

            mMap.setOnMarkerClickListener { marker ->
                // Pass the marker to showNodeDetailsDialog
                showNodeDetailsDialog(marker)
                true
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
        val spinnerNodeStatus = dialogView.findViewById<Spinner>(R.id.spinnerNodeStatus)
        val etNodeDescription = dialogView.findViewById<EditText>(R.id.etNodeDescription)

        // Set up Spinner adapter for node types
        val nodeTypes = arrayOf("Κλειδί", "Πυροσβεστικός Κρουνός", "Ταφ", "Γωνία", "Κολεκτέρ", "Παροχή")
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, nodeTypes)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNodeType.adapter = typeAdapter

        // Set up Spinner adapter for node status (optional field)
        val nodeStatuses = resources.getStringArray(R.array.node_statuses)
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, nodeStatuses)
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNodeStatus.adapter = statusAdapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Create New Node")
            .setPositiveButton("Create", null) // Set to null initially to override onClick
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val createButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            createButton.setOnClickListener {
                val name = etNodeName.text.toString().trim() // Trim whitespace
                val type = spinnerNodeType.selectedItem.toString()

                // Validation: Check if name and type are empty
                if (name.isEmpty()) {
                    etNodeName.error = "Name is required"
                    return@setOnClickListener // Don't dismiss the dialog
                }

                if (type.isEmpty()) {
                    Toast.makeText(this, "Please select a node type", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener // Don't dismiss the dialog
                }

                // Get optional values (capacity, status, description)
                val capacity = etNodeCapacity.text.toString().toIntOrNull()
                val status = spinnerNodeStatus.selectedItem.toString()
                val description = etNodeDescription.text.toString()

                val newNode = Node(
                    _id = null,
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
                dialog.dismiss() // Dismiss the dialog on success
            }
        }

        dialog.show()
    }

    private fun createNodeAndAddMarker(node: Node) {
        lifecycleScope.launch {
            val createdNode = nodeRepository.createNode(node)
            if (createdNode != null) {
                // Add marker to the map (assuming addMarkerToMap handles this)
                addMarkerToMap(createdNode)
                Toast.makeText(this@MainActivity, "Node created successfully", Toast.LENGTH_SHORT).show()
            } else {
                // Handle error
                Toast.makeText(this@MainActivity, "Failed to create node", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addMarkerToMap(node: Node) {
        val latLng = LatLng(node.location.latitude, node.location.longitude)

        // Assuming you want the icon to be 48dp wide (adjust as needed)
        val iconWidthDp = 48
        val iconHeightDp = 48 // Assuming a square icon, adjust if necessary

        // Convert dp to pixels
        val density = resources.displayMetrics.density
        val iconWidthPx = (iconWidthDp * density).toInt()
        val iconHeightPx = (iconHeightDp * density).toInt()

        // Get the appropriate icon based on node type
        val markerIconResource = when (node.type) {
            "Κλειδί" -> R.drawable.kleidi_icon
            "Πυροσβεστικός Κρουνός" -> R.drawable.krounos_icon
            "Ταφ" -> R.drawable.taf_icon
            "Γωνία" -> R.drawable.gonia_icon
            "Κολεκτέρ" -> R.drawable.kolekter_icon
            "Παροχή" -> R.drawable.paroxi_icon
            else -> null // Handle the case where the type is not recognized
        }

        // Check if a valid icon resource was found
        if (markerIconResource != null) {
            // Get the original bitmap
            val originalBitmap = BitmapFactory.decodeResource(resources, markerIconResource)

            // Resize the bitmap
            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, iconWidthPx, iconHeightPx, false)

            // Create a BitmapDescriptor from the resized bitmap
            val markerIcon = BitmapDescriptorFactory.fromBitmap(resizedBitmap)

            mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(node.name)
                    .icon(markerIcon)
                    .anchor(0.5f, 1.0f) // Set anchor to bottom-center
            )
        } else {
            // Handle the case where the node type is not recognized (e.g., log an error message)
            Log.e("addMarkerToMap", "Unrecognized node type: ${node.type}")
        }
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

    private fun showNodeDetailsDialog(marker: Marker) {

        val node = nodesMap[marker] ?: return

        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_node_details, null)

        val tvNodeNameValue = view.findViewById<TextView>(R.id.tvNodeNameValue)
        val tvNodeTypeValue = view.findViewById<TextView>(R.id.tvNodeTypeValue)
        val tvNodeLocationValue = view.findViewById<TextView>(R.id.tvNodeLocationValue)
        val tvNodeCapacityValue = view.findViewById<TextView>(R.id.tvNodeCapacityValue)
        val tvNodeStatusValue = view.findViewById<TextView>(R.id.tvNodeStatusValue)
        val tvNodeDescriptionValue = view.findViewById<TextView>(R.id.tvNodeDescriptionValue)

        val btnEditNode = view.findViewById<Button>(R.id.btnEditNode)
        val btnDeleteNode = view.findViewById<Button>(R.id.btnDeleteNode)
        val btnClose = view.findViewById<Button>(R.id.btnClose)

        if (authRepository.getUserRole() == "admin") {
            btnEditNode.visibility = View.VISIBLE
            btnDeleteNode.visibility = View.VISIBLE
        }

        tvNodeNameValue.text = node.name
        tvNodeTypeValue.text = node.type
        tvNodeLocationValue.text = "${node.location.latitude}, ${node.location.longitude}"
        tvNodeCapacityValue.text = node.capacity?.toString() ?: "N/A"
        tvNodeStatusValue.text = node.status
        tvNodeDescriptionValue.text = node.description

        builder.setView(view)
            .setTitle("Node Details")

        val dialog = builder.create()

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        btnEditNode.setOnClickListener {
            dialog.dismiss()
            showEditNodeDialog(node, marker) // Pass the node and marker
        }

        btnDeleteNode.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete this node?")
                .setPositiveButton("Delete") { _, _ ->
                    deleteNode(node, marker)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        dialog.show()
    }

    private fun showEditNodeDialog(node: Node, marker: Marker) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_node_edit, null)

        val etNodeName = dialogView.findViewById<EditText>(R.id.etNodeName)
        val spinnerNodeType = dialogView.findViewById<Spinner>(R.id.spinnerNodeType)
        val etNodeCapacity = dialogView.findViewById<EditText>(R.id.etNodeCapacity)
        val spinnerNodeStatus = dialogView.findViewById<Spinner>(R.id.spinnerNodeStatus)
        val etNodeDescription = dialogView.findViewById<EditText>(R.id.etNodeDescription)

        val nodeTypes = arrayOf("Κλειδί", "Πυροσβεστικός Κρουνός", "Ταφ", "Γωνία", "Κολεκτέρ", "Παροχή")
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, nodeTypes)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNodeType.adapter = typeAdapter

        val nodeStatuses = resources.getStringArray(R.array.node_statuses)
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, nodeStatuses)
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNodeStatus.adapter = statusAdapter

        etNodeName.setText(node.name)
        spinnerNodeType.setSelection(typeAdapter.getPosition(node.type))
        etNodeCapacity.setText(node.capacity?.toString() ?: "")
        spinnerNodeStatus.setSelection(statusAdapter.getPosition(node.status))
        etNodeDescription.setText(node.description)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Edit Node")
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val updatedName = etNodeName.text.toString().trim()
                val updatedType = spinnerNodeType.selectedItem.toString()
                val updatedCapacity = etNodeCapacity.text.toString().toIntOrNull()
                val updatedStatus = spinnerNodeStatus.selectedItem.toString()
                val updatedDescription = etNodeDescription.text.toString()

                if (updatedName.isEmpty() || updatedType.isEmpty()) {
                    Toast.makeText(this, "Name and type are required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Create an updated node object, including the _id
                val updatedNode = node.copy(
                    name = updatedName,
                    type = updatedType,
                    capacity = updatedCapacity,
                    status = updatedStatus,
                    description = updatedDescription
                    // createdAt and updatedAt should be managed by the backend
                )

                // Call updateNodeInBackend with the _id
                updateNodeInBackend(node._id!!, updatedNode, marker) // Now we are passing the _id
                dialog.dismiss()
            }
        }

        dialog.show()
    }


    private fun updateNodeInBackend(nodeId: String, updatedNode: Node, marker: Marker) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.updateNode(nodeId, updatedNode)
                }

                if (response.isSuccessful) {
                    // Remove the old marker
                    marker.remove()

                    // Add a new marker with updated details
                    val newMarker = mMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(updatedNode.location.latitude, updatedNode.location.longitude))
                            .title(updatedNode.name)
                            .icon(getMarkerIconFromType(updatedNode.type))
                    )

                    // Update nodesMap with the new marker and updated node
                    nodesMap.remove(marker) // Remove the old marker-node association
                    newMarker?.let { nodesMap[it] = updatedNode } // Add the new marker-node association

                    Toast.makeText(this@MainActivity, "Node updated successfully", Toast.LENGTH_SHORT).show()
                } else {
                    // Log the error response body for debugging
                    val errorBody = response.errorBody()?.string()
                    Log.e("MainActivity", "Failed to update node: ${response.code()} - $errorBody")
                    Toast.makeText(this@MainActivity, "Failed to update node", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Exception when updating node: ${e.message}")
                Toast.makeText(this@MainActivity, "Failed to update node", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getMarkerIconFromType(nodeType: String): BitmapDescriptor {
        val iconWidthDp = 48
        val iconHeightDp = 48 // Assuming a square icon, adjust if necessary

        // Convert dp to pixels
        val density = resources.displayMetrics.density
        val iconWidthPx = (iconWidthDp * density).toInt()
        val iconHeightPx = (iconHeightDp * density).toInt()

        // Get the appropriate icon resource based on node type
        val markerIconResource = when (nodeType) {
            "Κλειδί" -> R.drawable.kleidi_icon
            "Πυροσβεστικός Κρουνός" -> R.drawable.krounos_icon
            "Ταφ" -> R.drawable.taf_icon
            "Γωνία" -> R.drawable.gonia_icon
            "Κολεκτέρ" -> R.drawable.kolekter_icon
            "Παροχή" -> R.drawable.paroxi_icon
            else -> {
                Log.e("getMarkerIconFromType", "Unrecognized node type: $nodeType")
                null
            } // Handle unrecognized types
        }

        return if (markerIconResource != null) {
            // Resize the bitmap
            val originalBitmap = BitmapFactory.decodeResource(resources, markerIconResource)
            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, iconWidthPx, iconHeightPx, false)

            // Create and return a BitmapDescriptor
            BitmapDescriptorFactory.fromBitmap(resizedBitmap)
        } else {
            // Return a default marker if no icon is found
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
        }
    }

    private fun deleteNode(node: Node, marker: Marker) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.deleteNode(node._id!!) // Assuming your deleteNode API call takes the node ID
                }

                if (response.isSuccessful) {
                    // Remove the marker from the map
                    marker.remove()

                    // Remove the node from nodesMap
                    nodesMap.remove(marker)

                    // Optionally, update your 'nodes' list if you are using it for other purposes

                    Toast.makeText(this@MainActivity, "Node deleted successfully", Toast.LENGTH_SHORT).show()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("MainActivity", "Failed to delete node: ${response.code()} - $errorBody")
                    Toast.makeText(this@MainActivity, "Failed to delete node", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Exception when deleting node: ${e.message}")
                Toast.makeText(this@MainActivity, "Failed to delete node", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Cancels location request (if in flight)
        cancellationTokenSource.cancel()
    }
}