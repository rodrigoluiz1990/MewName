# Ficha detalhada da Pokédex

Implementação: 30/09/2026.

Ao tocar no cartão de um Pokémon, abre-se uma janela em tela cheia com título centralizado e fechar no canto superior direito. A lista permanece composta por trás, preservando busca e rolagem. A ficha permite selecionar as formas cadastradas para o mesmo número da National Dex e navegar pelos membros da família.

## Conteúdo

- Geral: sprites normal/brilhante do índice de assets do jogo, tipos, atributos base da forma, PC em níveis 15/20/25/40/50/51, fraquezas, resistências, clima favorável, família e ramificações.
- Golpes: rápidos/carregados, indicação legado/especial, tipo, dano, energia e duração PvE e dados PvP disponíveis no catálogo local.
- PvP: GL/UL/ML abertas do PvPoke, posição na lista, nota, moveset, confrontos favoráveis, principais ameaças e data da consulta. As regras IV Master cadastradas aparecem separadas, com a família correspondente e referência nível 40.
- Informações adicionais do PvPoke: distância por doce de companheiro, custo em poeira para segundo carregado e tags, quando fornecidos.
- Fontes: links GO Hub, Stadium Gaming, PvPoke e Pokébattler. Dados indisponíveis aparecem como tal, sem preencher outra forma por aproximação.

## Fontes pesquisadas

- GO Hub: https://db.pokemongohub.net/pokemon/59 — ficha consultada na pesquisa. O acesso direto automatizado retornou HTTP 403; não foi criada raspagem nem copiados dados editoriais. O botão abre a espécie por National Dex; o usuário seleciona a forma no site.
- PvPoke: https://github.com/pvpoke/pvpoke — integra os arquivos públicos de `src/data/gamemaster.json` e `src/data/rankings/all/overall/rankings-{1500,2500,10000}.json`. As três fontes de ranking foram acessadas com sucesso na validação. Textos editoriais não são reproduzidos.
- Stadium Gaming: https://www.stadiumgaming.gg/rank-checker — acesso externo para consultar IVs e escolher a forma; não atribuímos ao Stadium o ranking competitivo do PvPoke nem os dados locais do app.
- Pokébattler: https://www.pokebattler.com/raids — acesso à análise de raids.
- Dados locais existentes: `stats.json`, famílias, golpes, tipos, sprites e regras IV Master. As tabelas de atributos e IV Master não foram modificadas.

## Carregamento e limites

Dados locais são carregados em Dispatchers.IO; a ficha não aguarda a rede para apresentar a base local. Requisições online possuem timeout e limite de 8 MiB. Os downloads das ligas são independentes, após identificar a forma. Imagens são carregadas pelo Coil.

Cache privado em `files/pokedex-online-v1/`, compartilhado entre espécies, válido por 24 horas. Atualizar força nova consulta. Falha na atualização mantém a cópia anterior e identifica dados salvos. Gravações são atômicas. Sem cache/conexão, continuam disponíveis os dados locais; rankings ausentes não são inventados.

Correspondência exige National Dex e nome/alias normalizado exatos. Hisui, Galar, Alola e Paldea são normalizados para os nomes internacionais; não há fallback de forma regional para normal. Se não houver correspondência inequívoca, a ficha informa o limite. A lista de formas representa o cadastro local, não garante disponibilidade de cada forma no jogo. Um sprite brilhante também não confirma lançamento.

PC 15/20/25 mostra o intervalo de IVs 10/10/10 a 15/15/15, sem afirmar que toda espécie pode ser obtida por pesquisa, ovo ou raid. Níveis 40/50/51 mostram PC máximo 100%. Família não é apresentada como cadeia linear de evolução, pois pode conter ramos alternativos.

## Validação

Compilação assembleDebug e quatro testes Robolectric aprovados: identidade regional/Shadow; ranking por ID exato e metadados; atributos, tipos, fraqueza dupla e PC de Arcanine de Hisui; leitura de cache fresco e rejeição de caminhos não autorizados. Sem aparelho conectado para validação visual ou instalação nesta etapa.
