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

    // Garante que o jogador tem um ID (Login Anónimo)
    fun iniciarSessao(onSucesso: () -> Unit) {
        if (auth.currentUser != null) {
            onSucesso()
        } else {
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Firebase", "Login anónimo com sucesso: ${auth.currentUser?.uid}")
                    onSucesso()
                } else {
                    Log.e("Firebase", "Erro no login anónimo", task.exception)
                }
            }
        }
    }

    fun getMeuId(): String? = auth.currentUser?.uid

    // Cria uma sala com um código aleatório de 4 dígitos
    fun criarSala(onSalaCriada: (String) -> Unit, onErro: (String) -> Unit) {
        val meuId = getMeuId() ?: return onErro("Utilizador não autenticado")
        val codigoGerado = String.format("%04d", Random.nextInt(10000))

        val novaSala = Sala(
            codigo = codigoGerado,
            hostId = meuId,
            status = "ESPERANDO",
            turno = meuId
        )

        db.collection("salas").document(codigoGerado).set(novaSala)
            .addOnSuccessListener { onSalaCriada(codigoGerado) }
            .addOnFailureListener { onErro(it.message ?: "Erro ao criar sala") }
    }

    // Entra numa sala existente usando o código
    fun entrarSala(codigo: String, onSalaEntrada: (Sala) -> Unit, onErro: (String) -> Unit) {
        val meuId = getMeuId() ?: return onErro("Utilizador não autenticado")
        val salaRef = db.collection("salas").document(codigo)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(salaRef)
            if (!snapshot.exists()) {
                throw Exception("Sala não encontrada")
            }
            val sala = snapshot.toObject(Sala::class.java)!!
            
            if (sala.status != "ESPERANDO") {
                throw Exception("A sala já está cheia ou o jogo já começou")
            }
            if (sala.hostId == meuId) {
                throw Exception("Não pode entrar na sua própria sala")
            }

            transaction.update(salaRef, "guestId", meuId, "status", "JOGANDO")
            sala.copy(guestId = meuId, status = "JOGANDO")
        }.addOnSuccessListener { salaAtualizada ->
            onSalaEntrada(salaAtualizada)
        }.addOnFailureListener {
            onErro(it.message ?: "Erro ao entrar na sala")
        }
    }

    // Ouve as alterações na sala em tempo real
    fun escutarSala(codigo: String, onAtualizacao: (Sala) -> Unit) {
        listenerRegistration?.remove() // Remove ouvinte anterior, se existir
        listenerRegistration = db.collection("salas").document(codigo)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("Firebase", "Erro no listener", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val sala = snapshot.toObject(Sala::class.java)
                    if (sala != null) onAtualizacao(sala)
                }
            }
    }

    fun pararDeEscutar() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }
}
