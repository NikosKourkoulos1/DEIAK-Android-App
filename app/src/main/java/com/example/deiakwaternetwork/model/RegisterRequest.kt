package com.example.deiakwaternetwork.model

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: String
)
