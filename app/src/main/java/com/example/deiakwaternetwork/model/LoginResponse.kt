package com.example.deiakwaternetwork.model

data class LoginResponse(
    val accessToken: String, // Change from 'token'
    val refreshToken: String, // Add refreshToken
    val role: String
)