package com.example.diabetesdiary

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Button

class ChartActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var levelInput: EditText
    private lateinit var timePicker: TimePicker
    private lateinit var addButton: Button
    private lateinit var backButton: ImageButton  // <-- zmiana na ImageButton

    private val db = Firebase.firestore
    private val dateFormatDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateFormatHour = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart)

        lineChart = findViewById(R.id.lineChart)
        levelInput = findViewById(R.id.levelInput)
        timePicker = findViewById(R.id.timePicker)
        addButton = findViewById(R.id.addButton)
        backButton = findViewById(R.id.backButton)  // ImageButton
        timePicker.setIs24HourView(true)

        setupChart()
        loadDataFromFirestore()

        backButton.setOnClickListener {
            finish()
        }

        addButton.setOnClickListener {
            val levelText = levelInput.text.toString()
            val level = levelText.toFloatOrNull()

            if (level == null || level < 70 || level > 200) {
                levelInput.error = "Wartość musi być między 70 a 200"
                return@setOnClickListener
            }

            val hour = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                timePicker.hour
            } else {
                @Suppress("DEPRECATION")
                timePicker.currentHour
            }

            val minute = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                timePicker.minute
            } else {
                @Suppress("DEPRECATION")
                timePicker.currentMinute
            }

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val todayDate = dateFormatDay.format(Calendar.getInstance().time)
            val timeString = dateFormatHour.format(calendar.time)

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Toast.makeText(this, "Użytkownik nie jest zalogowany", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newRecord = hashMapOf(
                "timestamp" to timeString,
                "sugarlevel" to level,
                "date" to todayDate,
                "userId" to currentUser.uid
            )

            db.collection("sugar_level")
                .add(newRecord)
                .addOnSuccessListener {
                    Log.d("ChartActivity", "Dodano: $newRecord")
                    levelInput.text.clear()
                    loadDataFromFirestore()
                }
                .addOnFailureListener { e ->
                    Log.e("ChartActivity", "Błąd zapisu", e)
                    Toast.makeText(this, "Błąd zapisu danych", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupChart() {
        lineChart.apply {
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            axisRight.isEnabled = false
            description.text = "Poziomy cukru wg godzin"
            animateY(1000)
        }
    }

    private fun loadDataFromFirestore() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w("ChartActivity", "Użytkownik nie jest zalogowany - brak danych do wczytania")
            return
        }

        val todayDate = dateFormatDay.format(Calendar.getInstance().time)

        db.collection("sugar_level")
            .whereEqualTo("date", todayDate)
            .whereEqualTo("userId", currentUser.uid)
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { result ->
                Log.d("ChartActivity", "Pobrano ${result.size()} rekordów")

                val sugarLevels = mutableListOf<Pair<String, Float>>()

                for (doc in result) {
                    val time = doc.getString("timestamp") ?: continue
                    val level = doc.getDouble("sugarlevel")?.toFloat() ?: continue
                    sugarLevels.add(time to level)

                    Log.d("ChartActivity", "Record: time=$time, level=$level")
                }

                drawChart(sugarLevels)
            }
            .addOnFailureListener { e ->
                Log.e("ChartActivity", "Błąd Firestore", e)
                Toast.makeText(this, "Błąd podczas ładowania danych", Toast.LENGTH_SHORT).show()
            }
    }

    private fun drawChart(data: List<Pair<String, Float>>) {
        if (data.isEmpty()) {
            lineChart.clear()
            lineChart.invalidate()
            Log.d("ChartActivity", "Brak danych do wyświetlenia na wykresie")
            return
        }

        val entries = data.mapIndexed { index, pair ->
            Entry(index.toFloat(), pair.second)
        }
        val labels = data.map { it.first }

        val dataSet = LineDataSet(entries, "Poziom cukru").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            lineWidth = 2f
            valueTextColor = Color.BLACK
        }

        lineChart.apply {
            this.data = LineData(dataSet)
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return labels.getOrNull(value.toInt()) ?: ""
                }
            }
            this.data?.notifyDataChanged()
            notifyDataSetChanged()
            invalidate()
        }

        Log.d("ChartActivity", "Wykres narysowany z ${data.size} punktami")
    }
}
