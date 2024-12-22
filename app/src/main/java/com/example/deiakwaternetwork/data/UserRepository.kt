package com.example.deiakwaternetwork.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.deiakwaternetwork.model.LoginRequest
import com.example.deiakwaternetwork.model.LoginResponse
import com.example.deiakwaternetwork.model.RegisterRequest
import com.example.deiakwaternetwork.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class UserRepository(private val context: Context) { // Pass Context for SharedPreferences
    private val apiService = RetrofitClient.apiService

    suspend fun getUser(userId: String): User? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getUser(userId)
                if (response.isSuccessful) {
                    response.body()
                } else {
                    Log.e("UserRepository", "Error fetching user: ${response.code()} ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("UserRepository", "Error fetching user: ${e.message}")
                null
            }
        }
    }

    suspend fun updateUser(userId: String, user: User): User? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.updateUser(userId, user)
                if (response.isSuccessful) {
                    response.body()
                } else {
                    Log.e("UserRepository", "Error updating user: ${response.code()} ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("UserRepository", "Error updating user: ${e.message}")
                null
            }
        }
    }
}
