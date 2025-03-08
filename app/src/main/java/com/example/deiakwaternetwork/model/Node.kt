package com.example.deiakwaternetwork.model

data class Node(
    val _id: String?,
    val name: String,
    val type: String,
    val location: com.example.deiakwaternetwork.model.Location,
    val capacity: Int?,
    val status: String,
    val description: String,
    val createdAt: String?,
    val updatedAt: String?
)