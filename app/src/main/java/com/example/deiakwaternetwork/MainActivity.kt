package com.example.deiakwaternetwork

import NodeRepository
import PipeRepository
import android.Manifest
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.CancellationTokenSource
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
import android.widget.AdapterView
import android.widget.Button
import android.widget.TextView
import com.example.deiakwaternetwork.data.APIService
import com.example.deiakwaternetwork.data.RetrofitClient
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.Marker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.LinearLayout
import com.example.deiakwaternetwork.model.Pipe
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import android.graphics.Color
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.google.android.gms.maps.model.ButtCap
import com.google.android.gms.maps.model.CustomCap
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.PatternItem
import com.google.maps.android.SphericalUtil
import android.graphics.Canvas
import com.google.android.gms.maps.model.RoundCap
import android.graphics.Paint // Add this import at the top of your file
import android.graphics.drawable.VectorDrawable
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat



class MainActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnCameraIdleListener {

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
    private var visibleNodeTypes: MutableList<String> = mutableListOf()
    private lateinit var chipGroup: ChipGroup

    private var markerClickListener: GoogleMap.OnMarkerClickListener? = null //change val to var


    private var isDrawingPipe = false

    private var tempPipeLine: Polyline? = null // Temporary line during drawing

    private var pipePoints: MutableList<LatLng> = mutableListOf() // Stores LatLng points
    private lateinit var pipeRepository: PipeRepository
    private var pipes: List<Pipe>? = null

    private var pipeList = mutableListOf<Pipe>() // Add this!  Mutable list for updates.
    //private val pipesMap: MutableMap<Polyline, Pipe> = mutableMapOf()
    private val pipesMap: MutableMap<Polyline, PipeData> = mutableMapOf()

    private var tempMarkers: MutableList<Marker> = mutableListOf() // Add this list
    data class PipeData(val pipe: Pipe, val arrowMarkers: MutableList<Marker> = mutableListOf())
    private val polylineAnimators = mutableMapOf<Polyline, ValueAnimator>() // Store animators

    private val ARROW_SPACING_METERS = 15.0 // Adjust this as needed

    private val baseIconSizeDp = 40 // NODE markers size
    private val baseArrowSizeDp = 10 // ARROW markers size
    private val visibilityThresholdZoom = 15f // Zoom level for icons to appear
    private val pipeVisibilityThresholdZoom = 15f
    private val fixedIconSizePx: Int by lazy { (baseIconSizeDp * resources.displayMetrics.density).toInt() }
    private val fixedArrowSizePx: Int by lazy { (baseArrowSizeDp * resources.displayMetrics.density).toInt() }

    private lateinit var crosshairImageView: ImageView // Declare at the class level

    private var isAddingNode = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiService = RetrofitClient.getApiService(this)
        setContentView(R.layout.activity_main)

        authRepository = AuthRepository(this) // Initialize authRepository

        userRepository = UserRepository(this)
        userRepository = UserRepository(this)

        nodeRepository = NodeRepository(this)

        pipeRepository = PipeRepository(this)

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

            // Initialize the chipGroup
            chipGroup = findViewById(R.id.filterChipGroup)
            chipGroup.visibility = View.GONE

            // Create the filter chips after initializing the chip group
            createFilterChips()

            // Set the click listener for filterTextView
            val filterTextView = findViewById<TextView>(R.id.filterTextView)
            filterTextView.setOnClickListener {
                toggleChipGroupVisibility()
            }



        } else {
            // User is not logged in, redirect to LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
            finish() // Finish MainActivity to prevent going back
        }
    }



    private fun toggleChipGroupVisibility() {
        chipGroup.visibility = if (chipGroup.visibility == View.VISIBLE) {
            View.GONE
        } else {
            View.VISIBLE
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

        addCrosshair()
        // Set initial map position and constraints
        val corfuBounds = LatLngBounds(
            LatLng(39.45, 19.7), // Southwest corner of Corfu Island
            LatLng(39.8, 20.1)  // Northeast corner of Corfu Island
        )
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(corfuBounds, 0))
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL // Set default map type

        // Prevent the camera from moving outside of Corfu
        //mMap.setLatLngBoundsForCameraTarget(corfuBounds) // REPLACING OnCameraMoveListener with setLatLngBoundsForCameraTarget
        mMap.setOnCameraMoveListener {
            if (!corfuBounds.contains(mMap.cameraPosition.target)) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(corfuBounds, 0))
            }
        }


        // Set up location services.  Request permission if needed.
        mMap.setOnMyLocationButtonClickListener {
            checkLocationSettingsAndGetCurrentLocation()
            true
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }

        // --- Map Type Spinner --- (Keep this as it is)
        val mapTypeSpinner = findViewById<Spinner>(R.id.mapTypeSpinner)
        val mapTypes = arrayOf("Προεπιλογή", "Δορυφόρος", "Έδαφος", "Υβριδικό")
        val mapTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mapTypes)
        mapTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mapTypeSpinner.adapter = mapTypeAdapter
        mapTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedMapType = when (position) {
                    0 -> GoogleMap.MAP_TYPE_NORMAL
                    1 -> GoogleMap.MAP_TYPE_SATELLITE
                    2 -> GoogleMap.MAP_TYPE_TERRAIN
                    3 -> GoogleMap.MAP_TYPE_HYBRID
                    else -> GoogleMap.MAP_TYPE_NORMAL
                }
                mMap.mapType = selectedMapType
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                mMap.mapType = GoogleMap.MAP_TYPE_NORMAL // Default
                mapTypeSpinner.setSelection(0) // Select "Normal" in the spinner
            }
        }

        // Set up listeners *ONCE*, after the map is fully loaded.
        mMap.setOnMapLoadedCallback {

            // Node click listener.
            markerClickListener = GoogleMap.OnMarkerClickListener { marker ->
                val node = nodesMap[marker]
                if (node != null) {
                    showNodeDetailsDialog(marker)
                    true
                } else {
                    false
                }
            }
            mMap.setOnMarkerClickListener(markerClickListener)

            // Polyline click listener.
            mMap.setOnPolylineClickListener { polyline ->
                showPipeDetailsDialogFromPolyline(polyline)
            }

            // --- Load BOTH nodes and pipes *after* the listeners are set ---
            lifecycleScope.launch {
                nodes = withContext(Dispatchers.IO) { nodeRepository.getNodes() }
                pipes = withContext(Dispatchers.IO) { pipeRepository.getPipes() }

                withContext(Dispatchers.Main) {
                    // Add nodes first
                    nodes?.forEach { node -> addMarkerToMap(node) }
                    // Then add pipes (which depend on nodes)
                    pipes?.forEach { pipe -> addPipeToMap(pipe) }
                    updateMarkerIcons() //  ***CALL updateMarkerIcons HERE***
                }
            }
        }

        // Set the camera idle listener.  Do this *outside* of onMapLoadedCallback,
        // but *inside* onMapReady.
        mMap.setOnCameraIdleListener(this)


        // Set up FABs (Floating Action Buttons) - Keep this part as is
        val fabAddNode = findViewById<FloatingActionButton>(R.id.fabAddNode)
        val fabAddPipe = findViewById<FloatingActionButton>(R.id.fabAddPipe)

        if (authRepository.getUserRole() == "admin") {
            fabAddNode.visibility = View.VISIBLE
            fabAddPipe.visibility = View.VISIBLE
        }


        fabAddNode.setOnClickListener {
            if (!isAddingNode) {
                // First click: Enter node adding mode
                isAddingNode = true
                showCrosshair()
                fabAddNode.setImageResource(android.R.drawable.ic_menu_save) // Change icon to a "confirm" icon
                Toast.makeText(this, "Position the crosshair and tap the button again to add a node.", Toast.LENGTH_LONG).show()

            } else {
                // Second click: Capture coordinates and show dialog
                val centerLatLng = mMap.cameraPosition.target
                showNodeCreationDialog(centerLatLng)
                hideCrosshair()
                isAddingNode = false // Reset the state
                fabAddNode.setImageResource(android.R.drawable.ic_menu_add) // Change icon back to "add"
            }
        }

        fabAddPipe.setOnClickListener {
            if (authRepository.getUserRole() == "admin") {
                startPipeDrawingMode()
            }
        }
    }


    private fun addCrosshair() {
        crosshairImageView = ImageView(this)
        crosshairImageView.setImageResource(R.drawable.ic_crosshair) // Replace with your crosshair image

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER // Center it in the FrameLayout
        )
        crosshairImageView.layoutParams = params

        // Find the FrameLayout (the map's parent) and add the ImageView
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        val mapView = mapFragment.view as? ViewGroup // Get the map's view (usually a FrameLayout)

        mapView?.let{
            it.addView(crosshairImageView)
        }

        // Initially hide the crosshair
        crosshairImageView.visibility = View.GONE
    }

    private fun showCrosshair(){
        crosshairImageView.visibility = View.VISIBLE
    }

    private fun hideCrosshair(){
        crosshairImageView.visibility = View.GONE
    }
    override fun onCameraIdle() {
        updateMarkerIcons()
    }

    private fun updateMarkerIcons() {
        val currentZoom = mMap.cameraPosition.zoom

        val nodeMarkersVisible = currentZoom >= visibilityThresholdZoom
        nodesMap.keys.forEach { it.isVisible = nodeMarkersVisible }

        // Manage arrow and polyline visibility
        if (currentZoom >= visibilityThresholdZoom) {
            pipesMap.forEach { (polyline, pipeData) ->
                addArrowMarkers(polyline, pipeData.pipe.flow) // Re-add arrow markers
                //CHECK FOR PIPE VISIBILITY THRESHOLD
                polyline.isVisible = visibleNodeTypes.contains("Pipes") && currentZoom >= pipeVisibilityThresholdZoom // AND CHECK ZOOM
            }
        } else {
            pipesMap.forEach { (polyline, pipeData) ->
                // Hide arrow markers when zoomed out
                pipeData.arrowMarkers.forEach { it.isVisible = false }
                //CHECK FOR PIPE VISIBILITY THRESHOLD
                polyline.isVisible = visibleNodeTypes.contains("Pipes") && currentZoom >= pipeVisibilityThresholdZoom // AND CHECK ZOOM

            }
        }
    }

    private fun createBitmapDescriptor(iconResource: Int, width: Int, height: Int): BitmapDescriptor {
        val drawable = ResourcesCompat.getDrawable(resources, iconResource, null)
        return if (drawable is VectorDrawableCompat || drawable is VectorDrawable) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        } else {
            val originalBitmap = BitmapFactory.decodeResource(resources, iconResource)
            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, false)
            BitmapDescriptorFactory.fromBitmap(resizedBitmap)
        }
    }


    private fun createFilterChips() {
        val nodeTypes = arrayOf(
            "Κλειδί", "Πυροσβεστικός Κρουνός", "Ταφ", "Γωνία", "Κολεκτέρ", "Παροχή" // Removed "Pipes" from here
        )

        //chip for pipes - Add this *OUTSIDE* the loop
        val pipeChip = Chip(this)
        pipeChip.text = "Σωλήνες" // Changed to Greek
        pipeChip.isCheckable = true
        pipeChip.isChecked = true // Initially show pipes
        visibleNodeTypes.add("Pipes") // Keep this as "Pipes" internally
        pipeChip.setOnClickListener {
            handleChipClick(pipeChip) // Use same handler
        }
        chipGroup.addView(pipeChip)


        for (nodeType in nodeTypes) {
            val chip = Chip(this)
            chip.text = nodeType
            chip.isCheckable = true
            chip.isChecked = true // Initially show all node types
            visibleNodeTypes.add(nodeType) // Add all node types to the visible list
            chip.setOnClickListener {
                handleChipClick(chip) // Existing node type click handler
            }
            chipGroup.addView(chip)
        }
    }
    private fun handleChipClick(chip: Chip) {
        val type = chip.text.toString()
        val internalType = if (type == "Σωλήνες") "Pipes" else type // Convert to internal representation

        if (chip.isChecked) {
            if (!visibleNodeTypes.contains(internalType)) { // Use internalType
                visibleNodeTypes.add(internalType) // Use internalType
            }
        } else {
            visibleNodeTypes.remove(internalType) // Use internalType
        }

        nodesMap.forEach { (marker, node) ->
            marker.isVisible = visibleNodeTypes.contains(node.type)
        }

        // Always call filterAndRedrawPipes, no matter which chip.
        filterAndRedrawPipes()
    }

    private fun filterAndRedrawPipes() {
        val showPipes = visibleNodeTypes.contains("Pipes")
        val zoomSufficient = mMap.cameraPosition.zoom >= pipeVisibilityThresholdZoom // Calculate once

        pipesMap.forEach { (polyline, pipeData) ->
            polyline.isVisible = showPipes && zoomSufficient // Combined check

            if (!showPipes) {
                pipeData.arrowMarkers.forEach { it.isVisible = false }
            } else { // showPipes == true
                if (mMap.cameraPosition.zoom >= visibilityThresholdZoom) {
                    addArrowMarkers(polyline, pipeData.pipe.flow) // Show arrows if zoomed in
                } else{
                    //IF PIPES SHOW BUT ZOOM NOT SUFFICIENT, HIDE THE ARROWS
                    pipeData.arrowMarkers.forEach{it.isVisible = false}
                }
            }
        }
    }

    private fun refreshMap() {
        mMap.clear() // Clear *everything* (markers and polylines)
        nodesMap.clear() // Clear the node map

        lifecycleScope.launch {
            // Fetch updated data for BOTH nodes and pipes
            nodes = withContext(Dispatchers.IO) {
                nodeRepository.getNodes()
            }
            pipes = withContext(Dispatchers.IO){ // added pipes
                pipeRepository.getPipes()
            }

            // Update the map on the main thread after data is fetched
            withContext(Dispatchers.Main) {
                nodes?.forEach { node ->
                    if (visibleNodeTypes.contains(node.type)) {
                        addMarkerToMap(node) // Re-add markers
                    }
                }
                pipes?.forEach{ pipe ->
                    addPipeToMap(pipe) // Re-add pipes
                }

                // IMPORTANT: Re-set the listeners *AFTER* adding markers:
                mMap.setOnMarkerClickListener(markerClickListener)
                mMap.setOnPolylineClickListener { polyline ->
                    showPipeDetailsDialogFromPolyline(polyline)
                }
            }
        }
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

        val markerIconResource = when (node.type) {
            "Κλειδί" -> R.drawable.kleidi_icon
            "Πυροσβεστικός Κρουνός" -> R.drawable.krounos_icon
            "Ταφ" -> R.drawable.taf_icon
            "Γωνία" -> R.drawable.gonia_icon
            "Κολεκτέρ" -> R.drawable.kolekter_icon
            "Παροχή" -> R.drawable.paroxi_icon
            else -> null
        }
        if (markerIconResource == null){
            Log.e("addMarkerToMap", "Unrecognized node type: ${node.type}")
            return
        }

        // Use the createBitmapDescriptor function (defined below)
        val markerIcon = createBitmapDescriptor(markerIconResource, fixedIconSizePx, fixedIconSizePx)

        val marker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(node.name)
                .icon(markerIcon) //  SET THE ICON HERE
                .anchor(0.5f, 0.5f) // <---  CENTER THE MARKER.  THIS IS KEY!
        )
        marker?.let {
            nodesMap[it] = node
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

                    mMap.setOnMarkerClickListener { clickedMarker ->
                        // Find the node associated with the clicked marker
                        val clickedNode = nodesMap[clickedMarker]

                        if (clickedNode != null) {
                            showNodeDetailsDialog(clickedMarker)
                            true // Consume the click event
                        } else {
                            false // Don't consume the click event if no node is found
                        }
                    }
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
        // --- Fixed Size ---
        val iconWidthDp = 48
        val iconHeightDp = 48

        // --- Convert dp to pixels ---
        val density = resources.displayMetrics.density
        val iconWidthPx = (iconWidthDp * density).toInt()
        val iconHeightPx = (iconHeightDp * density).toInt()

        Log.d("MarkerDebug", "getMarkerIconFromType: iconWidthPx = $iconWidthPx, iconHeightPx = $iconHeightPx") // Keep Logging

        // --- Rest is similar, but using dynamic sizes ---
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
        if (markerIconResource == null){
            Log.e("addMarkerToMap", "Unrecognized node type: $nodeType")
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
        }

        // Use VectorDrawableCompat for vector drawables
        val drawable = ResourcesCompat.getDrawable(resources, markerIconResource!!, null) //Added !!
        return if (drawable is VectorDrawableCompat || drawable is VectorDrawable) {
            val bitmap = Bitmap.createBitmap(iconWidthPx, iconHeightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, iconWidthPx, iconHeightPx)
            drawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        } else {
            // Fallback for non-vector drawables (e.g., PNGs)
            val originalBitmap = BitmapFactory.decodeResource(resources, markerIconResource)
            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, iconWidthPx, iconHeightPx, false)
            BitmapDescriptorFactory.fromBitmap(resizedBitmap)
        }
    }

    private fun deleteNode(node: Node, marker: Marker) {
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "deleteNode called for node: ${node.name}, id: ${node._id}")
                val response = withContext(Dispatchers.IO) {
                    apiService.deleteNode(node._id!!)
                }

                if (response.isSuccessful) {
                    Log.d("MainActivity", "Node deletion successful, removing marker: ${marker.title}")
                    marker.remove() // Remove the marker from the map.
                    nodesMap.remove(marker) // Remove the entry from nodesMap.

                    // **FIX: Update the 'nodes' list *immediately*.**
                    nodes = nodes?.filter { it._id != node._id }

                    Toast.makeText(this@MainActivity, "Node deleted successfully", Toast.LENGTH_SHORT).show()
                    // Reload pipes after deletion
                    pipes = pipeRepository.getPipes()
                    filterAndRedrawPipes()  // Now this will use the updated 'nodes' list

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


    // --- Pipe Drawing Functions ---

    private fun startPipeDrawingMode() {
        isDrawingPipe = true // Set immediately
        pipePoints.clear()
        tempPipeLine?.remove()
        tempPipeLine = null
        mMap.setOnMarkerClickListener(null) // Keep this to prevent node clicks
        clearTempMarkers()
        showCrosshair() // Show Crosshair

        Toast.makeText(this, "Tap on the map to add pipe points.", Toast.LENGTH_LONG).show()

        mMap.setOnMapClickListener { latLng ->
            if (isDrawingPipe) {
                val centerLatLng = mMap.cameraPosition.target // Use map center
                addPointToPipe(centerLatLng) // Directly add the clicked point - NO SNAPPING
                Log.d("PipeDrawing", "Adding point at CLICK: ${centerLatLng.latitude}, ${centerLatLng.longitude}")
            }
        }

        val fabAddPipe = findViewById<FloatingActionButton>(R.id.fabAddPipe)
        fabAddPipe.setImageResource(android.R.drawable.ic_menu_save)
        fabAddPipe.setOnClickListener {
            finishPipeDrawing()
        }

        val fabCancelPipe = findViewById<FloatingActionButton>(R.id.fabCancelPipe)
        fabCancelPipe.visibility = View.VISIBLE
        fabCancelPipe.setOnClickListener {
            resetPipeDrawingState()
        }
    }




    private fun finishPipeDrawing() {
        isDrawingPipe = false // Set immediately!
        mMap.setOnMapClickListener(null) // Remove the map click listener

        if (pipePoints.size < 2) {
            Toast.makeText(this, "You need at least two points to create a pipe.", Toast.LENGTH_SHORT).show()
            resetPipeDrawingState() // Go back to the initial state
            return
        }

        // *** IMPORTANT: Create a copy of pipePoints ***
        val pipePointsCopy = pipePoints.toList()

        // Restore the "Add Pipe" button
        val fabAddPipe = findViewById<FloatingActionButton>(R.id.fabAddPipe)
        fabAddPipe.setImageResource(android.R.drawable.ic_menu_add) // Restore original icon
        fabAddPipe.setOnClickListener {
            if (authRepository.getUserRole() == "admin") {
                startPipeDrawingMode() // Restart drawing mode on click
            }
        }
        mMap.setOnMarkerClickListener(markerClickListener)


        val fabCancelPipe = findViewById<FloatingActionButton>(R.id.fabCancelPipe)
        fabCancelPipe.visibility = View.GONE
        hideCrosshair() // HIDE CROSSHAIR
        showPipeCreationDialog(pipePointsCopy) // Pass the *copy*
    }

    private fun addPointToPipe(latLng: LatLng) {
        pipePoints.add(latLng)  // Add the point.
        updateTempPolyline()   // THEN update the polyline.
        addTempMarker(latLng)  // THEN add the marker.  Strict order!
    }


    private fun addTempMarker(latLng: LatLng) {
        val marker =  mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)) // Use a distinct color
                .anchor(0.5f, 0.5f) // Center the marker  <- IMPORTANT!
        )
        marker?.let{tempMarkers.add(it)} // Add to the list , null check
    }

    private fun updateTempPolyline() {
        tempPipeLine?.remove() // Remove the previous line

        if (pipePoints.size >= 2) { // Need at least 2 points
            tempPipeLine = mMap.addPolyline(
                PolylineOptions()
                    .addAll(pipePoints)
                    .color(Color.GRAY) // Temporary line color
                    .width(5f)
                    .clickable(false) // Temporary line not clickable
            )
            Log.d("PipeDrawing", "Temp polyline updated with ${pipePoints.size} points.") // Log for debugging
        } else {
            Log.d("PipeDrawing", "Not enough points to draw temp polyline (${pipePoints.size} points).")
        }
    }

    private fun resetPipeDrawingState() {
        isDrawingPipe = false // Set flag first
        tempPipeLine?.remove() // Remove polyline
        pipePoints.clear() // Clear pipePoints
        clearTempMarkers()// THEN clear markers.
        mMap.setOnMapClickListener(null) // Remove pipe drawing listener
        mMap.setOnMarkerClickListener(markerClickListener)  // Restore saved listener
        val fabAddPipe = findViewById<FloatingActionButton>(R.id.fabAddPipe)
        fabAddPipe.setImageResource(android.R.drawable.ic_menu_add)
        fabAddPipe.setOnClickListener {
            if (authRepository.getUserRole() == "admin") {
                startPipeDrawingMode()
            }
        }
        val fabCancelPipe = findViewById<FloatingActionButton>(R.id.fabCancelPipe)
        fabCancelPipe.visibility = View.GONE
        hideCrosshair() // HIDE CROSSHAIR
    }

    private fun clearTempMarkers() {
        for (marker in tempMarkers) {
            marker.remove()
        }
        tempMarkers.clear()
    }

    private fun showPipeCreationDialog(pipePointsCopy: List<LatLng>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pipe_creation, null)


        val etStatus = dialogView.findViewById<Spinner>(R.id.spinnerPipeStatus)
        //val etFlow = dialogView.findViewById<EditText>(R.id.etPipeFlow) // Remove - No longer needed
        val etLength = dialogView.findViewById<EditText>(R.id.etPipeLength)
        val etDiameter = dialogView.findViewById<EditText>(R.id.etPipeDiameter)
        val etMaterial = dialogView.findViewById<EditText>(R.id.etPipeMaterial)
        val spinnerFlowDirection = dialogView.findViewById<Spinner>(R.id.spinnerFlowDirection) // Get reference

        // -- Set up Status Spinner
        val pipeStatuses = resources.getStringArray(R.array.pipe_statuses)
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, pipeStatuses)
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        etStatus.adapter = statusAdapter

        // Setup flow direction spinner
        val flowDirections = arrayOf("Start to End", "End to Start") // Human-readable options
        val directionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, flowDirections)
        directionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFlowDirection.adapter = directionAdapter


        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Create New Pipe")
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel") { _, _ ->
                resetPipeDrawingState() // Reset state if canceled
            }
            .create()

        dialog.setOnShowListener {
            val createButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            createButton.setOnClickListener {
                val status = etStatus.selectedItem.toString()
                val length = calculatePipeLength(pipePointsCopy) // Use the copy!
                val diameter = etDiameter.text.toString().toIntOrNull()
                val material = etMaterial.text.toString().trim()
                val flow = spinnerFlowDirection.selectedItemPosition

                if (status.isEmpty()) {
                    Toast.makeText(this, "Status is required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if(pipePointsCopy.size < 2) { //check size of the copy
                    Toast.makeText(this, "You need to select at least two points for a pipe", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val newPipe = Pipe(
                    _id = null,
                    coordinates = pipePointsCopy.map { com.example.deiakwaternetwork.model.Location(it.latitude, it.longitude) }, // Use the *copy*
                    status = status,
                    flow = flow,
                    length = length,
                    diameter = diameter,
                    material = material,
                    createdAt = null,
                    updatedAt = null
                )
                createPipeAndAddToMap(newPipe) //This function remains the same
                dialog.dismiss()
                resetPipeDrawingState() // Reset after creation
            }
        }
        dialog.show()
    }

    private fun createPipeAndAddToMap(pipe: Pipe) {
        lifecycleScope.launch {
            val createdPipe = pipeRepository.createPipe(pipe)
            if (createdPipe != null) {
                addPipeToMap(createdPipe) // Add to map *after* successful creation
                Toast.makeText(this@MainActivity, "Pipe created successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Failed to create pipe", Toast.LENGTH_SHORT).show()
            }
        }
    }


    fun addPipeToMap(pipe: Pipe) {
        Log.d("PipeDebug", "Adding pipe with ID: ${pipe._id}, flow: ${pipe.flow}")

        val points = pipe.coordinates.map { LatLng(it.latitude, it.longitude) }
        val polylineOptions = PolylineOptions()
            .addAll(points)
            .width(10f)
            .color(Color.BLUE)
            .clickable(true)
            .startCap(RoundCap())
            .endCap(RoundCap())

        val polyline = mMap.addPolyline(polylineOptions)
        Log.d("PipeDebug", "  Polyline added: ${polyline.id}")
        pipesMap[polyline] = PipeData(pipe)

        // Set initial visibility based on zoom and chip selection:
        polyline.isVisible = visibleNodeTypes.contains("Pipes") && mMap.cameraPosition.zoom >= pipeVisibilityThresholdZoom

        addArrowMarkers(polyline, pipe.flow)
    }


    private fun addArrowMarkers(polyline: Polyline, flowDirection: Int?) {
        val points = polyline.points
        if (points.size < 2) return

        // Get the PipeData associated with the polyline
        val pipeData = pipesMap[polyline] ?: return // Early return if no PipeData

        // Clear any existing arrow markers *before* adding new ones.
        pipeData.arrowMarkers.forEach { it.remove() }
        pipeData.arrowMarkers.clear()


        val arrowIcon = createArrowIcon(this)

        var distanceSoFar = 0.0
        for (i in 0 until points.size - 1) {
            val start = points[i]
            val end = points[i + 1]
            val segmentDistance = SphericalUtil.computeDistanceBetween(start, end)

            while (distanceSoFar < segmentDistance) {
                val arrowLatLng = SphericalUtil.interpolate(start, end, distanceSoFar / segmentDistance)
                val bearing = SphericalUtil.computeHeading(start, end)
                val adjustedBearing = if (flowDirection == 1) bearing + 180 else bearing

                val marker = mMap.addMarker(  // Store the marker reference!
                    MarkerOptions()
                        .position(arrowLatLng)
                        .icon(arrowIcon)
                        .rotation(adjustedBearing.toFloat())
                        .anchor(0.5f, 0.5f)
                        .flat(true)
                )

                // Add the marker to the list in PipeData
                marker?.let { pipeData.arrowMarkers.add(it) } // Add to the list!

                distanceSoFar += ARROW_SPACING_METERS
            }
            distanceSoFar -= segmentDistance
        }
    }


    private fun showPipeDetailsDialogFromPolyline(polyline: Polyline) {
        val pipeData = pipesMap[polyline] // Get the PipeData object
        if (pipeData != null) {
            showPipeDetailsDialog(pipeData.pipe) // Access the .pipe property
        } else {
            //IMPORTANT
            Log.e("MainActivity", "Polyline has no associated PipeData.")
        }
    }
    private fun createArrowIcon(context: Context): BitmapDescriptor {
        val arrowSizePx = fixedArrowSizePx
        val bitmap = Bitmap.createBitmap(arrowSizePx, arrowSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint().apply {
            this.color = ContextCompat.getColor(context, R.color.deiakBlue) // Use ContextCompat.getColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val path = android.graphics.Path().apply {
            moveTo(arrowSizePx / 2f, 0f)
            lineTo(arrowSizePx.toFloat(), arrowSizePx.toFloat())
            lineTo(0f, arrowSizePx.toFloat())
            close()
        }

        canvas.drawPath(path, paint)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }


    private fun showPipeDetailsDialog(pipe: Pipe) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pipe_details, null)

        //val tvStartNodeValue = dialogView.findViewById<TextView>(R.id.tvStartNodeValue) //Removed
        //val tvEndNodeValue = dialogView.findViewById<TextView>(R.id.tvEndNodeValue)   //Removed
        val tvPipeStatusValue = dialogView.findViewById<TextView>(R.id.tvPipeStatusValue)
        val tvPipeFlowValue = dialogView.findViewById<TextView>(R.id.tvPipeFlowValue)
        val tvPipeLengthValue = dialogView.findViewById<TextView>(R.id.tvPipeLengthValue)
        val tvPipeDiameterValue = dialogView.findViewById<TextView>(R.id.tvPipeDiameterValue)
        val tvPipeMaterialValue = dialogView.findViewById<TextView>(R.id.tvPipeMaterialValue)
        val tvPipeCoordinates = dialogView.findViewById<TextView>(R.id.tvPipeCoordinatesValue) // New TextView for coordinates


        val btnEditPipe = dialogView.findViewById<Button>(R.id.btnEditPipe)
        val btnDeletePipe = dialogView.findViewById<Button>(R.id.btnDeletePipe)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)

        // Set values (handle nulls gracefully)
        //tvStartNodeValue.text = startNode?.name ?: "Unknown" //Removed
        //tvEndNodeValue.text = endNode?.name ?: "Unknown"     // Removed
        tvPipeStatusValue.text = pipe.status
        tvPipeFlowValue.text = pipe.flow.toString()
        tvPipeLengthValue.text = pipe.length?.toString() ?: "N/A"
        tvPipeDiameterValue.text = pipe.diameter?.toString() ?: "N/A"
        tvPipeMaterialValue.text = pipe.material ?: "N/A"

        // Format and display coordinates
        val coordinatesText = pipe.coordinates.joinToString("\n") {
            "(${it.latitude}, ${it.longitude})"
        }
        tvPipeCoordinates.text = coordinatesText


        if (authRepository.getUserRole() == "admin") {
            btnEditPipe.visibility = View.VISIBLE
            btnDeletePipe.visibility = View.VISIBLE
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Pipe Details")
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }

        btnEditPipe.setOnClickListener {
            dialog.dismiss()
            showEditPipeDialog(pipe)
        }

        btnDeletePipe.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete this pipe?")
                .setPositiveButton("Delete") { _, _ ->
                    deletePipeAndRemoveFromMap(pipe)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        dialog.show()
    }


    private fun showEditPipeDialog(pipe: Pipe) {

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pipe_edit, null)

        val etStatus = dialogView.findViewById<Spinner>(R.id.spinnerPipeStatus)
        //val etFlow = dialogView.findViewById<EditText>(R.id.etPipeFlow) // Remove - No longer an EditText
        val etLength = dialogView.findViewById<EditText>(R.id.etPipeLength)
        val etDiameter = dialogView.findViewById<EditText>(R.id.etPipeDiameter)
        val etMaterial = dialogView.findViewById<EditText>(R.id.etPipeMaterial)
        val spinnerFlowDirection = dialogView.findViewById<Spinner>(R.id.spinnerFlowDirection) // Keep this

        // -- Set up Status Spinner (Keep this)
        val pipeStatuses = resources.getStringArray(R.array.pipe_statuses)
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, pipeStatuses)
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        etStatus.adapter = statusAdapter

        // Setup flow direction spinner (same as in creation dialog)
        val flowDirections = arrayOf("Start to End", "End to Start")
        val directionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, flowDirections)
        directionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFlowDirection.adapter = directionAdapter


        // Set initial values (handle nulls)
        etStatus.setSelection(statusAdapter.getPosition(pipe.status))
        //etFlow.setText(pipe.flow.toString()) // REMOVE - We're using the spinner
        etLength.setText(pipe.length?.toString() ?: "")
        etDiameter.setText(pipe.diameter?.toString() ?: "")
        etMaterial.setText(pipe.material ?: "")
        spinnerFlowDirection.setSelection(pipe.flow ?: 0) // Set initial selection, default to 0


        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Edit Pipe")
            .setPositiveButton("Save", null) // Set to null initially
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val updatedStatus = etStatus.selectedItem.toString()
                val updatedFlow = spinnerFlowDirection.selectedItemPosition // Get from spinner
                val updatedLength =  calculatePipeLength(pipePoints)
                val updatedDiameter = etDiameter.text.toString().toIntOrNull()
                val updatedMaterial = etMaterial.text.toString().trim()


                val updatedPipe = pipe.copy(
                    status = updatedStatus,
                    flow = updatedFlow,  // Use the selected direction
                    length = updatedLength, // Use the new calculated length
                    diameter = updatedDiameter,
                    material = updatedMaterial,
                    coordinates = pipePoints.map{com.example.deiakwaternetwork.model.Location(it.latitude, it.longitude)}
                )

                pipe._id?.let { id ->  // Use safe call and let for null safety
                    updatePipeAndRefreshMap(id, updatedPipe)
                }
                dialog.dismiss()

            }
        }
        dialog.show()
    }
    private fun updatePipeAndRefreshMap(pipeId: String, updatedPipe: Pipe) {
        lifecycleScope.launch {
            val result = pipeRepository.updatePipe(pipeId, updatedPipe)
            if (result != null) {
                // Find and remove the old polyline and its markers:
                val entry = pipesMap.entries.find { it.value.pipe._id == pipeId }
                entry?.let { (polyline, pipeData) ->
                    polyline.remove() // Remove old polyline
                    pipesMap.remove(polyline) // Remove from map
                }

                // Remove old arrow markers associated with this pipe.  Crucially, we need
                // to do this *before* adding the new pipe, or we'll have duplicate markers.
                removeAllMarkers() // Remove *all* markers (nodes and arrows)
                addPipeToMap(result) // Add the updated pipe (polyline and arrows)
                refreshAllNodes()  // Now, re-add nodes.  This is the key change.

                // Update local pipe list
                val index = pipeList.indexOfFirst { it._id == pipeId }
                if (index != -1) {
                    pipeList[index] = result
                    pipes = pipeList.toList()
                }
                Toast.makeText(this@MainActivity, "Pipe updated successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Failed to update pipe", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshAllNodes(){
        lifecycleScope.launch {
            nodes = withContext(Dispatchers.IO) { nodeRepository.getNodes() }
            withContext(Dispatchers.Main) {
                nodes?.forEach { node -> addMarkerToMap(node) }
                // Node click listener.  Use the markerClickListener you already have.
                markerClickListener = GoogleMap.OnMarkerClickListener { marker ->
                    val node = nodesMap[marker]
                    if (node != null) {
                        showNodeDetailsDialog(marker)
                        true
                    } else {
                        false
                    }
                }
                mMap.setOnMarkerClickListener(markerClickListener)

                // Polyline click listener - *CRUCIAL* to set this here.
                mMap.setOnPolylineClickListener { polyline ->
                    showPipeDetailsDialogFromPolyline(polyline)
                }
            }

        }

    }


    private fun removeAllMarkers() {
        for (marker in nodesMap.keys) { // Remove node markers
            marker.remove()
        }
        nodesMap.clear()

        //mMap.clear() // Clear *everything*, including polylines  -- REMOVE THIS

        for (marker in tempMarkers) { // Remove temp markers (if any)
            marker.remove()
        }
        tempMarkers.clear()
    }


    private fun deletePipeAndRemoveFromMap(pipe: Pipe) {
        lifecycleScope.launch {
            pipe._id?.let { pipeId ->
                val success = pipeRepository.deletePipe(pipeId)
                if (success) {
                    // Find the polyline and its associated PipeData:
                    val entry = pipesMap.entries.find { it.value.pipe._id == pipeId }
                    entry?.let { (polyline, pipeData) ->
                        // 1. Remove the polyline from the map:
                        polyline.remove()

                        // 2. Remove the entry from pipesMap:
                        pipesMap.remove(polyline)
                    }

                    // 3.  Remove the pipe from the 'pipeList'
                    pipeList.removeIf { it._id == pipeId } // Use removeIf for efficiency
                    pipes = pipeList.toList()  //create a copy of the list.

                    Toast.makeText(this@MainActivity, "Pipe deleted successfully", Toast.LENGTH_SHORT).show()

                } else {
                    Toast.makeText(this@MainActivity, "Failed to delete pipe", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun refreshMapAfterPipeChange(){
        lifecycleScope.launch{
            withContext(Dispatchers.Main){
                mMap.clear() //clear the map

                //re add the filtered nodes
                nodes?.forEach { node ->
                    if (visibleNodeTypes.contains(node.type)) {
                        addMarkerToMap(node)
                    }
                }

                pipes?.forEach{ pipe ->
                    addPipeToMap(pipe) // Re-add pipes
                }

                // IMPORTANT: Re-set the listeners *AFTER* adding markers:
                mMap.setOnMarkerClickListener(markerClickListener)
                mMap.setOnPolylineClickListener { polyline ->
                    showPipeDetailsDialogFromPolyline(polyline)
                }
            }
        }
    }

    // Helper function to calculate the total length of the pipe
    private fun calculatePipeLength(points: List<LatLng>): Double {
        var totalDistance = 0.0
        if (points.size >= 2) {
            for (i in 0 until points.size - 1) {
                val start = points[i]
                val end = points[i + 1]
                val results = FloatArray(1)
                Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
                totalDistance += results[0]
            }
        }
        return totalDistance
    }


    fun interpolateColor(color1: Int, color2: Int, fraction: Float): Int {
        val a1 = Color.alpha(color1)
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)

        val a2 = Color.alpha(color2)
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)

        val a = (a1 + (a2 - a1) * fraction).toInt()
        val r = (r1 + (r2 - r1) * fraction).toInt()
        val g = (g1 + (g2 - g1) * fraction).toInt()
        val b = (b1 + (b2 - b1) * fraction).toInt()

        return Color.argb(a, r, g, b)
    }
    override fun onStop() {
        super.onStop()
        cancellationTokenSource.cancel()
        // Clean up animators to prevent leaks
        polylineAnimators.values.forEach { it.cancel() }
        polylineAnimators.clear()
    }
}