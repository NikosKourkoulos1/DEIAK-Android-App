package com.example.deiakwaternetwork

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.deiakwaternetwork.data.AuthRepository
import com.example.deiakwaternetwork.model.RegisterRequest
import kotlinx.coroutines.launch
import retrofit2.HttpException

class RegistrationActivity : AppCompatActivity() {
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        supportActionBar?.hide()

        authRepository = AuthRepository(this)

        val nameEditText = findViewById<EditText>(R.id.nameEditText)
        val surnameEditText = findViewById<EditText>(R.id.surnameEditText)
        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val adminCodeEditText = findViewById<EditText>(R.id.adminCodeEditText)
        val registerButton = findViewById<Button>(R.id.registerButton)

        registerButton.setOnClickListener {
            val name = nameEditText.text.toString()
            val surname = surnameEditText.text.toString()
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            val adminCode = adminCodeEditText.text.toString()

            val role = if (adminCode == "admin") "admin" else "user"

            if (validateInput(name, surname, email, password, nameEditText, surnameEditText, emailEditText, passwordEditText)) {
                lifecycleScope.launch {
                    try {
                        val registerRequest = RegisterRequest(name, email, password, role)
                        val registrationSuccessful = authRepository.register(registerRequest)
                        if (registrationSuccessful) {
                            Log.d("RegistrationActivity", "Registration successful")
                            Toast.makeText(this@RegistrationActivity, "Registration successful", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@RegistrationActivity, LoginActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Log.e("RegistrationActivity", "Registration failed (API error)")
                            Toast.makeText(this@RegistrationActivity, "Registration failed", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("RegistrationActivity", "Registration error: ${e.message}", e) // Log the exception with stack trace
                        val errorMessage = when (e) {
                            is HttpException -> "Registration failed: Network error" // More specific error message for HTTP exceptions
                            else -> "Registration failed: ${e.message}"
                        }
                        Toast.makeText(this@RegistrationActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun validateInput(name: String, surname: String, email: String, password: String,
                              nameEditText: EditText, surnameEditText: EditText,
                              emailEditText: EditText, passwordEditText: EditText): Boolean {
        if (name.isBlank()) {
            nameEditText.error = "Name is required"
            return false
        }
        if (surname.isBlank()) {
            surnameEditText.error = "Surname is required"
            return false
        }
        if (email.isBlank()) {
            emailEditText.error = "Email is required"
            return false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Invalid email format"
            return false
        }
        if (password.isBlank()) {
            passwordEditText.error = "Password is required"
            return false
        } else if (password.length < 6) {
            passwordEditText.error = "Password must be at least 6 characters"
            return false
        }
        return true
    }
}