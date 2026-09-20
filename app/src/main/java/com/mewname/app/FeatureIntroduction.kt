package com.mewname.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal object FeatureIntroductionPreferences {
    private const val PREFS = "feature_introduction"
    private const val KEY_AUTO_SHOW = "auto_show"

    fun isAutoShowEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_SHOW, true)

    fun setAutoShowEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SHOW, enabled)
            .apply()
    }
}

@Composable
internal fun FeatureIntroductionDialog(onDismiss: (doNotShowAgain: Boolean) -> Unit) {
    val language = appLanguage()
    var doNotShowAgain by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDismiss(doNotShowAgain) },
        title = {
            Text(lt(language, "Conheça o MewName", "Meet MewName", "Conoce MewName"))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    lt(
                        language,
                        "Estas são as principais ferramentas para começar:",
                        "These are the main tools to get started:",
                        "Estas son las herramientas principales para comenzar:"
                    )
                )
                IntroductionItem(
                    "1",
                    lt(language, "Sobreposição", "Overlay", "Superposición"),
                    lt(
                        language,
                        "Leia a tela do Pokémon e gere nomes sem sair do jogo.",
                        "Read the Pokémon screen and generate names without leaving the game.",
                        "Lee la pantalla del Pokémon y genera nombres sin salir del juego."
                    )
                )
                IntroductionItem(
                    "2",
                    lt(language, "Definir Nomes", "Name Presets", "Definir Nombres"),
                    lt(
                        language,
                        "Monte formatos com IV, nível, PvP, gênero e outros campos.",
                        "Build presets with IV, level, PvP, gender, and other fields.",
                        "Crea formatos con IV, nivel, PvP, género y otros campos."
                    )
                )
                IntroductionItem(
                    "3",
                    lt(language, "Ferramentas de jogo", "Game tools", "Herramientas de juego"),
                    lt(
                        language,
                        "Consulte Pokédex, ataques, tipos, raids, pesquisas e eventos.",
                        "Browse the Pokédex, moves, types, raids, research, and events.",
                        "Consulta Pokédex, ataques, tipos, incursiones, investigaciones y eventos."
                    )
                )
                IntroductionItem(
                    "4",
                    lt(language, "Coleções e perfil", "Collections and profile", "Colecciones y perfil"),
                    lt(
                        language,
                        "Acompanhe brilhantes, trajes, fundos especiais e suas preferências.",
                        "Track shinies, costumes, special backgrounds, and your preferences.",
                        "Sigue brillantes, disfraces, fondos especiales y tus preferencias."
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { doNotShowAgain = !doNotShowAgain },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = doNotShowAgain, onCheckedChange = { doNotShowAgain = it })
                    Text(
                        lt(language, "Não mostrar novamente", "Do not show again", "No volver a mostrar"),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(doNotShowAgain) }) {
                Text(lt(language, "Começar", "Get started", "Comenzar"))
            }
        }
    )
}

@Composable
private fun IntroductionItem(number: String, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
