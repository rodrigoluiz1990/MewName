# Implementação das sugestões de raid

Implementado em 13/09/2026.

- RaidDetailsScreen compartilhada entre a navegação principal e a bolha. Golpes rápidos/carregados do chefe, fraquezas, PC 10/10/10 e 15/15/15 nos níveis 20 e 25, climas, lista de counters com seus golpes e filtro copiável.
- RaidCounterRepository integra os JSONs de Pokémon, golpes, categorias e simulações de Pokébattler. Resposta da simulação validada pelo chefe/categoria, ordenada pelo estimator do conjunto de golpes, compactada para até 12 counters e persistida atomicamente por chefe/categoria/nível 40 ou 50.
- Baseline de metadados e mapa de imagens incluídos em assets/raids. Atualização de metadados pelo ícone da lista de raids. Atualização dos counters pelo detalhe do chefe. Configuração inicial sem clima, incluindo Megas/sombrosos/lendários; não representa resultados personalizados da coleção.
- Bolha não faz chamadas de rede para dados nem imagens. Usa cache e oferece orientação para atualizar no app se o relatório faltar. Categorias e níveis diferentes não compartilham indevidamente um relatório.
- Formas de captura separadas do chefe (Mega/Primal, Crowned e fusões de Necrozma). Em raids sombrosas, 67% é referência 10/10/10, não mínimo possível.
- Filtro combina números da Pokédex das espécies e tipos de golpes com AND; não adiciona tags mega ou shadow em OR global. Ainda é necessário conferir formas e conjuntos de golpes após a busca.
- Catálogo atual online separado das categorias históricas locais; entradas legacy, future e Max excluídas do catálogo normal novo. Fluxo Max preservado.

Validação: testes unitários/Robolectric, JSON real de Zacian reduzido como fixture, PCs de Zacian 2100/2188 e 2625/2735, formas, ordenação/correspondência dos ataques, round-trip JSON, separação do cache, falhas e categorias. Build debug gerado com sucesso. Layout e leitura ainda não validados no celular nesta etapa.

Endpoints: https://fight.pokebattler.com/pokemon, https://fight.pokebattler.com/moves, https://fight.pokebattler.com/raids. Imagens oficiais do site via mapa de assets identificado na aplicação pública, armazenado localmente; fonte Pokébattler indicada na interface. A simulação é buscada pelo endpoint detalhado documentado em raid-redesign-analysis.md.
## Correção validada no telefone — 13/09/2026

O log do aparelho registrou às 16:43 e 16:44 IllegalStateException: No OnBackPressedDispatcherOwner was provided via LocalOnBackPressedDispatcherOwner, originada no BackHandler de RaidDetailsScreen durante a composição do overlay. Mesmo enabled=false exige um dispatcher. Agora o BackHandler só é composto fora do modo bolha.

Rótulos das categorias alterados para Raid. O seletor de categoria foi removido dos detalhes, mantendo apenas a informação da categoria determinada pelo fluxo de seleção/reconhecimento.

APK instalado por atualização, preservando dados. Duas leituras reais da tela de Zacian no Pokémon GO abriram o resultado, com o mesmo processo vivo, sem novo crash no buffer do aparelho. Conferidos os golpes, fraquezas, quatro PCs e a lista rolável de sugestões; confirmado Raid nas abas da tela principal. Os 159 testes automatizados passaram.