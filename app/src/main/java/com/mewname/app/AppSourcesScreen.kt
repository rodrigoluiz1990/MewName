package com.mewname.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSourcesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        AppTopBar(title = { Text(tr("Fontes de dados", "Data sources", "Fuentes de datos")) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.Back, tr("Voltar", "Back", "Volver"))
                }
            })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp,
                bottom = 16.dp + LocalNavigationContentPadding.current),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SourceCard("MewName", tr(
                    "Atributos, PC, golpes, famílias e regras de IV usam o catálogo local do app. A tabela de PC usa IVs de 10/10/10 a 15/15/15 para encontros e o máximo de 15/15/15 nos níveis 40, 50 e 51. Os níveis são referências de origem, não uma garantia de disponibilidade da espécie. O nível 51 inclui o bônus de Melhor Companheiro.",
                    "Stats, CP, moves, families and IV rules use the app's local catalog. CP ranges use 10/10/10 to 15/15/15 IVs for encounters and the 15/15/15 maximum at levels 40, 50 and 51. Levels are origin references, not a guarantee of species availability. Level 51 includes the Best Buddy boost.",
                    "Atributos, PC, ataques, familias y reglas de IV usan el catálogo local. Los encuentros muestran IV de 10/10/10 a 15/15/15 y los niveles 40, 50 y 51 el máximo de 15/15/15. Los niveles son referencias de origen, no garantizan la disponibilidad de la especie. El nivel 51 incluye el bono de Mejor Compañero."))
            }
            item { SourceCard("PvPoke", tr("Rankings competitivos, golpes recomendados e informações adicionais da Pokédex. Os dados ficam salvos por 24 horas; o ícone de atualizar permite uma nova consulta.", "Competitive rankings, recommended moves and additional Pokédex information. Data is cached for 24 hours; use the refresh icon to fetch it again.", "Rankings competitivos, ataques recomendados e información adicional de la Pokédex. Caché de 24 horas; el icono de actualizar permite una nueva consulta."), "https://pvpoke.com/", { openExternalUrl(context, it) }) }
            item { SourceCard("Pokémon GO", tr("Notícias oficiais e imagens dos eventos e temporadas da tela principal.", "Official news and event and season artwork on the home screen.", "Noticias oficiales e imágenes de eventos y temporadas en la pantalla principal."), "https://pokemongo.com/pt-BR/news", { openExternalUrl(context, it) }) }
            item { SourceCard("Leek Duck", tr("Catálogos de eventos, raids, ovos, pesquisas e Equipe GO Rocket.", "Event, raid, egg, research and Team GO Rocket catalogs.", "Catálogos de eventos, incursiones, huevos, investigaciones y Equipo GO Rocket."), "https://leekduck.com/", { openExternalUrl(context, it) }) }
            item { SourceCard("Pokébattler", tr("Dados de combate e sugestões de Pokémon para raids.", "Battle data and raid counter suggestions.", "Datos de combate y sugerencias para incursiones."), "https://www.pokebattler.com/raids", { openExternalUrl(context, it) }) }
            item { SourceCard("Pokémon GO · Kanto", tr("Disponibilidade das espécies originais de Kanto e de seus brilhantes, confirmada pelos anúncios oficiais do GO Tour. Não atribui a mesma data de estreia nem inclui formas regionais.", "Original Kanto species and shiny availability confirmed by official GO Tour announcements. Does not assign a shared debut date or include regional forms.", "Disponibilidad de especies originales de Kanto y sus variocolores confirmada por los anuncios oficiales del GO Tour. No asigna una fecha común ni incluye formas regionales."), "https://pokemongo.com/news/pokemongotour-kanto", { openExternalUrl(context, it) }) }
            item { SourceCard("Game Master · PokeMiners", tr("Dados offline de altura, peso, tamanhos, companheiro e custos da Pokédex. Catálogo consultado em 18/10/2026.", "Offline Pokédex height, weight, size, buddy and cost data. Catalog retrieved on 2026-10-18.", "Datos sin conexión de altura, peso, tamaños, compañero y costes de Pokédex. Catálogo consultado el 18/10/2026."), "https://github.com/PokeMiners/game_masters", { openExternalUrl(context, it) }) }
            item { SourceCard("Font Awesome Free 7.3.1", tr("Ícones de interface por Fonticons, Inc. (CC BY 4.0), convertidos para vetores Android. Licença incluída no aplicativo.", "Interface icons by Fonticons, Inc. (CC BY 4.0), converted to Android vectors. License bundled with the app.", "Iconos de interfaz de Fonticons, Inc. (CC BY 4.0), convertidos a vectores Android. Licencia incluida."), "https://fontawesome.com/license/free", { openExternalUrl(context, it) }) }
            item { SourceCard("PokeMiners", tr("Sprites e recursos visuais do Pokémon GO.", "Pokémon GO sprites and visual assets.", "Sprites y recursos visuales de Pokémon GO."), "https://github.com/PokeMiners/pogo_assets", { openExternalUrl(context, it) }) }
            item { SourceCard("Laboratório do Sam", tr("Calendário de eventos utilizado pelo app.", "Event calendar used by the app.", "Calendario de eventos utilizado por la app."), "https://rodrigoluiz1990.github.io/laboratorio-do-sam/Calendario/", { openExternalUrl(context, it) }) }
            item { SourceCard("Pokémon GO Hub", tr("Referência para consultar espécies, formas, ataques e PC. Selecione a forma desejada no site.", "Reference for species, forms, moves and CP. Select the desired form on the website.", "Referencia de especies, formas, ataques y PC. Selecciona la forma en el sitio."), "https://db.pokemongohub.net/", { openExternalUrl(context, it) }) }
            item { SourceCard("Stadium Gaming", tr("Consulta externa de IVs; selecione a espécie e a forma no site.", "External IV lookup; select the species and form on the website.", "Consulta externa de IV; selecciona la especie y la forma en el sitio."), "https://www.stadiumgaming.gg/rank-checker", { openExternalUrl(context, it) }) }
        }
    }
}

@Composable
private fun SourceCard(title: String, description: String, url: String? = null, onOpen: (String) -> Unit = {}) {
    AppSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (url != null) TextButton(onClick = { onOpen(url) }) {
                Text(lt(appLanguage(), "Abrir site", "Open website", "Abrir sitio"))
            }
        }
    }
}
