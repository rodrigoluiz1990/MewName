# Proposta: sugestões de raid e integração Pokébattler

Análise em 13/09/2026. Apenas estudo e simulação; nenhuma tela de produção foi alterada nesta etapa.

## Simulação visual

Imagem: raid-suggestions-concept.png. Gerada pela ferramenta integrada image_gen; exemplos ilustrativos de composição, sem representar o ranking atual da API. Na implementação, sprites e ícones devem vir dos assets do app, e os nomes dos golpes, dos arquivos de idioma.

Direção do prompt: duas telas Android lado a lado, modo bolha e detalhes no app principal; glassmorphism lavanda #F0EAF8 e azul gelo; chefe Zacian Hero, fraquezas Aço/Veneno, lista única com Metagross, Nihilego e Excadrill e seus golpes rápidos/carregados; botão compacto Copiar filtro; detalhes secundários; sem coluna Defensores, filtro extenso ou botão Log. A imagem é uma proposta visual, não valida os melhores counters do momento.

## Diagnóstico no código atual

- OverlayService.kt: showBattleSuggestionsOverlay usa LinearLayout/TextView e cores/tamanhos próprios. O cartão branco, o filtro extenso e as duas colunas não reutilizam o tema Compose atual.
- BattleAdvisor.buildAdvice usa loadRaidMetaSnapshot, lista estática geral, e compara tipos declarados dos atacantes. Não há simulação específica de conjuntos de golpes. Sem tipos do chefe, retorna os primeiros dez da lista genérica; a seção de defensores também pode repetir a lista.
- GameInfoRepository.loadBattlePokemonIndex associa o nome de names.json ao pokemon_name do catálogo de tipos por igualdade. `Zacian Hero` não corresponde a `Zacian`, forma `Hero`: types fica vazio. O catálogo distingue Hero (Fairy) de Crowned_sword (Fairy/Steel). Isso explica o fallback compatível com a captura enviada.
- O cálculo de defensores usa attackTypes como tipos defensivos e calcula médias de multiplicadores individuais. Isso não modela corretamente a resistência de tipos duplos nem os golpes do chefe. Remover essa coluna da proposta; qualquer informação de sobrevivência futura deve vir da simulação.
- O filtro atual junta termos com vírgulas: incluir mega ou primordial isolados amplia a busca para outros Pokémon, em vez de restringir uma forma. Construir a consulta com critérios agrupados corretamente; não converter diretamente a lista de tags em OR global.
- RaidPlannerScreen carrega um histórico local progressivamente. RaidHistorySection abre boss.url no navegador; ainda não é uma página nativa de counters atualizados.

## Visual e comportamento propostos

Bolha: modal com alça e fechar; foto/nome/forma do chefe; chips dos tipos de golpes super efetivos; uma lista de até seis opções, com sprite, nome/forma, rápido e carregado e os tipos DE CADA GOLPE. Marcar golpes especiais somente quando os dados confirmarem essa condição. Botão fixo Copiar filtro, sem texto enorme da consulta. Detalhes e diagnóstico ficam em ações secundárias.

App principal: manter pesquisa, abas e carregamento progressivo; abrir detalhes nativos ao tocar no chefe. Separar raids ativas de histórico. Mostrar fonte e data da consulta. Compartilhar o componente de sugestões com a bolha. Aba Chefe pode reunir golpes possíveis, tipos, PC de captura normal e com clima quando houver dados confiáveis. Opções avançadas: nível, clima, amizade, estratégia/esquiva e inclusão de Megas, sombrosos e lendários; chave de cache deve representar toda a configuração aplicada.

A ordem deve ser definida pela métrica da simulação; não é obrigatório que todo counter use exclusivamente um dos tipos super efetivos. Ataques neutros muito fortes podem ter melhor resultado. A indicação de fraquezas é independente do ranking simulado.

## Acesso verificado à API

GET sem credenciais nesta sessão retornou HTTP 200 e application/json:

- https://fight.pokebattler.com/pokemon — IDs, formas, tipos, atributos e golpes possíveis.
- https://fight.pokebattler.com/moves — catálogo dos golpes. A resposta observada inclui Cache-Control public,max-age=3600 e ETag.
- https://fight.pokebattler.com/raids — tiers, parâmetros e chefes; verificar vigência antes de rotular uma entrada como ativa.
- Consulta de counters testada:

```
https://fight.pokebattler.com/raids/defenders/ZACIAN_HERO_FORM/levels/RAID_LEVEL_5/attackers/levels/40/strategies/CINEMATIC_ATTACK_WHEN_POSSIBLE/DEFENSE_RANDOM_MC?sort=ESTIMATOR&weatherCondition=NO_WEATHER&dodgeStrategy=DODGE_REACTION_TIME&aggregation=AVERAGE&includeLegendary=true&includeMegas=true&includeShadow=true
```

A consulta retornou 2.731.339 bytes, PC do chefe 52195, 16 combinações de golpes do chefe e 30 candidatos na agregação randomMove. Não confundir os nomes dos campos: o chefe aparece em attackers[0]; os counters em attackers[0].randomMove.defenders. As combinações específicas ficam em attackers[0].byMove. Cada counter contém pokemonId, byMove, total e stats; move1/move2 identificam golpes, result contém métricas como estimator, edps e mortes. Selecionar combinação coerente com a métrica, não pegar o primeiro elemento indiscriminadamente.

O primeiro conjunto após ordenar total.estimator crescentemente nesta consulta foi Zamazenta Crowned Shield, Zacian Crowned Sword e Necrozma Dusk Mane. Isso é evidência pontual com os parâmetros acima, não uma lista fixa para codificar. A simulação visual usa exemplos mais simples e não afirma reproduzir esse ranking.

As rotas antigas /swagger-ui.html e /swagger/v1/api-docs retornaram HTML com título Pokebattler App Login, não especificação JSON. A API de dados está acessível, mas documentação contratual, limites e regras de redistribuição não foram confirmados. O repositório público https://github.com/celandro/pokebattler-fight avisa que seu código não é o servidor atual. Não copiar esse motor esperando resultados atuais.

## Arquitetura recomendada

1. Criar identidade estável de espécie/forma: dex real, formId interno e pokemonId do provedor. Exemplo Zacian Hero → dex 888, Hero → ZACIAN_HERO_FORM. Não usar o nome exibido ou um índice de registro como identidade. Testar Hero/Crowned e demais variantes.
2. RaidCounterRepository separa download, validação, transformação e cache. RaidCounter modela espécie/forma, golpe rápido e carregado com IDs/tipos, golpe especial quando informado, métrica/posição, configuração e fetchedAt.
3. Atualizar pelo modo normal. Exibir cache antes da rede; buscar detalhes do chefe selecionado, com timeout, cancelamento e concorrência limitada. Preservar o último cache válido se houver erro. Evitar baixar megabytes por cada chefe ao abrir a lista.
4. Salvar JSON compacto e normalizado de 6–12 counters por chefe/configuração, atomically; respeitar ETag/Cache-Control quando disponíveis. Traduzir por IDs usando arquivos do app e guardar/usar imagens localmente.
5. Bolha acessa somente cache. Se faltar o chefe/configuração, mostrar fraquezas calculadas localmente como orientação e indicar atualização no app; nunca mostrar uma lista genérica como ranking daquela raid.
6. Copiar filtro: opção por espécies sugeridas e/ou por tipos de golpes atuais. Usar IDs nacionais confirmados para espécies quando possível, com tratamento explícito de formas. Espécie encontrada não garante golpes, forma ou nível corretos; uma busca por tipos é uma triagem ampla, não uma reprodução exata dos melhores conjuntos da simulação.
7. Migrar apenas a apresentação de RAID para o componente Compose comum. Preservar o fluxo MAX/Dynamax até ter fonte/modelo próprio validado.

## Validações para a implementação

- Regressão Zacian Hero: nome lido resolve forma e Fairy; Crowned resolve Fairy/Steel.
- Parsing de resposta agregada e por moveset; ordenação e golpes correspondentes; campos ausentes e ataques especiais.
- Filtros em PT-BR, EN e ES, nomes com formas e tags, e conjuntos de golpes.
- Cache por configuração, modo avião, expiração, falha/timeout e persistência após reinício.
- Medir captura → chefe → primeiro resultado no celular; nenhuma consulta de counters na thread principal ou na leitura da bolha.
- UI com fontes grandes, três botões Android e gestos, listas longas e nomes compridos.

Fontes consultadas:
- https://www.pokebattler.com/raids/defenders/ZACIAN_HERO_FORM/levels/RAID_LEVEL_3/attackers/levels/40/strategies/CINEMATIC_ATTACK_WHEN_POSSIBLE/DEFENSE_RANDOM_MC (identificação das fraquezas; a chamada testada acima usa nível 5)
- https://www.pokemon.com/uk/strategy/zacian-pokemon-go-raid-battle-tips
- https://github.com/celandro/pokebattler-fight
- https://articles.pokebattler.com/privacy-policy-for-doduno-discord-bot/ (confirma uso de API para simulações)
- Endpoints JSON acima, consultados diretamente.