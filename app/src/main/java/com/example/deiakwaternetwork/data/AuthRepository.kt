package com.example.deiakwaternetwork.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.deiakwaternetwork.model.LoginRequest
import com.example.deiakwaternetwork.model.LoginResponse
import com.example.deiakwaternetwork.model.RegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.SharedPreferences

class AuthRepository(private val context: Context) {
    private val apiService = RetrofitClient.apiService
    private val sharedPrefs = createEncryptedSharedPrefs(context)

    suspend fun login(loginRequest: LoginRequest): LoginResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.login(loginRequest)
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    loginResponse?.let {
                        storeToken(it.token)
                        storeUserRole(it.role) // Store the role directly
                    }
                    loginResponse
                } else {
                    Log.e("AuthRepository", "Login error: ${response.code()} ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Login error: ${e.message}")
                null
            }
        }
    }


    suspend fun register(registerRequest: RegisterRequest): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.register(registerRequest)

                // Log the response code and message
                Log.d("AuthRepository", "Registration response: ${response.code()} ${response.message()}")

                // If the response is not successful, log the error body
                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    Log.e("AuthRepository", "Registration error body: $errorBody")
                }

                response.isSuccessful
            } catch (e: Exception) {
                Log.e("AuthRepository", "Registration error: ${e.message}")
                false
            }
        }
    }
    // --- Token Handling ---

    private fun storeToken(token: String) {
        sharedPrefs.edit().putString("auth_token", token).apply()
    }

    fun getToken(): String? {
        return sharedPrefs.getString("auth_token", null)
    }

    // --- User Role Handling ---

    private fun storeUserRole(role: String) {
        sharedPrefs.edit().putString("user_role", role).apply()
    }

    fun getUserRole(): String? {
        return sharedPrefs.getString("user_role", null)
    }

    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    fun logout() {
        sharedPrefs.edit().clear().apply() // Clear all stored data
    }

    // --- Helper Function for EncryptedSharedPreferences ---

    private fun createEncryptedSharedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "deiak_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}