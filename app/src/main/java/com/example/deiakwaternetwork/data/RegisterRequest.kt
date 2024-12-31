package com.example.deiakwaternetwork.data

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: String
)
