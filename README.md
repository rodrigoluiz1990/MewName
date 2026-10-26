# MewName

App Android em Kotlin e Jetpack Compose para gerar apelidos de Pokémon no Pokémon GO a partir de capturas de tela e OCR com ML Kit. Permite revisar os dados reconhecidos, consultar rankings e escolher o nome; a alteração no jogo permanece manual.

## Estado atual — 25/10/2026

Versão preparada para release: **1.0.33**. Consulte o [histórico desde a 1.0.32](CHANGELOG.md) e as [notas da release](RELEASE_NOTES.md). As verificações físicas registradas correspondem a builds debug e não equivalem à validação do APK release. O versionCode é gerado a cada build. A publicação pode ser conferida na [página da release](https://github.com/rodrigoluiz1990/MewName/releases/tag/v1.0.33).

Funcionalidades disponíveis:

- Captura por sobreposição e importação pela galeria, com revisão de nome, forma, PC/CP, PS/HP, IVs, nível e marcadores.
- Configuração de formatos de apelido, prévia dos resultados e seleção de ataques.
- Rankings PvP por espécie, família e liga; consulta de combinações de IV compatíveis com o PC na tela de captura.
- Pokédex, coleção pessoal, catálogos de ataques, efeitos de aventura, raids, calendário e outras consultas do jogo.
- Sobreposição com atalhos reorganizáveis e painéis de revisão e coleção.

Combinações compatíveis com o PC são possibilidades, não IVs confirmados. A leitura do arco de nível pode auxiliar fichas sem avaliação quando há calibração e compatibilidade com PC/PS; a regressão física completa permanece pendente.

## Melhorias recentes

### Coleções, filtros, missões e OCR — 1.0.33

- Coleções com abas de fantasias e adesivos, cartões compartilhados, formas regionais corrigidas e lista compacta na sobreposição. A busca posiciona a lista e o painel pode ser recolhido até a alça.
- Filtros com tags próprias/favoritas e ícones; editor flutuante permite alternar condições e copiar. Tags e formatos de nome podem ser reordenados por arraste.
- Missões com ordem por arraste e posicionamento livre ou junto ao botão de leitura. Backup automático diário mantém uma cópia na pasta escolhida, preservada após desinstalação.
- Rocket sugere uma equipe de três Pokémon, com ataque rápido e até dois carregados, por estimativa ofensiva. Raids usam programação, PC e estrelas para restringir candidatos.
- IV Master incorpora aprovações recentes; ícones e modais foram padronizados. O [changelog](CHANGELOG.md) detalha todas as alterações.
- Permanecem pendentes a regressão física do OCR e a divergência de poeira do Moltres purificado. O experimento visual Dinamax não integra a release.


### Pokédex, eventos e interface — 1.0.31

- `PokedexDetailsScreen` substitui a janela modal. Atributos em arcos sem bordas, PC com referência de origem por nível, clima favorável junto ao PC e atualização no topo. Abas Geral, Golpes e PvP; fontes em Treinador → Ajuda → Fontes de dados.
- `PokedexEvolution` e `PokedexDetailCards` montam famílias completas com custos acima das setas, ramificações para megaevoluções e formas Gigamax. Sprites de Pokémon GO também aparecem nos confrontos PvP, com aura e identificação de sombrosos e distinção de Palkia Origem.
- `HomeEventArtRepository`, `HomeEventBackground` e `HomeEventCarousel` consultam notícias e temporadas oficiais, selecionam imagens por período/prioridade e oferecem gesto horizontal e rotação configurável. A cobertura depende dos períodos reconhecidos nas fontes; não é um calendário completo de todos os eventos.
- `MoveSearchPanel` e `MainMoveCard` compartilham busca recolhível, filtros e cartões entre ataques, legados, efeitos de aventura e detalhes da Pokédex. Os vínculos canônicos dos legados foram revisados; isso não certifica todo o histórico de exclusividade.
- `SavedFiltersOverlay` aplica o padrão visual das telas de raids/Max aos filtros de Pokémon e amigos. `LocalNavigationContentPadding` permite conteúdo atrás do recorte do menu inferior sem esconder o fim da rolagem.
- Detalhes de raids permitem atualizar o catálogo quando o chefe não é identificado. Uma identidade inequívoca no catálogo ativo pode corrigir a contagem visual de símbolos; relatórios antigos não comprovam uma raid atual.

### OCR e processamento

- Parser dividido em leitores de barras, nomes, números, atributos visuais e geometria. `OcrPokemonParser` continua coordenando as evidências.
- Reconhecedor OCR reutilizado entre leituras; imagens importadas são reduzidas antes da análise quando necessário e têm a orientação EXIF aplicada. A decodificação limita o lado maior a 4096 pixels e a imagem a 8 milhões de pixels.
- Trabalho de CPU nos fluxos ajustados usa `runInterruptible`, com propagação do cancelamento e limites de tempo por etapa. Uma leitura cancelada não deve limpar o estado de uma nova solicitação.
- Medição da estrela de favorito reaproveitada, eliminando a leitura duplicada.
- Correção da classificação de barras incompletas como IV 15 após pequenas alterações de borda causadas por JPEG.
- Tratamento de “sC”/“5C” como erro de reconhecimento do prefixo “PC”, restrito a uma linha completa na região superior.
- Importar uma imagem leva à tela que mostra o progresso e a revisão. O validador de amostras reconhece as referências dos arquivos e executa o parser fora da thread da interface.

### Sobreposição

Captura, gerenciamento de janelas e apresentação dos resultados foram separados do serviço. O encerramento libera os recursos e ignora callbacks de sessões antigas. Foi corrigido o descarte do bitmap quando o recorte retorna a própria imagem original.

Cancelamento do OCR no overlay e no validador de IV usa `CurrentReadTask`: invalida a execução antes de cancelar o job e ignora resultados, erros, finalização e mensagens de progresso de leituras anteriores. O validador compartilha o mesmo controle entre amostras e imagens importadas; encerrar o serviço também invalida os callbacks pendentes.

Durante a análise do overlay, um aviso compacto centralizado, com gradiente claro translúcido e borda suave no estilo glassmorphism, mostra progresso e Cancelar. A janela não escurece a tela, não toma foco e permite interagir com o jogo fora do cartão. Rolagem durante o OCR e cancelamento foram validados no aparelho em 11/10/2026 (versionCode 424510984, antes do ajuste visual para o centro).

### Desempenho e persistência

- **Cache PvP: limite temporário de 36 tabelas por calculadora**, mantendo LRU. A nova política de cache ainda não foi implementada. São até 4096 combinações por tabela, ou 147.456 registros retidos por instância; esse limite não representa o consumo total de memória nem inclui cálculos temporários concorrentes.
- Busca da posição no ranking por pesquisa binária, preservando o primeiro lugar entre produtos empatados.
- Catálogo de ataques indexado uma vez por fonte e traduções reutilizadas por idioma. O bloqueio global não engloba o carregamento dos arquivos.
- Telas de ataques legados e efeitos de aventura carregam os catálogos em segundo plano, com indicação de carregamento ou falha.
- Coleção pessoal reutiliza entradas já interpretadas, migra o antigo array JSON na primeira edição e serializa somente o registro alterado. Leitura observada pela UI e marcações acontecem em segundo plano. O armazenamento continua em SharedPreferences, que pode regravar seu arquivo XML; não se trata de um banco com escrita física incremental.

### Build e atualização

A comparação de versões usa `ReleaseVersion`. O `versionCode` usa segundos decorridos desde uma referência UTC, em vez de componentes da data local. A release recebe seu nome da tag informada pelo workflow; builds locais usam a versão padrão de `app/build.gradle.kts`.

## Organização do código

Os caminhos abaixo são relativos a `app/src/main/java/com/mewname/app/`.

| Área | Componentes principais |
| --- | --- |
| Interface e estado | `MainActivity.kt`, `MainViewModel.kt`, `ReviewUi.kt`, `ReviewSelectionRules.kt` |
| Fluxo de importação e OCR | `ReviewOcrPipeline.kt`, `ocr/OcrEngine.kt` |
| Interpretação do OCR | `domain/OcrPokemonParser.kt`, `OcrIvBarReader.kt`, `OcrPokemonNameReader.kt`, `OcrNumericReader.kt`, `OcrVisualAttributeReader.kt`, `OcrTextGeometry.kt` (leitores em `domain/`) |
| Sobreposição | `OverlayService.kt`, `OverlayCaptureSession.kt`, `OverlayWindowHost.kt`, `OverlayResultsPresenter.kt` |
| Rankings e ataques | `domain/PvpRankCalculator.kt`, `domain/PokemonMoveRepository.kt` |
| Coleção | `domain/PersonalCollection.kt`, `PersonalCollectionUi.kt` |
| Geração de nomes e versões | `domain/NameGenerator.kt`, `domain/ReleaseVersion.kt` |

Dados embarcados ficam em `app/src/main/assets/`. Para relações entre espécies, consultar `pokemon/families.json`; para atributos base, `pokemon/stats.json`. Os catálogos passam por validação de JSON e de semântica no pré-build.

Detalhes: [componentes do OCR](docs/organizacao-parser-ocr.md), [componentes da sobreposição](docs/organizacao-overlay.md) e [cache PvP](docs/cache-rankings-pvp.md).


Ataques legados dos Dias Comunitários de 2026 revisados até **10/10/2026 (Zorua)**: acrescentados Rillaboom/Planta Mortal, Ninetales/Bola de Energia, Ninetales (Alola)/Água Gelada, Tinkaton/Martelo Gigatônico, Oinkologne/Tapa de Lama, Baxcalibur/Fúria de Glaive, Inteleon/Hidro Canhão, Thievul/Vento Congelante e Zoroark/Soco Enganador. Cinderace/Queimadura Explosiva já estava cadastrado. Ninetales e Zoroark agora têm listas separadas por forma regional; Martelo Gigatônico e Fúria de Glaive têm estatísticas PvE/PvP e traduções PT-BR/EN/ES. Esta revisão cobre os Dias Comunitários do período, sem certificar todos os registros históricos de outros eventos.

Fontes oficiais: [janeiro](https://pokemongo.com/news/communityday-january-2026-grookey), [fevereiro](https://pokemongo.com/en/news/202601_communityday-february-2026-vulpix), [março](https://pokemongo.com/news/communityday-march-2026-scorbunny), [abril](https://pokemongo.com/news/communityday-april-2026-tinkatink), [maio](https://pokemongo.com/news/communityday-may-2026-lechonk), [junho](https://pokemongo.com/news/communityday-june-2026-frigibax), [julho](https://pokemongo.com/news/communityday-july-2026-sobble), [agosto](https://pokemongo.com/news/communityday-august-2026-nickit?hl=en) e [outubro](https://pokemongo.com/pt-BR/news/communityday-october-2026-zorua). Estatísticas e listas por forma conferidas no [Game Master extraído pelo PokeMiners](https://github.com/PokeMiners/game_masters/blob/master/latest/latest.json), consultado em 11/10/2026; a disponibilidade do ataque de Zoroark segue o anúncio oficial, mesmo sem constar ainda como Elite nesse arquivo.
## Compilar e verificar

Requisitos do projeto: JDK 17, Android SDK com API 34 e Gradle Wrapper incluído. Configurar o SDK em `local.properties` (`sdk.dir`) ou no ambiente. O app suporta Android a partir da API 26; `compileSdk` e `targetSdk` são 34.

No PowerShell, a partir da raiz:

~~~powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:validateCatalogSemantics :app:testDebugUnitTest :app:lintDebug :app:lintRelease
~~~

Em Linux/macOS, usar `./gradlew`. O modo `--offline` depende de todas as dependências já estarem no cache local.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. Com o aparelho autorizado no ADB:

~~~powershell
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
~~~

A atualização exige assinatura compatível com a instalação existente. Não desinstalar nem limpar dados para contornar um conflito sem preservar os dados e obter autorização.

## Validação mais recente

Resultados registrados em **11/10/2026**, no modelo **2412DPC0AG**, Android 16, resolução 1220 × 2712:

- **418 testes JVM/Robolectric aprovados** na preparação da 1.0.31 em 14/10/2026; validação dos catálogos, `lintRelease`, `assembleRelease` e compilação dos testes instrumentados concluídos. Lint release sem erros, com 40 avisos. Os resultados físicos a seguir correspondem às builds debug e datas indicadas, não ao APK release 1.0.31.
- **14/14 imagens de referência aprovadas no aparelho**, incluindo IVs 0/0/0 e 15/15/15; cancelamento e nova execução do validador exercitados.
- Captura real de Ponyta no overlay e importação pela galeria. O teste instrumentado do JPEG com EXIF confirmou nome, PC 1521, IVs 12/14/15, favorito e sortudo.
- Imagem ampliada para 2440 × 5424, navegação após importação, buscas nos catálogos e seleção de ataques verificados.
- Persistência da coleção após encerrar o processo; marcação de teste desfeita e os 68 registros originais preservados.
- Overlay encerrado e reaberto com compartilhamento restrito ao Pokémon GO. Últimas leituras: 4176 ms e 1146 ms. Esses tempos são da mesma versão e não demonstram ganho em relação ao APK anterior.
- Nenhum crash registrado no intervalo da sessão. O compartilhamento foi encerrado ao terminar.

- Na build debug 1.0.29 (versionCode 424510270), cancelamento seguido de reinício validado novamente no aparelho: validador concluiu 14/14 amostras; overlay registrou leitura cancelada e nova leitura de Bulbasaur concluída. A concorrência com finalização atrasada e progresso enfileirado tem testes automatizados determinísticos.

A validação física é **parcial**: faltam cenários específicos de formas regionais, cobertura dos fundos especiais, timeout forçado e observação prolongada de memória. A capacidade de 36 tabelas tem cobertura automatizada, mas ainda precisa ser avaliada sob uso prolongado no aparelho.

Consultar o [roteiro e registro da validação física](docs/validacao-ocr-aparelho.md) ao retomar esses testes. Testes JVM e capturas salvas não substituem a execução no dispositivo.

## Release no GitHub

O workflow de pushes e pull requests valida os catálogos, compila o código debug, executa os testes unitários e o Lint debug. Para tags `v*`, o workflow de release exige validação dos catálogos, testes unitários e Lint release **antes** de preparar a assinatura e publicar o APK.

Relatórios de teste e lint não são enviados como artefatos ao GitHub; os logs normais das execuções permanecem disponíveis.

Fluxo de publicação:

1. Revisar, fazer commit e enviar as mudanças.
2. Criar a tag da versão, por exemplo `git tag v1.0.31`.
3. Enviar a tag com `git push origin v1.0.31`.
4. O GitHub Actions compila o APK e cria a release com o arquivo anexado.

Secrets opcionais de assinatura:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Sem esses secrets, o workflow produz um APK release sem assinatura de produção. Para os registros por versão, consultar [CHANGELOG.md](CHANGELOG.md) e [RELEASE_NOTES.md](RELEASE_NOTES.md).

## Para futuras análises

Usar este resumo datado como ponto de partida e conferir o código, `git status` e os resultados dos testes após novas alterações. Uma build instalada localmente não implica commit, push ou release publicada.

Prioridades ainda abertas: definir a política de cache PvP que substituirá o limite temporário, medir memória em sessões longas e concluir os cenários físicos pendentes. Evitar atribuir ganhos percentuais de desempenho sem medir versões comparáveis no mesmo aparelho.
