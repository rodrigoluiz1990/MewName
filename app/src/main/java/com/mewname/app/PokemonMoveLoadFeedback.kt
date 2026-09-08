package com.mewname.app

import android.util.Log
import com.mewname.app.domain.AppLanguage
import com.mewname.app.domain.LoadState
import com.mewname.app.domain.PokemonMoveSet

object PokemonMoveLoadFeedback {
    private const val logTag = "MewNameMoves"

    fun message(
        state: LoadState<PokemonMoveSet>,
        pokemonName: String?,
        language: AppLanguage
    ): String? {
        return when (state) {
            LoadState.Loading -> localized(language, "Carregando ataques...", "Loading moves...", "Cargando ataques...")
            LoadState.Empty -> {
                val name = pokemonName?.trim().orEmpty()
                if (name.isBlank()) {
                    localized(language, "Pokemon nao identificado para carregar os ataques.", "Pokemon was not identified to load moves.", "No se identifico el Pokemon para cargar los ataques.")
                } else {
                    localized(language, "Nenhum ataque cadastrado para $name.", "No moves are registered for $name.", "No hay ataques registrados para $name.")
                }
            }
            is LoadState.Error -> localized(
                language,
                "Nao foi possivel carregar os ataques. Consulte o log do app.",
                "Could not load moves. Check the app log.",
                "No se pudieron cargar los ataques. Consulta el registro de la app."
            )
            is LoadState.Success -> null
        }
    }

    fun reportFailure(pokemonName: String, exception: Throwable) {
        Log.e(logTag, "Failed to load moves for $pokemonName", exception)
    }

    private fun localized(language: AppLanguage, pt: String, en: String, es: String): String {
        return when (language) {
            AppLanguage.PT_BR -> pt
            AppLanguage.EN -> en
            AppLanguage.ES -> es
        }
    }
}