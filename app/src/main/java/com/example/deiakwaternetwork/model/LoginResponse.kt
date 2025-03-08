package com.example.deiakwaternetwork.model

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val role: String
)