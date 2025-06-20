package com.example.diabetesdiary

import android.app.*
import android.content.*
import android.os.*
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class CalendarActivity : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var selectedDateText: TextView
    private lateinit var noteInput: EditText
    private lateinit var addNoteButton: Button
    private lateinit var notesListView: ListView
    private lateinit var noNotesTextView: TextView

    private lateinit var adapter: ArrayAdapter<String>
    private val noteDocIds = mutableListOf<String>()

    private var selectedDate: String = ""
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        createNotificationChannel()

        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener { finish() }

        calendarView = findViewById(R.id.calendarView)
        selectedDateText = findViewById(R.id.selectedDateText)
        noteInput = findViewById(R.id.noteInput)
        addNoteButton = findViewById(R.id.addNoteButton)
        notesListView = findViewById(R.id.notesListView)
        noNotesTextView = findViewById(R.id.noNotesTextView)

        selectedDate = formatDate(calendarView.date)
        selectedDateText.text = "Wybrana data: $selectedDate"

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        notesListView.adapter = adapter

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
            selectedDateText.text = "Wybrana data: $selectedDate"
            loadNotesForDate(selectedDate)
        }

        addNoteButton.setOnClickListener {
            val noteText = noteInput.text.toString().trim()
            if (noteText.isNotEmpty()) {
                askForTimeAndAddNote(noteText)
            } else {
                Toast.makeText(this, "Wpisz treść notatki", Toast.LENGTH_SHORT).show()
            }
        }

        notesListView.setOnItemClickListener { _, _, position, _ ->
            val noteId = noteDocIds[position]
            showEditOrDeleteDialog(noteId)
        }

        loadNotesForDate(selectedDate)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "diabetes_channel",
                "Diabetes Reminder Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kanał powiadomień przypomnień o notatkach"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatDate(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
    }

    private fun askForTimeAndAddNote(noteText: String) {
        val timeInput = EditText(this).apply {
            hint = "Godzina (HH:mm)"
            inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
        }

        AlertDialog.Builder(this)
            .setTitle("Dodaj godzinę")
            .setView(timeInput)
            .setPositiveButton("OK") { _, _ ->
                val time = timeInput.text.toString().trim()
                if (time.isNotEmpty() && !isValidTime(time)) {
                    Toast.makeText(this, "Nieprawidłowa godzina", Toast.LENGTH_SHORT).show()
                } else {
                    addNoteForDateFirestore(selectedDate, noteText, time)
                }
            }
            .setNegativeButton("Pomiń") { _, _ ->
                addNoteForDateFirestore(selectedDate, noteText, "")
            }
            .show()
    }

    private fun isValidTime(time: String): Boolean {
        return try {
            SimpleDateFormat("HH:mm", Locale.getDefault()).parse(time)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun addNoteForDateFirestore(date: String, text: String, time: String) {
        val user = auth.currentUser ?: return

        val alarmRequestCode = UUID.randomUUID().hashCode()

        val noteData = hashMapOf(
            "date" to date,
            "time" to time,
            "text" to text,
            "timestamp" to Timestamp.now(),
            "userId" to user.uid,
            "alarmRequestCode" to alarmRequestCode
        )

        db.collection("notes")
            .add(noteData)
            .addOnSuccessListener { docRef ->
                Toast.makeText(this, "Notatka dodana", Toast.LENGTH_SHORT).show()
                noteInput.text.clear()
                loadNotesForDate(date)

                if (time.isNotEmpty()) {
                    scheduleNotification(this, text, date, time, alarmRequestCode)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Błąd dodawania notatki", Toast.LENGTH_SHORT).show()
                Log.e("CalendarActivity", "Błąd dodawania notatki", e)
            }
    }

    private fun loadNotesForDate(date: String) {
        val user = auth.currentUser ?: return

        db.collection("notes")
            .whereEqualTo("userId", user.uid)
            .whereEqualTo("date", date)
            .get()
            .addOnSuccessListener { docs ->
                val notePairs = mutableListOf<Pair<String, String>>()
                noteDocIds.clear()

                for (doc in docs) {
                    val time = doc.getString("time") ?: ""
                    val text = doc.getString("text") ?: ""
                    val display = if (time.isNotEmpty()) "$time - $text" else text
                    notePairs.add(Pair(time, display))
                    noteDocIds.add(doc.id)
                }

                notePairs.sortBy { if (it.first.isEmpty()) "99:99" else it.first }

                adapter.clear()
                adapter.addAll(notePairs.map { it.second })
                adapter.notifyDataSetChanged()
                noNotesTextView.visibility = if (notePairs.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                Log.e("CalendarActivity", "Błąd ładowania notatek", e)
            }
    }

    private fun showEditOrDeleteDialog(noteId: String) {
        AlertDialog.Builder(this)
            .setTitle("Co chcesz zrobić?")
            .setItems(arrayOf("Edytuj", "Usuń")) { _, which ->
                when (which) {
                    0 -> editNoteDialog(noteId)
                    1 -> deleteNoteById(noteId)
                }
            }.show()
    }

    private fun editNoteDialog(noteId: String) {
        db.collection("notes").document(noteId).get()
            .addOnSuccessListener { doc ->
                val oldText = doc.getString("text") ?: ""
                val oldTime = doc.getString("time") ?: ""
                val alarmRequestCode = doc.getLong("alarmRequestCode")?.toInt() ?: -1

                val layout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(50, 40, 50, 10)
                }

                val inputText = EditText(this).apply { setText(oldText) }
                val inputTime = EditText(this).apply {
                    hint = "Godzina (HH:mm)"
                    inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
                    setText(oldTime)
                }

                layout.addView(inputText)
                layout.addView(inputTime)

                AlertDialog.Builder(this)
                    .setTitle("Edytuj notatkę")
                    .setView(layout)
                    .setPositiveButton("Zapisz") { _, _ ->
                        val newText = inputText.text.toString().trim()
                        val newTime = inputTime.text.toString().trim()

                        if (newText.isEmpty()) {
                            Toast.makeText(this, "Treść nie może być pusta", Toast.LENGTH_SHORT).show()
                        } else if (newTime.isNotEmpty() && !isValidTime(newTime)) {
                            Toast.makeText(this, "Nieprawidłowa godzina", Toast.LENGTH_SHORT).show()
                        } else {
                            db.collection("notes").document(noteId)
                                .update("text", newText, "time", newTime)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Zmieniono notatkę", Toast.LENGTH_SHORT).show()
                                    loadNotesForDate(selectedDate)
                                    if (alarmRequestCode != -1) deleteNotification(this, alarmRequestCode)
                                    if (newTime.isNotEmpty()) {
                                        val newAlarmRequestCode = UUID.randomUUID().hashCode()
                                        db.collection("notes").document(noteId)
                                            .update("alarmRequestCode", newAlarmRequestCode)
                                            .addOnSuccessListener {
                                                scheduleNotification(this, newText, selectedDate, newTime, newAlarmRequestCode)
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("CalendarActivity", "Błąd aktualizacji alarmRequestCode", e)
                                            }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("CalendarActivity", "Błąd aktualizacji notatki", e)
                                }
                        }
                    }
                    .setNegativeButton("Anuluj", null)
                    .show()
            }
            .addOnFailureListener { e ->
                Log.e("CalendarActivity", "Błąd pobierania notatki do edycji", e)
            }
    }

    private fun deleteNoteById(noteId: String) {
        db.collection("notes").document(noteId).get()
            .addOnSuccessListener { doc ->
                val alarmRequestCode = doc.getLong("alarmRequestCode")?.toInt() ?: -1

                db.collection("notes").document(noteId).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Notatka usunięta", Toast.LENGTH_SHORT).show()
                        loadNotesForDate(selectedDate)
                        if (alarmRequestCode != -1) {
                            deleteNotification(this, alarmRequestCode)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("CalendarActivity", "Błąd usuwania notatki", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("CalendarActivity", "Błąd pobierania notatki do usunięcia", e)
            }
    }

    private fun scheduleNotification(
        context: Context,
        noteText: String,
        date: String,
        time: String,
        requestCode: Int
    ) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val triggerTime = try {
            sdf.parse("$date $time")?.time ?: return
        } catch (e: Exception) {
            Log.e("CalendarActivity", "Błąd parsowania daty do powiadomienia", e)
            return
        }

        if (triggerTime < System.currentTimeMillis()) {
            Log.w("CalendarActivity", "Czas powiadomienia jest w przeszłości, nie ustawiam alarmu.")
            return
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("noteText", noteText)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        Log.d("CalendarActivity", "Ustawiono powiadomienie na $date $time (requestCode=$requestCode)")
    }

    private fun deleteNotification(context: Context, requestCode: Int) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        Log.d("CalendarActivity", "Usunięto powiadomienie o requestCode=$requestCode")
    }
}
