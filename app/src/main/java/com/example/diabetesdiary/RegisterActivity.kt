package com.example.diabetesdiary

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.diabetesdiary.databinding.ActivityMainBinding
import com.example.diabetesdiary.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.toString
import android.app.AlarmManager
import android.os.Build
import android.provider.Settings
import android.content.Context


class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        requestExactAlarmPermissionIfNeeded()  // <- wywołanie funkcji tutaj

        binding.btnRegisterRegister.setOnClickListener {
            val email = binding.etRegisterEmail.text.toString()
            val username = binding.etU.text.toString()
            val password = binding.etRegisterPassword.text.toString()
            val confPassword = binding.etRegisterPassword2.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty() && confPassword.isNotEmpty()) {
                if (password != confPassword) {
                    Toast.makeText(this, "Hasła nie są takie same", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                firebaseAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val currentUser = firebaseAuth.currentUser
                        if (currentUser != null) {
                            val uid = currentUser.uid

                            val user = hashMapOf(
                                "email" to email,
                                "displayName" to username,
                                "userId" to uid
                            )

                            firestore.collection("users").document(uid).set(user)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Poprawnie dodano użytkownika do bazy danych", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(this, HomeActivity::class.java)
                                    intent.putExtra("extra_email", email)
                                    startActivity(intent)
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Błąd zapisu: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        Toast.makeText(this, "Rejestracja nie powiodła się: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Wypełnij wszystkie pola", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // <- Funkcja znajduje się na poziomie klasy
    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }
}
