package com.rbastem.sonino

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
    val pontosCartas = mapOf("A" to 11, "7" to 10, "K" to 4, "J" to 3, "Q" to 2)
    
    var baralho = mutableListOf<Carta>()
    var maoJogador = mutableListOf<Carta>()
    var maoAndroid = mutableListOf<Carta>()
    var trunfo = ""
    var pontosJogador = 0
    var pontosAndroid = 0

    lateinit var uiCartasJogador: List<TextView>
    lateinit var uiImgsJogador: List<ImageView>
    lateinit var mesaJogador: TextView
    lateinit var mesaAndroid: TextView
    lateinit var imgMesaJogador: ImageView
    lateinit var imgMesaAndroid: ImageView
    lateinit var txtPlacarJogador: TextView
    lateinit var txtPlacarAndroid: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mostrarMenuPrincipal()
    }

    fun mostrarMenuPrincipal() {
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnJogar).setOnClickListener {
            pontosJogador = 0
            pontosAndroid = 0
            setContentView(R.layout.activity_game)
            configurarElementosTela()
            iniciarJogo()
        }

        findViewById<Button>(R.id.btnRegras).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Regras do SONINO").setMessage("1. Cada jogador recebe 3 cartas.\n2. O trunfo é definido pela última carta do baralho.\n3. Ganha a rodada o trunfo mais forte ou a carta mais forte do naipe inicial.\n4. O vencedor é quem fizer 61 pontos ou mais.").setPositiveButton("Entendi", null).show()
        }

        findViewById<Button>(R.id.btnConfig).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Configurações").setMessage("Em breve poderá escolher a dificuldade do Android aqui!").setPositiveButton("OK", null).show()
        }
    }

    fun configurarElementosTela() {
        uiCartasJogador = listOf(findViewById(R.id.cartaJogador1), findViewById(R.id.cartaJogador2), findViewById(R.id.cartaJogador3))
        uiImgsJogador = listOf(findViewById(R.id.imgJogador1), findViewById(R.id.imgJogador2), findViewById(R.id.imgJogador3))
        mesaJogador = findViewById(R.id.cartaMesaJogador)
        mesaAndroid = findViewById(R.id.cartaMesaAndroid)
        imgMesaJogador = findViewById(R.id.imgMesaJogador)
        imgMesaAndroid = findViewById(R.id.imgMesaAndroid)
        
        // Referências para os placares no topo (precisamos achar os textos)
        val topo = findViewById<android.widget.LinearLayout>(R.id.topoPlacar)
        txtPlacarJogador = topo.getChildAt(0) as TextView
        txtPlacarAndroid = topo.getChildAt(2) as TextView
    }

    fun getImagemCarta(carta: Carta): Int {
        val valorNome = carta.valor.lowercase()
        val naipeNome = when(carta.naipe) {
            "♠" -> "espadas"
            "♥" -> "copas"
            "♦" -> "ouros"
            "♣" -> "paus"
            else -> ""
        }
        val nomeImagem = "${valorNome}_${naipeNome}"
        return resources.getIdentifier(nomeImagem, "drawable", packageName)
    }

    fun mostrarCarta(tv: TextView, img: ImageView, carta: Carta?) {
        if (carta == null) {
            tv.text = ""
            img.visibility = ImageView.GONE
            tv.visibility = TextView.GONE
            return
        }

        val idImagem = getImagemCarta(carta)
        if (idImagem != 0) {
            img.setImageResource(idImagem)
            img.visibility = ImageView.VISIBLE
            tv.visibility = TextView.GONE
        } else {
            img.visibility = ImageView.GONE
            tv.visibility = TextView.VISIBLE
            tv.text = carta.toString()
            tv.setTextColor(if (carta.naipe == "♥" || carta.naipe == "♦") Color.RED else Color.BLACK)
        }
    }
    
    fun atualizarPlacar() {
        txtPlacarJogador.text = "👤 Você: $pontosJogador"
        txtPlacarAndroid.text = "🤖 Android: $pontosAndroid"
    }

    fun iniciarJogo() {
        baralho.clear()
        for (n in naipes) { for (v in valores) { baralho.add(Carta(v, n)) } }
        baralho.shuffle()
        trunfo = baralho.last().naipe
        findViewById<TextView>(R.id.txtTrunfo).text = "Trunfo: $trunfo"

        for (i in 0..2) {
            maoJogador.add(baralho.removeAt(0))
            maoAndroid.add(baralho.removeAt(0))
        }
        atualizarTela()
        atualizarPlacar()
    }

    fun atualizarTela() {
        for (i in 0..2) {
            if (i < maoJogador.size) {
                mostrarCarta(uiCartasJogador[i], uiImgsJogador[i], maoJogador[i])
                uiCartasJogador[i].setOnClickListener { jogarCarta(i) }
                uiImgsJogador[i].setOnClickListener { jogarCarta(i) }
            } else {
                mostrarCarta(uiCartasJogador[i], uiImgsJogador[i], null)
            }
        }
    }

    fun jogarCarta(index: Int) {
        if (maoJogador.size <= index) return
        val cartaJogada = maoJogador.removeAt(index)
        mostrarCarta(mesaJogador, imgMesaJogador, cartaJogada)
        atualizarTela()
        uiCartasJogador.forEach { it.setOnClickListener(null) }
        uiImgsJogador.forEach { it.setOnClickListener(null) }

        Handler(Looper.getMainLooper()).postDelayed({ androidPensa(cartaJogada) }, 1000)
    }

    fun androidPensa(cartaJogador: Carta) {
        if (maoAndroid.isEmpty()) return
        var indexJogada = 0
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
                val p = pontosCartas[maoAndroid[i].valor] ?: 0
                if (p < menorValor) { menorValor = p; indexJogada = i }
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

        val pontosRodada = (pontosCartas[cartaJogador.valor] ?: 0) + (pontosCartas[cartaAndroid.valor] ?: 0)
        if (jogadorGanhou) pontosJogador += pontosRodada else pontosAndroid += pontosRodada
        
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
            atualizarTela()

            if (maoJogador.isEmpty() && maoAndroid.isEmpty()) finalizarJogo()
        }, 1000)
    }

    fun finalizarJogo() {
        val mensagem = "Fim de Jogo!\n\nVocê: $pontosJogador pontos\nAndroid: $pontosAndroid pontos\n\n" +
                if (pontosJogador >= 61) "VOCÊ VENCEU! 🏆" else if (pontosAndroid >= 61) "O ANDROID VENCEU! 🤖" else "EMPATE! 🤝"

        AlertDialog.Builder(this).setTitle("SONINO").setMessage(mensagem).setCancelable(false)
            .setPositiveButton("Voltar ao Menu") { _, _ ->
                mostrarMenuPrincipal()
            }.show()
    }
}
