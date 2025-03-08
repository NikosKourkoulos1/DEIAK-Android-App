package com.example.deiakwaternetwork

import NodeRepository
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.example.deiakwaternetwork.data.APIService
import com.example.deiakwaternetwork.data.AuthRepository
import com.example.deiakwaternetwork.model.Location
import com.example.deiakwaternetwork.model.Node
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NodeManager(
    private val context: Context,
    private val mMap: GoogleMap,
    private val nodeRepository: NodeRepository,
    private val apiService: APIService,
    private val authRepository: AuthRepository,
    private val nodesMap: MutableMap<Marker, Node>,
    private val visibleNodeTypes: MutableList<String>
) {

    private var isAddingNode = false
    private var isMovingNode = false
    private var nodeToMoveId: String? = null
    private var nodeToMoveMarker: Marker? = null
    private lateinit var crosshairImageView: ImageView

    private val baseIconSizeDp = 40
    private val visibilityThresholdZoom = 15f
    private val fixedIconSizePx = (baseIconSizeDp * context.resources.displayMetrics.density).toInt()
    private val initialIconSizeDp = 7
    private val maxIconSizeDp = 40
    private val initialIconSizePx = (initialIconSizeDp * context.resources.displayMetrics.density).toInt()
    private val maxIconSizePx = (maxIconSizeDp * context.resources.displayMetrics.density).toInt()
    private val zoomScaleFactor = 3.0f

    init {
        setupFab()
        addCrosshair()
    }

    private fun setupFab() {
        val fabAddNode = (context as MainActivity).findViewById<FloatingActionButton>(R.id.fabAddNode)
        if (authRepository.getUserRole() == "admin") {
            fabAddNode.visibility = View.VISIBLE
        }
        fabAddNode.setOnClickListener {
            handleFabClick()
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

    private fun handleFabClick() {
        val fabAddNode = (context as MainActivity).findViewById<FloatingActionButton>(R.id.fabAddNode)
        if (!isAddingNode && !isMovingNode) {
            isAddingNode = true
            showCrosshair()
            fabAddNode.setImageResource(android.R.drawable.ic_menu_save)
            Toast.makeText(context, "Position the crosshair and tap the button again to add a node.", Toast.LENGTH_LONG).show()
        } else if (isAddingNode) {
            val centerLatLng = mMap.cameraPosition.target
            showNodeCreationDialog(centerLatLng)
            hideCrosshair()
            isAddingNode = false
            fabAddNode.setImageResource(android.R.drawable.ic_menu_add)
        } else if (isMovingNode) {
            val newLatLng = mMap.cameraPosition.target
            nodeToMoveId?.let { nodeId ->
                val updatedNode = nodesMap[nodeToMoveMarker]?.copy(location = Location(newLatLng.latitude, newLatLng.longitude))
                if (updatedNode != null) {
                    updateNodeInBackend(nodeId, updatedNode, nodeToMoveMarker!!)
                }
            }
            hideCrosshair()
            isMovingNode = false
            nodeToMoveId = null
            nodeToMoveMarker = null
            fabAddNode.setImageResource(android.R.drawable.ic_menu_add)
        }
    }

    fun addMarkerToMap(node: Node) {
        val latLng = LatLng(node.location.latitude, node.location.longitude)
        val markerIconResource = getMarkerIconResource(node.type) ?: run {
            Log.e("NodeManager", "Unrecognized node type: ${node.type}, using default icon")
            R.drawable.ic_crosshair // Fallback to default icon
        }
        val markerIcon = createBitmapDescriptor(markerIconResource, fixedIconSizePx, fixedIconSizePx)
        val marker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(node.name)
                .icon(markerIcon)
                .anchor(0.5f, 0.5f)
        )
        marker?.let {
            nodesMap[it] = node
            it.isVisible = visibleNodeTypes.contains(node.type) && mMap.cameraPosition.zoom >= visibilityThresholdZoom
        }
    }

    fun updateMarkerIcons(currentZoom: Float) {
        val nodeMarkersVisible = currentZoom >= visibilityThresholdZoom
        nodesMap.forEach { (marker, node) ->
            if (nodeMarkersVisible && visibleNodeTypes.contains(node.type)) {
                marker.isVisible = true
                val iconResource = getMarkerIconResource(node.type) ?: run {
                    Log.e("NodeManager", "Unrecognized node type: ${node.type}, using default icon")
                    R.drawable.ic_crosshair // Fallback to default icon
                }
                val scaledIcon = getScaledMarkerIcon(iconResource, node.type, currentZoom)
                marker.setIcon(scaledIcon)
            } else {
                marker.isVisible = false
            }
        }
    }

    private fun getMarkerIconResource(nodeType: String): Int? {
        return when (nodeType) {
            "Κλειδί" -> R.drawable.kleidi_icon
            "Πυροσβεστικός Κρουνός" -> R.drawable.krounos_icon
            "Ταφ" -> R.drawable.taf_icon
            "Γωνία" -> R.drawable.gonia_icon
            "Κολεκτέρ" -> R.drawable.kolekter_icon
            "Παροχή" -> R.drawable.paroxi_icon
            else -> null
        }
    }

    private fun createBitmapDescriptor(iconResource: Int, width: Int, height: Int): BitmapDescriptor {
        val drawable = ResourcesCompat.getDrawable(context.resources, iconResource, null)
        return if (drawable is VectorDrawableCompat || drawable is android.graphics.drawable.VectorDrawable) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        } else {
            val originalBitmap = BitmapFactory.decodeResource(context.resources, iconResource)
            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, false)
            BitmapDescriptorFactory.fromBitmap(resizedBitmap)
        }
    }

    private fun getScaledMarkerIcon(iconResource: Int, nodeType: String, currentZoom: Float): BitmapDescriptor {
        val zoomDifference = (currentZoom - visibilityThresholdZoom).coerceAtLeast(0f)
        val scaleFactor = (1 + zoomDifference * zoomScaleFactor).coerceIn(1f, 5f)
        val scaledWidth = (initialIconSizePx * scaleFactor).toInt().coerceAtMost(maxIconSizePx)
        val scaledHeight = (initialIconSizePx * scaleFactor).toInt().coerceAtMost(maxIconSizePx)
        return createBitmapDescriptor(iconResource, scaledWidth, scaledHeight)
    }

    fun showNodeDetailsDialog(marker: Marker) {
        val node = nodesMap[marker] ?: return
        val builder = AlertDialog.Builder(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_node_details, null)

        view.findViewById<TextView>(R.id.tvNodeNameValue).text = node.name
        view.findViewById<TextView>(R.id.tvNodeTypeValue).text = node.type
        view.findViewById<TextView>(R.id.tvNodeLocationValue).text = "${node.location.latitude}, ${node.location.longitude}"
        view.findViewById<TextView>(R.id.tvNodeCapacityValue).text = node.capacity?.toString() ?: "N/A"
        view.findViewById<TextView>(R.id.tvNodeStatusValue).text = node.status
        view.findViewById<TextView>(R.id.tvNodeDescriptionValue).text = node.description

        val btnEditNode = view.findViewById<Button>(R.id.btnEditNode)
        val btnDeleteNode = view.findViewById<Button>(R.id.btnDeleteNode)
        val btnClose = view.findViewById<Button>(R.id.btnClose)

        if (authRepository.getUserRole() == "admin") {
            btnEditNode.visibility = View.VISIBLE
            btnDeleteNode.visibility = View.VISIBLE
        }

        builder.setView(view).setTitle("Node Details")
        val dialog = builder.create()

        btnClose.setOnClickListener { dialog.dismiss() }
        btnEditNode.setOnClickListener {
            dialog.dismiss()
            showEditNodeDialog(node, marker)
        }
        btnDeleteNode.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(context)
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete this node?")
                .setPositiveButton("Delete") { _, _ -> deleteNode(node, marker) }
                .setNegativeButton("Cancel", null)
                .show()
        }
        dialog.show()
    }

    private fun showNodeCreationDialog(latLng: LatLng) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_node_creation, null)
        val etNodeName = dialogView.findViewById<EditText>(R.id.etNodeName)
        val spinnerNodeType = dialogView.findViewById<Spinner>(R.id.spinnerNodeType)
        val etNodeCapacity = dialogView.findViewById<EditText>(R.id.etNodeCapacity)
        val spinnerNodeStatus = dialogView.findViewById<Spinner>(R.id.spinnerNodeStatus)
        val etNodeDescription = dialogView.findViewById<EditText>(R.id.etNodeDescription)

        val nodeTypes = arrayOf("Κλειδί", "Πυροσβεστικός Κρουνός", "Ταφ", "Γωνία", "Κολεκτέρ", "Παροχή")
        spinnerNodeType.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, nodeTypes).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val nodeStatuses = context.resources.getStringArray(R.array.node_statuses)
        spinnerNodeStatus.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, nodeStatuses).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setTitle("Create New Node")
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etNodeName.text.toString().trim()
                val type = spinnerNodeType.selectedItem.toString()
                if (name.isEmpty()) {
                    etNodeName.error = "Name is required"
                    return@setOnClickListener
                }
                if (type.isEmpty()) {
                    Toast.makeText(context, "Please select a node type", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val capacity = etNodeCapacity.text.toString().toIntOrNull()
                val status = spinnerNodeStatus.selectedItem.toString()
                val description = etNodeDescription.text.toString()

                val newNode = Node(
                    _id = null, name = name, type = type,
                    location = Location(latLng.latitude, latLng.longitude),
                    capacity = capacity, status = status, description = description,
                    createdAt = "", updatedAt = ""
                )
                createNodeAndAddMarker(newNode)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun createNodeAndAddMarker(node: Node) {
        (context as MainActivity).lifecycleScope.launch {
            val createdNode = nodeRepository.createNode(node)
            if (createdNode != null) {
                addMarkerToMap(createdNode)
                Toast.makeText(context, "Node created successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to create node", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditNodeDialog(node: Node, marker: Marker) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_node_edit, null)
        val etNodeName = dialogView.findViewById<EditText>(R.id.etNodeName)
        val spinnerNodeType = dialogView.findViewById<Spinner>(R.id.spinnerNodeType)
        val etNodeCapacity = dialogView.findViewById<EditText>(R.id.etNodeCapacity)
        val spinnerNodeStatus = dialogView.findViewById<Spinner>(R.id.spinnerNodeStatus)
        val etNodeDescription = dialogView.findViewById<EditText>(R.id.etNodeDescription)
        val btnMoveNode = dialogView.findViewById<Button>(R.id.btnMoveNode)

        val nodeTypes = arrayOf("Κλειδί", "Πυροσβεστικός Κρουνός", "Ταφ", "Γωνία", "Κολεκτέρ", "Παροχή")
        spinnerNodeType.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, nodeTypes).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerNodeType.setSelection(nodeTypes.indexOf(node.type))

        val nodeStatuses = context.resources.getStringArray(R.array.node_statuses)
        spinnerNodeStatus.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, nodeStatuses).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerNodeStatus.setSelection(nodeStatuses.indexOf(node.status))

        etNodeName.setText(node.name)
        etNodeCapacity.setText(node.capacity?.toString() ?: "")
        etNodeDescription.setText(node.description)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setTitle("Edit Node")
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val updatedName = etNodeName.text.toString().trim()
                val updatedType = spinnerNodeType.selectedItem.toString()
                val updatedCapacity = etNodeCapacity.text.toString().toIntOrNull()
                val updatedStatus = spinnerNodeStatus.selectedItem.toString()
                val updatedDescription = etNodeDescription.text.toString()

                if (updatedName.isEmpty() || updatedType.isEmpty()) {
                    Toast.makeText(context, "Name and type are required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val updatedNode = node.copy(
                    name = updatedName, type = updatedType,
                    capacity = updatedCapacity, status = updatedStatus, description = updatedDescription
                )
                node._id?.let { updateNodeInBackend(it, updatedNode, marker) }
                dialog.dismiss()
            }
            btnMoveNode.setOnClickListener {
                dialog.dismiss()
                isMovingNode = true
                nodeToMoveId = node._id
                nodeToMoveMarker = marker
                showCrosshair()
                val fabAddNode = (context as MainActivity).findViewById<FloatingActionButton>(R.id.fabAddNode)
                fabAddNode.setImageResource(android.R.drawable.ic_menu_save)
                Toast.makeText(context, "Position the crosshair and tap the button again to move the node.", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    private fun updateNodeInBackend(nodeId: String, updatedNode: Node, marker: Marker) {
        (context as MainActivity).lifecycleScope.launch {
            val updated = nodeRepository.updateNode(nodeId, updatedNode)
            if (updated != null) {
                marker.position = LatLng(updated.location.latitude, updated.location.longitude)
                nodesMap[marker] = updated
                Toast.makeText(context, "Node updated successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to update node", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteNode(node: Node, marker: Marker) {
        (context as MainActivity).lifecycleScope.launch {
            val success = nodeRepository.deleteNode(node._id!!)
            if (success) {
                marker.remove()
                nodesMap.remove(marker)
                Toast.makeText(context, "Node deleted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to delete node", Toast.LENGTH_SHORT).show()
            }
        }
    }
}