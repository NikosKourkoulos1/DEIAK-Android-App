package com.example.deiakwaternetwork.model

data class Pipe(
    val _id: String,
    val startNode: String, // Assuming you'll store Node _id
    val endNode: String,   // Assuming you'll store Node _id
    val status: String,
    val flow: Int,
    val length: Int?,
    val diameter: Int?,
    val material: String?,
    val createdAt: String, // Adjust type if needed
    val updatedAt: String  // Adjust type if needed
)
