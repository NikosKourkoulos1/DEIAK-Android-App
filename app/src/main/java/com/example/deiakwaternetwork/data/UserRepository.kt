package com.example.deiakwaternetwork.data

import android.content.Context
import android.util.Log
import com.example.deiakwaternetwork.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(private val context: Context) {
    private val apiService = RetrofitClient.create(context) // Initialize apiService

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

    suspend fun getAllUsers(): List<User>? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getAllUsers()
                if (response.isSuccessful) {
                    response.body()
                } else {
                    Log.e("UserRepository", "Error fetching all users: ${response.code()} ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("UserRepository", "Error fetching all users: ${e.message}")
                null
            }
        }
    }

    suspend fun deleteUser(userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.deleteUser(userId)
                response.isSuccessful
            } catch (e: Exception) {
                Log.e("UserRepository", "Error deleting user: ${e.message}")
                false
            }
        }
    }
}