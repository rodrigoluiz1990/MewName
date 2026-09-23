# Raids e sugestões Max — revisão de 21/09/2026

## Categoria visual e cálculo

A categoria Raid · Super Mega identifica encontros com escudos.
RAID_LEVEL_MEGA_5 é um nível de simulação antigo e não comprova essa mecânica.

Groudon Primal, Kyogre Primal, Rayquaza Mega, Latias Mega e Latios Mega ficam em
Raid · Mega, mantendo o nível de cálculo individual RAID_LEVEL_MEGA_5.
Cada registro pode guardar battleTier; agrupar abas não altera o nível enviado ao serviço.

O histórico Super Mega contém Victreebel, Malamar, Dragonite, Falinks, Raichu X,
Raichu Y, Starmie, Mewtwo X, Mewtwo Y e Staraptor. O registro de Staraptor foi adicionado;
os demais foram reunidos a partir do histórico existente. Datas conhecidas foram preservadas.

O histórico do provedor ainda lista várias Super Megas nos níveis Mega antigos.
A lista confirmada em RaidCategoryRules.kt corrige essas categorias históricas.
Para encontros atuais, prevalece o tipo explícito informado pelo serviço
(MEGA_ENHANCED indica escudos); não se presume que qualquer encontro de uma espécie
historicamente Super Mega tenha a mesma mecânica.

Nas Super Megas salvas cujo serviço só oferece o cálculo antigo, a tela informa que
as sugestões usam os dados Mega disponíveis e que a estimativa não inclui a fase de escudos.
Não são inventados níveis de simulação que o provedor não anunciou para aquele chefe.

## Fontes oficiais

- [Mecânica dos escudos, Victreebel e Malamar](https://pokemongo.com/news/mega-evolution-2026-update).
  Um treinador quebra um escudo usando um Pokémon megaevoluído. Primal Groudon,
  Primal Kyogre e Ditto transformado em Mega não podem quebrar escudos.
- [Dragonite](https://pokemongo.com/gotour/global).
- [Falinks](https://pokemongo.com/news/falinks-super-mega-raid-day-2026).
- [Raichu X/Y](https://pokemongo.com/news/raichu-super-mega-raid-day-2026).
- [Starmie](https://pokemongo.com/news/starmie-super-mega-raid-day-2026).
- [Mewtwo X/Y](https://pokemongo.com/gofest/megafinale).
- [Staraptor, 19/09/2026](https://pokemongo.com/news/staraptor-super-mega-raid-day-2026).

## Sugestões Max

A tela usa agora o ranking da API pública do Pokébattler, substituindo as listas
heurísticas locais de ataque, defesa e vida. O elenco não é limitado à lista manual
do app; novos Pokémon retornados pelo provedor são mantidos.

- Ataque: ESTIMATOR.
- Escudo: TANK.
- Vida/Cura: TOTAL_HEAL.
- Padrões da página pública: nível 40, NO_WEATHER, DODGE_100,
  AVERAGE, CINEMATIC_ATTACK_WHEN_POSSIBLE, DEFENSE_RANDOM_MC e numParty=1.

A API retorna defensores e combinações de golpes em ordem inversa à interface.
O parser inverte ambas as listas, como a função reverseMoves do site, sem reordenar
Escudo e Cura por estimator. Usa a agregação randomMove, apresentada pelo site antes
das combinações individuais do chefe.

O nível Max selecionado na busca é enviado à API. Para comparar, o link no fim da
lista abre o mesmo chefe, nível da batalha e função. A página inicial do chefe no
site pode selecionar outro nível automaticamente; nesse caso o ranking pode diferir.

Cache separado por chefe, nível e função; carregamento em segundo plano, atualização
manual e data da consulta no rodapé. Sem conexão, são preservados os rankings salvos.
Sem cache, a tela informa indisponibilidade, sem apresentar estimativas locais como
se fossem resultados do Pokébattler.

Os golpes recomendados são exibidos nos cards. O link Pokébattler foi retirado do
cartão de informações do chefe e colocado após as sugestões, como na tela de raids.

Fontes verificadas:
- https://www.pokebattler.com/max/DYNAMAX_ARTICUNO
- https://www.pokebattler.com/max/DYNAMAX_ARTICUNO?sort=TANK
- JavaScript público v8.17.59 (parâmetros e transformação da resposta).
- Amostras da API em src/test/resources/raids/max-articuno-*.json.

As heurísticas antigas permanecem apenas no modelo de análise OCR; a tela Max não
as utiliza para preencher o ranking.

## Retorno à busca

O carregamento do catálogo e seus efeitos ficam antes da navegação condicional aos
detalhes. Assim, entrar/sair dos detalhes não descarta os dados nem reinicia a carga
progressiva. Um SaveableStateHolder mantém aba, pesquisa e rolagem. Agrupamento e
ordenação são memorizados. Atualização manual continua disponível; recriar o processo
ou sair completamente da rota pode carregar o catálogo novamente.