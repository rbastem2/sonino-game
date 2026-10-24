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
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
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

/**
 * Classe de dados que representa uma carta do baralho.
 * Exemplo: valor="A", naipe="♠"
 */
data class Carta(val valor: String, val naipe: String) {
    override fun toString(): String { return "$valor $naipe" }
}

/**
 * Activity principal do SONINO.
 * Gere o ciclo de vida do jogo, interface, regras e inteligência artificial.
 */
class MainActivity : AppCompatActivity() {
    
    // --- CONSTANTES DO JOGO ---
    val naipes = listOf("♠", "♥", "♦", "♣")
    val valores = listOf("A", "7", "K", "J", "Q", "6", "5", "4", "3", "2")
    // Mapeamento de pontos oficiais da Bisca (Ás=11, Sete=10, etc.)
    val pontosCartas = mapOf("A" to 11, "7" to 10, "K" to 4, "J" to 3, "Q" to 2)
    
    // --- ESTADO ATUAL DO JOGO ---
    var baralho = mutableListOf<Carta>()
    var maoJogador = mutableListOf<Carta>()
    var maoAndroid = mutableListOf<Carta>()
    var trunfo = ""
    var cartaTrunfo: Carta? = null
    var cartasJaJogadas = mutableListOf<Carta>() // Memória usada pela IA Mestre
    
    // Placar da Partida Atual
    var pontosJogador = 0
    var pontosAndroid = 0
    
    // Placar do Torneio (Melhor de 3)
    var partidasJogador = 0
    var partidasAndroid = 0
    
    var jogadorComeca = true // Define quem joga a primeira carta da rodada
    var dificuldade = "Difícil" // Nível da IA
    var somAtivado = true
    var vibracaoAtivada = true

    // --- FERRAMENTAS DE SISTEMA ---
    private var toneG: ToneGenerator? = null // Gerador de som nativo
    private var mInterstitialAd: InterstitialAd? = null // Anúncio do AdMob
    // QA FIX: Handler centralizado para evitar Memory Leaks ao fechar o app
    private val mainHandler = Handler(Looper.getMainLooper()) 

    // --- REFERÊNCIAS DA INTERFACE (VIEWS) ---
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
    
    // Cartas temporárias na mesa (para controle de estado)
    var cartaAndroidJogada: Carta? = null
    var cartaJogadorJogada: Carta? = null
    private var isJogoAtivo = false // Flag para evitar callbacks se o utilizador sair da tela

    /**
     * Ciclo de vida: Chamado quando a aplicação inicia.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Mostra o Splash Screen (Tela de abertura)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // 2. Edge-to-Edge (Desenha por trás da barra de status do telemóvel)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 3. Inicializa o áudio do sistema
        toneG = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        
        // 4. Inicializa os Anúncios (AdMob)
        MobileAds.initialize(this) {}
        carregarAnuncio()
        
        // 5. Carrega a dificuldade que o jogador escolheu por último
        val prefs = getSharedPreferences("sonino_prefs", Context.MODE_PRIVATE)
        dificuldade = prefs.getString("dificuldade_salva", "Difícil") ?: "Difícil"
        
        // 6. Abre o Menu Principal
        mostrarMenuPrincipal()
    }

    /**
     * Ciclo de vida: Chamado quando o utilizador sai do app ou muda de tela.
     */
    override fun onStop() {
        super.onStop()
        // Auto-Save: Se sair no meio do jogo, guarda o estado automaticamente
        if (isJogoAtivo) salvarEstadoJogo()
    }

    /**
     * Ciclo de vida: Chamado quando a tela vai ser destruída.
     */
    override fun onDestroy() {
        isJogoAtivo = false
        // QA FIX: Cancela todos os eventos atrasados pendentes para não crashar o app
        mainHandler.removeCallbacksAndMessages(null) 
        toneG?.release()
        super.onDestroy()
    }

    // =================================================================================
    // MÉTODOS DE FEEDBACK (SOM E VIBRAÇÃO)
    // =================================================================================

    /** Toca um som do sistema se o som estiver ativado */
    fun tocarSom(tipo: Int, duracao: Int) {
        if (somAtivado) toneG?.startTone(tipo, duracao)
    }

    /** Faz o telemóvel vibrar (Compatível com todas as versões do Android) */
    fun vibrar(duracao: Long = 50) {
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
                    // Amplitude 255 força a vibração máxima ignorando configurações do sistema
                    vibrator.vibrate(VibrationEffect.createOneShot(duracao, 120))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duracao)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // =================================================================================
    // MÉTODOS DO ADMOB (MONETIZAÇÃO)
    // =================================================================================

    /** Prepara um anúncio de tela cheia nos bastidores */
    fun carregarAnuncio() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-6316829094801811/4524927652", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
                override fun onAdFailedToLoad(adError: LoadAdError) { mInterstitialAd = null }
            })
    }

    /** Mostra o anúncio. Se falhar (ex: sem internet), continua o jogo na hora */
    fun mostrarAnuncio(aoFechar: () -> Unit) {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mInterstitialAd = null
                    carregarAnuncio() // Prepara o próximo
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
            aoFechar() // Se não tiver anúncio pronto, não bloqueia o jogador
        }
    }

    // =================================================================================
    // TELAS E NAVEGAÇÃO
    // =================================================================================

    /** Constrói a tela do Menu Principal e as suas funcionalidades */
    fun mostrarMenuPrincipal() {
        isJogoAtivo = false
        setContentView(R.layout.activity_main)

        // 1. Lógica do Bónus Diário
        findViewById<TextView>(R.id.btnBonusDiario).setOnClickListener {
            vibrar()
            val prefs = getSharedPreferences("sonino_bonus", Context.MODE_PRIVATE)
            val agoraMillis = System.currentTimeMillis()
            val ultimaDataMillis = prefs.getLong("ultima_data_millis", 0L)
            
            val tempoDecorrido = agoraMillis - ultimaDataMillis
            val umDiaEmMillis = 24 * 60 * 60 * 1000L

            if (tempoDecorrido in 0 until umDiaEmMillis) {
                // Já recebeu hoje
                AlertDialog.Builder(this)
                    .setTitle("Bónus Diário")
                    .setMessage("Você já resgatou o seu bónus hoje! Volte amanhã.")
                    .setPositiveButton("OK", null).show()
            } else if (tempoDecorrido < 0) {
                // Segurança: O jogador tentou mudar a hora do telemóvel
                AlertDialog.Builder(this)
                    .setTitle("Erro de Segurança")
                    .setMessage("A data do seu telemóvel está incorreta.")
                    .setPositiveButton("OK", null).show()
            } else {
                // Dá o bónus e calcula o streak (dias seguidos)
                var sequencia = prefs.getInt("sequencia", 0)
                if (tempoDecorrido > (2 * umDiaEmMillis)) sequencia = 1 else sequencia++
                
                val pontosBonus = sequencia * 50
                
                val statsPrefs = getSharedPreferences("sonino_stats", Context.MODE_PRIVATE)
                val totalAtual = statsPrefs.getInt("total_pontos", 0)
                statsPrefs.edit().putInt("total_pontos", totalAtual + pontosBonus).apply()
                
                prefs.edit().putLong("ultima_data_millis", agoraMillis).putInt("sequencia", sequencia).apply()
                
                AlertDialog.Builder(this)
                    .setTitle("Bónus Diário (Dia $sequencia)")
                    .setMessage("Você ganhou $pontosBonus pontos!")
                    .setPositiveButton("Receber", null).show()
                    
                tocarSom(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 1000)
            }
        }

        // 2. Botão Jogar (Verifica se há jogo salvo)
        findViewById<Button>(R.id.btnJogar).setOnClickListener {
            vibrar()
            val savePrefs = getSharedPreferences("sonino_save", Context.MODE_PRIVATE)
            if (savePrefs.getBoolean("tem_jogo_salvo", false)) {
                AlertDialog.Builder(this)
                    .setTitle("Jogo em Andamento")
                    .setMessage("Existe uma partida não terminada. Deseja continuar ou iniciar um novo jogo?")
                    .setPositiveButton("Continuar") { _, _ ->
                        // Restaura o jogo
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
                        // Abandona o jogo antigo e conta como derrota
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

        // 3. Botão Regras
        findViewById<Button>(R.id.btnRegras).setOnClickListener {
            vibrar()
            AlertDialog.Builder(this).setTitle("Regras do SONINO")
                .setMessage("• Baralho de 40 cartas.\n• Trunfo: última carta do baralho.\n• Ganha a rodada o trunfo mais forte ou a carta mais forte do naipe inicial.\n• Quem ganha, joga primeiro.\n• Vence quem fizer 61 pontos.\n• Torneio: melhor de 5 (o primeiro a vencer 3 partidas ganha).")
                .setPositiveButton("Entendi", null).show()
        }

        // 4. Botão Nível (Configura a dificuldade da IA)
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

        // 5. Botão Estatísticas
        findViewById<Button>(R.id.btnHistorico).setOnClickListener {
            vibrar()
            mostrarEstatisticas()
        }
    }

    /** Configura a navegação e os botões dentro do tabuleiro de jogo */
    fun configurarBotoesDeJogo() {
        // Botão de voltar (Salva o jogo e volta ao menu instantaneamente)
        findViewById<Button>(R.id.btnMenu).setOnClickListener {
            vibrar()
            salvarEstadoJogo()
            mostrarMenuPrincipal()
        }
        
        // Botão de ligar/desligar som
        btnSom.setOnClickListener {
            vibrar()
            somAtivado = !somAtivado
            btnSom.text = if (somAtivado) "🔊" else "🔇"
        }
        btnSom.text = if (somAtivado) "🔊" else "🔇"

        btnVibracao.setOnClickListener {
            vibracaoAtivada = !vibracaoAtivada
            btnVibracao.text = if (vibracaoAtivada) "📳" else "📴"
            if (vibracaoAtivada) vibrar(100) // Dá um feedback ao ligar
        }
        btnVibracao.text = if (vibracaoAtivada) "📳" else "📴" 
    }

    // =================================================================================
    // GESTÃO DE ESTADO (SAVE / LOAD)
    // =================================================================================

    /** Conta uma derrota e encerra o torneio atual nas estatísticas */
    fun registrarDerrotaAbandono() {
        val prefs = getSharedPreferences("sonino_stats", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("total_torneios", prefs.getInt("total_torneios", 0) + 1)
        editor.putInt("torneios_perdidos", prefs.getInt("torneios_perdidos", 0) + 1)
        editor.putInt("total_partidas", prefs.getInt("total_partidas", 0) + 1)
        editor.putInt("partidas_perdidas", prefs.getInt("partidas_perdidas", 0) + 1)
        editor.apply()
    }

    /** Serializa o estado atual do jogo para SharedPreferences */
    fun salvarEstadoJogo() {
        val prefs = getSharedPreferences("sonino_save", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putBoolean("tem_jogo_salvo", true)
        
        // Converte listas de objetos para String (Ex: "A|♠,7|♥")
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

    /** Reconstrói o estado do jogo a partir das SharedPreferences */
    fun carregarEstadoJogo() {
        val prefs = getSharedPreferences("sonino_save", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("tem_jogo_salvo", false)) return
        
        // Funções locais para converter String de volta para Objetos
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

    /** Atualiza a UI com as cartas do jogo salvo */
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

    /** Mostra a tela de estatísticas com os dados guardados */
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

        // Preenche os TextViews
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

    // =================================================================================
    // LÓGICA PRINCIPAL DO JOGO
    // =================================================================================

    /** Inicia uma nova partida limpando todos os estados anteriores */
    fun iniciarNovaPartida() {
        isJogoAtivo = true
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

    /** Mapeia os IDs dos XMLs para as variáveis no código */
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

    /** Procura a imagem da carta na pasta drawable. Ex: "a_copas" */
    fun getImagemCarta(carta: Carta): Int {
        val valorNome = if (carta.valor.toIntOrNull() != null) "n${carta.valor}" else carta.valor.lowercase()
        val naipeNome = when(carta.naipe) { "♠" -> "espadas"; "♥" -> "copas"; "♦" -> "ouros"; "♣" -> "paus"; else -> "" }
        return resources.getIdentifier("${valorNome}_${naipeNome}", "drawable", packageName)
    }

    /** Renderiza uma carta (Imagem ou Texto) num determinado local da tela */
    fun mostrarCarta(tv: TextView, img: ImageView, carta: Carta?) {
        if (carta == null) {
            tv.text = ""; img.visibility = ImageView.GONE; tv.visibility = TextView.GONE; return
        }
        val idImagem = getImagemCarta(carta)
        if (idImagem != 0) {
            // Usa a imagem
            img.setImageResource(idImagem); img.visibility = ImageView.VISIBLE; tv.visibility = TextView.GONE
        } else {
            // Fallback: Usa texto caso a imagem não exista
            img.visibility = ImageView.GONE; tv.visibility = TextView.VISIBLE
            tv.text = carta.toString()
            tv.setTextColor(if (carta.naipe == "♥" || carta.naipe == "♦") Color.RED else Color.BLACK)
        }
    }
    
    /** Atualiza o placar numérico no topo da tela */
    fun atualizarPlacar() {
        txtScoreJogador.text = "$pontosJogador pts"
        txtScoreAndroid.text = "$pontosAndroid pts"
        txtPartidas.text = "$partidasJogador x $partidasAndroid"
    }

    /** Prepara o baralho e distribui as cartas de uma nova rodada */
    fun iniciarRodada() {
        var isFirstDeal = false
        if (baralho.isEmpty() && maoJogador.isEmpty() && maoAndroid.isEmpty()) {
            // Cria o baralho de 40 cartas
            for (n in naipes) { for (v in valores) { baralho.add(Carta(v, n)) } }
            baralho.shuffle()
            
            // Define o trunfo (última carta)
            cartaTrunfo = baralho.last()
            trunfo = cartaTrunfo!!.naipe
            
            mostrarCarta(txtTrunfoMesa, imgTrunfoMesa, cartaTrunfo)
            imgTrunfoMesa.visibility = View.VISIBLE
            txtTrunfoMesa.visibility = View.VISIBLE

            // Distribui 3 cartas para cada jogador
            for (i in 0..2) {
                maoJogador.add(baralho.removeAt(0))
                maoAndroid.add(baralho.removeAt(0))
            }
            isFirstDeal = true
        }
        
        imgMonte.visibility = if (baralho.isNotEmpty()) View.VISIBLE else View.GONE

        // Limpa a mesa e reseta animações
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
            // Espera a animação terminar antes de liberar o jogo
            mainHandler.postDelayed({
                if (!isJogoAtivo) return@postDelayed
                if (jogadorComeca) habilitarCliquesJogador(true)
                else androidJogaPrimeiro()
            }, 1600)
        } else {
            // Rodada normal
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

    /** Cria o efeito visual das cartas a voarem do monte para as mãos */
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

    /** Efeito visual das cartas a voarem para o vencedor */
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

    /** Permite ou bloqueia o jogador de clicar nas cartas */
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

    /** Desenha as cartas atuais na mão do jogador */
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

    /** Ação disparada quando o jogador clica numa carta */
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
            // O Android já jogou, então avaliamos direto
            mainHandler.postDelayed({ 
                if (isJogoAtivo) avaliarRodada(cartaJogada, cartaAndroidJogada!!, false) 
            }, 800)
        } else {
            // Espera o Android jogar
            mainHandler.postDelayed({ 
                if (isJogoAtivo) androidResponde(cartaJogada) 
            }, 1000)
        }
    }

    /** A IA do Android joga primeiro (ela escolheu quem começa) */
    fun androidJogaPrimeiro() {
        if (maoAndroid.isEmpty() || !isJogoAtivo) return
        
        var indexJogada = 0
        when (dificuldade) {
            "Fácil" -> indexJogada = maoAndroid.indices.random() // Joga aleatório
            "Difícil" -> {
                // Tenta livrar-se das cartas sem pontos primeiro
                val naoTrunfos = maoAndroid.indices.filter { maoAndroid[it].naipe != trunfo }
                if (naoTrunfos.isNotEmpty()) indexJogada = naoTrunfos.minByOrNull { valores.indexOf(maoAndroid[it].valor) }!!
                else indexJogada = maoAndroid.indices.minByOrNull { valores.indexOf(maoAndroid[it].valor) }!!
            }
            "Mestre" -> {
                // Ataca com Ás (garante a rodada) ou joga a menor carta
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

        habilitarCliquesJogador(true) // Libera o jogador para responder
    }

    /** A IA do Android responde à jogada do jogador */
    fun androidResponde(cartaJogador: Carta) {
        if (maoAndroid.isEmpty() || !isJogoAtivo) return
        
        var indexJogada = 0
        val pontosNaMesa = pontosCartas[cartaJogador.valor] ?: 0
        
        when (dificuldade) {
            "Fácil" -> indexJogada = maoAndroid.indices.random()
            "Difícil" -> {
                // Encontra cartas que consigam ganhar a rodada
                var cartasQueGanham = mutableListOf<Int>()
                for (i in maoAndroid.indices) {
                    val c = maoAndroid[i]
                    if (c.naipe == cartaJogador.naipe && valores.indexOf(c.valor) < valores.indexOf(cartaJogador.valor)) cartasQueGanham.add(i)
                    else if (c.naipe == trunfo && cartaJogador.naipe != trunfo) cartasQueGanham.add(i)
                }
                // Usa a menor carta possível para ganhar e guarda as boas
                if (cartasQueGanham.isNotEmpty()) indexJogada = cartasQueGanham.maxByOrNull { valores.indexOf(maoAndroid[it].valor) }!!
                else indexJogada = maoAndroid.indices.minByOrNull { pontosCartas[maoAndroid[it].valor] ?: 0 }!!
            }
            "Mestre" -> {
                // Inteligência avançada (Mestre)
                var cartasQueGanham = mutableListOf<Int>()
                for (i in maoAndroid.indices) {
                    val c = maoAndroid[i]
                    if (c.naipe == cartaJogador.naipe && valores.indexOf(c.valor) < valores.indexOf(cartaJogador.valor)) cartasQueGanham.add(i)
                    else if (c.naipe == trunfo && cartaJogador.naipe != trunfo) cartasQueGanham.add(i)
                }
                if (cartasQueGanham.isNotEmpty()) {
                    // Não gasta trunfo para ganhar rodada sem pontos
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

    /** Verifica quem ganhou a rodada e distribui os pontos */
    fun avaliarRodada(cartaJogador: Carta, cartaAndroid: Carta, jogadorFoiPrimeiro: Boolean) {
        var jogadorGanhou = false
        
        // Regras de vencedor da rodada
        if (cartaJogador.naipe == cartaAndroid.naipe) {
            if (valores.indexOf(cartaJogador.valor) < valores.indexOf(cartaAndroid.valor)) jogadorGanhou = true
        } else {
            if (cartaJogador.naipe == trunfo) jogadorGanhou = true
            else if (cartaAndroid.naipe == trunfo) jogadorGanhou = false
            else jogadorGanhou = jogadorFoiPrimeiro // Ninguém jogou trunfo, quem começou leva
        }

        cartasJaJogadas.add(cartaJogador)
        cartasJaJogadas.add(cartaAndroid)

        // Soma os pontos
        val pontosRodada = (pontosCartas[cartaJogador.valor] ?: 0) + (pontosCartas[cartaAndroid.valor] ?: 0)
        
        if (jogadorGanhou) {
            pontosJogador += pontosRodada
            vibrar(100) 
        } else {
            pontosAndroid += pontosRodada
            vibrar(80)
        }
        
        jogadorComeca = jogadorGanhou // O vencedor começa a próxima
        atualizarPlacar()

        animarDescarte(jogadorGanhou)

        // Prepara para a próxima rodada após 1.5 segundos
        mainHandler.postDelayed({
            if (!isJogoAtivo) return@postDelayed
            mostrarCarta(mesaJogador, imgMesaJogador, null)
            mostrarCarta(mesaAndroid, imgMesaAndroid, null)
            cartaAndroidJogada = null
            cartaJogadorJogada = null
            
            // Compra cartas novas se houver no baralho
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
                finalizarPartida() // Acabaram as cartas
            } else {
                iniciarRodada()
            }
        }, 1500)
    }

    /** Chamado quando o baralho e as mãos ficam vazios */
    fun finalizarPartida() {
        if (!isJogoAtivo) return
        
        var mensagem = ""
        var partidaGanha = false
        var partidaEmpatada = false

        // Regra de vitória (61 pontos)
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
            // Caso ninguém chegue a 61, quem tiver mais pontos ganha
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

        // Guarda as estatísticas da partida
        val prefs = getSharedPreferences("sonino_stats", Context.MODE_PRIVATE)
        var editor = prefs.edit()
        editor.putInt("total_pontos", prefs.getInt("total_pontos", 0) + pontosJogador)
        editor.putInt("total_partidas", prefs.getInt("total_partidas", 0) + 1)
        
        if (partidaGanha) editor.putInt("partidas_ganhas", prefs.getInt("partidas_ganhas", 0) + 1)
        else if (partidaEmpatada) editor.putInt("partidas_empatadas", prefs.getInt("partidas_empatadas", 0) + 1)
        else editor.putInt("partidas_perdidas", prefs.getInt("partidas_perdidas", 0) + 1)
        
        editor.apply()

        // Espera para o jogador ler a mensagem
        mainHandler.postDelayed({
            if (!isJogoAtivo) return@postDelayed
            
            // Verifica se o torneio (melhor de 3) acabou
            if (partidasJogador == 3 || partidasAndroid == 3) {
                val vencedorFinal = if (partidasJogador == 3) "VOCÊ VENCEU O TORNEIO!" else "O ANDROID VENCEU O TORNEIO!"
                mostrarFeedback(vencedorFinal)
                tocarSom(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
                vibrar(300)
                
                // Salva estatísticas do torneio
                editor = prefs.edit()
                editor.putInt("total_torneios", prefs.getInt("total_torneios", 0) + 1)
                if (partidasJogador == 2) {
                    editor.putInt("torneios_ganhos", prefs.getInt("torneios_ganhos", 0) + 1)
                } else {
                    editor.putInt("torneios_perdidos", prefs.getInt("torneios_perdidos", 0) + 1)
                }
                editor.apply()

                // Apaga o auto-save, pois o torneio já terminou
                getSharedPreferences("sonino_save", Context.MODE_PRIVATE).edit().clear().apply()

                mainHandler.postDelayed({
                    if (isJogoAtivo) {
                        // Mostra o anúncio antes de voltar ao menu
                        mostrarAnuncio { mostrarMenuPrincipal() }
                    }
                }, 4000)
                
            } else {
                // Vai para a próxima partida do torneio
                baralho.clear()
                maoJogador.clear()
                maoAndroid.clear()
                cartasJaJogadas.clear()
                iniciarNovaPartida() 
            }
        }, 4000)
    }

    /** Mostra uma pequena caixa de texto no centro do ecrã */
    fun mostrarFeedback(texto: String) {
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
