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

    // Agora recebe um callback que só é chamado quando o utilizador tem um ID válido
    fun iniciarSessao(onPronto: () -> Unit, onErro: (String) -> Unit) {
        if (auth.currentUser != null) {
            Log.d("FirebaseDebug", "Sessão já ativa: ${auth.currentUser?.uid}")
            onPronto()
        } else {
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FirebaseDebug", "Login anónimo OK: ${auth.currentUser?.uid}")
                    onPronto()
                } else {
                    Log.e("FirebaseDebug", "Erro no login anónimo", task.exception)
                    onErro("Falha na autenticação: ${task.exception?.message}")
                }
            }
        }
    }

    fun getMeuId(): String? = auth.currentUser?.uid

    fun criarSala(onSalaCriada: (String) -> Unit, onErro: (String) -> Unit) {
        val meuId = getMeuId() ?: return onErro("Utilizador não autenticado. Tente novamente.")
        val codigoGerado = String.format("%04d", Random.nextInt(10000))

        val novaSala = Sala(codigo = codigoGerado, hostId = meuId, status = "ESPERANDO", turno = meuId)

        db.collection("salas").document(codigoGerado).set(novaSala)
            .addOnSuccessListener { onSalaCriada(codigoGerado) }
            .addOnFailureListener { onErro(it.message ?: "Erro ao criar sala") }
    }

    fun entrarSala(codigo: String, onSalaEntrada: (Sala) -> Unit, onErro: (String) -> Unit) {
        val meuId = getMeuId() ?: return onErro("Utilizador não autenticado. Tente novamente.")
        val salaRef = db.collection("salas").document(codigo)

        salaRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) { onErro("Sala não encontrada"); return@addOnSuccessListener }
            val sala = snapshot.toObject(Sala::class.java)
            if (sala == null) { onErro("Erro ao ler dados"); return@addOnSuccessListener }
            if (sala.status != "ESPERANDO") { onErro("A sala já está cheia"); return@addOnSuccessListener }
            if (sala.hostId == meuId) { onErro("Não pode entrar na sua própria sala"); return@addOnSuccessListener }

            salaRef.update("guestId", meuId, "status", "JOGANDO")
                .addOnSuccessListener { onSalaEntrada(sala.copy(guestId = meuId, status = "JOGANDO")) }
                .addOnFailureListener { onErro("Falha ao atualizar: ${it.message}") }
        }.addOnFailureListener { onErro("Falha na rede: ${it.message}") }
    }

    fun escutarSala(codigo: String, onAtualizacao: (Sala) -> Unit) {
        listenerRegistration?.remove()
        listenerRegistration = db.collection("salas").document(codigo)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.e("FirebaseDebug", "Erro no listener", e); return@addSnapshotListener }
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
