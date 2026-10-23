package com.rbastem.sonino

import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.doOnLayout
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
    var somAtivado = true

    private var toneG: ToneGenerator? = null

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
    lateinit var txtFeedbackRodada: TextView
    lateinit var btnSom: ImageButton
    
    var cartaAndroidJogada: Carta? = null
    private var isJogoAtivo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toneG = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        mostrarMenuPrincipal()
    }

    override fun onDestroy() {
        isJogoAtivo = false
        toneG?.release()
        super.onDestroy()
    }

    fun tocarSom(tipo: Int, duracao: Int) {
        if (somAtivado) toneG?.startTone(tipo, duracao)
    }

    fun mostrarMenuPrincipal() {
        isJogoAtivo = false
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
            mostrarEstatisticas()
        }
    }

    fun mostrarEstatisticas() {
        isJogoAtivo = false
        setContentView(R.layout.activity_stats)

        val prefs = getSharedPreferences("sonino_stats", Context.MODE_PRIVATE)
        
        val totalPontos = prefs.getInt("total_pontos", 0)
        val totalTorneios = prefs.getInt("total_torneios", 0)
        val torneiosGanhos = prefs.getInt("torneios_ganhos", 0)
        val torneiosPerdidos = prefs.getInt("torneios_perdidos", 0)
        
        val totalPartidas = prefs.getInt("total_partidas", 0)
        val partidasGanhas = prefs.getInt("partidas_ganhas", 0)
        val partidasPerdidas = prefs.getInt("partidas_perdidas", 0)
        val partidasEmpatadas = prefs.getInt("partidas_empatadas", 0)

        findViewById<TextView>(R.id.txtNivelStats).text = "Nível: $dificuldade"
        findViewById<TextView>(R.id.txtPontos).text = "Pontos: $totalPontos"
        findViewById<TextView>(R.id.txtTotalTorneios).text = "Total de Torneios: $totalTorneios"
        findViewById<TextView>(R.id.txtTorneiosGanhos).text = "Ganhos: $torneiosGanhos"
        findViewById<TextView>(R.id.txtTorneiosPerdidos).text = "Perdidos: $torneiosPerdidos"
        findViewById<TextView>(R.id.txtTotalPartidas).text = "Total de Partidas: $totalPartidas"
        findViewById<TextView>(R.id.txtPartidasGanhas).text = "Ganhas: $partidasGanhas"
        findViewById<TextView>(R.id.txtPartidasPerdidas).text = "Perdidas: $partidasPerdidas"
        findViewById<TextView>(R.id.txtPartidasEmpatadas).text = "Empatadas: $partidasEmpatadas"

        findViewById<Button>(R.id.btnVoltarStats).setOnClickListener {
            mostrarMenuPrincipal()
        }

        findViewById<Button>(R.id.btnResetStats).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Resetar Estatísticas")
                .setMessage("Tem certeza que deseja apagar todos os dados?")
                .setPositiveButton("Sim") { _, _ ->
                    prefs.edit().clear().apply()
                    mostrarEstatisticas()
                }
                .setNegativeButton("Não", null).show()
        }
    }

    fun iniciarNovaPartida() {
        isJogoAtivo = true
        pontosJogador = 0
        pontosAndroid = 0
        jogadorComeca = true
        setContentView(R.layout.activity_game)
        configurarElementosTela()
        iniciarRodada()
        
        findViewById<Button>(R.id.btnMenu).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Sair").setMessage("Deseja voltar ao menu?")
                .setPositiveButton("Sim") { _, _ -> mostrarMenuPrincipal() }
                .setNegativeButton("Não", null).show()
        }
        
        btnSom.setOnClickListener {
            somAtivado = !somAtivado
            btnSom.setImageResource(if (somAtivado) android.R.drawable.ic_lock_silent_mode_off else android.R.drawable.ic_lock_silent_mode)
        }
        btnSom.setImageResource(if (somAtivado) android.R.drawable.ic_lock_silent_mode_off else android.R.drawable.ic_lock_silent_mode)
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
        txtFeedbackRodada = findViewById(R.id.txtFeedbackRodada)
        btnSom = findViewById(R.id.btnSom)
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
        var isFirstDeal = false
        if (baralho.isEmpty() && maoJogador.isEmpty() && maoAndroid.isEmpty()) {
            for (n in naipes) { for (v in valores) { baralho.add(Carta(v, n)) } }
            baralho.shuffle()
            
            cartaTrunfo = baralho.last()
            trunfo = cartaTrunfo!!.naipe
            
            // Mostra o trunfo
            mostrarCarta(txtTrunfoMesa, imgTrunfoMesa, cartaTrunfo)
            imgTrunfoMesa.visibility = View.VISIBLE
            txtTrunfoMesa.visibility = View.VISIBLE

            for (i in 0..2) {
                maoJogador.add(baralho.removeAt(0))
                maoAndroid.add(baralho.removeAt(0))
            }
            isFirstDeal = true
        }
        
        imgMonte.visibility = if (baralho.isNotEmpty()) View.VISIBLE else View.GONE

        mesaJogador.text = ""; mesaAndroid.text = ""
        imgMesaJogador.visibility = ImageView.GONE; imgMesaAndroid.visibility = ImageView.GONE
        cartaAndroidJogada = null
        
        atualizarTela()
        atualizarPlacar()

        if (isFirstDeal) {
            habilitarCliquesJogador(false)
            animarDistribuicao()
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isJogoAtivo) return@postDelayed
                if (jogadorComeca) habilitarCliquesJogador(true)
                else androidJogaPrimeiro()
            }, 1600)
        } else {
            if (jogadorComeca) {
                habilitarCliquesJogador(true)
            } else {
                habilitarCliquesJogador(false)
                Handler(Looper.getMainLooper()).postDelayed({ 
                    if (isJogoAtivo) androidJogaPrimeiro() 
                }, 1000)
            }
        }
    }

    fun animarDistribuicao() {
        // Usa doOnLayout para garantir que o layout já foi desenhado
        imgMonte.doOnLayout {
            if (!isJogoAtivo) return@doOnLayout
            val startLoc = IntArray(2)
            imgMonte.getLocationOnScreen(startLoc)
            val startX = startLoc[0].toFloat()
            val startY = startLoc[1].toFloat()

            var delayAnim = 0L

            fun jogarCartaAnimada(cardView: View) {
                val cardLoc = IntArray(2)
                cardView.getLocationOnScreen(cardLoc)
                val targetX = cardLoc[0].toFloat()
                val targetY = cardLoc[1].toFloat()

                cardView.translationX = startX - targetX
                cardView.translationY = startY - targetY
                cardView.alpha = 0f

                cardView.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(500)
                    .setStartDelay(delayAnim)
                    .start()
                
                delayAnim += 150
            }

            uiImgsJogador.forEach { if (it.visibility == View.VISIBLE) jogarCartaAnimada(it) }
            uiImgsAndroid.forEach { if (it.visibility == View.VISIBLE) jogarCartaAnimada(it) }

            tocarSom(ToneGenerator.TONE_PROP_BEEP2, 200)
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
        if (maoJogador.size <= index || !isJogoAtivo) return
        habilitarCliquesJogador(false)
        
        tocarSom(ToneGenerator.TONE_PROP_BEEP, 100)

        val cartaJogada = maoJogador.removeAt(index)
        mostrarCarta(mesaJogador, imgMesaJogador, cartaJogada)
        atualizarTela()

        if (cartaAndroidJogada != null) {
            Handler(Looper.getMainLooper()).postDelayed({ 
                if (isJogoAtivo) avaliarRodada(cartaJogada, cartaAndroidJogada!!, false) 
            }, 800)
        } else {
            Handler(Looper.getMainLooper()).postDelayed({ 
                if (isJogoAtivo) androidResponde(cartaJogada) 
            }, 1000)
        }
    }

    fun androidJogaPrimeiro() {
        if (maoAndroid.isEmpty() || !isJogoAtivo) return
        
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

        tocarSom(ToneGenerator.TONE_PROP_BEEP, 100)
        cartaAndroidJogada = maoAndroid.removeAt(indexJogada)
        mostrarCarta(mesaAndroid, imgMesaAndroid, cartaAndroidJogada)
        atualizarTela()

        habilitarCliquesJogador(true)
    }

    fun androidResponde(cartaJogador: Carta) {
        if (maoAndroid.isEmpty() || !isJogoAtivo) return
        
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

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isJogoAtivo) return@postDelayed
            tocarSom(ToneGenerator.TONE_PROP_BEEP, 100)
            val cartaAndroid = maoAndroid.removeAt(indexJogada)
            mostrarCarta(mesaAndroid, imgMesaAndroid, cartaAndroid)
            atualizarTela()

            Handler(Looper.getMainLooper()).postDelayed({ 
                if (isJogoAtivo) avaliarRodada(cartaJogador, cartaAndroid, true) 
            }, 1000)
        }, 500)
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

        val pontosRodada = (pontosCartas[cartaJogador.valor] ?: 0) + (pontosCartas[cartaAndroid.valor] ?: 0)
        if (jogadorGanhou) {
            pontosJogador += pontosRodada
        } else {
            pontosAndroid += pontosRodada
        }
        
        jogadorComeca = jogadorGanhou 
        atualizarPlacar()

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isJogoAtivo) return@postDelayed
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
        }, 1500)
    }

    fun finalizarPartida() {
        if (!isJogoAtivo) return
        
        var mensagem = ""
        var partidaGanha = false
        var partidaEmpatada = false

        if (pontosJogador >= 61 && pontosJogador > pontosAndroid) {
            partidasJogador++
            partidaGanha = true
            mensagem = "Você ganhou a partida!"
            tocarSom(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 1000)
        } else if (pontosAndroid >= 61 && pontosAndroid > pontosJogador) {
            partidasAndroid++
            mensagem = "O Android ganhou a partida!"
            tocarSom(ToneGenerator.TONE_CDMA_ABBR_ALERT, 1000)
        } else if (pontosJogador == 60 && pontosAndroid == 60) {
            partidaEmpatada = true
            mensagem = "Empate na partida!"
        } else {
            if (pontosJogador > pontosAndroid) {
                partidasJogador++
                partidaGanha = true
                mensagem = "Você ganhou a partida!"
            } else {
                partidasAndroid++
                mensagem = "O Android ganhou a partida!"
            }
        }
        
        atualizarPlacar()
        mostrarFeedback(mensagem)

        // Salvar estatísticas da partida
        val prefs = getSharedPreferences("sonino_stats", Context.MODE_PRIVATE)
        var editor = prefs.edit()
        editor.putInt("total_pontos", prefs.getInt("total_pontos", 0) + pontosJogador)
        editor.putInt("total_partidas", prefs.getInt("total_partidas", 0) + 1)
        
        if (partidaGanha) editor.putInt("partidas_ganhas", prefs.getInt("partidas_ganhas", 0) + 1)
        else if (partidaEmpatada) editor.putInt("partidas_empatadas", prefs.getInt("partidas_empatadas", 0) + 1)
        else editor.putInt("partidas_perdidas", prefs.getInt("partidas_perdidas", 0) + 1)
        
        editor.apply()

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isJogoAtivo) return@postDelayed
            
            if (partidasJogador == 2 || partidasAndroid == 2) {
                val vencedorFinal = if (partidasJogador == 2) "VOCÊ VENCEU O TORNEIO!" else "O ANDROID VENCEU O TORNEIO!"
                mostrarFeedback(vencedorFinal)
                tocarSom(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
                
                // Salvar estatísticas do torneio
                editor = prefs.edit()
                editor.putInt("total_torneios", prefs.getInt("total_torneios", 0) + 1)
                if (partidasJogador == 2) {
                    editor.putInt("torneios_ganhos", prefs.getInt("torneios_ganhos", 0) + 1)
                } else {
                    editor.putInt("torneios_perdidos", prefs.getInt("torneios_perdidos", 0) + 1)
                }
                editor.apply()

                Handler(Looper.getMainLooper()).postDelayed({
                    if (isJogoAtivo) mostrarMenuPrincipal()
                }, 4000)
                
            } else {
                baralho.clear()
                maoJogador.clear()
                maoAndroid.clear()
                iniciarNovaPartida() 
            }
        }, 4000)
    }

    fun mostrarFeedback(texto: String) {
        txtFeedbackRodada.text = texto
        txtFeedbackRodada.visibility = View.VISIBLE
        txtFeedbackRodada.alpha = 0f
        txtFeedbackRodada.animate().alpha(1f).setDuration(300).start()
    }
}
