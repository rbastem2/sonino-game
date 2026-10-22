package com.rbastem.sonino

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnJogar = findViewById<Button>(R.id.btnJogar)
        
        // Quando clicar em JOGAR, carrega a tela da mesa
        btnJogar.setOnClickListener {
            setContentView(R.layout.activity_game)
        }
    }
}
