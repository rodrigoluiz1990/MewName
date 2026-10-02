# Auditoria PVP: formas de Hisui

As 16 formas têm nomes canônicos, aliases e entradas de atributos separados. O cálculo e seu cache usam o nome canônico da forma. As famílias mantêm cada forma como uma opção própria nas quatro ligas.

## Correções

Além de Growlithe/Arcanine, os atributos normais de Electrode, Typhlosion, Qwilfish, Lilligant, Zorua, Zoroark, Braviary e Avalugg estavam preenchidos com os valores de Hisui. Foram restaurados os atributos normais e criados registros regionais separados. Samurott e Decidueye já estavam separados. Voltorb e Sneasel têm os mesmos atributos nas duas formas, o que pode produzir rankings iguais sem indicar mistura.

| Pokémon | Normal (Ataque/Defesa/Stamina) | Hisui |
|---|---|---|
| Growlithe | 136/93/146 | 142/92/155 |
| Arcanine | 227/166/207 | 232/165/216 |
| Voltorb | 109/111/120 | 109/111/120 |
| Electrode | 173/173/155 | 176/176/155 |
| Typhlosion | 223/173/186 | 238/172/177 |
| Qwilfish | 184/138/163 | 184/151/163 |
| Sneasel | 189/146/146 | 189/146/146 |
| Samurott | 212/157/216 | 218/152/207 |
| Lilligant | 214/155/172 | 208/159/172 |
| Zorua | 153/78/120 | 162/79/111 |
| Zoroark | 250/127/155 | 261/128/146 |
| Braviary | 232/152/225 | 213/137/242 |
| Avalugg | 196/240/216 | 214/238/216 |
| Decidueye | 210/179/186 | 213/174/204 |
| Sliggoo | 159/176/169 | 153/190/151 |
| Goodra | 220/242/207 | 211/255/190 |

## Fontes e limites

Consulta dos dados: 24/09/2026. Auditoria concluída: 26/09/2026.

- PvPoke, catálogo utilizado para as formas normais e 14 formas de Hisui: https://github.com/pvpoke/pvpoke/blob/master/src/data/gamemaster/pokemon.json
- Game Master consultado: https://github.com/PokeMiners/game_masters/blob/master/latest/latest.json
- Sliggoo/Goodra de Hisui não constavam nessas duas fontes. Seus atributos são calculados conforme a conversão para Pokémon GO publicada em https://pokemongohub.net/post/news/hisuian-pokemon-stats-in-pokemon-go/ . São provisórios; a presença no catálogo não confirma lançamento nem elegibilidade em copas específicas.

## Verificação

HisuiPvpCatalogTest confere as 16 formas, aliases, atributos e PC calculado independentemente. Também verifica oito linhas por par normal/Hisui nas quatro ligas, sem agrupar as formas. ArcaninePvpTest preserva a regressão 1/15/12 e o empate de produto de atributos na Ultra League. As formas de Hisui foram retiradas da lista de exceções que permitia famílias sem nomes/atributos.

Esta auditoria abrange atributos e ranking de IV de PVP. Não altera a lista manual de combinações IV Master nem os catálogos de golpes.
