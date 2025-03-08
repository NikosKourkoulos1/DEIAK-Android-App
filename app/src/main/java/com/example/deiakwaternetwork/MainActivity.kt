package com.example.deiakwaternetwork

import NodeRepository
import PipeRepository
import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.deiakwaternetwork.data.*
import com.example.deiakwaternetwork.model.Node
import com.example.deiakwaternetwork.model.Pipe
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnCameraIdleListener {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var settingsClient: SettingsClient
    private lateinit var locationRequest: LocationRequest
    private var cancellationTokenSource = CancellationTokenSource()

    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var nodeRepository: NodeRepository
    private lateinit var pipeRepository: PipeRepository
    private lateinit var apiService: APIService

    private val nodesMap: MutableMap<Marker, Node> = mutableMapOf()
    private val pipesMap: MutableMap<Polyline, PipeData> = mutableMapOf()
    private var visibleNodeTypes: MutableList<String> = mutableListOf()
    private lateinit var chipGroup: ChipGroup

    private lateinit var nodeManager: NodeManager
    private lateinit var pipeManager: PipeManager

    data class PipeData(val pipe: Pipe, val arrowMarkers: MutableList<Marker> = mutableListOf())

    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val REQUEST_CHECK_SETTINGS = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiService = RetrofitClient.getApiService(this)
        setContentView(R.layout.activity_main)

        authRepository = AuthRepository(this)
        userRepository = UserRepository(this)
        nodeRepository = NodeRepository(this)
        pipeRepository = PipeRepository(this)

        if (authRepository.isLoggedIn()) {
            val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayShowTitleEnabled(false)

            val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
            mapFragment.getMapAsync(this)

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            settingsClient = LocationServices.getSettingsClient(this)
            locationRequest = LocationRequest.create().apply {
                interval = 10000
                fastestInterval = 5000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            chipGroup = findViewById(R.id.filterChipGroup)
            chipGroup.visibility = View.GONE
            createFilterChips()

            findViewById<TextView>(R.id.filterTextView).setOnClickListener {
                toggleChipGroupVisibility()
            }
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun toggleChipGroupVisibility() {
        chipGroup.visibility = if (chipGroup.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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
                showUserDetails()
                true
            }
            R.id.action_all_users -> {
                showAllUsers()
                true
            }
            R.id.action_logout -> {
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
                val userId = getUserIdFromToken() ?: return@launch
                val user = userRepository.getUser(userId) ?: run {
                    Toast.makeText(this@MainActivity, "Failed to fetch user details", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("User Details")
                    .setMessage("Name: ${user.name}\nEmail: ${user.email}\nRole: ${user.role}")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .create()
                    .show()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching user details: ${e.message}")
                Toast.makeText(this@MainActivity, "Failed to fetch user details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAllUsers() {
        lifecycleScope.launch {
            try {
                val users = userRepository.getAllUsers() ?: run {
                    Toast.makeText(this@MainActivity, "Failed to fetch users", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val formattedUserList = users.mapIndexed { index, user ->
                    "${index + 1}. Email: ${user.email}, Role: ${user.role}"
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("All Users")
                    .setItems(formattedUserList.toTypedArray()) { _, _ -> }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    .show()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching users: ${e.message}")
                Toast.makeText(this@MainActivity, "Failed to fetch users", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getUserIdFromToken(): String? {
        val token = authRepository.getToken() ?: return null
        return try {
            val payload = String(Base64.decode(token.split(".")[1], Base64.URL_SAFE))
            JSONObject(payload).getString("id")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error decoding token: ${e.message}")
            null
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val corfuBounds = LatLngBounds(LatLng(39.45, 19.7), LatLng(39.8, 20.1))
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(corfuBounds, 0))
        mMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
        mMap.setOnCameraMoveListener {
            if (!corfuBounds.contains(mMap.cameraPosition.target)) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(corfuBounds, 0))
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
        mMap.setOnMyLocationButtonClickListener {
            checkLocationSettingsAndGetCurrentLocation()
            true
        }

        setupMapTypeSpinner()
        nodeManager = NodeManager(this, mMap, nodeRepository, apiService, authRepository, nodesMap, visibleNodeTypes)
        pipeManager = PipeManager(this, mMap, pipeRepository, apiService, authRepository, pipesMap, visibleNodeTypes)

        mMap.setOnMapLoadedCallback {
            // Set marker click listener once and keep it
            mMap.setOnMarkerClickListener { marker ->
                Log.d("MainActivity", "Marker clicked: ${marker.title}")
                nodeManager.showNodeDetailsDialog(marker)
                true
            }
            mMap.setOnPolylineClickListener { polyline ->
                pipeManager.showPipeDetailsDialog(polyline)
            }
            lifecycleScope.launch {
                val nodes = withContext(Dispatchers.IO) { nodeRepository.getNodes() }
                val pipes = withContext(Dispatchers.IO) { pipeRepository.getPipes() }
                withContext(Dispatchers.Main) {
                    nodes?.forEach { nodeManager.addMarkerToMap(it) }
                    pipes?.forEach { pipeManager.addPipeToMap(it) }
                }
            }
        }
        mMap.setOnCameraIdleListener(this)
    }

    private fun setupMapTypeSpinner() {
        val mapTypeSpinner = findViewById<Spinner>(R.id.mapTypeSpinner)
        val mapTypes = arrayOf("Έδαφος", "Δορυφόρος", "Υβριδικό")
        val mapTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mapTypes)
        mapTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mapTypeSpinner.adapter = mapTypeAdapter
        mapTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                mMap.mapType = when (position) {
                    0 -> GoogleMap.MAP_TYPE_TERRAIN
                    1 -> GoogleMap.MAP_TYPE_SATELLITE
                    2 -> GoogleMap.MAP_TYPE_NORMAL
                    else -> GoogleMap.MAP_TYPE_TERRAIN
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                mMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
                mapTypeSpinner.setSelection(0)
            }
        }
    }

    override fun onCameraIdle() {
        val currentZoom = mMap.cameraPosition.zoom
        nodeManager.updateMarkerIcons(currentZoom)
        pipeManager.updatePipeVisibility(currentZoom)
    }

    private fun createFilterChips() {
        val nodeTypes = arrayOf("Κλειδί", "Πυροσβεστικός Κρουνός", "Ταφ", "Γωνία", "Κολεκτέρ", "Παροχή")
        val pipeChip = Chip(this).apply {
            text = "Σωλήνες"
            isCheckable = true
            isChecked = true
            visibleNodeTypes.add("Pipes")
            setOnClickListener { handleChipClick(this) }
        }
        chipGroup.addView(pipeChip)

        nodeTypes.forEach { nodeType ->
            val chip = Chip(this).apply {
                text = nodeType
                isCheckable = true
                isChecked = true
                visibleNodeTypes.add(nodeType)
                setOnClickListener { handleChipClick(this) }
            }
            chipGroup.addView(chip)
        }
    }

    private fun handleChipClick(chip: Chip) {
        val type = chip.text.toString()
        val internalType = if (type == "Σωλήνες") "Pipes" else type
        if (chip.isChecked) {
            if (!visibleNodeTypes.contains(internalType)) {
                visibleNodeTypes.add(internalType)
            }
        } else {
            visibleNodeTypes.remove(internalType)
        }
        nodesMap.forEach { (marker, node) ->
            marker.isVisible = visibleNodeTypes.contains(node.type)
        }
        pipeManager.updatePipeVisibility(mMap.cameraPosition.zoom)
    }

    private fun checkLocationSettingsAndGetCurrentLocation() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        settingsClient.checkLocationSettings(builder.build())
            .addOnSuccessListener { getCurrentLocation() }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(this, REQUEST_CHECK_SETTINGS)
                    } catch (e: IntentSender.SendIntentException) {
                        Log.e("LocationService", "Error getting location settings resolution: ${e.message}")
                    }
                }
            }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f))
                }
            }
            .addOnFailureListener { e -> Log.e("LocationService", "Error getting current location: ${e.message}") }
    }

    override fun onStop() {
        super.onStop()
        cancellationTokenSource.cancel()
    }
}