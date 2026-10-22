package com.rbastem.sonino

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog

data class Carta(val valor: String, val naipe: String) {
    override fun toString(): String { return "$valor $naipe" }
}

class MainActivity : AppCompatActivity() {
    val naipes = listOf("♠", "♥", "♦", "♣")
    // Ordem de força (do mais forte para o mais fraco)
    val valores = listOf("A", "7", "K", "J", "Q", "10", "9", "8", "6", "5", "4", "3", "2")
    val pontosCartas = mapOf("A" to 11, "7" to 10, "K" to 4, "J" to 3, "Q" to 2)
    
    var baralho = mutableListOf<Carta>()
    var maoJogador = mutableListOf<Carta>()
    var maoAndroid = mutableListOf<Carta>()
    var trunfo = ""
    
    var pontosJogador = 0
    var pontosAndroid = 0
    var jogadorComeca = true

    // Elementos da tela
    lateinit var uiCartasJogador: List<TextView>
    lateinit var uiCartasAndroid: List<TextView>
    lateinit var mesaJogador: TextView
    lateinit var mesaAndroid: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnJogar).setOnClickListener {
            setContentView(R.layout.activity_game)
            configurarElementosTela()
            iniciarJogo()
        }
    }

    fun configurarElementosTela() {
        uiCartasJogador = listOf(findViewById(R.id.cartaJogador1), findViewById(R.id.cartaJogador2), findViewById(R.id.cartaJogador3))
        uiCartasAndroid = listOf(findViewById(R.id.cartaAndroid1), findViewById(R.id.cartaAndroid2), findViewById(R.id.cartaAndroid3))
        mesaJogador = findViewById(R.id.cartaMesaJogador)
        mesaAndroid = findViewById(R.id.cartaMesaAndroid)
    }

    fun iniciarJogo() {
        for (n in naipes) {
            for (v in valores) { baralho.add(Carta(v, n)) }
        }
        baralho.shuffle()

        trunfo = baralho.last().naipe
        findViewById<TextView>(R.id.txtTrunfo).text = "Trunfo: $trunfo"

        for (i in 0..2) {
            maoJogador.add(baralho.removeAt(0))
            maoAndroid.add(baralho.removeAt(0))
        }

        atualizarTela()
    }

    fun atualizarTela() {
        // Mostra a mão do jogador
        for (i in 0..2) {
            val tv = uiCartasJogador[i]
            if (i < maoJogador.size) {
                val carta = maoJogador[i]
                tv.text = carta.toString()
                tv.setTextColor(if (carta.naipe == "♥" || carta.naipe == "♦") Color.RED else Color.BLACK)
                tv.setOnClickListener { jogarCarta(i) }
            } else {
                tv.text = ""
                tv.setOnClickListener(null)
            }
        }

        // Mostra a mão do Android (virada para baixo)
        for (i in 0..2) {
            uiCartasAndroid[i].text = if (i < maoAndroid.size) "?" else ""
            uiCartasAndroid[i].setTextColor(Color.WHITE)
        }
    }

    fun jogarCarta(index: Int) {
        if (maoJogador.size <= index) return
        
        val cartaJogada = maoJogador.removeAt(index)
        mesaJogador.text = cartaJogada.toString()
        mesaJogador.setTextColor(if (cartaJogada.naipe == "♥" || cartaJogada.naipe == "♦") Color.RED else Color.BLACK)
        
        atualizarTela() // Esconde a carta jogada da mão

        // Desativa cliques enquanto o Android joga
        uiCartasJogador.forEach { it.setOnClickListener(null) }

        Handler(Looper.getMainLooper()).postDelayed({
            jogarAndroid(cartaJogada)
        }, 1000) // Espera 1 segundo
    }

    fun jogarAndroid(cartaJogador: Carta) {
        if (maoAndroid.isEmpty()) return
        
        val cartaAndroid = maoAndroid.removeAt(0)
        mesaAndroid.text = cartaAndroid.toString()
        mesaAndroid.setTextColor(if (cartaAndroid.naipe == "♥" || cartaAndroid.naipe == "♦") Color.RED else Color.BLACK)
        
        atualizarTela()

        Handler(Looper.getMainLooper()).postDelayed({
            avaliarRodada(cartaJogador, cartaAndroid)
        }, 1500) // Espera 1.5 segundos para o jogador ver as cartas na mesa
    }

    fun avaliarRodada(cartaJogador: Carta, cartaAndroid: Carta) {
        var jogadorGanhou = false

        if (cartaJogador.naipe == cartaAndroid.naipe) {
            // Mesmo naipe: a mais forte ganha
            if (valores.indexOf(cartaJogador.valor) < valores.indexOf(cartaAndroid.valor)) {
                jogadorGanhou = true
            }
        } else {
            // Naipes diferentes: verifica o trunfo
            if (cartaJogador.naipe == trunfo) {
                jogadorGanhou = true
            } else if (cartaAndroid.naipe == trunfo) {
                jogadorGanhou = false
            } else {
                // Ninguém jogou trunfo: o jogador que começou ganha (simplificação)
                jogadorGanhou = jogadorComeca
            }
        }

        // Soma os pontos
        val pontosRodada = (pontosCartas[cartaJogador.valor] ?: 0) + (pontosCartas[cartaAndroid.valor] ?: 0)
        if (jogadorGanhou) {
            pontosJogador += pontosRodada
            jogadorComeca = true
        } else {
            pontosAndroid += pontosRodada
            jogadorComeca = false
        }

        // Limpa a mesa após 1 segundo
        Handler(Looper.getMainLooper()).postDelayed({
            mesaJogador.text = ""
            mesaAndroid.text = ""
            
            // Compra cartas
            if (baralho.isNotEmpty()) {
                if (jogadorGanhou) {
                    maoJogador.add(baralho.removeAt(0))
                    if (baralho.isNotEmpty()) maoAndroid.add(baralho.removeAt(0))
                } else {
                    maoAndroid.add(baralho.removeAt(0))
                    if (baralho.isNotEmpty()) maoJogador.add(baralho.removeAt(0))
                }
            }

            atualizarTela()

            // Verifica fim de jogo
            if (maoJogador.isEmpty() && maoAndroid.isEmpty()) {
                finalizarJogo()
            } else if (!jogadorComeca) {
                // Se o Android ganhou, ele "pensa" e joga primeiro (para simplificar, o jogador ainda clica)
                // Futuramente podemos fazer o Android iniciar a rodada automaticamente
            }

        }, 1000)
    }

    fun finalizarJogo() {
        val mensagem = "Fim de Jogo!\n\nVocê: $pontosJogador pontos\nAndroid: $pontosAndroid pontos\n\n" +
                if (pontosJogador >= 61) "VOCÊ VENCEU! 🏆"
                else if (pontosAndroid >= 61) "O ANDROID VENCEU! 🤖"
                else "EMPATE! 🤝"

        AlertDialog.Builder(this)
            .setTitle("SONINO")
            .setMessage(mensagem)
            .setCancelable(false)
            .setPositiveButton("Jogar Novamente") { _, _ ->
                // Reinicia o app
                val intent = intent
                finish()
                startActivity(intent)
            }
            .show()
    }
}
