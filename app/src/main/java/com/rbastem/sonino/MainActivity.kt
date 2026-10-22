package com.rbastem.sonino

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog

data class Carta(val valor: String, val naipe: String) {
    override fun toString(): String { return "$valor $naipe" }
}

class MainActivity : AppCompatActivity() {
    val naipes = listOf("♠", "♥", "♦", "♣")
    val valores = listOf("A", "7", "K", "J", "Q", "10", "9", "8", "6", "5", "4", "3", "2")
    
    var baralho = mutableListOf<Carta>()
    var maoJogador = mutableListOf<Carta>()
    var maoAndroid = mutableListOf<Carta>()
    var trunfo = ""
    
    // Placar da Partida (Mãos ganhas)
    var maosJogador = 0
    var maosAndroid = 0
    
    // Placar do Jogo (Melhor de 3 partidas)
    var partidasJogador = 0
    var partidasAndroid = 0
    
    var jogadorComeca = true
    var dificuldade = "Difícil"

    lateinit var uiCartasJogador: List<TextView>
    lateinit var uiImgsJogador: List<ImageView>
    lateinit var mesaJogador: TextView
    lateinit var mesaAndroid: TextView
    lateinit var imgMesaJogador: ImageView
    lateinit var imgMesaAndroid: ImageView
    lateinit var txtScoreJogador: TextView
    lateinit var txtScoreAndroid: TextView
    
    var cartaAndroidJogada: Carta? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mostrarMenuPrincipal()
    }

    fun mostrarMenuPrincipal() {
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnJogar).setOnClickListener {
            partidasJogador = 0
            partidasAndroid = 0
            iniciarNovaPartida()
        }

        findViewById<Button>(R.id.btnRegras).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Regras do SONINO")
                .setMessage("• Melhor de 3 partidas.\n• Cada partida é ganha por quem fizer 3 mãos primeiro.\n• Trunfo: última carta do baralho.\n• Quem ganha a mão joga primeiro na próxima.\n• A pontuação oficial (61 pontos) será implementada em breve.")
                .setPositiveButton("Entendi", null).show()
        }

        findViewById<Button>(R.id.btnConfig).setOnClickListener {
            val opcoes = arrayOf("Fácil", "Difícil", "Mestre")
            val indexAtual = opcoes.indexOf(dificuldade)
            AlertDialog.Builder(this).setTitle("Nível do Android").setSingleChoiceItems(opcoes, indexAtual) { dialog, which ->
                dificuldade = opcoes[which]
                dialog.dismiss()
            }.show()
        }

        findViewById<Button>(R.id.btnHistorico).setOnClickListener {
            val prefs = getSharedPreferences("sonino_prefs", Context.MODE_PRIVATE)
            val hist = prefs.getString("historico", "Nenhum jogo registado ainda.") ?: "Nenhum jogo."
            AlertDialog.Builder(this).setTitle("Histórico").setMessage(hist).setPositiveButton("Fechar", null).show()
        }
    }

    fun iniciarNovaPartida() {
        maosJogador = 0
        maosAndroid = 0
        jogadorComeca = true
        setContentView(R.layout.activity_game)
        configurarElementosTela()
        iniciarRodada()
        
        findViewById<Button>(R.id.btnMenu).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Sair").setMessage("Deseja voltar ao menu? O progresso desta partida será perdido.")
                .setPositiveButton("Sim") { _, _ -> mostrarMenuPrincipal() }
                .setNegativeButton("Não", null).show()
        }
    }

    fun configurarElementosTela() {
        uiCartasJogador = listOf(findViewById(R.id.cartaJogador1), findViewById(R.id.cartaJogador2), findViewById(R.id.cartaJogador3))
        uiImgsJogador = listOf(findViewById(R.id.imgJogador1), findViewById(R.id.imgJogador2), findViewById(R.id.imgJogador3))
        mesaJogador = findViewById(R.id.cartaMesaJogador)
        mesaAndroid = findViewById(R.id.cartaMesaAndroid)
        imgMesaJogador = findViewById(R.id.imgMesaJogador)
        imgMesaAndroid = findViewById(R.id.imgMesaAndroid)
        txtScoreJogador = findViewById(R.id.txtScoreJogador)
        txtScoreAndroid = findViewById(R.id.txtScoreAndroid)
    }

    fun getImagemCarta(carta: Carta): Int {
        val valorNome = carta.valor.lowercase()
        val naipeNome = when(carta.naipe) { "♠" -> "espadas"; "♥" -> "copas"; "♦" -> "ouros"; "♣" -> "paus"; else -> "" }
        return resources.getIdentifier("${valorNome}_${naipeNome}", "drawable", packageName)
    }

    fun mostrarCarta(tv: TextView, img: ImageView, carta: Carta?) {
        if (carta == null) {
            tv.text = ""; img.visibility = ImageView.GONE; tv.visibility = TextView.GONE; return
        }
        val idImagem = getImagemCarta(carta)
        if (idImagem != 0) {
            img.setImageResource(idImagem); img.visibility = ImageView.VISIBLE; tv.visibility = TextView.GONE
        } else {
            img.visibility = ImageView.GONE; tv.visibility = TextView.VISIBLE
            tv.text = carta.toString()
            tv.setTextColor(if (carta.naipe == "♥" || carta.naipe == "♦") Color.RED else Color.BLACK)
        }
    }
    
    fun atualizarPlacar() {
        txtScoreJogador.text = "👤 Você: $maosJogador (Partidas: $partidasJogador)"
        txtScoreAndroid.text = "🤖 Android: $maosAndroid (Partidas: $partidasAndroid)"
    }

    fun iniciarRodada() {
        baralho.clear()
        for (n in naipes) { for (v in valores) { baralho.add(Carta(v, n)) } }
        baralho.shuffle()
        
        if (trunfo.isEmpty()) {
            trunfo = baralho.last().naipe
            findViewById<TextView>(R.id.txtTrunfo).text = "Trunfo: $trunfo"
        }

        maoJogador.clear(); maoAndroid.clear()
        for (i in 0..2) {
            maoJogador.add(baralho.removeAt(0))
            maoAndroid.add(baralho.removeAt(0))
        }
        
        mesaJogador.text = ""; mesaAndroid.text = ""
        imgMesaJogador.visibility = ImageView.GONE; imgMesaAndroid.visibility = ImageView.GONE
        cartaAndroidJogada = null
        
        atualizarTela()
        atualizarPlacar()

        if (jogadorComeca) {
            habilitarCliquesJogador(true)
        } else {
            habilitarCliquesJogador(false)
            Handler(Looper.getMainLooper()).postDelayed({ androidJogaPrimeiro() }, 1000)
        }
    }

    fun habilitarCliquesJogador(habilitar: Boolean) {
        for (i in 0..2) {
            if (habilitar) {
                uiCartasJogador[i].setOnClickListener { jogarCarta(i) }
                uiImgsJogador[i].setOnClickListener { jogarCarta(i) }
            } else {
                uiCartasJogador[i].setOnClickListener(null)
                uiImgsJogador[i].setOnClickListener(null)
            }
        }
    }

    fun atualizarTela() {
        for (i in 0..2) {
            if (i < maoJogador.size) {
                mostrarCarta(uiCartasJogador[i], uiImgsJogador[i], maoJogador[i])
            } else {
                mostrarCarta(uiCartasJogador[i], uiImgsJogador[i], null)
            }
        }
    }

    fun jogarCarta(index: Int) {
        if (maoJogador.size <= index) return
        habilitarCliquesJogador(false)
        
        val cartaJogada = maoJogador.removeAt(index)
        mostrarCarta(mesaJogador, imgMesaJogador, cartaJogada)
        atualizarTela()

        if (cartaAndroidJogada != null) {
            // O Android já tinha jogado, então nós avaliamos a mesa
            avaliarRodada(cartaJogada, cartaAndroidJogada!!)
        } else {
            // O Jogador jogou primeiro, o Android vai responder
            Handler(Looper.getMainLooper()).postDelayed({ androidResponde(cartaJogada) }, 1000)
        }
    }

    fun androidJogaPrimeiro() {
        if (maoAndroid.isEmpty()) return
        
        var indexJogada = 0
        if (dificuldade == "Fácil") {
             indexJogada = (0 until maoAndroid.size).random()
        } else {
             var menorValor = 100
             for (i in maoAndroid.indices) {
                 val p = mapOf("A" to 11, "7" to 10, "K" to 4, "J" to 3, "Q" to 2)[maoAndroid[i].valor] ?: 0
                 if (p < menorValor) { menorValor = p; indexJogada = i }
             }
        }

        cartaAndroidJogada = maoAndroid.removeAt(indexJogada)
        mostrarCarta(mesaAndroid, imgMesaAndroid, cartaAndroidJogada)
        atualizarTela()

        habilitarCliquesJogador(true)
    }

    fun androidResponde(cartaJogador: Carta) {
        if (maoAndroid.isEmpty()) return
        
        var indexJogada = 0
        if (dificuldade == "Fácil") {
            indexJogada = (0 until maoAndroid.size).random()
        } else {
            var cartaQueGanha = -1
            for (i in maoAndroid.indices) {
                val c = maoAndroid[i]
                if (c.naipe == cartaJogador.naipe && valores.indexOf(c.valor) < valores.indexOf(cartaJogador.valor)) {
                    cartaQueGanha = i; break
                } else if (c.naipe == trunfo && cartaJogador.naipe != trunfo) {
                    cartaQueGanha = i; break
                }
            }
            if (cartaQueGanha != -1) { indexJogada = cartaQueGanha } 
            else {
                var menorValor = 100
                for (i in maoAndroid.indices) {
                    val p = mapOf("A" to 11, "7" to 10, "K" to 4, "J" to 3, "Q" to 2)[maoAndroid[i].valor] ?: 0
                    if (p < menorValor) { menorValor = p; indexJogada = i }
                }
            }
        }

        val cartaAndroid = maoAndroid.removeAt(indexJogada)
        mostrarCarta(mesaAndroid, imgMesaAndroid, cartaAndroid)
        atualizarTela()

        Handler(Looper.getMainLooper()).postDelayed({ avaliarRodada(cartaJogador, cartaAndroid) }, 1500)
    }

    fun avaliarRodada(cartaJogador: Carta, cartaAndroid: Carta) {
        var jogadorGanhou = false
        if (cartaJogador.naipe == cartaAndroid.naipe) {
            if (valores.indexOf(cartaJogador.valor) < valores.indexOf(cartaAndroid.valor)) jogadorGanhou = true
        } else {
            if (cartaJogador.naipe == trunfo) jogadorGanhou = true
            else if (cartaAndroid.naipe == trunfo) jogadorGanhou = false
            else jogadorGanhou = true 
        }

        if (jogadorGanhou) maosJogador++ else maosAndroid++
        
        // REGRA: Quem ganhou, começa a próxima
        jogadorComeca = jogadorGanhou 
        atualizarPlacar()

        Handler(Looper.getMainLooper()).postDelayed({
            mostrarCarta(mesaJogador, imgMesaJogador, null)
            mostrarCarta(mesaAndroid, imgMesaAndroid, null)
            
            if (baralho.isNotEmpty()) {
                if (jogadorGanhou) {
                    maoJogador.add(baralho.removeAt(0))
                    if (baralho.isNotEmpty()) maoAndroid.add(baralho.removeAt(0))
                } else {
                    maoAndroid.add(baralho.removeAt(0))
                    if (baralho.isNotEmpty()) maoJogador.add(baralho.removeAt(0))
                }
            }

            if (maosJogador == 3 || maosAndroid == 3) {
                finalizarPartida()
            } else {
                iniciarRodada()
            }
        }, 1000)
    }

    fun finalizarPartida() {
        var mensagem = ""
        if (maosJogador == 3) {
            partidasJogador++
            mensagem = "Você ganhou esta partida!"
        } else {
            partidasAndroid++
            mensagem = "O Android ganhou esta partida!"
        }

        if (partidasJogador == 2 || partidasAndroid == 2) {
            val vencedorFinal = if (partidasJogador == 2) "VOCÊ" else "ANDROID"
            val msgFinal = "$mensagem\n\nFIM DE JOGO!\n\n$vencedorFinal venceu o torneio por ${partidasJogador} a ${partidasAndroid}."
            
            val prefs = getSharedPreferences("sonino_prefs", Context.MODE_PRIVATE)
            val dataAtual = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val historicoAntigo = prefs.getString("historico", "") ?: ""
            val novoHistorico = "$dataAtual - $vencedorFinal venceu ($partidasJogador-$partidasAndroid)\n$historicoAntigo"
            prefs.edit().putString("historico", novoHistorico).apply()

            AlertDialog.Builder(this).setTitle("Fim do Torneio").setMessage(msgFinal).setCancelable(false)
                .setPositiveButton("Menu Principal") { _, _ -> mostrarMenuPrincipal() }.show()
        } else {
            val msgProx = "$mensagem\n\nPlacar do Torneio: Você $partidasJogador x $partidasAndroid Android\n\nPróxima partida!"
            AlertDialog.Builder(this).setTitle("Fim da Partida").setMessage(msgProx).setCancelable(false)
                .setPositiveButton("Continuar") { _, _ -> 
                    trunfo = ""
                    iniciarNovaPartida() 
                }.show()
        }
    }
}
