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
import android.content.SharedPreferences

class AuthRepository(private val context: Context) { // Pass Context for SharedPreferences
    private val apiService = RetrofitClient.apiService
    private val sharedPrefs = createEncryptedSharedPrefs(context)

    suspend fun login(loginRequest: LoginRequest): LoginResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.login(loginRequest)
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    loginResponse?.let {
                        storeToken(it.token) // Store token on successful login
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

    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    fun logout() {
        sharedPrefs.edit().remove("auth_token").apply()
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