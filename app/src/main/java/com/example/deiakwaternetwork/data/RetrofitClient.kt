package com.example.deiakwaternetwork.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor // Import HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object RetrofitClient {
    private const val BASE_URL = "https://deiak-rendbackend.onrender.com/"
    //private const val BASE_URL = "http://192.168.2.5:3000/"

    private lateinit var apiService: APIService
    private lateinit var masterKey: MasterKey

    fun getMasterKey(context: Context): MasterKey {
        if (!::masterKey.isInitialized) {
            masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }
        return masterKey
    }

    fun getApiService(context: Context): APIService {
        if (!::apiService.isInitialized) {
            initialize(context)
        }
        return apiService
    }

    private fun initialize(context: Context) {
        // Add logging interceptor
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Log request/response bodies
        }

        val okHttpClient: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthorizationInterceptor(context))
            .addInterceptor(loggingInterceptor) // Add the logging interceptor
            .build()

        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(APIService::class.java)
    }
}

class AuthorizationInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = getAuthTokenFromStorage()

        Log.d("AuthorizationInterceptor", "Retrieved token: $token") // Log the retrieved token

        val newRequest = if (token != null) {
            Log.d("AuthorizationInterceptor", "Adding token to request: Bearer $token")
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            Log.d("AuthorizationInterceptor", "No token found") // Log if no token is found
            request
        }

        return chain.proceed(newRequest)
    }

    private fun getAuthTokenFromStorage(): String? {
        // Access masterKey through RetrofitClient's function
        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "deiak_prefs",
            RetrofitClient.getMasterKey(context), // Use the function to get masterKey
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return sharedPreferences.getString("auth_token", null)
    }
}