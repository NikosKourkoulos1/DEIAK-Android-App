package com.example.deiakwaternetwork

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

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

        // GPS Location (with permission check)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Request location permission
            return
        }
        mMap.isMyLocationEnabled = true

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12f))
                }
            }
    }
}