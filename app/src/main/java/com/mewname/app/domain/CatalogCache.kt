package com.mewname.app.domain

/** Coordinates invalidation of catalog data after an asset update or language change. */
object CatalogCache {
    fun invalidateAssets() {
        GameTextRepository.clearCache()
        GameInfoRepository.clearCache()
        GameCatalogRepository.clearCache()
        PokemonMoveRepository.clearCache()
    }

    fun invalidateLanguage(language: AppLanguage) {
        GameTextRepository.clearCache(language)
        GameInfoRepository.clearCache(language)
        PokemonMoveRepository.clearCache(language)
    }
}