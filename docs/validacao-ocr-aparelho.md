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
