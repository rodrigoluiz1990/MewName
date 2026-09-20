package com.mewname.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.BattleAdvice
import com.mewname.app.domain.BattleAdvisor
import com.mewname.app.domain.BattleSuggestionEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MaxBattleSuggestionsScreen(
    advice: BattleAdvice,
    showLogAction: Boolean,
    onCopy: (String) -> Unit,
    onExportLog: () -> Unit,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val language = appLanguage()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    val localizedFilter by produceState(initialValue = advice.copyText, advice) {
        value = withContext(Dispatchers.IO) {
            BattleAdvisor.copyTextFor(context, advice.mode, advice.suggestions)
        }.ifBlank { advice.copyText }
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    val attackers = advice.suggestions.take(10)
    val defenders = advice.defenderSuggestions.ifEmpty { advice.suggestions }.take(10)
    val displayed = if (selectedTab == 0) attackers else defenders

    Scaffold(
        topBar = {
            Column {
                Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(42.dp, 4.dp).background(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        )
                    )
                }
                AppTopBar(
                    title = { Text(lt(language, "Detalhes da Batalha Max", "Max Battle details", "Detalles del Combate Max")) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = lt(language, "Fechar", "Close", "Cerrar"))
                        }
                    }
                )
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppActionButton(
                    onClick = {
                        onCopy(localizedFilter)
                        copied = true
                    },
                    enabled = localizedFilter.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (copied) lt(language, "Copiado!", "Copied!", "¡Copiado!") else lt(language, "Copiar filtro", "Copy filter", "Copiar filtro"))
                }
                if (showLogAction) {
                    AppActionButton(onClick = onExportLog, secondary = true) {
                        Text("Log")
                    }
                }

            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(LocalAppAppearance.current.background)).padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppSectionCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RaidPortrait(maxPortraitId(advice.bossName), offline = true, modifier = Modifier.size(72.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                advice.bossName ?: lt(language, "Chefe não identificado", "Boss not identified", "Jefe no identificado"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            MaxTypeGroup(
                                title = lt(language, "Tipos", "Types", "Tipos"),
                                types = advice.bossTypes,
                                emptyText = lt(language, "Não identificados", "Not identified", "No identificados")
                            )
                        }
                    }
                    if (advice.bossName.isNullOrBlank()) {
                        Text(
                            lt(
                                language,
                                "Capture novamente com o nome do chefe visível. As sugestões abaixo usam apenas o elenco Max disponível.",
                                "Capture again with the boss name visible. Suggestions below only use the available Max roster.",
                                "Captura de nuevo con el nombre del jefe visible. Las sugerencias usan solo el plantel Max disponible."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MaxTypeGroup(
                        title = lt(language, "Fraquezas", "Weaknesses", "Debilidades"),
                        types = advice.weaknessTypes,
                        emptyText = lt(language, "Aguardando o tipo do chefe", "Waiting for boss typing", "Esperando el tipo del jefe")
                    )
                }
            }

            AppSectionCard(
                modifier = Modifier.fillMaxWidth().clickable(enabled = localizedFilter.isNotBlank()) {
                    onCopy(localizedFilter)
                    copied = true
                }
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (copied) lt(language, "Filtro copiado", "Filter copied", "Filtro copiado")
                        else lt(language, "Filtro para Pokémon elegíveis", "Eligible Pokémon filter", "Filtro de Pokémon elegibles"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        localizedFilter.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AppPillTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp, divider = {}) {
                listOf(
                    lt(language, "Atacantes", "Attackers", "Atacantes"),
                    lt(language, "Defensores", "Defenders", "Defensores")
                ).forEachIndexed { index, label ->
                    AppPillTab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text("$label (${if (index == 0) attackers.size else defenders.size})") }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(displayed, key = { index, entry -> "$selectedTab|$index|${entry.name}" }) { index, entry ->
                    MaxSuggestionRow(index + 1, entry, language)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaxTypeGroup(title: String, types: List<String>, emptyText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (types.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                types.forEach { type -> TypeIcon(type = type, language = appLanguage()) }
            }
        }
    }
}

@Composable
private fun MaxSuggestionRow(
    position: Int,
    entry: BattleSuggestionEntry,
    language: com.mewname.app.domain.AppLanguage
) {
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                position.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            RaidPortrait(maxPortraitId(entry.searchTerms.firstOrNull() ?: entry.name), offline = true, modifier = Modifier.size(56.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    entry.attackTypes.take(2).forEach { type -> TypeIcon(type = type, language = language) }
                }
            }
        }
    }
}

private fun maxPortraitId(value: String?): String {
    return value.orEmpty()
        .replace(Regex("(?i)^(gigantamax|gigamax|dynamax|dinamax)\\s+"), "")
        .replace(Regex("(?i)\\s+(crowned sword|crowned shield)$"), "")
        .trim()
        .uppercase(Locale.US)
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')
}
