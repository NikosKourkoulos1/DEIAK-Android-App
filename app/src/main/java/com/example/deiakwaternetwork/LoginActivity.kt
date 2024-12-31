package com.example.deiakwaternetwork

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.deiakwaternetwork.data.AuthRepository
import com.example.deiakwaternetwork.model.LoginRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL


class LoginActivity : AppCompatActivity() {
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        supportActionBar?.hide()

        authRepository = AuthRepository(this)

        //button to test connection
        val testConnectionButton = findViewById<Button>(R.id.testConnectionButton)
        testConnectionButton.setOnClickListener {
            lifecycleScope.launch {
                testConnection()
            }
        }


        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val registerTextView = findViewById<TextView>(R.id.registerTextView)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (validateInput(email, password)) {
                lifecycleScope.launch {
                    val loginRequest = LoginRequest(email, password)
                    val loginResponse = authRepository.login(loginRequest)
                    if (loginResponse != null) {
                        // Login successful
                        Log.d("LoginActivity", "Login successful: ${loginResponse.token}")
                        // Start MainActivity or the appropriate Activity
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish() // Optional: Finish LoginActivity to prevent going back
                    } else {
                        // Login failed
                        Toast.makeText(this@LoginActivity, "Login failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        registerTextView.setOnClickListener {
            startActivity(Intent(this, RegistrationActivity::class.java))
        }
    }

   //test connection button on login || also added on activity_login.xml
    private suspend fun testConnection() {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("http://192.168.2.5:3000/") // Your backend URL
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Connection successful
                    Log.d("MainActivity", "Connection successful")
                    runOnUiThread {  // Update UI on the main thread
                        Toast.makeText(this@LoginActivity, "Connection successful", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Connection failed
                    Log.e("MainActivity", "Connection failed: $responseCode")
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Connection failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Connection error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun validateInput(email: String, password: String): Boolean {
        // Implement your input validation logic here
        // ...
        return true // Return true if input is valid, false otherwise
    }
}