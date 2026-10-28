# Validação do OCR no aparelho

Estado: **validação física parcial realizada em 11/10/2026**. Continuam pendentes os cenários indicados abaixo.

## Roteiro

- Confirmar o aparelho disponível e autorizado no ADB; registrar modelo, versão do Android e resolução.
- Validar captura pela sobreposição e importação pela galeria: nome, forma regional, família, CP, HP, nível e IVs.
- Comparar os resultados com os valores visíveis na tela; incluir barras 0/0/0, 15/15/15 e valores intermediários.
- Conferir capturas comuns, imagens grandes e imagens com rotação EXIF, verificando se a redução de resolução preserva nomes e barras.
- Conferir fundos especiais e estrela de favorito após a divisão dos detectores.
- Cancelar uma leitura e iniciar outra; verificar que o fluxo continua utilizável. Quando possível, exercitar timeout e nova tentativa.
- Encerrar e reabrir a sobreposição e observar falhas, uso de memória e tempo de leitura.

## Registro

Preencher após a execução: data, aparelho, versão do app, cenários executados, resultados e limitações. Manter itens não executados como pendentes.

A refatoração é verificada separadamente pela suíte local, incluindo capturas já salvas de Psyduck e Scatterbug. Isso não confirma o funcionamento no aparelho conectado.

## Execução de 11/10/2026

- Aparelho 2412DPC0AG, Android 16, 1220 × 2712; APK debug 1.0.29, versionCode 424508030, instalado preservando dados.
- Captura real de Ponyta pelo overlay e importação pela galeria: IVs 12/14/15 (91%); teste instrumentado confirmou PC 1521, favorito e sortudo.
- 14/14 imagens de referência no aparelho, incluindo 0/0/0 e 15/15/15; cancelamento e nova execução do validador funcionaram.
- Imagem ampliada (2440 × 5424) e JPEG com EXIF 6: IVs conferidos. O teste JPEG identificou e motivou uma correção do arredondamento de 14 para 15.
- Corrigidos também o contador de referências do validador, seu processamento na thread da interface, a navegação após importar imagem e o prefixo PC reconhecido como sC na região superior.
- Catálogos de ataques legados/efeitos de aventura, buscas e seletor de ataques exercitados. Coleção persistiu após encerrar o processo; marcação de teste desfeita e os 68 registros originais conferidos.
- Overlay encerrado e reaberto com compartilhamento restrito ao Pokémon GO. Últimas capturas: 4176 ms e 1146 ms; não é comparação com APK anterior.
- 377 testes JVM/Robolectric e lintDebug sem erros (32 avisos). Nenhum crash registrado no intervalo desta sessão. Compartilhamento encerrado ao terminar.
- Pendentes: validação física específica de formas regionais, todos os fundos especiais, timeout forçado e observação prolongada de memória. Os testes desta sessão não cobrem todos esses cenários.
## Revalidação de cancelamento — 11/10/2026

- Mesmo aparelho 2412DPC0AG; APK debug 1.0.29, versionCode 424510270, instalado com dados preservados.
- Validador de amostras: iniciado, cancelado durante a análise e reiniciado imediatamente; resultado final 14/14.
- Overlay com compartilhamento restrito ao Pokémon GO: leitura de Bulbasaur cancelada pelo botão da janela e nova captura iniciada imediatamente. Logs: leitura 54fb4075 terminou como cancelled (657 ms); leitura 336b291e terminou como success (1220 ms), exibindo nomes sugeridos. Os tempos não são comparação de desempenho.
- Suíte completa: 385 testes aprovados; lintDebug sem erros, com 39 avisos. Testes de regressão simulam finalização tardia, erro/resultado antigo, timeout, bloqueio de execução duplicada e progresso do overlay enfileirado.
- Compartilhamento encerrado ao terminar. Formas regionais, cobertura completa de fundos, timeout físico forçado e memória prolongada continuam pendentes.

## Progresso sem bloqueio — 11/10/2026

- APK debug 1.0.29, versionCode 424510984, instalado no mesmo aparelho. Confirmada rolagem da ficha de Bulbasaur enquanto o cartão mostrava análise em andamento; tela sem camada cinza e toques externos recebidos pelo jogo.
- Botão Cancelar do cartão conferido: leitura d1bfb32e terminou como cancelled. Cinco testes de regressão de cancelamento aprovados; assembleDebug e validação dos catálogos aprovados. Compartilhamento encerrado ao terminar.

## Atualização de raids no aparelho — 11/10/2026

- APK debug 1.0.30, versionCode 424518539, instalado com `adb install -r` no mesmo aparelho, preservando os dados.
- Teste instrumentado executou o componente real `RaidDetailsScreen` em uma Activity com chefe vazio, nos modos `bubble=true` e `bubble=false`. Conferidos ícone à direita, atualização pela rede (arquivo de catálogo renovado), 75 opções retornadas e abertura do seletor após atualizar. Captura de tela do modo bubble conferida visualmente.
- A configuração bubble foi exercitada na Activity de teste, não em uma janela do serviço sobre o Pokémon GO. Não foi feita nova captura de uma raid no jogo nem simulação de falha de rede nesta sessão.
- Regressão OCR executada no aparelho com o JPEG EXIF de Ponyta: nome, PC 1521, IVs 12/14/15, favorito e sortudo confirmados. Esta imagem de referência não substitui os cenários físicos ainda pendentes acima.
- Teste auxiliar `RaidRefreshInstrumentation` incluído para repetir a verificação. Requer o fixture `cache/raid-test-ponyta.jpg` no app (cópia de `app/src/test/resources/ocr/ponyta-exif.jpg`) e runner `com.mewname.app.RaidRefreshInstrumentation`.

## Yveltal no overlay real — 11/10/2026

- Build debug 1.0.30, versionCode 424519260, instalada via `adb install -r`, com dados preservados.
- Validação no Pokémon GO aberto na raid de Yveltal (PC 45899), com captura pelo serviço de sobreposição real. Tela e logs confirmaram `YVELTAL / RAID_LEVEL_5` e carregamento das sugestões salvas (level=40, hit=true).
- Leitura f8277e7f: detector retornou nível 2; resolução final manteve Yveltal na raid 5. Leitura 47a759ab: detector retornou nível 3 e a resolução também manteve raid 5. Leitura ccab2cad retornou nível 5 e funcionou normalmente.
- Conferidos visualmente nome, categoria Raid 5, tipos Sombrio/Voador, golpes, fraquezas e seção de PC de captura no painel sobre o jogo.
- A contagem visual dos símbolos continua oscilando; a correção validada impede que esse erro descarte uma identidade inequívoca do catálogo atual. Não equivale a recalibrar o detector visual nem validar todas as espécies/formas.

## Visual dos filtros salvos — 11/10/2026

- Painel compartilhado de filtros Pokémon/amigos migrado para Compose e AppAppearanceTheme, com AppModalHeader e AppSectionCard.
- Build debug instalada preservando dados. Teste instrumentado no aparelho mostrou o componente em Activity nos temas Glass, Dark e Light, com 26 filtros de exemplo, texto longo, lista rolável, callbacks de copiar/fechar e estado vazio de amigos. Preferência original de tema restaurada; filtros de exemplo não foram persistidos e a área de transferência não foi alterada pelo teste.
- Capturas Glass/Dark/Light inspecionadas. Este teste cobre o componente visual; não exerceu a janela do serviço sobre o jogo nesta sessão. AssembleDebug e lintDebug aprovados.

## Filtros com padrão raids/Max — 11/10/2026

- Build debug 1.0.30, versionCode 424521802: fundo em gradiente separado dos cartões e AppTopBar com fechar à esquerda. Raids e Max preservados; cópias locais em `backups/raid-max-style-2026-10-11-163610/` (ignorado pelo Git), com hashes SHA-256.
- Compilação aprovada; teste instrumentado do componente passou em Glass, Dark e Light, incluindo lista longa, copiar/fechar e estado vazio de amigos. Preferência de tema restaurada.
- Conferidos os painéis reais de filtros de amigos e Pokémon abertos pelo menu da bolha sobre o Pokémon GO. Fechar retornou ao jogo; tocar no filtro Pokémon executou a ação de copiar e fechou o painel. Não foi feita colagem na busca do jogo nesta sessão.

## Faixa inferior dos filtros — 12/10/2026

- Build debug 1.0.30, versionCode 424601492, instalada preservando dados. Movido o padding vertical de FilterBuilderScreen para dentro da rolagem, removendo a faixa fixa de 8 dp acima do menu inferior.
- Conferida a tela Filtros no aparelho antes/depois e após rolar até a última linha de tipos. Calendário e Formatos de Nome inspecionados, sem confirmação da mesma faixa fixa. Nenhum teste OCR executado nesta sessão.

## Recorte do menu inferior — 12/10/2026

- O ajuste anterior do padding dos filtros não resolvia a separação ao redor do botão central relatada pelo usuário. Substituída a curva aproximada do menu por um semicírculo de raio 40 dp, deixando 8 dp de folga para o botão de raio 32 dp.
- O fundo compartilhado agora usa o gradiente do tema; Filtros usa um Scaffold transparente para continuar esse fundo atrás do recorte.
- AssembleDebug aprovado; build debug 1.0.30, versionCode 424602060 instalada preservando dados. Conferidas capturas reais de Filtros e da tela principal atmosférica na build final. Calendário conferido na build intermediária do recorte, antes da alteração do fundo compartilhado. A remoção dos botões redundantes de filtros salvos também está nesta instalação.
- Nenhum teste de OCR executado nesta sessão.
## Conteúdo visível pelo recorte — 12/10/2026

- Corrigida a causa restante: o host encerrava o conteúdo no topo do dock. Filtros, Calendário e Formatos de Nome agora ocupam a área atrás do menu e reservam espaço dentro da rolagem via LocalNavigationContentPadding. A área opaca do dock consome toques para não acionar controles encobertos.
- AssembleDebug aprovado e build 1.0.30, versionCode 424602424 instalada preservando dados. No aparelho, confirmado texto da aba Status dos filtros e de um cartão do Calendário visível através do recorte. Confirmado fim da rolagem dos filtros com o botão Adicionar totalmente acima da bolha central. Navegação pelos atalhos Calendário e Formatos de Nome funcionou; a lista atual de nomes tem quatro itens, sem necessidade de rolagem.
- A validação não cobre todas as demais telas nem OCR. O ajuste anterior de geometria/fundo sozinho não resolvia a ausência de conteúdo sob o recorte.
## Recorte nas demais telas — 12/10/2026

- Estendido o conteúdo atrás do dock a todas as rotas secundárias e ao painel de perfil. Espaço final aplicado dentro de listas/grades/rolagem, incluindo Pokédex, Coleções, fundos, ataques, tipos, raids, fontes LeekDuck, ajuda, privacidade, atualização e testes. Salvar na edição de nomes e Copiar filtro nos detalhes de raids/Max do app principal ficam no fim da rolagem; os rodapés dos detalhes em janela de sobreposição mantêm o comportamento anterior.
- AssembleDebug e lintDebug aprovados. Build 1.0.30, versionCode 424602965 instalada sem apagar dados. Conferidos no aparelho: Pokédex, Coleções, Ataques, lista de Raids, Tipos, Ajuda, perfil e Layout; verificado fim da edição de nomes com Salvar e fim de Layout com a última preferência acima da bolha. Nenhum formato foi salvo nem preferência alterada.
- A primeira tentativa de percorrer destinos com am start sem reiniciar permaneceu na Pokédex e não conta como validação das outras telas; a conferência seguinte usou reinício da Activity/app e inspeção das capturas. Não executados todos os fluxos de cada tela, detalhes de raids/Max nem OCR nesta etapa.
## Detalhes da Pokédex como tela — 12/10/2026

- Substituído o Dialog por PokedexDetailsScreen dentro da navegação da Pokédex, com voltar pelo cabeçalho/sistema e estado da grade mantido. Indicadores semicirculares ATK/DEF/HP seguem a referência visual fornecida; os arcos usam o maior valor de cada atributo no catálogo como escala. Removidas as legendas sobre base auditada e disponibilidade do sprite brilhante.
- AssembleDebug aprovado; build 1.0.30, versionCode 424604363 instalada preservando dados. No aparelho, abertos Bulbasaur (118/111/128) e Squirtle (94/121/127). Conferidos os indicadores e os dois meios de voltar; a grade retornou à mesma posição após abrir Squirtle. Nenhum teste OCR executado nesta etapa.
## Tipos, dano e família na Pokédex — 12/10/2026

- Tipos centralizados; fraquezas/resistências em cartões com ícone, nome e percentual. Família com sprites do Pokémon GO, custos e condições entre as evoluções; caminhos ramificados separados e rolagem horizontal por caminho. Catálogo de 489 evoluções importado do Game Master PokeMiners, revisão 8e227be44f288d34463e23bf04e9b564d3c16f79.
- AssembleDebug e nove testes direcionados (PokedexEvolutionTest/PokedexDetailsTest) aprovados. Build 1.0.30, versionCode 424604906 instalada preservando dados. No aparelho, conferidos cartões de Bulbasaur, evolução por 25 doces até Ivysaur e por 100 até Venusaur, rolagem horizontal e abertura de Venusaur pelo sprite da família; tipos centralizados confirmados nessa tela.
- Ramificações e formas regionais cobertas pelos testes automatizados, não conferidas visualmente no aparelho nesta etapa. Nenhum teste OCR executado.

## Família completa até Mega Evolução — 12/10/2026

- A Pokédex expande a família pelas conexões de evolução, incluindo ancestrais e formas Mega presentes no catálogo Game Master com sprite GO disponível. Custo inicial de Mega Energia separado dos doces; X/Y em caminhos próprios.
- AssembleDebug e 11 testes direcionados aprovados após a geração final do catálogo. Build 1.0.30, versionCode 424605451 instalada preservando dados. Conferida no aparelho a família de Charmander: início com Charmander/Charmeleon e rolagem de ambos os caminhos até Charizard e Mega Charizard X/Y, com sprites carregados e 200 Mega Energia (1ª vez).
- Demais famílias cobertas parcialmente pelos testes automatizados; nenhum teste OCR executado nesta etapa.

## Ícones únicos e ramificação visual da família — 12/10/2026

- Fraquezas/resistências usam somente o círculo presente no asset do tipo, sem os fundos/bordas adicionais. Caminhos da família agrupados pelo prefixo comum: cada ancestral aparece uma vez, e destinos divergentes recebem setas diagonais. Rolagem horizontal compartilhada.
- AssembleDebug aprovado e build 1.0.30, versionCode 424612754 instalada preservando dados. Conferidos os ícones finais no aparelho; na build intermediária, com o mesmo código de família, conferida a sequência única Charmander/Charmeleon e a divisão de Charizard para Mega X/Y com sprites, setas e custos. Nenhum teste OCR executado nesta etapa.

## Gigamax na família da Pokédex — 12/10/2026

- Formas alternativas derivadas do catálogo Gigamax das Coleções, somente com sprite GO disponível. Ligações pontilhadas e rótulo Forma Gigamax, sem custo; sequência inicial compartilhada com as Megas.
- AssembleDebug e 12 testes direcionados aprovados. APK instalado preservando dados. Conferida no aparelho a família de Charmander, com Mega Charizard X/Y e Charizard Gigamax, sprite carregado, ligação pontilhada e ausência de custo no ramo Gigamax. Nenhum teste OCR executado nesta etapa.

## Ícones de custos/clima e cartões uniformes — 12/10/2026

- Assets locais do PokeMiners, revisão 8353f3c4451f4b9154684222ce55128b8a33c7e4, com origem em pokedex_icons/SOURCE.txt. Doces renderizados com textura RGB e paleta da família; Mega Energia usa símbolo genérico do tutorial do jogo (não reproduz cores por espécie). Sete ícones de clima com contraste do tema. Cartões de tipos com largura/altura uniformes.
- AssembleDebug e 13 testes direcionados aprovados. APK instalado preservando dados. No aparelho conferidos cartões uniformes de Charmander, clima ensolarado e doce laranja da família. Nenhum teste OCR executado nesta etapa.

## Cartões compactos e textura de Mega Energia — 12/10/2026

- Cartões reduzidos de 88×116 dp para 68×90 dp, padding lateral de 7 para 3 dp e área do nome de 34 para 20 dp. Mantido tamanho uniforme. Mega Energia substituída pela máscara original preenchida, renderizada com sombreamento, símbolo e paleta por forma; aproximação estática do material animado do jogo.
- AssembleDebug e 13 testes direcionados aprovados; APK instalado preservando dados. No aparelho conferidos quatro tipos por linha nas resistências de Charmander (antes três), espaçamento compacto e textura preenchida nas Mega Energias X/Y. Nenhum teste OCR executado.

## Fundo da Home por evento oficial — 12/10/2026

- HomeEventArtRepository busca até 30 notícias oficiais, mantém título/imagem localizados e extrai o período introdutório em inglês (ano explícito no texto ou slug). Datas de publicação não definem vigência. Eventos sem período seguro e anúncios identificados como presenciais são ignorados. Seleção por dia local, incluindo todo o último dia.
- Prioridade centralizada: GO Fest/Tour global (600), Dia Comunitário (500), Gigamax/Max Battle Day (450), Raid/Hatch Day (400), horários especiais (350), eventos gerais (300), temporada/Passe GO (100). Empate favorece duração menor, início mais recente e URL estável.
- Fundo da Home com degradê de contraste, fallback para fundo existente, cache de metadados por idioma por seis horas e imagens via Coil. Atualização limitada a quatro requisições simultâneas, cancelamento propagado e nova tentativa após falha limitada a uma hora enquanto o componente permanece ativo. Cobertura limitada à página de notícias e aos formatos de período reconhecidos; não usa o calendário antigo como fonte de datas.
- AssembleDebug e oito testes direcionados aprovados. APK final instalado preservando dados. Conferido carregamento real do catálogo e imagem do Passe GO de outubro como fundo da Home atmosférica, mantendo os atalhos legíveis. Prioridade/troca de dia verificadas nos testes, sem alterar o relógio do celular. Nenhum teste OCR executado nesta etapa.

## Arte inteira no topo da Home — 12/10/2026

- Imagem ajustada à largura e proporção original no topo, sem o recorte de tela vertical. Continuação inferior com cor média de 32 amostras da borda inferior da imagem e degradê até o tema; transição suave no fim da arte. Amostragem executada uma vez por carregamento, sem blur contínuo.
- AssembleDebug aprovado, APK instalado preservando dados e captura real conferida com o Passe GO: composição completa no topo e complemento inferior com atalhos legíveis. Nenhum teste OCR executado nesta etapa.

## Carrossel de eventos na Home — 12/10/2026

- Imagens de eventos ativos ordenadas por prioridade e deduplicadas. Gesto horizontal restrito à área superior da arte, indicadores e transição de 600 ms. Rotação padrão de 10 segundos, opções 5/10/15/30 segundos e liga/desliga em Aparência/Layout. Pausa de 30 segundos após navegação manual; sem rotação com uma imagem. Retorno à Home/foreground reinicia pela maior prioridade; temporizador suspenso fora de RESUMED.
- AssembleDebug e 11 testes direcionados aprovados. APK instalado. Teste no aparelho com três eventos simulados temporariamente no cache: início pelo Dia Comunitário, avanço automático após o intervalo e deslize para a segunda arte; confirmado que permaneceu na segunda durante os 12 segundos seguintes ao gesto. Pausa completa de 30 segundos não cronometrada precisamente nesta conferência. Preferências/persistência e navegação circular cobertas pelos testes automatizados.
- Cache original restaurado e quantidade de eventos/timestamp conferidos após os testes; nenhum evento simulado permaneceu ativo. Nenhum teste OCR executado.

## Raid de Voltorb de Hisui — 14/10/2026

- APK debug corrigido instalado com `adb install -r`, preservando dados.
- Executadas duas leituras reais pela bolha de sobreposição sobre a raid aberta no Pokémon GO. O OCR produziu `Vettorb` e `Votorb`; ambas resultaram em `boss=Voltorb` e seleção automática de `VOLTORB_HISUIAN_FORM`, `RAID_LEVEL_1`, sem escolha manual.
- Conferidos no aparelho sprite regional, tipos Elétrico/Planta, movimentos e PC de captura. Fechamento e nova leitura funcionaram.
- Acionado o ícone superior de atualizar: logs confirmaram atualização dos relatórios dos níveis 50 (654 ms) e 20 (748 ms), mantendo a forma correta.
- Esta validação física cobre a raid de Voltorb de Hisui. As leituras `Voforb`/`Vforb` e a ambiguidade entre formas foram cobertas pelos testes automatizados anteriores, não reproduzidas nesta captura real. Demais cenários do roteiro de OCR continuam pendentes.
## Missões diárias — 14/10/2026

- Adicionada tela Missões diárias, com cadastro, edição, exclusão, escolha de ícones e conclusão/desfazer. Sem limite artificial de quantidade; ícones locais disponíveis offline.
- Caixa vertical na sobreposição: tocar conclui, concluir todas remove a caixa, renovação pela data local. Caixa oculta durante a captura do OCR e removida com o serviço.
- Build debug instalada preservando dados. Sete testes JVM/Robolectric passaram (DailyMissionStoreTest, BubbleActionsTest e OverlayShutdownTest), incluindo persistência, mudança de dia, 100 missões, assets, conclusão da última missão e ocultação durante captura.
- Conferência visual da nova tela/caixa no aparelho pendente: celular bloqueado no momento da tentativa. Nenhuma nova validação física de OCR realizada nesta etapa.
## Validação visual das missões no aparelho — 14/10/2026

- Após desbloqueio, cadastradas pela interface duas missões temporárias, escolhendo Poké Bola e coração do companheiro no seletor de ícones.
- Ativada a sobreposição: conferida caixa arredondada com os dois ícones empilhados sobre o Pokémon GO. Toque no primeiro concluiu apenas sua missão; toque no segundo removeu a caixa. Conferidos registros de conclusão com a data local.
- Retorno à tela Missões diárias mostrou ambas concluídas. Desmarcar a primeira restaurou o estado pendente e fez o ícone reaparecer na caixa, confirmado visualmente.
- Excluídas pela interface somente as duas missões temporárias, com confirmação; lista final vazia, como antes do teste.
- Renovação à meia-noite, listas longas e ocultação durante captura permanecem cobertas pelos testes automatizados, não por esta conferência física. Nenhum novo OCR executado nesta etapa.
## Glassmorphism e arraste das missões — 15/10/2026

- APK atualizado instalado preservando dados. Conferido o modal com campos/botões do app, ícone à esquerda do nome e seletor incluindo PokéStop e caixa de pesquisa.
- Criada pela interface uma missão temporária com a caixa de pesquisa. Conferidos no Pokémon GO fundo translúcido, borda suave e ícones compactos.
- Arrastada a caixa da direita para a esquerda pela alça, sem concluir tarefas. Toque no ícone temporário concluiu somente essa missão; a caixa diminuiu mantendo a posição. Posição salva confirmada nas preferências.
- Caixa devolvida à posição anterior e missão temporária excluída pela interface. Mantidas as seis missões originais e seus estados, incluindo raid pendente.
- Tema escuro/claro, reinício da sobreposição, virada de dia e OCR não exercitados nesta conferência física.
## Modais de missões e nomes, filtros e ajuda — 15/10/2026

- APK debug instalado com adb install -r, preservando os dados.
- Conferidos no aparelho: lista de missões sem legenda; modal de nova missão com título e Salvar centralizados, X funcional e sem Cancelar.
- Conferidos os modais de Gênero, edição do símbolo Masculino e Texto Livre: títulos centralizados, X superior e ausência de Cancelar/Fechar no rodapé; Adicionar centralizado nos modais de campo e texto.
- Navegação realizada em um novo formato não salvo; lista final manteve os quatro formatos anteriores.
- Confirmada pela interface a sequência dos 18 tipos na tela Filtros e a presença dos três tópicos de missões em Ajuda.
- Não executados nesta etapa: OCR físico, validação visual dos custos de fortalecimento, demais idiomas/temas e seleção de tipos na sobreposição.

## Raid de Mega Dragonite — 18/10/2026

- Corrigida recuperação de nomes fragmentados na mesma linha da faixa do chefe, incluindo tiers Mega e mantendo nível detectado, catálogo atual e correspondência única.
- 28 testes JVM/Robolectric passaram (RaidOccludedNameTest e RaidBossResolverTest). Incluem a leitura anterior `Me Dra aite`, ausência no catálogo, nível incorreto, texto de ginásio e ambiguidade entre Mega Charizard X/Y.
- APK debug compilado e instalado com `adb install -r`, preservando dados.
- Após instalação, logs do aparelho conectado registraram seis leituras reais da raid de Mega Dragonite, todas selecionando `DRAGONITE_MEGA`, `RAID_LEVEL_MEGA`. A leitura `Me Dra nite` foi recuperada pelo caminho de nome parcial; as demais foram `Mea Dramanite`, `Mecn Draanite`, `Mega Dragonite`, `Meaa Drasanite` e `Me Drannite`.
- A leitura exata `Me Dra aite` foi validada por teste automatizado; não foi novamente observada após instalação. Esta verificação confirma identificação pelos logs, sem nova conferência visual completa dos detalhes. Outros cenários do roteiro permanecem pendentes.

## Identificação de raid pelo PC — 18/10/2026

- Adicionado fallback por PC exato do chefe na faixa superior, nível detectado e correspondência única no catálogo atual, quando o reconhecimento pelo nome falha. Categorias sem cálculo suportado permanecem sem inferência por PC.
- 32 testes passaram: RaidBossCpTest, RaidOccludedNameTest e RaidBossResolverTest. Cobrem Dialga 53394, Origem 54074, formas ambíguas, ausência no catálogo, nível incorreto, números fora da faixa e dígitos inválidos.
- APK debug instalado com adb install -r, preservando dados.
- Leitura física no aparelho às 16:38:51: OCR registrou PC 53394 e nome ilegível (`DịỦja`); log confirmou `bossCp recovered=RaidChoice(id=DIALGA, tier=RAID_LEVEL_5)` e atualização dos dados do chefe. Identificação confirmada por logs; não realizada conferência visual completa do modal.
- Forma Origem validada somente por teste automatizado, não em raid física nesta etapa. Demais cenários do roteiro continuam pendentes.

## Diagnóstico detalhado e referência dos símbolos — 18/10/2026

- Logs debug agora incluem posição/texto de referência do PC, tamanho da captura, região dos símbolos, método de contagem, nível final, leituras de PC, candidatos atuais e motivo de rejeição.
- Referência dos símbolos alterada da legenda PC/CP para a linha dos dígitos grandes na faixa do chefe. Sem dígitos válidos, mantém referência proporcional fixa.
- Durante testes físicos das versões intermediárias, os logs identificaram nível 5 correto, mas PC rejeitado por ruídos `53394-`, `& p53394-` e `53394,`. Parser compartilhado passou a aceitar esses ruídos externos, preservando dígitos e rejeitando pontuação interna, intervalos e decimais. Não foi possível estabelecer retrospectivamente a causa exata das falhas anteriores sem esses logs.
- 35 testes JVM/Robolectric passaram: RaidLevelIconDetectorTest, RaidBossCpTest, RaidOccludedNameTest e RaidBossResolverTest. APK final instalado preservando dados.
- Três primeiras leituras físicas da versão final às 16:46:40, 16:46:44 e 16:46:49 reconheceram DIALGA/RAID_LEVEL_5 pelo PC 53394, com nomes OCR Du, DOa e DUa. Logs confirmaram nível 5 por componentes em todas. Amostra curta, sem garantia de ausência de futuras falhas.
- Conferido também o modal de Dialga no aparelho durante a rodada intermediária, com movimentos, tipos e PC de captura. Os textos com pontuação foram cobertos por testes automatizados após as correções; não reapareceram nas três primeiras leituras da versão final.

## Prévia Dinamax do Squirtle — 18/10/2026

- APK debug compilado e instalado preservando os dados.
- Executada leitura real da tela Squirtle Dinamax no Pokémon GO; abriu o modal de detalhes Max do Squirtle. Conferidos sprite normal sem fantasia, aura rosa com fundo transparente e três nuvens acima da cabeça.
- Efeito estático 2D limitado ao Squirtle no componente de retrato Max. Não reproduz a animação/modelo 3D do jogo. Outros temas e o Squirtle na lista de atacantes não foram conferidos fisicamente nesta etapa.

## Missões padrão e leitura de PC/símbolos — 21/10/2026

- Na primeira versão desta sessão, leitura física de Palkia às 12:44:10: PC real 54793, OCR `PC4793`; projeção encontrou quatro componentes com espaçamento irregular e retornou nível indefinido. Chefe não identificado. A raid encerrou antes de repetir com a versão final.
- Versão final: segunda passagem ampliada somente na faixa do PC quando nome ou nível ficam indefinidos; três limiares de contraste para a projeção dos símbolos, com rejeição de contagens conflitantes. Junção de fragmentos numéricos limitada por alinhamento, altura e distância; símbolos precisam estar centralizados.
- Leitura física de Mega Charizard X às 12:47:27–29: OCR inicial `D41255` e nome fragmentado; recorte ampliado recuperou `41255`; quatro símbolos confirmados, resolução por PC selecionou `CHARIZARD_MEGA_X / RAID_LEVEL_MEGA`. Modal conferido no aparelho. Uma leitura bem-sucedida, sem afirmar estabilidade em todos os fundos/animações.
- APK instalado preservando dados. Conferido no aparelho o modelo Girar PokéStop no editor de missões: nome e ícone preenchidos; fechado sem salvar uma duplicata.
- Pendente repetir Palkia com a versão final e demais cenários físicos do roteiro. Testes automatizados e capturas não substituem essas verificações.


## 2026-10-21 — Índice de PC e recuperação do nome de Axew

- Criado `raids/boss_cp.json`, em ordem da Nacional Dex: 2.322 Pokémon/formas e 6.780 PCs calculados por categoria. Referência de identificação, não lista de raids disponíveis. Regeneração: `tools/generate_raid_boss_cp.py`.
- Verificação automatizada: 514 testes JVM/Robolectric passaram, incluindo ordenação e correspondência de todas as entradas com a fórmula, empate Axew/Gastly Sombroso, categoria explícita, ausência de catálogo e rejeição de PC conflitante/fora da faixa. APK debug compilado e instalado com preservação dos dados.
- Validação física às 13:10: raid de Axew aberta no Pokémon GO. Primeira leitura: nome ausente e PC ilegível (`A58`); um símbolo detectado. A nova leitura ampliada do nome retornou `*AXeW`. Roteamento identificou Axew, selecionou `AXEW / RAID_LEVEL_1` e abriu o modal com movimentos, fraquezas e PC de captura. Não foi necessário entrar na batalha.
- Limites: esse teste físico validou a recuperação do nome, não o desempate por categoria. Raid sombrosa e seleção manual dos candidatos do índice ainda precisam de validação física. Leituras numéricas danificadas continuam sem correção por adivinhação.

## 2026-10-21 — Seleção visual de candidatos de raid

- Combobox de chefe não identificado substituído por cartões clicáveis com sprite, nome e categoria. Seleção de variantes após identificar o chefe preservada.
- 11 testes relacionados passaram; APK instalado preservando dados. No aparelho, confirmados os cartões e sprites de Gastly Sombroso e Axew. Log às 13:17:44 confirmou seleção de Axew e carregamento do relatório salvo.
- Em leituras posteriores, o detector contou dois símbolos na raid de Axew e não gerou candidatos quando o nome também falhou. Pendência do OCR, independente da apresentação dos cartões.


## 2026-10-23 — Ficha sem avaliação: Zacian Coroado

- APK debug compilado e instalado preservando dados. 13 testes direcionados JVM/Robolectric passaram (CpIvEvidenceTest e DetailArcCalibrationTest), incluindo ambiguidade de PC/PS, restrição de níveis, invalidação da calibração e preservação de rótulos da avaliação.
- Leitura física final às 00:06:51: ficha sem avaliação, título decorado e espada encobrindo parte do PC. Log confirmou `Zacian Crowned Sword`, doces da família Zacian, PS máximo 173, tipos Aço/Fada e PC ausente. Conferido `Zacian (Coroado)` no modal; nível e IVs permaneceram vazios. Não se confirmou 100% pelo apelido.
- Corrigida a associação de formas pelos tipos na leitura sem avaliação, inclusive quando o nome não é reconhecido e há doces da família.
- Arco visual implementado como evidência auxiliar com calibração por três níveis distintos confirmados por PC/PS, abrangendo pelo menos cinco níveis. Sem calibração coerente não restringe resultados; leituras conflitantes são descartadas. Não validado fisicamente nesta sessão porque o PC do Zacian estava encoberto. Permanecem pendentes fichas de níveis diferentes com PC/PS legíveis e regressão física com avaliação aberta.

## 2026-10-24 — Desempate de nível por PC/PS do Moltres

- Antes do ajuste, conferida no aparelho a tela de nomes sugeridos: Moltres 15/15/15 com nível 35,5; o seletor calculava PC 3242 e PS 169. O PC na ficha do jogo estava parcialmente encoberto pelo Pokémon. Não foi recuperado o diagnóstico completo da leitura original.
- Corrigido o desempate por PS: com IVs conhecidos, o PC observado deve corresponder exatamente ao candidato; sem evidência suficiente, níveis empatados não são reduzidos ao maior nível.
- Testes automatizados confirmaram Moltres PC 3218/PS 169 no nível 35 e PC 3242/PS 169 no nível 35,5, além de ambiguidade sem PC e rejeição de PC incompatível. 15 testes direcionados passaram; APK debug compilado e instalado preservando dados.
- Pendente repetir a captura física do Moltres com a versão corrigida e PC visível. Instalação não equivale a validação física do novo OCR.

### Releitura física após o ajuste

- Adicionada segunda leitura ampliada do cabeçalho quando o PC não é encontrado, sem reconstruir dígitos ausentes, além de diagnóstico do nível no log debug. APK compilado e instalado preservando dados.
- No aparelho, às 15:04:58 e 15:06:01, o cabeçalho foi reconhecido como `PR218` e `18`. O Moltres encobria parte do PC. As duas leituras reconheceram PS 169 e IVs 15/15/15, mas permaneceram sem nível porque 35 e 35,5 são compatíveis com esses PS.
- A recuperação física do nível 35 continua pendente de uma captura com o PC completo legível; não foi validada por estas tentativas.

### Inferência por PC parcial, PS e IVs

- Implementado cruzamento dos fragmentos numéricos do cabeçalho com PCs calculados por nível, exigindo PS exatos e três IVs conhecidos. Retorna nível somente quando há um candidato; fragmentos conflitantes ou empatados permanecem sem estimativa. Não transforma PC parcial em PC observado completo.
- 18 testes direcionados passaram, cobrindo fragmentos 218/18, nível 35,5 com 242, empates, conflitos e exclusão de números fora do cabeçalho. APK instalado preservando dados.
- Validação física em 24/10 às 15:21:39: Moltres com PS 169 e IVs 15/15/15; fragmento de PC `18`; diagnóstico `pc_parcial_hp_ivs`, nível 35. Conferido visualmente o campo Nível preenchido com 35 no modal de nomes sugeridos. Leitura anterior `A210` permaneceu sem estimativa.

## 2026-10-25 — Moltres purificado, tags e coleção compacta

- Conferido no jogo o preview de fortalecimento do Moltres: PC 3218 → 3465, 106 doces e 78.300 poeiras. Nenhum fortalecimento foi confirmado.
- Ajustado o arredondamento de doces comuns de purificados: nível 35 → 40 passa de 107 para 106 doces. A divergência de poeira continua pendente: cálculo atual 79.200 versus 78.300 observados; não foi alterada a tabela sem confirmação da causa.
- Acrescentada detecção experimental de brilhos brancos de purificação e restrição da classificação por fundo roxo. Precisa de validação física com purificado, sombroso e Pokémon comum; testes sintéticos não comprovam precisão no jogo.
- Capturada no aparelho a tag Favoritos com busca preenchida por “mol”, sem o texto Buscar nem as abas da bolsa. O roteamento agora aceita a distribuição espacial dos PCs na grade, rejeitando âncoras da ficha/avaliação. Ainda não confirmada a abertura do filtro por uma leitura física dessa versão.
- Coleção em sobreposição: busca em uma linha ao lado do seletor de layout; prioridade para nome/número exato, depois prefixo e trecho; altura mínima reduzida à alça de 24 dp. Conferência visual no aparelho ainda pendente.
- 39 testes JVM/Robolectric direcionados passaram, cobrindo custos, roteamento de filtros, busca, altura do painel, brilhos e regressão de nível por PC/PS/IVs. APK debug compilado e instalado preservando dados.
- A retomada da captura do OCR exigiu autorização do Android. A revisão automática bloqueou o toque nessa autorização por privacidade; solicitada autorização manual ao usuário. Não registrar instalação como validação física do novo detector.
### Conferência física após autorização

- Usuário autorizou a captura e deixou o aparelho livre. Modal do Moltres exibiu a forma Purificado selecionada e nível 35. O nível/IVs também podem vir do estado anterior: a leitura sem avaliação registrada às 12:00:15 permaneceu sem nível/IVs suficientes; não considerar essa tentativa uma nova validação da inferência de nível.
- Conferido o seletor do MewName até nível 39,5: PC 3440, PS 174, 70.200 poeiras e 93 doces. Cancelado sem salvar a mudança. O resultado de 106 doces até 40 foi validado automaticamente; não registrar como conferido nesse seletor no aparelho.
- Coleção em sobreposição conferida: campo único de busca ao lado dos dois layouts, painel recolhido até apenas a alça inferior e reaberto por arraste, mantendo o conteúdo.
- Busca física: digitado “mew”, primeira linha Mew; após pausa, acrescentado “t”, primeira linha Mewtwo. Nenhuma marca de coleção alterada. Painel fechado ao terminar.
- Permanecem pendentes regressão física de purificado/sombroso/comum, leitura da grade de uma tag pela bolha e investigação da diferença de poeira. Os bloqueios de interação automática foram resolvidos por autorização do usuário e verificação dos controles antes dos toques.

## Desempenho e PC encoberto — 26/10/2026

- Aparelho 2412DPC0AG (POCO X7 Pro), Android 16, 1220 × 2712, ADB por Wi-Fi. APK debug 1.0.33→1.0.34 assinado com a chave local; os dados foram copiados por `run-as`, o app reinstalado e os dados restaurados, com o backup automático do app preservado.
- Leituras reais pela sobreposição no Pokémon GO (Charmander, Pikachu, Zapdos, Ponyta, Venusaur, Regice, Mewtwo, Pinsir, Grimmsnarl, Impidimp, Zamazenta, Necrozma), com e sem avaliação, e raids/Max Battles (Woobat, Axew, Sableye, Drapion, Raichu). Tempos conferidos pelos logs `Read[...]` e por perfil `simpleperf`.
- Captura: 130–200 ms (antes 340–404 ms); leitura de Pokémon aquecida com mediana de ~0,9 s (antes 1,2–1,4 s); primeira leitura após religar a sobreposição ~2,2 s com falas Rocket lidas do disco.
- Zapdos com apelido e PC encoberto pela imagem: a versão anterior indicava Altaria ou nenhum nome; após a correção, 20 leituras seguidas resultaram em Zapdos. Quadro da gravação de tela incluído como teste `HiddenCpDetailScreenTest`.
- Abertura das telas medida por `atrace`; ficha da Pokédex deixou de travar (pior quadro de 1,3–1,8 s para ~150 ms).
- Suíte local: 574 testes aprovados. Continuam pendentes a regressão física completa de fundos especiais, formas regionais, timeout forçado e memória prolongada.

## R8 e Baseline Profile — 27/10/2026

- POCO X7 Pro, Android 16. Build `benchmark` (release com R8 e perfil, assinado com a chave de debug local) instalado por cima do debug preservando os dados; backup prévio por `run-as` em `MewName-backup-2026-10-27`.
- Abertura a frio medida por `am start -W` e telas por `atrace`, duas rodadas alternadas por condição: 360 ms sem R8, ~317 ms com R8, ~266 ms com R8 e perfil aplicado na instalação. Pior quadro das telas leves e médias caiu pela metade; Pokédex, Raids e Análise PvP mudaram pouco.
- 22 leituras reais pela sobreposição no Pokémon GO com o build R8: 16 Pokémon, 3 filtros, 1 catálogo, 1 batalha e 1 treinador, todas `success`, sem falhas no log. Mediana de ~0,9 s nas leituras de Pokémon, igual ao debug. O usuário conferiu que as informações reconhecidas continuam as mesmas de antes.
- Confirmado no APK que os nomes de enums gravados em preferências e backups continuam intactos com R8.
- Suíte local: 581 testes aprovados; lint debug/release sem erros. Continua pendente a melhoria do reconhecimento sem barra de IV e a regressão física completa de fundos especiais e formas regionais.

## Purificado, sombroso, fundos especiais e ataques — 27–28/10/2026

- POCO X7 Pro, Android 16, build `benchmark` instalado por cima preservando os dados, por Wi-Fi e depois por USB (cabo com mau contato: a conexão caiu várias vezes). Leituras feitas pelo usuário pela sobreposição; logs `MewNameVisual` e `MewNameLearnedBg` e capturas por `screencap` analisados (só da tela do Pokémon GO).
- Purificado: o detector visual antigo marcava 207 de 285 capturas; com a origem pelo custo de Fortalecer, Porygon-Z, Machamp, Regigigas, Gallade e Snorlax saíram corretos e custos ambíguos ficaram sem marca.
- Fundo especial nas capturas com gabarito: 9/41 na v1.0.34, 37/41 agora, com 1 falso positivo antigo (Necrozma). Conferidos ao vivo: Ho-Oh sombroso com o cartão dourado (GO Tour, 12/12 quadros) e com o GO Wild Area 2025 (7/8; o restante era transição), fundo de sombroso como comum (22/22 quadros), Regidrago com Ancients Recovered (9/9) e fundo de Dragão como comum (7/7), Ponyta com Líder Vermelho (7/7, apesar do teste visual de sortudo), Regirock e Groudon sombrosos como comuns.
- Junção de leituras: Regidragos comuns lidos depois de um especial herdavam o cartão, pois a asa esconde o PC; após exigir mesmo PC (ou PS máximo e IVs), o usuário conferiu a sequência correta.
- Fundos aprendidos pela cópia do nome com a tag: 13 recortes salvos (GO Fest, Líder, Temporadas) e reaproveitados nas leituras seguintes do mesmo Pokémon com a categoria certa (0,5–1,3); todos mostram o cartão especial (conferido pelos recortes no backup do usuário, que já os incluía). O usuário conferiu a categoria aprendida também em leituras posteriores. A pasta legível pelo cabo e a restauração de backup com recortes não foram conferidas no aparelho, só nos testes.
- Ataques: Mewtwo com Contra-atacar no ataque rápido, conferido pelo usuário após completar `current_moves.json` com o Game Master (86 combinações).
- Suíte local: 616 testes aprovados; validação de catálogos, lint debug/release e build de release sem erros. Continua pendente a leitura sem barra de IV, Pokémon sem nome lido (Dragão/Aço), cartões muito parecidos com o fundo do tipo (Gengar) e sombrosos lidos sem prova de sombroso.