package com.example.deiakwaternetwork.model

data class Pipe(
    val _id: String?,
    // Removed startNode and endNode
    val coordinates: List<Location>, // List of Location objects
    val status: String,
    val flow: Int,
    val length: Double?,  // Changed to Double
    val diameter: Int?,
    val material: String?,
    val createdAt: String?, // Use String? for consistency
    val updatedAt: String?  // Use String? for consistency
)
