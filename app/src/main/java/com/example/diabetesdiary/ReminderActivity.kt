package com.example.diabetesdiary

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.*

class ReminderActivity : AppCompatActivity() {

    private lateinit var switchEnableReminders: Switch
    private lateinit var editMealName: EditText
    private lateinit var timePicker: TimePicker
    private lateinit var btnSetReminder: Button
    private lateinit var listViewMeals: ListView
    private lateinit var backButton: ImageButton

    private val remindersList = mutableListOf<String>()
    private val remindersData = mutableListOf<Reminder>()
    private lateinit var adapter: ArrayAdapter<String>

    private val firestore = FirebaseFirestore.getInstance()
    private val remindersCollection = firestore.collection("reminders")

    private val auth = FirebaseAuth.getInstance()

    private val PREFS_NAME = "reminder_prefs"
    private val KEY_REMINDERS_ENABLED = "reminders_enabled"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder)

        // Sprawdź uprawnienia do ustawiania dokładnych alarmów (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }

        // Inicjalizacja widoków
        switchEnableReminders = findViewById(R.id.switchEnableReminders)
        editMealName = findViewById(R.id.editMealName)
        timePicker = findViewById(R.id.timePicker)
        btnSetReminder = findViewById(R.id.btnSetReminder)
        listViewMeals = findViewById(R.id.listViewMeals)
        backButton = findViewById(R.id.backButton)

        backButton.setOnClickListener { finish() }

        // Usuwanie przypomnienia po długim kliknięciu na element listy
        listViewMeals.setOnItemLongClickListener { _, _, position, _ ->
            val reminderToDelete = remindersData[position]
            deleteReminder(reminderToDelete)
            true
        }

        setupTimePickerStyle()

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, remindersList)
        listViewMeals.adapter = adapter

        // Wczytaj i ustaw stan przełącznika przypomnień
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        switchEnableReminders.isChecked = prefs.getBoolean(KEY_REMINDERS_ENABLED, false)

        switchEnableReminders.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_REMINDERS_ENABLED, isChecked).apply()
        }

        loadRemindersFromFirestore()

        btnSetReminder.setOnClickListener {
            if (!switchEnableReminders.isChecked) {
                Toast.makeText(this, "Włącz najpierw przypomnienia!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUser = auth.currentUser
            if (currentUser == null) {
                Toast.makeText(this, "Musisz być zalogowany, aby ustawić przypomnienie", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val mealName = editMealName.text.toString().trim()
            if (mealName.isEmpty()) {
                Toast.makeText(this, "Podaj nazwę posiłku", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val (hour, minute) = getTimePickerHourMinute()

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val reminder = Reminder(
                userId = currentUser.uid,
                mealName = mealName,
                hour = hour,
                minute = minute,
                timestamp = Timestamp(calendar.time)
            )

            saveReminderToFirestore(reminder)
        }
    }

    private fun setupTimePickerStyle() {
        timePicker.setBackgroundColor(Color.parseColor("#E8F5E9"))
        timePicker.setIs24HourView(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.hour = 12
            timePicker.minute = 0

            // Kolorowanie pola tekstowego w NumberPicker (godzina i minuta)
            val hourPickerId = Resources.getSystem().getIdentifier("hour", "id", "android")
            val minutePickerId = Resources.getSystem().getIdentifier("minute", "id", "android")
            val hourNumberPicker = timePicker.findViewById<NumberPicker>(hourPickerId)
            val minuteNumberPicker = timePicker.findViewById<NumberPicker>(minutePickerId)

            val textColor = Color.parseColor("#4CAF50")
            listOf(hourNumberPicker, minuteNumberPicker).forEach { np ->
                np?.let {
                    for (i in 0 until it.childCount) {
                        val child = it.getChildAt(i)
                        if (child is EditText) {
                            child.setTextColor(textColor)
                        }
                    }
                }
            }
        }
    }

    private fun getTimePickerHourMinute(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 23) {
            Pair(timePicker.hour, timePicker.minute)
        } else {
            @Suppress("DEPRECATION")
            Pair(timePicker.currentHour, timePicker.currentMinute)
        }
    }

    private fun loadRemindersFromFirestore() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Musisz być zalogowany, aby wyświetlić przypomnienia", Toast.LENGTH_SHORT).show()
            return
        }

        remindersCollection
            .whereEqualTo("userId", currentUser.uid)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                remindersList.clear()
                remindersData.clear()

                for (doc in documents) {
                    val reminder = doc.toObject(Reminder::class.java)
                    reminder.id = doc.id // Zapisz ID dokumentu Firestore

                    remindersData.add(reminder)
                    val timeFormatted = "%02d:%02d".format(reminder.hour, reminder.minute)
                    remindersList.add("${reminder.mealName} - przypomnienie o $timeFormatted")

                    setAlarm(reminder)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Błąd podczas ładowania przypomnień", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveReminderToFirestore(reminder: Reminder) {
        remindersCollection
            .add(reminder)
            .addOnSuccessListener {
                val timeFormatted = "%02d:%02d".format(reminder.hour, reminder.minute)
                remindersList.add("${reminder.mealName} - przypomnienie o $timeFormatted")
                remindersData.add(reminder)
                adapter.notifyDataSetChanged()

                setAlarm(reminder)
                editMealName.text.clear()
                Toast.makeText(this, "Ustawiono przypomnienie na $timeFormatted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Błąd podczas zapisywania przypomnienia", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setAlarm(reminder: Reminder) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("mealName", reminder.mealName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reminder.mealName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExact(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    private fun deleteReminder(reminder: Reminder) {
        // Usuwanie dokumentu w Firestore na podstawie unikalnych pól przypomnienia
        remindersCollection
            .whereEqualTo("userId", reminder.userId)
            .whereEqualTo("mealName", reminder.mealName)
            .whereEqualTo("hour", reminder.hour)
            .whereEqualTo("minute", reminder.minute)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    remindersCollection.document(doc.id).delete()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Usunięto przypomnienie: ${reminder.mealName}", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Błąd przy usuwaniu przypomnienia", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Błąd przy wyszukiwaniu przypomnienia do usunięcia", Toast.LENGTH_SHORT).show()
            }

        // Usuwanie z lokalnych list i aktualizacja adaptera
        val index = remindersData.indexOfFirst {
            it.userId == reminder.userId &&
                    it.mealName == reminder.mealName &&
                    it.hour == reminder.hour &&
                    it.minute == reminder.minute
        }
        if (index >= 0) {
            remindersData.removeAt(index)
            remindersList.removeAt(index)
            adapter.notifyDataSetChanged()
        }

        cancelAlarm(reminder)
    }

    private fun cancelAlarm(reminder: Reminder) {
        val intent = Intent(this, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reminder.mealName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }
}
