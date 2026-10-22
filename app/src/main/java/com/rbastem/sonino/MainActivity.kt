package com.rbastem.sonino

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
    val nomesNaipes = mapOf("♠" to "Espadas", "♥" to "Copas", "♦" to "Ouros", "♣" to "Paus")
    val valores = listOf("A", "7", "K", "J", "Q", "6", "5", "4", "3", "2")
    val pontosCartas = mapOf("A" to 11, "7" to 10, "K" to 4, "J" to 3, "Q" to 2)
    
    var baralho = mutableListOf<Carta>()
    var maoJogador = mutableListOf<Carta>()
    var maoAndroid = mutableListOf<Carta>()
    var trunfo = ""
    var cartaTrunfo: Carta? = null
    
    var pontosJogador = 0
    var pontosAndroid = 0
    var partidasJogador = 0
    var partidasAndroid = 0
    
    var jogadorComeca = true
    var dificuldade = "Difícil"

    // Memória para a IA Mestre
    var cartasJaJogadas = mutableListOf<Carta>()

    lateinit var uiCartasJogador: List<TextView>
    lateinit var uiImgsJogador: List<ImageView>
    lateinit var uiImgsAndroid: List<ImageView>
    lateinit var mesaJogador: TextView
    lateinit var mesaAndroid: TextView
    lateinit var imgMesaJogador: ImageView
    lateinit var imgMesaAndroid: ImageView
    lateinit var txtScoreJogador: TextView
    lateinit var txtScoreAndroid: TextView
    lateinit var txtPartidas: TextView
    lateinit var imgMonte: ImageView
    lateinit var imgTrunfoMesa: ImageView
    lateinit var txtTrunfoMesa: TextView
    
    var cartaAndroidJogada: Carta? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mostrarMenuPrincipal()
    }

    fun mostrarMenuPrincipal() {
        setContentView(R.layout.activity_main)
        trunfo = ""

        findViewById<Button>(R.id.btnJogar).setOnClickListener {
            partidasJogador = 0
            partidasAndroid = 0
            iniciarNovaPartida()
        }

        findViewById<Button>(R.id.btnRegras).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Regras do SONINO")
                .setMessage("• Baralho de 40 cartas.\n• Trunfo: última carta do baralho.\n• Ganha a rodada o trunfo mais forte ou a carta mais forte do naipe inicial.\n• Quem ganha, joga primeiro.\n• Vence quem fizer 61 pontos.\n• Torneio: melhor de 3.")
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
        pontosJogador = 0
        pontosAndroid = 0
        jogadorComeca = true
        cartasJaJogadas.clear()
        setContentView(R.layout.activity_game)
        configurarElementosTela()
        iniciarRodada()
        
        findViewById<Button>(R.id.btnMenu).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Sair").setMessage("Deseja voltar ao menu?")
                .setPositiveButton("Sim") { _, _ -> mostrarMenuPrincipal() }
                .setNegativeButton("Não", null).show()
        }
    }

    fun configurarElementosTela() {
        uiCartasJogador = listOf(findViewById(R.id.cartaJogador1), findViewById(R.id.cartaJogador2), findViewById(R.id.cartaJogador3))
        uiImgsJogador = listOf(findViewById(R.id.imgJogador1), findViewById(R.id.imgJogador2), findViewById(R.id.imgJogador3))
        uiImgsAndroid = listOf(findViewById(R.id.cartaAndroid1), findViewById(R.id.cartaAndroid2), findViewById(R.id.cartaAndroid3))
        mesaJogador = findViewById(R.id.cartaMesaJogador)
        mesaAndroid = findViewById(R.id.cartaMesaAndroid)
        imgMesaJogador = findViewById(R.id.imgMesaJogador)
        imgMesaAndroid = findViewById(R.id.imgMesaAndroid)
        txtScoreJogador = findViewById(R.id.txtScoreJogador)
        txtScoreAndroid = findViewById(R.id.txtScoreAndroid)
        txtPartidas = findViewById(R.id.txtPartidas)
        imgMonte = findViewById(R.id.imgMonte)
        imgTrunfoMesa = findViewById(R.id.imgTrunfoMesa)
        txtTrunfoMesa = findViewById(R.id.txtTrunfoMesa)
    }

    fun getImagemCarta(carta: Carta): Int {
        val valorNome = if (carta.valor.toIntOrNull() != null) "n${carta.valor}" else carta.valor.lowercase()
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
        txtScoreJogador.text = "$pontosJogador pts"
        txtScoreAndroid.text = "$pontosAndroid pts"
        txtPartidas.text = "$partidasJogador x $partidasAndroid"
    }

    fun iniciarRodada() {
        if (baralho.isEmpty() && maoJogador.isEmpty() && maoAndroid.isEmpty()) {
            for (n in naipes) { for (v in valores) { baralho.add(Carta(v, n)) } }
            baralho.shuffle()
            
            cartaTrunfo = baralho.last()
            trunfo = cartaTrunfo!!.naipe
            
            // Mostra a carta de trunfo na mesa
            mostrarCarta(txtTrunfoMesa, imgTrunfoMesa, cartaTrunfo)
            imgTrunfoMesa.visibility = View.VISIBLE

            for (i in 0..2) {
                maoJogador.add(baralho.removeAt(0))
                maoAndroid.add(baralho.removeAt(0))
            }
        }
        
        // Atualiza o monte visual
        imgMonte.visibility = if (baralho.isNotEmpty()) View.VISIBLE else View.GONE

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
        for (i in 0..2) {
            uiImgsAndroid[i].visibility = if (i < maoAndroid.size) ImageView.VISIBLE else ImageView.GONE
        }
    }

    fun jogarCarta(index: Int) {
        if (maoJogador.size <= index) return
        habilitarCliquesJogador(false)
        
        val cartaJogada = maoJogador.removeAt(index)
        mostrarCarta(mesaJogador, imgMesaJogador, cartaJogada)
        atualizarTela()

        if (cartaAndroidJogada != null) {
            Handler(Looper.getMainLooper()).postDelayed({ avaliarRodada(cartaJogada, cartaAndroidJogada!!, false) }, 800)
        } else {
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
                 val p = pontosCartas[maoAndroid[i].valor] ?: 0
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
            
            // Inteligência Mestre: Se não pode ganhar, tenta descartar carta de ponto baixo, 
            // a menos que saiba que o jogador não tem trunfo.
            if (cartaQueGanha != -1) { 
                indexJogada = cartaQueGanha 
            } else {
                var menorValor = 100
                for (i in maoAndroid.indices) {
                    val p = pontosCartas[maoAndroid[i].valor] ?: 0
                    if (p < menorValor) { menorValor = p; indexJogada = i }
                }
            }
        }

        val cartaAndroid = maoAndroid.removeAt(indexJogada)
        mostrarCarta(mesaAndroid, imgMesaAndroid, cartaAndroid)
        atualizarTela()

        Handler(Looper.getMainLooper()).postDelayed({ avaliarRodada(cartaJogador, cartaAndroid, true) }, 1500)
    }

    fun avaliarRodada(cartaJogador: Carta, cartaAndroid: Carta, jogadorFoiPrimeiro: Boolean) {
        var jogadorGanhou = false
        
        if (cartaJogador.naipe == cartaAndroid.naipe) {
            if (valores.indexOf(cartaJogador.valor) < valores.indexOf(cartaAndroid.valor)) jogadorGanhou = true
        } else {
            if (cartaJogador.naipe == trunfo) jogadorGanhou = true
            else if (cartaAndroid.naipe == trunfo) jogadorGanhou = false
            else jogadorGanhou = jogadorFoiPrimeiro 
        }

        // Adiciona à memória (para o nível Mestre)
        cartasJaJogadas.add(cartaJogador)
        cartasJaJogadas.add(cartaAndroid)

        val pontosRodada = (pontosCartas[cartaJogador.valor] ?: 0) + (pontosCartas[cartaAndroid.valor] ?: 0)
        if (jogadorGanhou) pontosJogador += pontosRodada else pontosAndroid += pontosRodada
        
        jogadorComeca = jogadorGanhou 
        atualizarPlacar()

        Handler(Looper.getMainLooper()).postDelayed({
            mostrarCarta(mesaJogador, imgMesaJogador, null)
            mostrarCarta(mesaAndroid, imgMesaAndroid, null)
            
            if (baralho.isNotEmpty()) {
                if (jogadorGanhou) {
                    if (baralho.isNotEmpty()) maoJogador.add(baralho.removeAt(0))
                    if (baralho.isNotEmpty()) maoAndroid.add(baralho.removeAt(0))
                } else {
                    if (baralho.isNotEmpty()) maoAndroid.add(baralho.removeAt(0))
                    if (baralho.isNotEmpty()) maoJogador.add(baralho.removeAt(0))
                }
            }

            if (maoJogador.isEmpty() && maoAndroid.isEmpty()) {
                finalizarPartida()
            } else {
                iniciarRodada()
            }
        }, 1200)
    }

    fun finalizarPartida() {
        var mensagem = ""
        if (pontosJogador >= 61 && pontosJogador > pontosAndroid) {
            partidasJogador++
            mensagem = "Você ganhou a partida com $pontosJogador pontos!"
        } else if (pontosAndroid >= 61 && pontosAndroid > pontosJogador) {
            partidasAndroid++
            mensagem = "O Android ganhou a partida com $pontosAndroid pontos!"
        } else if (pontosJogador == 60 && pontosAndroid == 60) {
            mensagem = "Empate na partida (60x60)!"
        } else {
            if (pontosJogador > pontosAndroid) {
                partidasJogador++
                mensagem = "Você ganhou por ter mais pontos ($pontosJogador a $pontosAndroid)!"
            } else {
                partidasAndroid++
                mensagem = "O Android ganhou por ter mais pontos ($pontosAndroid a $pontosJogador)!"
            }
        }

        if (partidasJogador == 2 || partidasAndroid == 2) {
            val vencedorFinal = if (partidasJogador == 2) "VOCÊ" else "ANDROID"
            val msgFinal = "$mensagem\n\nFIM DO TORNEIO!\n\n$vencedorFinal venceu o jogo por ${partidasJogador} a ${partidasAndroid}."
            
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
                    baralho.clear()
                    maoJogador.clear()
                    maoAndroid.clear()
                    iniciarNovaPartida() 
                }.show()
        }
    }
}
