# Auditoria de ataques — 7 de setembro de 2026

## Idioma

A Activity fornecia LocalAppLanguage, mas a janela Compose do OverlayService não fornecia esse valor e usava português por padrão. Além disso, o seletor publicava nomes crus em inglês antes do enriquecimento: ao abrir o modal nesse intervalo, as opções ficavam congeladas em inglês.

A bolha agora acompanha a preferência app_language. PokemonMoveRepository.load retorna nomes traduzidos antes da publicação do resultado, mantém o identificador canônico para seleção e separa o cache por idioma e nome solicitado. As traduções usam os IDs das tabelas locais; Mystical Fire e Wildbolt Storm também recebem tradução mesmo sem estatísticas locais. A seleção não é apagada durante a recarga do idioma.

## Ausências corrigidas

| Espécie/forma | Ataque acrescentado |
| --- | --- |
| Zacian Crowned Sword | Behemoth Blade |
| Zamazenta Crowned Shield | Behemoth Bash |
| Dialga Origin | Roar Of Time |
| Palkia Origin | Spacial Rend |
| Necrozma Dusk Mane | Sunsteel Strike |
| Necrozma Dawn Wings | Moongeist Beam |
| Rayquaza | Dragon Ascent |
| Heracross | Rock Tomb |
| Cinderace | Blast Burn |

Zacian e Zamazenta tinham uma única entrada com golpes misturados de Hero e Crowned. Foram separados por forma, com aliases usados na interface. O resolvedor agora verifica formas exatas antes de recorrer à espécie base. Não se acrescentou Behemoth Blade ao Zacian Hero: ele substitui Iron Head quando Zacian assume a forma Crowned Sword.

As listas normais das novas formas vêm do Game Master consultado; os ataques obtidos por transformação, fusão ou item foram acrescentados explicitamente com base nas fontes oficiais abaixo. O marcador booleano legacy continua sendo o marcador de ataques especiais usado pelo aplicativo, incluindo ataques por transformação/item, e não uma promessa de obtenção por MT de Elite.

## Resultado da comparação dos catálogos locais

Foram encontradas 24 combinações ausentes em 23 espécies, comparando todos os termos de legacyMoves que correspondem a nomes canônicos do catálogo de golpes com a versão original de current_moves.json. A comparação não depende de grupos de três idiomas: há entradas que não seguem esse formato.

- 9 combinações corrigidas na tabela acima.
- 2 registros de Solgaleo/Lunala apontam para ataques que as fontes oficiais consultadas atribuem às fusões de Necrozma; não foram copiados para as espécies base.
- 13 registros restantes não foram confirmados nos campos de golpes normais/Elite do Game Master consultado. Ausência nesse arquivo não prova indisponibilidade histórica; foram preservados para revisão, sem acrescentar ataques por suposição.

A lista completa está em [move-audit-missing.csv](source-data/move-audit-missing.csv). Os registros do Game Master das espécies envolvidas foram preservados em [move-audit-species.json](source-data/move-audit-species.json).

Limites: nomes sem correspondência canônica, como FIRE PERSONA, BURN UP e WEATHER BALL sem tipo, não são contados como ausências confirmadas. Esta auditoria não substitui uma revisão completa da disponibilidade histórica e das variantes de todos os golpes. Nenhuma alteração foi feita em families.json.

## Fontes consultadas

- [Pokémon GO — transformação de Zacian e Zamazenta](https://pokemongo.com/news/crowned-energy-resource-zacian-zamazenta).
- [Pokémon GO — efeitos de aventura de Dialga/Palkia Origem](https://pokemongo.com/news/origin-forme-adventure-effects-dialga-palkia).
- [Pokémon GO — efeitos de aventura das fusões de Necrozma](https://pokemongo.com/pl/post/fusion-adventure-effects-necrozma).
- [Ajuda oficial — Meteorito e Dragon Ascent](https://niantic.helpshift.com/hc/en/6-pokemon-go/faq/3332-how-can-i-mega-evolve-my-pokemon/).
- [Game Master extraído pelo PokeMiners](https://github.com/PokeMiners/game_masters/blob/master/latest/latest.json), consultado em 2026-09-07. Dados extraídos do jogo, sujeitos a mudanças e sem garantia de disponibilidade histórica completa.

## Validação

Testes de idioma PT-BR/EN/ES, aliases das formas, isolamento do cache, ataques especiais por forma e traduções sem ficha estatística. Compilação e validação semântica dos assets executadas pelo Gradle. A janela flutuante ainda precisa de teste visual em aparelho.
## Correção posterior do carregamento

Comparação com o commit 6fdcc88: a lista original lia apenas current_moves.json. A primeira correção de tradução tornou load dependente do enriquecimento completo, incluindo estatísticas e tabelas de texto grandes. A atualização do modal aberto não eliminava essa espera.

O carregamento inicial voltou a ser independente das estatísticas. Usa current_moves.json, os marcadores locais de legado e catalogs/move_labels.json (aproximadamente 75 KB). O enriquecimento de rankings ocorre após publicar opções selecionáveis; falhas nessa etapa preservam a lista. tools/generate-move-labels.ps1 regenera o arquivo compacto a partir das tabelas locais, e os testes comparam as traduções dos três idiomas com as fontes.