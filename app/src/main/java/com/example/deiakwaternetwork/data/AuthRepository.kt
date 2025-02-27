package com.example.deiakwaternetwork.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.deiakwaternetwork.model.LoginRequest
import com.example.deiakwaternetwork.model.LoginResponse
import com.example.deiakwaternetwork.model.RegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(private val context: Context) {
    private val apiService = RetrofitClient.getApiService(context)
    private lateinit var sharedPrefs: SharedPreferences

    init {
        initializeSharedPrefs()
    }

    // --- Initialization with Error Handling ---
    private fun initializeSharedPrefs() {
        try {
            sharedPrefs = createEncryptedSharedPrefs(context)
            Log.d("AuthRepository", "EncryptedSharedPreferences initialized successfully")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to initialize EncryptedSharedPreferences: ${e.message}", e)
            // Clear the prefs file and retry
            context.getSharedPreferences("deiak_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            try {
                sharedPrefs = createEncryptedSharedPrefs(context)
                Log.d("AuthRepository", "Successfully reinitialized EncryptedSharedPreferences after clearing")
            } catch (e2: Exception) {
                Log.e("AuthRepository", "Failed to reinitialize EncryptedSharedPreferences: ${e2.message}", e2)
                throw RuntimeException("Cannot initialize encrypted storage", e2)
            }
        }
    }

    // --- Helper Function for EncryptedSharedPreferences ---
    private fun createEncryptedSharedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        Log.d("AuthRepository", "Master key alias: $masterKeyAlias")
        return EncryptedSharedPreferences.create(
            "deiak_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- Login Functionality ---
    suspend fun login(loginRequest: LoginRequest): LoginResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.login(loginRequest)
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    loginResponse?.let {
                        storeToken(it.accessToken) // Store accessToken
                        storeRefreshToken(it.refreshToken) // Store refreshToken
                        storeUserRole(it.role)
                    }
                    loginResponse
                } else {
                    Log.e("AuthRepository", "Login error: ${response.code()} ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Login error: ${e.message}", e)
                null
            }
        }
    }

    // --- Register Functionality ---
    suspend fun register(registerRequest: RegisterRequest): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.register(registerRequest)
                Log.d("AuthRepository", "Registration response: ${response.code()} ${response.message()}")

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    Log.e("AuthRepository", "Registration error body: $errorBody")
                }

                response.isSuccessful
            } catch (e: Exception) {
                Log.e("AuthRepository", "Registration error: ${e.message}", e)
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

    private fun storeRefreshToken(refreshToken: String) {
        sharedPrefs.edit().putString("refresh_token", refreshToken).apply()
    }

    fun getRefreshToken(): String? {
        return sharedPrefs.getString("refresh_token", null)
    }

    // --- User Role Handling ---
    private fun storeUserRole(role: String) {
        sharedPrefs.edit().putString("user_role", role).apply()
    }

    fun getUserRole(): String? {
        return sharedPrefs.getString("user_role", null)
    }

    // --- Utility Functions ---
    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    fun logout() {
        sharedPrefs.edit().clear().apply() // Clear all stored data
    }
}