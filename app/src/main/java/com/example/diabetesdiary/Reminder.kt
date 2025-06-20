package com.example.diabetesdiary
import com.google.firebase.Timestamp

data class Reminder(
    var userId: String = "",
    var mealName: String = "",
    var hour: Int = 0,
    var minute: Int = 0,
    var timestamp: Timestamp? = null,
    var id: String = ""  // ID dokumentu Firestore (opcjonalne)
)

