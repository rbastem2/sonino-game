package com.rbastem.sonino

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnJogar = findViewById<Button>(R.id.btnJogar)
        
        btnJogar.setOnClickListener {
            Toast.makeText(this, "O jogo vai começar em breve!", Toast.LENGTH_SHORT).show()
        }
    }
}
