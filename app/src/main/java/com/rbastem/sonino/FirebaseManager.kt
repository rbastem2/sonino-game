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

    fun iniciarSessao(onSucesso: () -> Unit) {
        if (auth.currentUser != null) {
            Log.d("FirebaseDebug", "Sessão já iniciada: ${auth.currentUser?.uid}")
            onSucesso()
        } else {
            Log.d("FirebaseDebug", "A iniciar sessão anónima...")
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FirebaseDebug", "Login anónimo OK: ${auth.currentUser?.uid}")
                    onSucesso()
                } else {
                    Log.e("FirebaseDebug", "Erro no login anónimo", task.exception)
                }
            }
        }
    }

    fun getMeuId(): String? = auth.currentUser?.uid

    fun criarSala(onSalaCriada: (String) -> Unit, onErro: (String) -> Unit) {
        val meuId = getMeuId() ?: return onErro("Utilizador não autenticado")
        val codigoGerado = String.format("%04d", Random.nextInt(10000))
        Log.d("FirebaseDebug", "A criar sala com código: $codigoGerado")

        val novaSala = Sala(
            codigo = codigoGerado,
            hostId = meuId,
            status = "ESPERANDO",
            turno = meuId
        )

        db.collection("salas").document(codigoGerado).set(novaSala)
            .addOnSuccessListener { 
                Log.d("FirebaseDebug", "Sala criada com sucesso: $codigoGerado")
                onSalaCriada(codigoGerado) 
            }
            .addOnFailureListener { 
                Log.e("FirebaseDebug", "Erro ao criar sala", it)
                onErro(it.message ?: "Erro ao criar sala") 
            }
    }

    fun entrarSala(codigo: String, onSalaEntrada: (Sala) -> Unit, onErro: (String) -> Unit) {
        val meuId = getMeuId() ?: return onErro("Utilizador não autenticado")
        Log.d("FirebaseDebug", "A tentar entrar na sala: $codigo. Meu ID: $meuId")
        
        val salaRef = db.collection("salas").document(codigo)

        db.runTransaction { transaction ->
            Log.d("FirebaseDebug", "A iniciar transação para sala $codigo")
            val snapshot = transaction.get(salaRef)
            
            if (!snapshot.exists()) {
                Log.e("FirebaseDebug", "Transação: Sala não encontrada!")
                throw Exception("Sala não encontrada")
            }
            
            val sala = snapshot.toObject(Sala::class.java)!!
            Log.d("FirebaseDebug", "Transação: Sala encontrada. Status: ${sala.status}, Host: ${sala.hostId}")

            if (sala.status != "ESPERANDO") {
                Log.e("FirebaseDebug", "Transação: Sala não está ESPERANDO (status: ${sala.status})")
                throw Exception("A sala já está cheia ou o jogo já começou")
            }
            if (sala.hostId == meuId) {
                Log.e("FirebaseDebug", "Transação: Tentativa de entrar na própria sala")
                throw Exception("Não pode entrar na sua própria sala")
            }

            Log.d("FirebaseDebug", "Transação: A atualizar sala com guestId: $meuId")
            transaction.update(salaRef, "guestId", meuId, "status", "JOGANDO")
            sala.copy(guestId = meuId, status = "JOGANDO")
        }.addOnSuccessListener { salaAtualizada ->
            Log.d("FirebaseDebug", "Transação CONCLUÍDA com sucesso: $salaAtualizada")
            onSalaEntrada(salaAtualizada)
        }.addOnFailureListener {
            Log.e("FirebaseDebug", "Transação FALHOU", it)
            onErro(it.message ?: "Erro ao entrar na sala")
        }
    }

    fun escutarSala(codigo: String, onAtualizacao: (Sala) -> Unit) {
        listenerRegistration?.remove()
        listenerRegistration = db.collection("salas").document(codigo)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseDebug", "Erro no listener da sala", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val sala = snapshot.toObject(Sala::class.java)
                    if (sala != null) {
                        Log.d("FirebaseDebug", "Listener atualizou sala. Status: ${sala.status}")
                        onAtualizacao(sala)
                    }
                }
            }
    }

    fun pararDeEscutar() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }
}
