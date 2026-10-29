package com.rbastem.sonino

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlin.random.Random

object FirebaseManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listenerRegistration: ListenerRegistration? = null
    val naipes = listOf("♠", "♥", "♦", "♣")
    val valores = listOf("A", "7", "K", "J", "Q", "6", "5", "4", "3", "2")

    fun iniciarSessao(onPronto: () -> Unit, onErro: (String) -> Unit) {
        if (auth.currentUser != null) onPronto()
        else auth.signInAnonymously().addOnCompleteListener { if (it.isSuccessful) onPronto() else onErro("Erro auth") }
    }

    fun getMeuId(): String? = auth.currentUser?.uid

    fun criarSala(onSalaCriada: (String) -> Unit, onErro: (String) -> Unit) {
        val meuId = getMeuId() ?: return onErro("Não autenticado")
        val codigo = String.format("%04d", Random.nextInt(10000))
        val novaSala = Sala(codigo = codigo, hostId = meuId, status = "ESPERANDO", turno = meuId)
        db.collection("salas").document(codigo).set(novaSala).addOnSuccessListener { onSalaCriada(codigo) }.addOnFailureListener { onErro("Erro") }
    }

    fun entrarSala(codigo: String, onSalaEntrada: (Sala) -> Unit, onErro: (String) -> Unit) {
        val meuId = getMeuId() ?: return onErro("Não autenticado")
        val salaRef = db.collection("salas").document(codigo)
        salaRef.get().addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener onErro("Erro de rede")
            val snapshot = task.result
            if (snapshot == null || !snapshot.exists()) return@addOnCompleteListener onErro("Sala não encontrada")
            val sala = snapshot.toObject(Sala::class.java)!!
            if (sala.status != "ESPERANDO") return@addOnCompleteListener onErro("Sala cheia")
            if (sala.hostId == meuId) return@addOnCompleteListener onErro("Sua própria sala")
            salaRef.update("guestId", meuId, "status", "JOGANDO").addOnSuccessListener { onSalaEntrada(sala.copy(guestId = meuId, status = "JOGANDO")) }.addOnFailureListener { onErro("Erro ao entrar") }
        }
    }

    fun iniciarPartida(sala: Sala, onSalaAtualizada: (Sala) -> Unit) {
        val baralho = mutableListOf<CartaFire>()
        for (n in naipes) for (v in valores) baralho.add(CartaFire(v, n))
        baralho.shuffle()

        val maoHost = mutableListOf<CartaFire>()
        val maoGuest = mutableListOf<CartaFire>()
        for (i in 0 until 3) {
            maoHost.add(baralho.removeAt(0))
            maoGuest.add(baralho.removeAt(0))
        }

        val trunfo = baralho.last().naipe // Última carta define o trunfo

        val salaAtualizada = sala.copy(
            baralho = baralho,
            maoHost = maoHost,
            maoGuest = maoGuest,
            trunfo = trunfo,
            turno = sala.hostId,
            iniciouRodada = sala.hostId
        )
        db.collection("salas").document(sala.id).set(salaAtualizada).addOnSuccessListener { onSalaAtualizada(salaAtualizada) }
    }

    fun enviarJogada(salaId: String, salaAtualizada: Sala) {
        db.collection("salas").document(salaId).set(salaAtualizada)
    }

    fun escutarSala(codigo: String, onAtualizacao: (Sala) -> Unit) {
        listenerRegistration?.remove()
        listenerRegistration = db.collection("salas").document(codigo).addSnapshotListener { snapshot, e ->
            if (e == null && snapshot != null && snapshot.exists()) {
                val sala = snapshot.toObject(Sala::class.java)
                if (sala != null) onAtualizacao(sala)
            }
        }
    }

    fun pararDeEscutar() { listenerRegistration?.remove(); listenerRegistration = null }
}
