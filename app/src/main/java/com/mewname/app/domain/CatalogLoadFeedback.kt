package com.mewname.app.domain

import android.util.Log

enum class CatalogLoadArea {
    OCR,
    PRESETS,
    PVP
}

object CatalogLoadFeedback {
    fun message(area: CatalogLoadArea, language: AppLanguage): String {
        val messages = when (area) {
            CatalogLoadArea.OCR -> Triple("Nao foi possivel analisar a imagem.", "Could not analyze the image.", "No se pudo analizar la imagen.")
            CatalogLoadArea.PRESETS -> Triple("Nao foi possivel carregar os nomes salvos. Os padroes foram restaurados.", "Could not load saved presets. Default presets were restored.", "No se pudieron cargar los nombres guardados. Se restauraron los patrones predeterminados.")
            CatalogLoadArea.PVP -> Triple("Nao foi possivel carregar os dados de PvP.", "Could not load PvP data.", "No se pudieron cargar los datos de PvP.")
        }
        return when (language) {
            AppLanguage.PT_BR -> messages.first
            AppLanguage.EN -> messages.second
            AppLanguage.ES -> messages.third
        }
    }

    fun ocrTimeoutMessage(language: AppLanguage): String = when (language) {
        AppLanguage.PT_BR -> "A leitura da imagem demorou demais. Tente novamente com uma captura menor."
        AppLanguage.EN -> "Reading the image took too long. Try again with a smaller screenshot."
        AppLanguage.ES -> "La lectura de la imagen tardo demasiado. Intenta de nuevo con una captura mas pequena."
    }

    fun reportFailure(area: CatalogLoadArea, exception: Throwable) {
        Log.e("MewName${area.name}", "Failed to load ${area.name.lowercase()} data", exception)
    }
}