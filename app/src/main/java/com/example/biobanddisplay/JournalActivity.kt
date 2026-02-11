package com.example.biobanddisplay

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class JournalActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journal)

        val sorenessInput = findViewById<EditText>(R.id.soreness_input)
        val dateInput = findViewById<EditText>(R.id.date_input)
        val saveButton = findViewById<Button>(R.id.save_journal_button)
        val backButton = findViewById<Button>(R.id.back_button)

        // Set current date as default
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dateInput.setText(sdf.format(Date()))

        saveButton.setOnClickListener {
            val soreness = sorenessInput.text.toString()
            val date = dateInput.text.toString()

            if (soreness.isEmpty() || date.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sorenessInt = soreness.toIntOrNull()
            if (sorenessInt == null || sorenessInt < 1 || sorenessInt > 10) {
                Toast.makeText(this, "Soreness must be a number between 1 and 10", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save data using SharedPreferences
            val sharedPref = getSharedPreferences("BiobandJournal", Context.MODE_PRIVATE)
            with (sharedPref.edit()) {
                putString(date, soreness)
                apply()
            }

            Toast.makeText(this, "Entry saved for $date", Toast.LENGTH_SHORT).show()
            
            // Clear input after saving
            sorenessInput.text.clear()
        }

        backButton.setOnClickListener {
            finish()
        }
    }
}
