package com.example.deiakwaternetwork.model

data class Node(
    val name: String,
    val type: String,
    val location: Location,
    val capacity: Int?,
    val status: String,
    val description: String,
    val createdAt: String, // Adjust type if needed
    val updatedAt: String  // Adjust type if needed
)