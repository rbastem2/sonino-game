package com.rbastem.sonino

import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.Locale

data class Carta(val valor: String, val naipe: String) {
    override fun toString(): String { return "$valor $naipe" }
}

class MainActivity : AppCompatActivity() {
    val naipes = listOf("♠", "♥", "♦", "♣")
    val valores = listOf("A", "7", "K", "J", "Q", "6", "5", "4", "3", "2")
    val pontosCartas = mapOf("A" to 11, "7" to 10, "K" to 4, "J" to 3, "Q" to 2)
    
    var baralho = mutableListOf<Carta>()
    var maoJogador = mutableListOf<Carta>()
    var maoAndroid = mutableListOf<Carta>()
    var trunfo = ""
    var cartaTrunfo: Carta? = null
    var cartasJaJogadas = mutableListOf<Carta>()
    
    var pontosJogador = 0
    var pontosAndroid = 0
    var partidasJogador = 0
    var partidasAndroid = 0
    
    var jogadorComeca = true
    var dificuldade = "Difícil"
    var somAtivado = true
    var vibracaoAtivada = true
    var saldoMoedas = 0

    private var toneG: ToneGenerator? = null
    private var mInterstitialAd: InterstitialAd? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var telaAtual = "MENU"
    private var salaAtual: Sala? = null
    private var isDialogShowing = false

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
    lateinit var btnSom: Button
    lateinit var btnVibracao: Button
    
    var cartaAndroidJogada: Carta? = null
    var cartaJogadorJogada: Carta? = null
    private var isJogoAtivo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        toneG = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        
        MobileAds.initialize(this) {}
        carregarAnuncio()
        
        val prefs = getSharedPreferences("sonino_prefs", Context.MODE_PRIVATE)
        dificuldade = prefs.getString("dificuldade_salva", "Difícil") ?: "Difícil"
        saldoMoedas = prefs.getInt("saldo_moedas", 0)
        somAtivado = prefs.getBoolean("som_ativo", true)
        vibracaoAtivada = prefs.getBoolean("vibracao_ativa", true)
        
        mostrarMenuPrincipal()
    }

    override fun onStop() {
        super.onStop()
        if (isJogoAtivo) salvarEstadoJogo()
    }

    override fun onDestroy() {
        isJogoAtivo = false
        mainHandler.removeCallbacksAndMessages(null)
        FirebaseManager.pararDeEscutar()
        toneG?.release()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (telaAtual == "MENU") {
            super.onBackPressed()
        } else {
            if (isJogoAtivo) salvarEstadoJogo()
            FirebaseManager.pararDeEscutar()
            mostrarMenuPrincipal()
        }
    }

    // --- SONS E VIBRAÇÃO ---
    fun tocarSom(tipo: Int, duracao: Int) {
        if (somAtivado) toneG?.startTone(tipo, duracao)
    }

    fun vibrar(duracao: Long = 100) {
        if (!vibracaoAtivada) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duracao, 120))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duracao)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // --- ANÚNCIOS ---
    fun carregarAnuncio() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-6316829094801811/4524927652", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
                override fun onAdFailedToLoad(adError: LoadAdError) { mInterstitialAd = null }
            })
    }

    fun mostrarAnuncio(aoFechar: () -> Unit) {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mInterstitialAd = null
                    carregarAnuncio()
                    aoFechar()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    mInterstitialAd = null
                    carregarAnuncio()
                    aoFechar()
                }
            }
            mInterstitialAd?.show(this)
        } else {
            aoFechar()
        }
    }

    // --- MENU PRINCIPAL ---
    fun mostrarMenuPrincipal() {
        isJogoAtivo = false
        telaAtual = "MENU"
        setContentView(R.layout.activity_main)
        FirebaseManager.pararDeEscutar()

        findViewById<TextView>(R.id.txtMoedas).text = saldoMoedas.toString()

        findViewById<TextView>(R.id.btnBonusDiario).setOnClickListener {
            vibrar()
            val prefs = getSharedPreferences("sonino_bonus", Context.MODE_PRIVATE)
            val agoraMillis = System.currentTimeMillis()
            val ultimaDataMillis = prefs.getLong("ultima_data_millis", 0L)
            val umDiaEmMillis = 24 * 60 * 60 * 1000L
            val tempoDecorrido = agoraMillis - ultimaDataMillis

            var sequenciaAtual = prefs.getInt("sequencia", 0)
            val jaResgatouHoje = tempoDecorrido in 0 until umDiaEmMillis
            val tentouTrapaca = tempoDecorrido < 0
            
            if (!jaResgatouHoje && (tempoDecorrido > (2 * umDiaEmMillis) || sequenciaAtual >= 7)) {
                sequenciaAtual = 0
            }
            
            val diaParaMostrar = if (jaResgatouHoje) sequenciaAtual else sequenciaAtual + 1

            val dialogView = layoutInflater.inflate(R.layout.activity_daily_reward, null)
            val layoutDias = dialogView.findViewById<LinearLayout>(R.id.layoutDias)
            val btnReceber = dialogView.findViewById<Button>(R.id.btnReceberBonus)
            val txtStatus = dialogView.findViewById<TextView>(R.id.txtStatusBonus)

            if (tentouTrapaca) {
                txtStatus.text = "Erro: A data do seu telemóvel está errada."
                btnReceber.isEnabled = false
                btnReceber.text = "FECHAR"
                btnReceber.setBackgroundResource(R.drawable.btn_escuro)
                btnReceber.setTextColor(Color.parseColor("#D4AF37"))
            } else if (jaResgatouHoje) {
                txtStatus.text = "Já resgatou hoje! Volte amanhã."
                btnReceber.text = "FECHAR"
                btnReceber.setBackgroundResource(R.drawable.btn_escuro)
                btnReceber.setTextColor(Color.parseColor("#D4AF37"))
            }

            val premios = intArrayOf(50, 100, 150, 200, 300, 400, 500)
            
            for (i in 0 until 7) {
                val itemView = layoutInflater.inflate(R.layout.item_daily_reward, layoutDias, false)
                val txtDia = itemView.findViewById<TextView>(R.id.txtDiaSemana)
                val txtQtd = itemView.findViewById<TextView>(R.id.txtQtdMoedas)
                val card = itemView.findViewById<LinearLayout>(R.id.cardDia)

                txtDia.text = "Dia ${i + 1}"
                txtQtd.text = premios[i].toString()

                if (i + 1 < diaParaMostrar) {
                    itemView.alpha = 0.5f
                    txtQtd.setTextColor(Color.GRAY)
                } else if (i + 1 == diaParaMostrar) {
                    if (jaResgatouHoje) {
                        txtDia.text = "Resgatado"
                        itemView.alpha = 0.5f
                        txtQtd.setTextColor(Color.GRAY)
                    } else {
                        card.setBackgroundResource(R.drawable.btn_dourado)
                        txtDia.setTextColor(Color.BLACK)
                        txtQtd.setTextColor(Color.BLACK)
                    }
                } else {
                    txtQtd.setTextColor(Color.parseColor("#D4AF37"))
                }
                layoutDias.addView(itemView)
            }

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            btnReceber.setOnClickListener {
                vibrar()
                if (!jaResgatouHoje && !tentouTrapaca) {
                    val valorBonus = premios[sequenciaAtual]
                    saldoMoedas += valorBonus
                    prefs.edit().putLong("ultima_data_millis", agoraMillis).putInt("sequencia", sequenciaAtual + 1).apply()
                    getSharedPreferences("sonino_prefs", Context.MODE_PRIVATE).edit().putInt("saldo_moedas", saldoMoedas).apply()
                    findViewById<TextView>(R.id.txtMoedas).text = saldoMoedas.toString()
                    tocarSom(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 1000)
                }
                dialog.dismiss()
            }
            dialog.show()
        }

        findViewById<Button>(R.id.btnJogar).setOnClickListener {
            vibrar()
            val savePrefs = getSharedPreferences("sonino_save", Context.MODE_PRIVATE)
            if (savePrefs.getBoolean("tem_jogo_salvo", false)) {
                AlertDialog.Builder(this)
                    .setTitle("Jogo em Andamento")
                    .setMessage("Deseja continuar ou iniciar um novo jogo?")
                    .setPositiveButton("Continuar") { _, _ ->
                        carregarEstadoJogo()
                        setContentView(R.layout.activity_game)
                        configurarElementosTela()
                        atualizarTelaCompleta()
                        
                        if (cartaJogadorJogada != null && cartaAndroidJogada == null) {
                            habilitarCliquesJogador(false)
                            androidResponde(cartaJogadorJogada!!)
                        } else if (cartaAndroidJogada != null) {
                            habilitarCliquesJogador(true)
                        } else if (jogadorComeca) {
                            habilitarCliquesJogador(true)
                        } else {
                            habilitarCliquesJogador(false)
                            androidJogaPrimeiro()
                        }
                        configurarBotoesDeJogo()
                    }
                    .setNegativeButton("Novo Jogo") { _, _ ->
                        registrarDerrotaAbandono()
                        savePrefs.edit().clear().apply()
                        partidasJogador = 0
                        partidasAndroid = 0
                        iniciarNovaPartida()
                    }
                    .setCancelable(false)
                    .show()
            } else {
                partidasJogador = 0
                partidasAndroid = 0
                iniciarNovaPartida()
            }
        }

        findViewById<Button>(R.id.btnJogarOnline).setOnClickListener {
            vibrar()
            mostrarTelaMultiplayer()
        }

        findViewById<Button>(R.id.btnRegras).setOnClickListener {
            vibrar()
            AlertDialog.Builder(this).setTitle("Regras do SONINO")
                .setMessage("• Baralho de 40 cartas.\n• Trunfo: última carta do baralho.\n• Ganha a rodada o trunfo mais forte ou a carta mais forte do naipe inicial.\n• Quem ganha, joga primeiro.\n• Vence quem fizer 61 pontos.\n• Torneio: melhor de 3.")
                .setPositiveButton("Entendi", null).show()
        }

        findViewById<Button>(R.id.btnConfig).setOnClickListener {
            vibrar()
            val opcoes = arrayOf("Fácil", "Difícil", "Mestre")
            val indexAtual = opcoes.indexOf(dificuldade)
            AlertDialog.Builder(this).setTitle("Nível do Android").setSingleChoiceItems(opcoes, indexAtual) { dialog, which ->
                dificuldade = opcoes[which]
                getSharedPreferences("sonino_prefs", Context.MODE_PRIVATE).edit().putString("dificuldade_salva", dificuldade).apply()
                dialog.dismiss()
            }.show()
        }

        findViewById<Button>(R.id.btnHistorico).setOnClickListener {
            vibrar()
            mostrarEstatisticas()
        }
    }

    // --- MULTIPLAYER LOBBY ---
    fun mostrarTelaMultiplayer() {
        isJogoAtivo = false
        telaAtual = "MULTIPLAYER"
        setContentView(R.layout.activity_multiplayer)

        val btnCriar = findViewById<Button>(R.id.btnCriarSala)
        val btnEntrar = findViewById<Button>(R.id.btnEntrarSala)
        val txtStatus = findViewById<TextView>(R.id.txtStatusSala)

        btnCriar.isEnabled = false
        btnEntrar.isEnabled = false
        txtStatus.text = "A ligar ao servidor..."

        FirebaseManager.iniciarSessao(
            onPronto = {
                runOnUiThread {
                    btnCriar.isEnabled = true
                    btnEntrar.isEnabled = true
                    txtStatus.text = "Ligado! Pode criar ou entrar numa sala."
                }
            },
            onErro = { erro ->
                runOnUiThread { txtStatus.text = "Erro: $erro" }
            }
        )

        findViewById<Button>(R.id.btnVoltarMulti).setOnClickListener {
            vibrar()
            mostrarMenuPrincipal()
        }

        btnCriar.setOnClickListener {
            vibrar()
            txtStatus.text = "A criar sala..."
            FirebaseManager.criarSala(
                onSalaCriada = { codigo ->
                    runOnUiThread {
                        findViewById<TextView>(R.id.txtCodigoSala).text = "Código: $codigo"
                        txtStatus.text = "Aguardando oponente..."
                        FirebaseManager.escutarSala(codigo) { sala ->
                            if (sala.status == "JOGANDO") {
                                FirebaseManager.pararDeEscutar()
                                runOnUiThread { iniciarJogoOnline(sala) }
                            }
                        }
                    }
                },
                onErro = { erro -> runOnUiThread { txtStatus.text = "Erro: $erro" } }
            )
        }

        btnEntrar.setOnClickListener {
            vibrar()
            val codigo = findViewById<EditText>(R.id.edtCodigoEntrar).text.toString()
            if (codigo.length == 4) {
                txtStatus.text = "A entrar na sala $codigo..."
                FirebaseManager.entrarSala(
                    codigo = codigo,
                    onSalaEntrada = { sala -> runOnUiThread { iniciarJogoOnline(sala) } },
                    onErro = { erro -> runOnUiThread { txtStatus.text = "Erro: $erro" } }
                )
            } else {
                txtStatus.text = "Digite um código de 4 dígitos."
            }
        }
    }

    fun iniciarJogoOnline(salaInicial: com.rbastem.sonino.Sala) {
        salaAtual = salaInicial
        isDialogShowing = false
        telaAtual = "ONLINE_GAME"
        setContentView(R.layout.activity_online_game)
        
        val btnMenu = findViewById<Button>(R.id.btnMenuOnline)
        btnMenu.setOnClickListener {
            FirebaseManager.pararDeEscutar()
            mostrarMenuPrincipal()
        }

        val souHost = salaInicial.hostId == FirebaseManager.getMeuId()
        
        if (souHost) {
            FirebaseManager.iniciarPartida(salaInicial) { novaSala ->
                salaAtual = novaSala
                FirebaseManager.escutarSala(novaSala.id) { salaAtualizada ->
                    salaAtual = salaAtualizada
                    runOnUiThread { atualizarTelaOnline(salaAtualizada) }
                }
            }
        } else {
            FirebaseManager.escutarSala(salaInicial.id) { salaAtualizada ->
                salaAtual = salaAtualizada
                runOnUiThread { atualizarTelaOnline(salaAtualizada) }
            }
        }
    }

    fun atualizarTelaOnline(sala: Sala) {
        val meuId = FirebaseManager.getMeuId()
        val souHostLocal = sala.hostId == meuId
        val minhaMao = if (souHostLocal) sala.maoHost else sala.maoGuest
        val mesaOponente = if (souHostLocal) sala.mesaGuest else sala.mesaHost
        val mesaEu = if (souHostLocal) sala.mesaHost else sala.mesaGuest

        findViewById<TextView>(R.id.txtMinhaPontuacaoOnline).text = (if (souHostLocal) sala.pontosHost else sala.pontosGuest).toString() + " pts"
        findViewById<TextView>(R.id.txtPontuacaoOponenteOnline).text = (if (souHostLocal) sala.pontosGuest else sala.pontosHost).toString() + " pts"

        val opCards = listOf(R.id.cartaOp1, R.id.cartaOp2, R.id.cartaOp3)
        val maoOponenteTamanho = if (souHostLocal) sala.maoGuest.size else sala.maoHost.size
        for (i in 0 until 3) {
            findViewById<ImageView>(opCards[i]).visibility = if (i < maoOponenteTamanho) View.VISIBLE else View.GONE
        }

        val imgMesaOp = findViewById<ImageView>(R.id.imgMesaOp)
        val imgMesaEu = findViewById<ImageView>(R.id.imgMesaEu)
        val imgTrunfo = findViewById<ImageView>(R.id.imgTrunfoOnline)
        
        if (mesaOponente != null) {
            imgMesaOp.setImageResource(getImagemCarta(Carta(mesaOponente.valor, mesaOponente.naipe)))
            imgMesaOp.visibility = View.VISIBLE
        } else imgMesaOp.visibility = View.GONE

        if (mesaEu != null) {
            imgMesaEu.setImageResource(getImagemCarta(Carta(mesaEu.valor, mesaEu.naipe)))
            imgMesaEu.visibility = View.VISIBLE
        } else imgMesaEu.visibility = View.GONE

        if (sala.trunfo.isNotEmpty()) {
            imgTrunfo.setImageResource(getImagemCarta(Carta("A", sala.trunfo)))
            imgTrunfo.visibility = View.VISIBLE
        }

        val myCardsUI = listOf(R.id.myCard1, R.id.myCard2, R.id.myCard3)
        for (i in 0 until 3) {
            val img = findViewById<ImageView>(myCardsUI[i])
            if (i < minhaMao.size) {
                img.setImageResource(getImagemCarta(Carta(minhaMao[i].valor, minhaMao[i].naipe)))
                img.visibility = View.VISIBLE
                if (sala.turno == meuId && sala.status == "JOGANDO") {
                    img.setOnClickListener { jogarCartaOnline(i) }
                    img.alpha = 1.0f
                } else {
                    img.setOnClickListener(null)
                    img.alpha = 0.6f
                }
            } else img.visibility = View.GONE
        }

        val txtVez = findViewById<TextView>(R.id.txtVez)
        if (sala.status == "FINALIZADO") {
            txtVez.text = "Fim de Jogo"
            txtVez.setTextColor(Color.WHITE)
            if (!isDialogShowing) {
                isDialogShowing = true
                val vencedor = if (sala.pontosHost > sala.pontosGuest) "VOCE VENCEU!" else if (sala.pontosGuest > sala.pontosHost) "VOCE PERDEU!" else "EMPATE!"
                val msgFinal = "Pontos: " + sala.pontosHost + " vs " + sala.pontosGuest + " | " + vencedor
                
                // Guarda nas estatísticas locais
                val statsPrefs = getSharedPreferences("sonino_stats", Context.MODE_PRIVATE)
                val editor = statsPrefs.edit()
                editor.putInt("total_partidas", statsPrefs.getInt("total_partidas", 0) + 1)
                if (sala.pontosHost > sala.pontosGuest) {
                    editor.putInt("partidas_ganhas", statsPrefs.getInt("partidas_ganhas", 0) + 1)
                } else if (sala.pontosGuest > sala.pontosHost) {
                    editor.putInt("partidas_perdidas", statsPrefs.getInt("partidas_perdidas", 0) + 1)
                } else {
                    editor.putInt("partidas_empatadas", statsPrefs.getInt("partidas_empatadas", 0) + 1)
                }
                editor.apply()

                AlertDialog.Builder(this)
                    .setTitle("Fim do Jogo Online")
                    .setMessage(msgFinal)
                    .setPositiveButton("Sair") { _, _ ->
                        FirebaseManager.pararDeEscutar()
                        mostrarAnuncio { mostrarMenuPrincipal() } // Mostra o AdMob
                    }.setCancelable(false).show()
            }
        } else if (sala.turno == meuId) {
            txtVez.text = "A sua vez!"
            txtVez.setTextColor(Color.GREEN)
        } else {
            txtVez.text = "Vez do oponente..."
            txtVez.setTextColor(Color.RED)
        }
    }

    fun jogarCartaOnline(index: Int) {
        val sala = salaAtual ?: return
        val meuId = FirebaseManager.getMeuId() ?: return
        if (sala.turno != meuId) return

        vibrar(50)
        tocarSom(ToneGenerator.TONE_PROP_BEEP, 100)

        val souHostLocal = sala.hostId == meuId
        val minhaMao = if (souHostLocal) sala.maoHost.toMutableList() else sala.maoGuest.toMutableList()
        if (index >= minhaMao.size) return
        val cartaJogada = minhaMao.removeAt(index)

        var s = sala
        if (souHostLocal) s = s.copy(maoHost = minhaMao, mesaHost = cartaJogada, turno = sala.guestId)
        else s = s.copy(maoGuest = minhaMao, mesaGuest = cartaJogada, turno = sala.hostId)

        if (s.mesaHost != null && s.mesaGuest != null) {
            // Envia o estado com as duas cartas na mesa para os dois verem
            FirebaseManager.enviarJogada(sala.id, s)
            
            // Espera 1.5 segundos antes de avaliar (igual ao offline)
            mainHandler.postDelayed({
                val cH = Carta(s.mesaHost!!.valor, s.mesaHost!!.naipe)
            val cG = Carta(s.mesaGuest!!.valor, s.mesaGuest!!.naipe)
            var hostGanhou = false

            if (cH.naipe == cG.naipe) {
                if (valores.indexOf(cH.valor) < valores.indexOf(cG.valor)) hostGanhou = true
            } else {
                if (cH.naipe == s.trunfo) hostGanhou = true
                else if (cG.naipe == s.trunfo) hostGanhou = false
                else hostGanhou = (s.iniciouRodada == s.hostId)
            }

            val ptsH = pontosCartas[cH.valor] ?: 0
            val ptsG = pontosCartas[cG.valor] ?: 0
            val total = ptsH + ptsG

            var nPontosHost = s.pontosHost
            var nPontosGuest = s.pontosGuest
            if (hostGanhou) nPontosHost += total else nPontosGuest += total

            if (souHostLocal == hostGanhou) {
                vibrar(100)
                mostrarFeedback("Você ganhou a rodada!")
            } else {
                vibrar(80)
                mostrarFeedback("Oponente ganhou a rodada!")
            }

            val bara = s.baralho.toMutableList()
            val mHost = s.maoHost.toMutableList()
            val mGuest = s.maoGuest.toMutableList()
            
            if (bara.isNotEmpty()) {
                mHost.add(bara.removeAt(0))
                if (bara.isNotEmpty()) mGuest.add(bara.removeAt(0))
            }

            val proxTurno = if (hostGanhou) s.hostId else s.guestId
            var status = "JOGANDO"
            if (mHost.isEmpty() && mGuest.isEmpty()) status = "FINALIZADO"

                s = s.copy(
                    mesaHost = null, mesaGuest = null,
                    pontosHost = nPontosHost, pontosGuest = nPontosGuest,
                    baralho = bara, maoHost = mHost, maoGuest = mGuest,
                    turno = proxTurno, iniciouRodada = proxTurno, status = status
                )
                FirebaseManager.enviarJogada(sala.id, s)
            }, 1500)
        } else {
            FirebaseManager.enviarJogada(sala.id, s)
        }
    }

    // --- MODO OFFLINE (JOGO CONTRA IA) ---
    fun iniciarNovaPartida() {
        isJogoAtivo = true
        telaAtual = "JOGO"
        pontosJogador = 0
        pontosAndroid = 0
        jogadorComeca = true
        baralho.clear()
        maoJogador.clear()
        maoAndroid.clear()
        cartasJaJogadas.clear()
        cartaTrunfo = null
        cartaAndroidJogada = null
        cartaJogadorJogada = null
        trunfo = ""
        
        setContentView(R.layout.activity_game)
        configurarElementosTela()
        configurarBotoesDeJogo()
        iniciarRodada()
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
        btnVibracao = findViewById(R.id.btnVibracao)
    }

    fun configurarBotoesDeJogo() {
        findViewById<Button>(R.id.btnMenu).setOnClickListener {
            vibrar()
            salvarEstadoJogo()
            mostrarMenuPrincipal()
        }
        
        btnSom.text = if (somAtivado) "🔊" else "🔇"
        btnSom.setOnClickListener {
            vibrar()
            somAtivado = !somAtivado
            btnSom.text = if (somAtivado) "🔊" else "🔇"
            getSharedPreferences("sonino_prefs", Context.MODE_PRIVATE).edit().putBoolean("som_ativo", somAtivado).apply()
        }

        btnVibracao.text = if (vibracaoAtivada) "📳" else "📴"
        btnVibracao.setOnClickListener {
            vibracaoAtivada = !vibracaoAtivada
            btnVibracao.text = if (vibracaoAtivada) "📳" else "📴"
            if (vibracaoAtivada) vibrar(100)
            getSharedPreferences("sonino_prefs", Context.MODE_PRIVATE).edit().putBoolean("vibracao_ativa", vibracaoAtivada).apply()
        }
    }

    fun registrarDerrotaAbandono() {
        val prefs = getSharedPreferences("sonino_stats", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("total_torneios", prefs.getInt("total_torneios", 0) + 1)
        editor.putInt("torneios_perdidos", prefs.getInt("torneios_perdidos", 0) + 1)
        editor.putInt("total_partidas", prefs.getInt("total_partidas", 0) + 1)
        editor.putInt("partidas_perdidas", prefs.getInt("partidas_perdidas", 0) + 1)
        editor.apply()
    }

    fun salvarEstadoJogo() {
        val prefs = getSharedPreferences("sonino_save", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putBoolean("tem_jogo_salvo", true)
        
        editor.putString("baralho", baralho.joinToString(",") { "${it.valor}|${it.naipe}" })
        editor.putString("maoJogador", maoJogador.joinToString(",") { "${it.valor}|${it.naipe}" })
        editor.putString("maoAndroid", maoAndroid.joinToString(",") { "${it.valor}|${it.naipe}" })
        editor.putString("cartasJaJogadas", cartasJaJogadas.joinToString(",") { "${it.valor}|${it.naipe}" })
        editor.putString("trunfo", trunfo)
        editor.putString("cartaTrunfo", if (cartaTrunfo == null) "" else "${cartaTrunfo!!.valor}|${cartaTrunfo!!.naipe}")
        editor.putString("cartaAndroidJogada", if (cartaAndroidJogada == null) "" else "${cartaAndroidJogada!!.valor}|${cartaAndroidJogada!!.naipe}")
        editor.putString("cartaJogadorJogada", if (cartaJogadorJogada == null) "" else "${cartaJogadorJogada!!.valor}|${cartaJogadorJogada!!.naipe}")
        
        editor.putInt("pontosJogador", pontosJogador)
        editor.putInt("pontosAndroid", pontosAndroid)
        editor.putInt("partidasJogador", partidasJogador)
        editor.putInt("partidasAndroid", partidasAndroid)
        editor.putBoolean("jogadorComeca", jogadorComeca)
        
        editor.apply()
    }

    fun carregarEstadoJogo() {
        val prefs = getSharedPreferences("sonino_save", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("tem_jogo_salvo", false)) return
        
        fun parseCartas(str: String?): MutableList<Carta> {
            if (str.isNullOrEmpty()) return mutableListOf()
            return str.split(",").filter { it.isNotEmpty() }.map { 
                val p = it.split("|")
                Carta(p[0], p[1])
            }.toMutableList()
        }
        
        fun parseCarta(str: String?): Carta? {
            if (str.isNullOrEmpty()) return null
            val p = str.split("|")
            return Carta(p[0], p[1])
        }

        baralho = parseCartas(prefs.getString("baralho", ""))
        maoJogador = parseCartas(prefs.getString("maoJogador", ""))
        maoAndroid = parseCartas(prefs.getString("maoAndroid", ""))
        cartasJaJogadas = parseCartas(prefs.getString("cartasJaJogadas", ""))
        trunfo = prefs.getString("trunfo", "") ?: ""
        cartaTrunfo = parseCarta(prefs.getString("cartaTrunfo", ""))
        cartaAndroidJogada = parseCarta(prefs.getString("cartaAndroidJogada", ""))
        cartaJogadorJogada = parseCarta(prefs.getString("cartaJogadorJogada", ""))
        
        pontosJogador = prefs.getInt("pontosJogador", 0)
        pontosAndroid = prefs.getInt("pontosAndroid", 0)
        partidasJogador = prefs.getInt("partidasJogador", 0)
        partidasAndroid = prefs.getInt("partidasAndroid", 0)
        jogadorComeca = prefs.getBoolean("jogadorComeca", true)
        
        isJogoAtivo = true
    }

    fun atualizarTelaCompleta() {
        atualizarPlacar()
        
        if (cartaTrunfo != null) {
            mostrarCarta(txtTrunfoMesa, imgTrunfoMesa, cartaTrunfo)
            imgTrunfoMesa.visibility = View.VISIBLE
            txtTrunfoMesa.visibility = View.VISIBLE
        }
        
        imgMonte.visibility = if (baralho.isNotEmpty()) View.VISIBLE else View.GONE
        
        if (cartaAndroidJogada != null) {
            mostrarCarta(mesaAndroid, imgMesaAndroid, cartaAndroidJogada)
        } else {
            mostrarCarta(mesaAndroid, imgMesaAndroid, null)
        }
        
        if (cartaJogadorJogada != null) {
            mostrarCarta(mesaJogador, imgMesaJogador, cartaJogadorJogada)
        } else {
            mostrarCarta(mesaJogador, imgMesaJogador, null)
        }
        
        atualizarTela()
    }

    fun mostrarEstatisticas() {
        isJogoAtivo = false
        telaAtual = "STATS"
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
            vibrar()
            mostrarMenuPrincipal()
        }

        findViewById<Button>(R.id.btnResetStats).setOnClickListener {
            vibrar()
            AlertDialog.Builder(this).setTitle("Resetar Estatísticas")
                .setMessage("Tem certeza que deseja apagar todos os dados?")
                .setPositiveButton("Sim") { _, _ ->
                    prefs.edit().clear().apply()
                    mostrarEstatisticas()
                }
                .setNegativeButton("Não", null).show()
        }
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
        
        imgMesaJogador.translationX = 0f; imgMesaJogador.translationY = 0f; imgMesaJogador.alpha = 1f
        imgMesaAndroid.translationX = 0f; imgMesaAndroid.translationY = 0f; imgMesaAndroid.alpha = 1f

        cartaAndroidJogada = null
        cartaJogadorJogada = null
        
        atualizarTela()
        atualizarPlacar()

        if (isFirstDeal) {
            habilitarCliquesJogador(false)
            animarDistribuicao()
            mainHandler.postDelayed({
                if (!isJogoAtivo) return@postDelayed
                if (jogadorComeca) habilitarCliquesJogador(true)
                else androidJogaPrimeiro()
            }, 1600)
        } else {
            if (jogadorComeca) {
                habilitarCliquesJogador(true)
            } else {
                habilitarCliquesJogador(false)
                mainHandler.postDelayed({ 
                    if (isJogoAtivo) androidJogaPrimeiro() 
                }, 1000)
            }
        }
    }

    fun animarDistribuicao() {
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

    fun animarDescarte(jogadorGanhou: Boolean) {
        val targetArea = if (jogadorGanhou) findViewById<View>(R.id.areaJogador) else findViewById<View>(R.id.areaAndroid)
        val targetLoc = IntArray(2)
        targetArea.getLocationOnScreen(targetLoc)

        val viewsToAnimate = listOf(imgMesaJogador, imgMesaAndroid)
        for (v in viewsToAnimate) {
            if (v.visibility == View.VISIBLE) {
                val cardLoc = IntArray(2)
                v.getLocationOnScreen(cardLoc)
                v.animate()
                    .translationX((targetLoc[0] - cardLoc[0]).toFloat())
                    .translationY((targetLoc[1] - cardLoc[1]).toFloat())
                    .alpha(0f)
                    .setDuration(400)
                    .start()
            }
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
        
        vibrar(50)
        tocarSom(ToneGenerator.TONE_PROP_BEEP, 100)

        val cartaJogada = maoJogador.removeAt(index)
        cartaJogadorJogada = cartaJogada
        mostrarCarta(mesaJogador, imgMesaJogador, cartaJogada)
        atualizarTela()

        if (cartaAndroidJogada != null) {
            mainHandler.postDelayed({ 
                if (isJogoAtivo) avaliarRodada(cartaJogada, cartaAndroidJogada!!, false) 
            }, 800)
        } else {
            mainHandler.postDelayed({ 
                if (isJogoAtivo) androidResponde(cartaJogada) 
            }, 1000)
        }
    }

    fun androidJogaPrimeiro() {
        if (maoAndroid.isEmpty() || !isJogoAtivo) return
        
        var indexJogada = 0
        when (dificuldade) {
            "Fácil" -> indexJogada = maoAndroid.indices.random()
            "Difícil" -> {
                val naoTrunfos = maoAndroid.indices.filter { maoAndroid[it].naipe != trunfo }
                if (naoTrunfos.isNotEmpty()) indexJogada = naoTrunfos.minByOrNull { valores.indexOf(maoAndroid[it].valor) }!!
                else indexJogada = maoAndroid.indices.minByOrNull { valores.indexOf(maoAndroid[it].valor) }!!
            }
            "Mestre" -> {
                val ases = maoAndroid.indices.filter { maoAndroid[it].valor == "A" }
                if (ases.isNotEmpty()) indexJogada = ases.first()
                else {
                    val naoTrunfos = maoAndroid.indices.filter { maoAndroid[it].naipe != trunfo }
                    if (naoTrunfos.isNotEmpty()) indexJogada = naoTrunfos.minByOrNull { valores.indexOf(maoAndroid[it].valor) }!!
                    else indexJogada = maoAndroid.indices.minByOrNull { valores.indexOf(maoAndroid[it].valor) }!!
                }
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
        val pontosNaMesa = pontosCartas[cartaJogador.valor] ?: 0
        
        when (dificuldade) {
            "Fácil" -> indexJogada = maoAndroid.indices.random()
            "Difícil" -> {
                var cartasQueGanham = mutableListOf<Int>()
                for (i in maoAndroid.indices) {
                    val c = maoAndroid[i]
                    if (c.naipe == cartaJogador.naipe && valores.indexOf(c.valor) < valores.indexOf(cartaJogador.valor)) cartasQueGanham.add(i)
                    else if (c.naipe == trunfo && cartaJogador.naipe != trunfo) cartasQueGanham.add(i)
                }
                if (cartasQueGanham.isNotEmpty()) indexJogada = cartasQueGanham.maxByOrNull { valores.indexOf(maoAndroid[it].valor) }!!
                else indexJogada = maoAndroid.indices.minByOrNull { pontosCartas[maoAndroid[it].valor] ?: 0 }!!
            }
            "Mestre" -> {
                var cartasQueGanham = mutableListOf<Int>()
                for (i in maoAndroid.indices) {
                    val c = maoAndroid[i]
                    if (c.naipe == cartaJogador.naipe && valores.indexOf(c.valor) < valores.indexOf(cartaJogador.valor)) cartasQueGanham.add(i)
                    else if (c.naipe == trunfo && cartaJogador.naipe != trunfo) cartasQueGanham.add(i)
                }
                if (cartasQueGanham.isNotEmpty()) {
                    if (pontosNaMesa == 0 && !cartasQueGanham.any { maoAndroid[it].naipe != trunfo }) indexJogada = maoAndroid.indices.minByOrNull { pontosCartas[maoAndroid[it].valor] ?: 0 }!!
                    else indexJogada = cartasQueGanham.maxByOrNull { valores.indexOf(maoAndroid[it].valor) }!!
                } else indexJogada = maoAndroid.indices.minByOrNull { pontosCartas[maoAndroid[it].valor] ?: 0 }!!
            }
        }

        mainHandler.postDelayed({
            if (!isJogoAtivo) return@postDelayed
            tocarSom(ToneGenerator.TONE_PROP_BEEP, 100)
            val cartaAndroid = maoAndroid.removeAt(indexJogada)
            cartaAndroidJogada = cartaAndroid
            mostrarCarta(mesaAndroid, imgMesaAndroid, cartaAndroid)
            atualizarTela()

            mainHandler.postDelayed({ 
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

        cartasJaJogadas.add(cartaJogador)
        cartasJaJogadas.add(cartaAndroid)

        val pontosRodada = (pontosCartas[cartaJogador.valor] ?: 0) + (pontosCartas[cartaAndroid.valor] ?: 0)
        if (jogadorGanhou) {
            pontosJogador += pontosRodada
            vibrar(100) 
        } else {
            pontosAndroid += pontosRodada
            vibrar(80)
        }
        
        jogadorComeca = jogadorGanhou 
        atualizarPlacar()

        animarDescarte(jogadorGanhou)

        mainHandler.postDelayed({
            if (!isJogoAtivo) return@postDelayed
            mostrarCarta(mesaJogador, imgMesaJogador, null)
            mostrarCarta(mesaAndroid, imgMesaAndroid, null)
            cartaAndroidJogada = null
            cartaJogadorJogada = null
            
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
            vibrar(200)
        } else if (pontosAndroid >= 61 && pontosAndroid > pontosJogador) {
            partidasAndroid++
            mensagem = "O Android ganhou a partida!"
            tocarSom(ToneGenerator.TONE_CDMA_ABBR_ALERT, 1000)
            vibrar(150)
        } else if (pontosJogador == 60 && pontosAndroid == 60) {
            partidaEmpatada = true
            mensagem = "Empate na partida!"
            vibrar(100)
        } else {
            if (pontosJogador > pontosAndroid) {
                partidasJogador++
                partidaGanha = true
                mensagem = "Você ganhou a partida!"
                vibrar(200)
            } else {
                partidasAndroid++
                mensagem = "O Android ganhou a partida!"
                vibrar(150)
            }
        }
        
        atualizarPlacar()
        mostrarFeedback(mensagem)

        val prefs = getSharedPreferences("sonino_stats", Context.MODE_PRIVATE)
        var editor = prefs.edit()
        editor.putInt("total_pontos", prefs.getInt("total_pontos", 0) + pontosJogador)
        editor.putInt("total_partidas", prefs.getInt("total_partidas", 0) + 1)
        
        if (partidaGanha) editor.putInt("partidas_ganhas", prefs.getInt("partidas_ganhas", 0) + 1)
        else if (partidaEmpatada) editor.putInt("partidas_empatadas", prefs.getInt("partidas_empatadas", 0) + 1)
        else editor.putInt("partidas_perdidas", prefs.getInt("partidas_perdidas", 0) + 1)
        
        editor.apply()

        mainHandler.postDelayed({
            if (!isJogoAtivo) return@postDelayed
            
            if (partidasJogador == 2 || partidasAndroid == 2) {
                val vencedorFinal = if (partidasJogador == 2) "VOCÊ VENCEU O TORNEIO!" else "O ANDROID VENCEU O TORNEIO!"
                if (partidasJogador == 2) {
                    saldoMoedas += 100
                    getSharedPreferences("sonino_prefs", Context.MODE_PRIVATE).edit().putInt("saldo_moedas", saldoMoedas).apply()
                }
                mostrarFeedback(vencedorFinal)
                tocarSom(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
                vibrar(300)
                
                editor = prefs.edit()
                editor.putInt("total_torneios", prefs.getInt("total_torneios", 0) + 1)
                if (partidasJogador == 2) {
                    editor.putInt("torneios_ganhos", prefs.getInt("torneios_ganhos", 0) + 1)
                } else {
                    editor.putInt("torneios_perdidos", prefs.getInt("torneios_perdidos", 0) + 1)
                }
                editor.apply()

                getSharedPreferences("sonino_save", Context.MODE_PRIVATE).edit().clear().apply()

                mainHandler.postDelayed({
                    if (isJogoAtivo) mostrarAnuncio { mostrarMenuPrincipal() }
                }, 4000)
                
            } else {
                baralho.clear()
                maoJogador.clear()
                maoAndroid.clear()
                cartasJaJogadas.clear()
                iniciarNovaPartida() 
            }
        }, 4000)
    }

    fun mostrarFeedback(texto: String) {
        if (telaAtual == "ONLINE_GAME") {
            val txt = findViewById<TextView>(R.id.txtFeedbackOnline)
            txt.text = texto
            txt.visibility = View.VISIBLE
            txt.alpha = 0f
            txt.animate().alpha(1f).setDuration(300).withEndAction {
                mainHandler.postDelayed({
                    txt.animate().alpha(0f).setDuration(300).withEndAction { txt.visibility = View.GONE }
                }, 1000)
            }.start()
        } else {
            txtFeedbackRodada.text = texto
            txtFeedbackRodada.visibility = View.VISIBLE
            txtFeedbackRodada.alpha = 0f
            txtFeedbackRodada.animate().alpha(1f).setDuration(300).withEndAction {
                mainHandler.postDelayed({
                    txtFeedbackRodada.animate().alpha(0f).setDuration(300).withEndAction { txtFeedbackRodada.visibility = View.GONE }
                }, 1000)
            }.start()
        }
    }
}
