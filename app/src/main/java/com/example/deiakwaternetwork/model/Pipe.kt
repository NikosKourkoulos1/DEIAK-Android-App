package com.example.deiakwaternetwork.model

data class Pipe(
    val _id: String?,
    // Removed startNode and endNode
    val coordinates: List<Location>,
    val status: String,
    val flow: Int,
    val length: Double?,
    val diameter: Int?,
    val material: String?,
    val createdAt: String?,
    val updatedAt: String?
)
