package com.example.deiakwaternetwork

import PipeRepository
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.deiakwaternetwork.data.APIService
import com.example.deiakwaternetwork.data.AuthRepository
import com.example.deiakwaternetwork.model.Location
import com.example.deiakwaternetwork.model.Pipe
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.launch

class PipeManager(
    private val context: Context,
    private val mMap: GoogleMap,
    private val pipeRepository: PipeRepository,
    private val apiService: APIService,
    private val authRepository: AuthRepository,
    private val pipesMap: MutableMap<Polyline, MainActivity.PipeData>,
    private val visibleNodeTypes: MutableList<String>
) {

    private var isDrawingPipe = false
    private var tempPipeLine: Polyline? = null
    private var pipePoints: MutableList<LatLng> = mutableListOf()
    private var tempMarkers: MutableList<Marker> = mutableListOf()
    private val pipeVisibilityThresholdZoom = 15f
    private val visibilityThresholdZoom = 15f
    private val ARROW_SPACING_METERS = 15.0
    private val baseArrowSizeDp = 10
    private val fixedArrowSizePx = (baseArrowSizeDp * context.resources.displayMetrics.density).toInt()
    private lateinit var crosshairImageView: ImageView

    init {
        setupFab()
        addCrosshair()
    }

    private fun setupFab() {
        val fabAddPipe = (context as MainActivity).findViewById<FloatingActionButton>(R.id.fabAddPipe)
        val fabCancelPipe = (context as MainActivity).findViewById<FloatingActionButton>(R.id.fabCancelPipe)
        if (authRepository.getUserRole() == "admin") {
            fabAddPipe.visibility = View.VISIBLE
        }
        fabAddPipe.setOnClickListener {
            if (authRepository.getUserRole() == "admin") {
                startPipeDrawingMode()
            }
        }
        fabCancelPipe.setOnClickListener {
            resetPipeDrawingState()
        }
    }

    private fun addCrosshair() {
        crosshairImageView = ImageView(context)
        crosshairImageView.setImageResource(R.drawable.ic_crosshair)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER
        )
        crosshairImageView.layoutParams = params
        val mapFragment = (context as MainActivity).supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        val mapView = mapFragment.view as? ViewGroup
        mapView?.addView(crosshairImageView)
        crosshairImageView.visibility = View.GONE
    }

    private fun showCrosshair() {
        crosshairImageView.visibility = View.VISIBLE
    }

    private fun hideCrosshair() {
        crosshairImageView.visibility = View.GONE
    }
    private fun startPipeDrawingMode() {
        isDrawingPipe = true
        pipePoints.clear()
        tempPipeLine?.remove()
        tempPipeLine = null
        clearTempMarkers()
        showCrosshair()
        Toast.makeText(context, "Tap on the map to add pipe points.", Toast.LENGTH_LONG).show()

        mMap.setOnMapClickListener { latLng ->
            if (isDrawingPipe) {
                val centerLatLng = mMap.cameraPosition.target // Use crosshair position
                addPointToPipe(centerLatLng)
            }
        }

        val fabAddPipe = (context as MainActivity).findViewById<FloatingActionButton>(R.id.fabAddPipe)
        fabAddPipe.setImageResource(android.R.drawable.ic_menu_save)
        fabAddPipe.setOnClickListener { finishPipeDrawing() }

        val fabCancelPipe = (context as MainActivity).findViewById<FloatingActionButton>(R.id.fabCancelPipe)
        fabCancelPipe.visibility = View.VISIBLE
    }

    private fun finishPipeDrawing() {
        isDrawingPipe = false
        mMap.setOnMapClickListener(null)
        hideCrosshair()
        if (pipePoints.size < 2) {
            Toast.makeText(context, "You need at least two points to create a pipe.", Toast.LENGTH_SHORT).show()
            resetPipeDrawingState()
            return
        }
        val pipePointsCopy = pipePoints.toList()
        val fabAddPipe = (context as MainActivity).findViewById<FloatingActionButton>(R.id.fabAddPipe)
        fabAddPipe.setImageResource(android.R.drawable.ic_menu_add)
        fabAddPipe.setOnClickListener {
            if (authRepository.getUserRole() == "admin") {
                startPipeDrawingMode()
            }
        }
        val fabCancelPipe = (context as MainActivity).findViewById<FloatingActionButton>(R.id.fabCancelPipe)
        fabCancelPipe.visibility = View.GONE
        showPipeCreationDialog(pipePointsCopy)
    }

    private fun addPointToPipe(latLng: LatLng) {
        pipePoints.add(latLng)
        updateTempPolyline()
        addTempMarker(latLng)
    }

    private fun addTempMarker(latLng: LatLng) {
        val marker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                .anchor(0.5f, 0.5f)
        )
        marker?.let { tempMarkers.add(it) }
    }

    private fun updateTempPolyline() {
        tempPipeLine?.remove()
        if (pipePoints.size >= 2) {
            tempPipeLine = mMap.addPolyline(
                PolylineOptions()
                    .addAll(pipePoints)
                    .color(Color.GRAY)
                    .width(5f)
                    .clickable(false)
            )
        }
    }

    private fun resetPipeDrawingState() {
        isDrawingPipe = false
        tempPipeLine?.remove()
        pipePoints.clear()
        clearTempMarkers()
        mMap.setOnMapClickListener(null)
        hideCrosshair()
        val fabAddPipe = (context as MainActivity).findViewById<FloatingActionButton>(R.id.fabAddPipe)
        fabAddPipe.setImageResource(android.R.drawable.ic_menu_add)
        fabAddPipe.setOnClickListener {
            if (authRepository.getUserRole() == "admin") {
                startPipeDrawingMode()
            }
        }
        val fabCancelPipe = (context as MainActivity).findViewById<FloatingActionButton>(R.id.fabCancelPipe)
        fabCancelPipe.visibility = View.GONE
    }

    private fun clearTempMarkers() {
        tempMarkers.forEach { it.remove() }
        tempMarkers.clear()
    }

    fun addPipeToMap(pipe: Pipe) {
        val points = pipe.coordinates.map { LatLng(it.latitude, it.longitude) }
        val polylineOptions = PolylineOptions()
            .addAll(points)
            .width(10f)
            .color(Color.BLUE)
            .clickable(true)
            .startCap(RoundCap())
            .endCap(RoundCap())
        val polyline = mMap.addPolyline(polylineOptions)
        pipesMap[polyline] = MainActivity.PipeData(pipe)
        polyline.isVisible = visibleNodeTypes.contains("Pipes") && mMap.cameraPosition.zoom >= pipeVisibilityThresholdZoom
        addArrowMarkers(polyline, pipe.flow)
    }

    fun updatePipeVisibility(currentZoom: Float) {
        pipesMap.forEach { (polyline, pipeData) ->
            polyline.isVisible = visibleNodeTypes.contains("Pipes") && currentZoom >= pipeVisibilityThresholdZoom
            if (currentZoom >= visibilityThresholdZoom && visibleNodeTypes.contains("Pipes")) {
                addArrowMarkers(polyline, pipeData.pipe.flow)
            } else {
                pipeData.arrowMarkers.forEach { it.isVisible = false }
            }
        }
    }

    private fun addArrowMarkers(polyline: Polyline, flowDirection: Int?) {
        val points = polyline.points
        if (points.size < 2) return
        val pipeData = pipesMap[polyline] ?: return
        pipeData.arrowMarkers.forEach { it.remove() }
        pipeData.arrowMarkers.clear()

        val arrowIcon = createArrowIcon(context)
        var distanceSoFar = 0.0
        for (i in 0 until points.size - 1) {
            val start = points[i]
            val end = points[i + 1]
            val segmentDistance = SphericalUtil.computeDistanceBetween(start, end)
            while (distanceSoFar < segmentDistance) {
                val arrowLatLng = SphericalUtil.interpolate(start, end, distanceSoFar / segmentDistance)
                val bearing = SphericalUtil.computeHeading(start, end)
                val adjustedBearing = if (flowDirection == 1) bearing + 180 else bearing
                val marker = mMap.addMarker(
                    MarkerOptions()
                        .position(arrowLatLng)
                        .icon(arrowIcon)
                        .rotation(adjustedBearing.toFloat())
                        .anchor(0.5f, 0.5f)
                        .flat(true)
                )
                marker?.let { pipeData.arrowMarkers.add(it) }
                distanceSoFar += ARROW_SPACING_METERS
            }
            distanceSoFar -= segmentDistance
        }
    }

    private fun createArrowIcon(context: Context): BitmapDescriptor {
        val arrowSizePx = fixedArrowSizePx
        val bitmap = Bitmap.createBitmap(arrowSizePx, arrowSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        val paint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.deiakBlue)
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

    fun showPipeDetailsDialog(polyline: Polyline) {
        val pipeData = pipesMap[polyline] ?: return
        val pipe = pipeData.pipe
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_pipe_details, null)

        dialogView.findViewById<TextView>(R.id.tvPipeStatusValue).text = pipe.status
        dialogView.findViewById<TextView>(R.id.tvPipeFlowValue).text = pipe.flow.toString()
        dialogView.findViewById<TextView>(R.id.tvPipeLengthValue).text = pipe.length?.toString() ?: "N/A"
        dialogView.findViewById<TextView>(R.id.tvPipeDiameterValue).text = pipe.diameter?.toString() ?: "N/A"
        dialogView.findViewById<TextView>(R.id.tvPipeMaterialValue).text = pipe.material ?: "N/A"
        dialogView.findViewById<TextView>(R.id.tvPipeCoordinatesValue).text = pipe.coordinates.joinToString("\n") {
            "(${it.latitude}, ${it.longitude})"
        }

        val btnEditPipe = dialogView.findViewById<Button>(R.id.btnEditPipe)
        val btnDeletePipe = dialogView.findViewById<Button>(R.id.btnDeletePipe)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)

        if (authRepository.getUserRole() == "admin") {
            btnEditPipe.visibility = View.VISIBLE
            btnDeletePipe.visibility = View.VISIBLE
        }

        val dialog = AlertDialog.Builder(context)
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
            AlertDialog.Builder(context)
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete this pipe?")
                .setPositiveButton("Delete") { _, _ -> deletePipeAndRemoveFromMap(pipe) }
                .setNegativeButton("Cancel", null)
                .show()
        }
        dialog.show()
    }

    private fun showPipeCreationDialog(pipePointsCopy: List<LatLng>) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_pipe_creation, null)
        val etStatus = dialogView.findViewById<Spinner>(R.id.spinnerPipeStatus)
        val etLength = dialogView.findViewById<EditText>(R.id.etPipeLength)
        val etDiameter = dialogView.findViewById<EditText>(R.id.etPipeDiameter)
        val etMaterial = dialogView.findViewById<EditText>(R.id.etPipeMaterial)
        val spinnerFlowDirection = dialogView.findViewById<Spinner>(R.id.spinnerFlowDirection)

        val pipeStatuses = context.resources.getStringArray(R.array.pipe_statuses)
        etStatus.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, pipeStatuses).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val flowDirections = arrayOf("Start to End", "End to Start")
        spinnerFlowDirection.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, flowDirections).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setTitle("Create New Pipe")
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel") { _, _ -> resetPipeDrawingState() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val status = etStatus.selectedItem.toString()
                val length = calculatePipeLength(pipePointsCopy)
                val diameter = etDiameter.text.toString().toIntOrNull()
                val material = etMaterial.text.toString().trim()
                val flow = spinnerFlowDirection.selectedItemPosition

                if (status.isEmpty()) {
                    Toast.makeText(context, "Status is required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (pipePointsCopy.size < 2) {
                    Toast.makeText(context, "You need at least two points for a pipe", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val newPipe = Pipe(
                    _id = null,
                    coordinates = pipePointsCopy.map { Location(it.latitude, it.longitude) },
                    status = status,
                    flow = flow,
                    length = length,
                    diameter = diameter,
                    material = material,
                    createdAt = null,
                    updatedAt = null
                )
                createPipeAndAddToMap(newPipe)
                dialog.dismiss()
                resetPipeDrawingState()
            }
        }
        dialog.show()
    }

    private fun createPipeAndAddToMap(pipe: Pipe) {
        (context as MainActivity).lifecycleScope.launch {
            val createdPipe = pipeRepository.createPipe(pipe)
            if (createdPipe != null) {
                addPipeToMap(createdPipe)
                Toast.makeText(context, "Pipe created successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to create pipe", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditPipeDialog(pipe: Pipe) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_pipe_edit, null)
        val etStatus = dialogView.findViewById<Spinner>(R.id.spinnerPipeStatus)
        val etLength = dialogView.findViewById<EditText>(R.id.etPipeLength)
        val etDiameter = dialogView.findViewById<EditText>(R.id.etPipeDiameter)
        val etMaterial = dialogView.findViewById<EditText>(R.id.etPipeMaterial)
        val spinnerFlowDirection = dialogView.findViewById<Spinner>(R.id.spinnerFlowDirection)

        val pipeStatuses = context.resources.getStringArray(R.array.pipe_statuses)
        etStatus.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, pipeStatuses).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        etStatus.setSelection(pipeStatuses.indexOf(pipe.status))

        val flowDirections = arrayOf("Start to End", "End to Start")
        spinnerFlowDirection.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, flowDirections).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerFlowDirection.setSelection(pipe.flow ?: 0)

        etLength.setText(pipe.length?.toString() ?: "")
        etDiameter.setText(pipe.diameter?.toString() ?: "")
        etMaterial.setText(pipe.material ?: "")

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setTitle("Edit Pipe")
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val updatedStatus = etStatus.selectedItem.toString()
                val updatedFlow = spinnerFlowDirection.selectedItemPosition
                val updatedLength = calculatePipeLength(pipePoints)
                val updatedDiameter = etDiameter.text.toString().toIntOrNull()
                val updatedMaterial = etMaterial.text.toString().trim()

                val updatedPipe = pipe.copy(
                    status = updatedStatus,
                    flow = updatedFlow,
                    length = updatedLength,
                    diameter = updatedDiameter,
                    material = updatedMaterial,
                    coordinates = pipePoints.map { Location(it.latitude, it.longitude) }
                )
                pipe._id?.let { updatePipeAndRefreshMap(it, updatedPipe) }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun updatePipeAndRefreshMap(pipeId: String, updatedPipe: Pipe) {
        (context as MainActivity).lifecycleScope.launch {
            val result = pipeRepository.updatePipe(pipeId, updatedPipe)
            if (result != null) {
                val entry = pipesMap.entries.find { it.value.pipe._id == pipeId }
                entry?.let { (polyline, _) ->
                    polyline.remove()
                    pipesMap.remove(polyline)
                }
                addPipeToMap(result)
                Toast.makeText(context, "Pipe updated successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to update pipe", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deletePipeAndRemoveFromMap(pipe: Pipe) {
        (context as MainActivity).lifecycleScope.launch {
            pipe._id?.let { pipeId ->
                val success = pipeRepository.deletePipe(pipeId)
                if (success) {
                    val entry = pipesMap.entries.find { it.value.pipe._id == pipeId }
                    entry?.let { (polyline, pipeData) ->
                        pipeData.arrowMarkers.forEach { it.remove() }
                        pipeData.arrowMarkers.clear()
                        polyline.remove()
                        pipesMap.remove(polyline)
                    }
                    Toast.makeText(context, "Pipe deleted successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to delete pipe", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun calculatePipeLength(points: List<LatLng>): Double {
        var totalDistance = 0.0
        if (points.size >= 2) {
            for (i in 0 until points.size - 1) {
                val start = points[i]
                val end = points[i + 1]
                val results = FloatArray(1)
                android.location.Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
                totalDistance += results[0]
            }
        }
        return totalDistance
    }
}