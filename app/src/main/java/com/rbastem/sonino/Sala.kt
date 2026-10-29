package com.rbastem.sonino

import com.google.firebase.firestore.DocumentId

data class CartaFire(val valor: String, val naipe: String)

data class Sala(
    @DocumentId val id: String = "",
    val codigo: String = "",
    val hostId: String = "",
    val guestId: String = "",
    val status: String = "ESPERANDO", // ESPERANDO, JOGANDO, FINALIZADO
    val turno: String = "", // ID do jogador que deve jogar
    val iniciouRodada: String = "", // ID de quem jogou a primeira carta desta rodada
    val trunfo: String = "",
    val baralho: List<CartaFire> = emptyList(),
    val maoHost: List<CartaFire> = emptyList(),
    val maoGuest: List<CartaFire> = emptyList(),
    val mesaHost: CartaFire? = null,
    val mesaGuest: CartaFire? = null,
    val pontosHost: Int = 0,
    val pontosGuest: Int = 0
)
