# Auditoria Dittobase × Stadium Gaming — 30/09/2026

## Conclusão

O Dittobase apresentou melhor cobertura de formas e maior concordância dos atributos verificáveis nesta captura. Isso não prova que todos os seus registros estejam atualizados ou presentes no jogo: 45 entradas ficaram sem confirmação. O Stadium continua amplamente compatível, mas tem uma divergência confirmada de atributos e três registros sem atributos. Nenhum cadastro do MewName foi alterado.

Para o app, usar o Game Master como referência dos atributos; usar os sites como comparação, com espécie/forma, nível máximo, piso de IV e critérios de desempate explicitamente alinhados. Não copiar posições de ranking entre sites como se fossem equivalentes.

## Fontes e abrangência

- [Dittobase IV checker](https://www.dittobase.com/pokemon-go/pvp-ivs): HTML, JavaScript público e [API completa usada pelo calculador](https://www.dittobase.com/api/pokemon-for-calc?v=3), 1.830 registros.
- [Stadium Gaming Rank Checker](https://www.stadiumgaming.gg/rank-checker): HTML e [bundle público](https://www.stadiumgaming.gg/js/app.js?id=7f6f4d2438c943b8453993e9fc81c04a), módulo 7315 com 1.500 registros e módulo 7846 com calculador.
- Game Master extraído anteriormente do celular, em `reference-assets/game-master-phone-2026-09-27/stats.json`: 2.477 templates com atributos.
- [Game Master público, commit 8e227be](https://github.com/PokeMiners/game_masters/blob/8e227be44f288d34463e23bf04e9b564d3c16f79/latest/latest.json), de 29/08/2026: era a última alteração desse arquivo na consulta de 30/09. Os 2.472 templates compartilhados com a captura do celular coincidem nos três atributos. Megas foram comparadas com `tempEvoOverrides` desta fonte pública; o JSON resumido do celular não contém esses overrides.

Os arquivos consultados e hashes estão em `reference-assets/iv-checkers-2026-09-30/`. Contagens são de registros, não de espécies únicas: incluem formas, trajes, duplicatas e sombras. Ter mais registros não comprova maior atualização.

## Comparação completa dos atributos

Ordem dos atributos: ataque / defesa / stamina.

| Resultado | Dittobase | Stadium |
|---|---:|---:|
| Registros examinados | 1.830 | 1.500 |
| Atributos iguais à referência identificada | 1.785 | 1.494 |
| Divergência confirmada | 0 | 1 |
| Registro sem atributos | 0 | 3 |
| Sem correspondência segura/sem confirmação | 45 | 2 |

Cada linha e seu template de referência estão em [auditoria-dittobase-stadium.csv](auditoria-dittobase-stadium.csv).

### Diferenças relevantes

- **Dugtrio normal:** Stadium 167/134/111; template `DUGTRIO_NORMAL` do jogo e Dittobase 167/136/111. O template genérico antigo também existe no Game Master; a comparação prioriza a forma NORMAL explícita. Isso explica a provável origem do valor do Stadium, sem provar sua rotina de atualização.
- **Honedge, Doublade e Aegislash:** existem no bundle do Stadium, mas com `stats: {}`. Não fornecem uma referência válida de IV nesse conjunto de dados.
- **Aegislash no Dittobase:** Espada 272/97/155; Escudo 97/272/155. Ambos coincidem com os templates específicos do jogo. O link genérico AEGISLASH do Stadium não é equivalente ao link aegislash-blade.
- **Pumpkaboo e Gourgeist genéricos:** nos dois sites usam os atributos da forma Pequena. A auditoria os relaciona explicitamente à forma SMALL. As formas Average têm entradas próprias; não se deve presumir que o nome genérico signifique Médio.
- **Nidoran:** dois registros do Stadium têm o mesmo identificador NIDORAN, sem identificador sexual seguro nos campos examinados. Ficaram pendentes, não classificados como atributos errados. Dittobase diferencia `nidoran-f` e `nidoran-m`.

### Dittobase: 45 registros sem confirmação

Incluem 36 megas sem override correspondente nesta referência; Hisuian Sliggoo e Goodra; Basculegion e Basculegion Female; três máscaras de Ogerpon; Terapagos Stellar e Terastal. A lista exata está no CSV. Não significa que estejam errados nem que estejam indisponíveis no jogo; significa que a referência utilizada não permite validar seus atributos. Não transferir os atributos da forma normal para essas formas automaticamente.

## Compatibilidade do cálculo

Os dois calculadores buscam o maior produto de atributos dentro do limite de PC e do nível configurado. Isso mede eficiência de IV, não força no meta, taxa de vitórias, classificação de golpes ou elegibilidade para uma copa específica.

| Critério | Dittobase | Stadium |
|---|---|---|
| Nível padrão | 50 | 40 em sessão sem preferências XL/companheiro |
| Master: limite interno de PC | 10.000 | 9.999 |
| Produto de atributos antes de ordenar | Arredondado para inteiro | Mantido como número fracionário |
| Desempate padrão | Ataque efetivo, PS, PC, IV de stamina | Soma de IVs após produto igual |
| CPM em 16,5 | 0,542635762230353 | 0,5426357375 |
| CPM em 22,5 | 0,633649181622743 | 0,6336491432 |

Os outros 107 dos 109 CPMs publicados coincidem. Esta comparação identifica as diferenças entre os sites; não valida a precisão desses dois meios níveis contra o cliente do jogo. O produto é `(AtaqueBase+IV) × (DefesaBase+IV) × CPM² × floor((StaminaBase+IV) × CPM)` antes dos arredondamentos adicionais. Não confundir porcentagem desse produto com porcentagem da soma de IVs.

### Teste exaustivo nas formas compartilhadas

Foram comparadas **1.217 chaves de forma** com atributos confirmados e idênticos nos dois sites, eliminando duplicatas por chave. Para cada uma: todas as **4.096 combinações de IV**, piso zero, níveis máximos **40 e 50**, ligas de **500, 1.500, 2.500 e Master**. Total: **9.736 cenários e 39.878.656 combinações**.

Método: execução do módulo JavaScript público original do Stadium com ordenação padrão; reprodução local das fórmulas, tabela CPM, arredondamento e desempate identificados no JavaScript do Dittobase. Não foi automação manual da interface de cada Pokémon. Os números descrevem este ensaio computacional e estes parâmetros, não todos os modos/opções possíveis dos sites.

- Melhor IV igual em **9.727/9.736 cenários (99,91%)**.
- Posição numérica diferente em **5.346.769 combinações (13,41%)**; portanto, coincidência de posição de aproximadamente **86,59%**.
- **80 diferenças no PC calculado**, sem diferenças no nível escolhido neste ensaio. Diferenças pequenas de precisão e implementação podem afetar PCs na fronteira de um inteiro.
- Maior diferença de posição: 106, em Shedinja. Não representa diferença proporcional de força; os produtos próximos/empatados podem mudar de posição. A existência no cálculo não comprova elegibilidade em batalha.

Arquivo completo: [auditoria-dittobase-stadium-rankings.csv](auditoria-dittobase-stadium-rankings.csv).

Cenários em que o primeiro lugar mudou:

| Forma | Liga | Nível máximo | Stadium | Dittobase |
|---|---|---|---|---|
| Slowbro de Galar | 500 | 40 e 50 | 0/14/8 | 0/12/11 |
| Chinchou | 500 | 40 e 50 | 0/14/14 | 0/13/15 |
| Entei | 2.500 | 40 e 50 | 0/13/14 | 0/12/15 |
| Tepig | 500 | 40 e 50 | 0/15/11 | 0/14/12 |
| Carkol | 1.500 | 50 | 0/14/13 | 0/13/14 |

Esses casos não demonstram erro nos atributos: usaram exatamente a mesma tripla base. Também não garantem que os IVs sejam obteníveis atualmente; o teste usa piso zero deliberadamente.

## Limites da conclusão

A auditoria dos atributos percorreu todos os registros baixados; o teste de ranking cobre as formas compartilhadas e verificáveis, não as pendentes ou sem atributos. Não foram auditados todos os golpes, regras de disponibilidade, atualização de imagens ou rankings de desempenho em batalha. O teste não incluiu nível 51 nem todos os pisos de captura.

Não havia telefone conectado nesta verificação. A referência do cliente é de 27/09; não há garantia sobre mudanças do servidor depois dessa captura. A data de download de um site não é a data de atualização de cada registro. Portanto, a conclusão defensável é **melhor cobertura e concordância verificável do Dittobase nesta captura**, não uma garantia absoluta de que seja sempre o site mais atualizado.

Para a tabela IV Master do MewName, preservar a regra já definida: nível 40 e 67%=10/10/10 deliberado. Esta auditoria não altera essa convenção e não autorizou correções automáticas.
