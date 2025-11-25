package com.codepalace.accelerometer

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

data class PontoFixo(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val nome: String = ""
)

object PontosFixosRepository {

    suspend fun getPontosFixos(): List<PontoFixo> {
        val ref = FirebaseDatabase.getInstance().getReference("pontos_fixos")
        val snapshot = ref.get().await()

        return snapshot.children.mapNotNull {
            it.getValue(PontoFixo::class.java)
        }
    }

    suspend fun criarPontosFixosSeNaoExistirem() {
        val ref = FirebaseDatabase.getInstance().getReference("pontos_fixos")

        val snapshot = ref.get().await()
        if (snapshot.exists()) return // evita recriar

        val pontos = mapOf(
            "1" to PontoFixo(-23.638725296201766, -46.546012666577845, "Buraco 1"),
            "2" to PontoFixo(-23.618493786022565, -46.57880951067621, "Buraco 2"),
            "3" to PontoFixo(-23.66420322, -46.50678892, "Buraco 3")
        )

        ref.setValue(pontos).await()
    }
}
