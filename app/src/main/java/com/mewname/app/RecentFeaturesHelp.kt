package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun RecentFeaturesHelp() {
    val language = appLanguage()
    val topics = listOf(
        lt(language, "Navegação e perfil", "Navigation and profile", "Navegación y perfil") to
            lt(language,
                "A barra inferior acompanha as telas do app. O botão central inicia ou para o modo sobreposição. Perfil abre um painel acima da barra; arraste o traço superior para baixo, toque fora dele, no ícone de perfil ou use Voltar para fechar. O traço também fecha o painel nos submenus. Toque na foto para alterá-la e no lápis para editar os dados. O QR Code abre sozinho em um modal; toque fora ou use Voltar para fechar. O rodapé mostra local nas compilações locais e o número da versão nas releases do GitHub.",
                "The bottom bar stays available across app screens. The centre button starts or stops overlay mode. Profile opens a panel above the bar; drag the top handle down, tap outside, tap Profile again or use Back to close. The handle also closes the panel from submenus. Tap the photo to change it and the pencil to edit your details. The QR code opens in its own dialog; tap outside or use Back to close. The footer identifies the version and whether the build is local or from a release.",
                "La barra inferior permanece en las pantallas del app. El botón central inicia o detiene la superposición. Perfil abre un panel sobre la barra; arrastra la línea superior hacia abajo, toca fuera, Perfil otra vez o Atrás para cerrar. La línea también cierra el panel desde los submenús. Toca la foto para cambiarla y el lápiz para editar los datos. El QR abre un modal propio; toca fuera o Atrás para cerrar. El pie indica la versión y si es una compilación local o de una release."),
        lt(language, "Atualizar o perfil pelo jogo", "Update your profile from the game", "Actualizar el perfil desde el juego") to
            lt(language,
                "Com o modo sobreposição ativo, abra seu perfil de treinador no jogo e toque na sobreposição para ler nome e nível. Na tela do seu código de treinador, repita a leitura para atualizar o código de amizade e guardar o QR Code quando ele for detectado. Uma mensagem confirma a atualização. Dados não encontrados são preservados; equipe e demais dados podem ser corrigidos pelo lápis. Se o QR Code estiver indisponível, faça a leitura da tela do código no jogo.",
                "With overlay mode active, open your own trainer profile in the game and tap the overlay to read your name and level. Repeat on your trainer-code screen to update the friend code and save the QR code when detected. A message confirms the update. Missing fields keep their previous values; use the pencil to correct your team or other details. If the QR code is unavailable, read the code screen in the game.",
                "Con la superposición activa, abre tu perfil de entrenador en el juego y toca la superposición para leer nombre y nivel. Repite en la pantalla de tu código de entrenador para actualizar el código de amistad y guardar el QR si se detecta. Un mensaje confirma la actualización. Los datos no encontrados se conservan; usa el lápiz para corregir equipo u otros datos. Si falta el QR, lee la pantalla del código en el juego."),
        lt(language, "Atalhos da sobreposição", "Overlay shortcuts", "Accesos de la superposición") to
            lt(language,
                "Pressione a sobreposição por mais de um segundo para abrir os atalhos. Arraste para mudar a posição. Em Perfil → Atalhos da sobreposição, ative ou desative cada opção tocando na linha ou no interruptor; mantenha pelo menos uma ativa. Tudo é salvo automaticamente; reabra o menu da sobreposição para aplicar. Quando houver mais atalhos, use Mais atalhos para ver as próximas opções.",
                "Hold the overlay for more than one second to open shortcuts. Drag to reposition it. In Profile → Overlay shortcuts, tap a row or switch to enable or disable an option; keep at least one active. Choices are saved automatically; reopen the overlay menu to apply. Use More shortcuts to see additional options.",
                "Mantén pulsada la superposición más de un segundo para abrir los accesos. Arrastra para moverla. En Perfil → Accesos de superposición, toca una fila o interruptor para activar o desactivar cada opción; conserva al menos una activa. Se guarda automáticamente; vuelve a abrir el menú para aplicar. Usa Más accesos para ver las demás opciones."),
        lt(language, "Layout, idioma e logs", "Layout, language and logs", "Layout, idioma y registros") to
            lt(language,
                "Em Perfil → Layout, escolha Glass, Claro ou Escuro, grade ou lista compacta e o idioma. As alterações são salvas automaticamente. Mostrar opções de log vem desativado; ative para acessar os diagnósticos do perfil, nomes sugeridos e outras leituras. Em Nomes sugeridos → Exportar log, selecione os campos desejados e compartilhe o resultado. No perfil, exporte o log depois de uma leitura. Desativar a opção esconde os controles e preserva os diagnósticos.",
                "In Profile → Layout, choose Glass, Light or Dark, grid or compact list, and language. Changes are saved automatically. Show log options is off by default; enable it to access diagnostics for the profile, suggested names and other readings. In Suggested names → Export log, select fields and share the result. Export the profile log after a reading. Disabling the option hides controls while preserving diagnostics.",
                "En Perfil → Layout, elige Glass, Claro u Oscuro, cuadrícula o lista compacta e idioma. Los cambios se guardan automáticamente. Mostrar opciones de registro está desactivado por defecto; actívalo para acceder a diagnósticos del perfil, nombres sugeridos y otras lecturas. En Nombres sugeridos → Exportar registro, selecciona campos y comparte. Exporta el registro del perfil después de una lectura. Desactivar oculta controles y conserva diagnósticos."),
        lt(language, "Leitura de Pokémon e filtros", "Pokémon readings and filters", "Lectura de Pokémon y filtros") to
            lt(language,
                "Toque na sobreposição sobre os dados do Pokémon para revisar a leitura nas abas Geral, PVP e Extras e escolher um nome sugerido. Confira os dados antes de copiar; Evoluir e Purificar são interruptores. Nas listas de Pokémon e amigos, a leitura pode abrir os filtros salvos correspondentes. O modo sobreposição precisa das permissões de sobreposição e captura de tela; quando necessário, autorize novamente pelo botão central.",
                "Tap the overlay over a Pokémon's details to review the reading in General, PVP and Extras and choose a suggested name. Check the data before copying; Evolve and Purify are switches. On Pokémon and friend lists, reading can open the corresponding saved filters. Overlay mode requires overlay and screen-capture permission; grant them again using the centre button when needed.",
                "Toca la superposición sobre los datos del Pokémon para revisar la lectura en General, PVP y Extras y elegir un nombre sugerido. Revisa los datos antes de copiar; Evolucionar y Purificar son interruptores. En listas de Pokémon y amigos, la lectura puede abrir los filtros guardados correspondientes. La superposición requiere permisos de superposición y captura; autorízalos otra vez desde el botón central si hace falta.")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        topics.forEach { (title, body) ->
            AppSectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}