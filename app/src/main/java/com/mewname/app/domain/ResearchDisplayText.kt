package com.mewname.app.domain

internal object ResearchDisplayText {
    private fun text(language: AppLanguage, pt: String, en: String, es: String) = when (language) {
        AppLanguage.PT_BR -> pt; AppLanguage.EN -> en; AppLanguage.ES -> es
    }
    fun reward(label: String, terms: Map<String, String>, language: AppLanguage): String {
        val quantity = Regex("\\s*[×x]\\s*\\d+\\s*$").find(label)
        val name = label.substring(0, quantity?.range?.first ?: label.length).trim()
        val mega = Regex("(.+) Mega Energy", RegexOption.IGNORE_CASE).matchEntire(name)
        val translated = terms[catalogText(name)] ?: when {
            mega != null -> text(language, "Megaenergia de ", "Mega Energy: ", "Megaenergía de ") + pokemonDisplayName(mega.groupValues[1])
            name == "XP" -> "XP"
            name == "Rare Candy XL" -> text(language, "Doce Raro GG", name, "Caramelo Raro XL")
            else -> pokemonDisplayName(name)
        }
        return translated + quantity?.value.orEmpty()
    }
    fun detail(value: String, language: AppLanguage): String = value
        .replace("Max CP", text(language, "PC máximo", "Max CP", "PC máximo"))
        .replace("Min CP", text(language, "PC mínimo", "Min CP", "PC mínimo"))

    fun group(value: String, language: AppLanguage): String {
        if (language == AppLanguage.EN) return value
        val labels = mapOf(
            "Catching Tasks" to text(language, "Capturas", value, "Capturas"),
            "Throwing Tasks" to text(language, "Arremessos", value, "Lanzamientos"),
            "Battling Tasks" to text(language, "Batalhas", value, "Combates"),
            "Exploring Tasks" to text(language, "Exploração", value, "Exploración"),
            "Training Tasks" to text(language, "Treinamento", value, "Entrenamiento"),
            "Buddy & Friendship Tasks" to text(language, "Companheiro e amizade", value, "Compañero y amistad"),
            "Team GO Rocket Tasks" to text(language, "Equipe GO Rocket", value, "Equipo GO Rocket"),
            "Sponsored Tasks" to text(language, "Pesquisas patrocinadas", value, "Investigaciones patrocinadas")
        )
        return labels[value] ?: value
            .replace("Community Day Classic", text(language, "Dia Comunitário Clássico", "", "Día de la Comunidad Clásico"))
            .replace("Special backgrounds", text(language, "Fundos especiais", "", "Fondos especiales"))
            .replace("Mega Squads", text(language, "Esquadrões Mega", "", "Escuadrones Mega"))
            .replace(Regex("September (\\d+)")) { it.groupValues[1] + text(language, " de setembro", "", " de septiembre") }
            .removeSuffix(" Tasks")
    }
}