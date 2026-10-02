# Auditoria dos atributos base e ordem National Dex

## Situação atual após completar as formas

- As 36 formas distintas listadas na auditoria foram cadastradas em names/stats e vinculadas às famílias, independentemente da elegibilidade para PvP. Seus atributos vieram do cache do celular.
- Os templates ZYGARDE_TEN_PERCENT e ZYGARDE_COMPLETE_TEN_PERCENT usam 205/173/144 e não geraram duas formas 10% duplicadas.
- NIDORAN genérico foi removido de stats e evolution_stages; macho e fêmea e seus aliases continuam separados. O tratamento de OCR sem símbolo permanece apenas para oferecer as duas famílias, sem atribuir stats a uma espécie genérica.
- Total atual: **1.159 entradas de atributos**. Mantida a ordem da National Dex.
- A exceção de validação para famílias sem cadastro ficou restrita a Keldeo (Resolute). As demais referências anteriores agora resolvem nomes e stats cadastrados.
- Testes de resolução de aliases, atributos por forma e famílias por tamanho passaram, junto das regressões de Hisui/Arcanine.

Pendências reais: confirmar Sliggoo/Goodra de Hisui e Gimmighoul Chest; Basculegion permanece com dados do PvPoke sem confirmação no cache do celular. Também falta uma auditoria completa das formas de atributos idênticos (incluindo Keldeo Resolute), Mega/Primal, aliases e dados auxiliares como golpes/tipos/imagens. O cadastro de atributos permite calcular PC e ranking de IV; não equivale a avaliar competitividade por golpes ou desempenho em batalhas, nem a afirmar elegibilidade para ligas.

As seções abaixo preservam o histórico da auditoria inicial. A antiga lista de lacunas foi atendida nesta atualização.

Verificação: 27/09/2026. A ordem dos números nas tabelas é ataque/defesa/stamina.

## Fontes fixadas

- [Game Master publicado pelo PokeMiners](https://github.com/PokeMiners/game_masters/blob/8e227be44f288d34463e23bf04e9b564d3c16f79/latest/latest.json), revisão de 29/08/2026.
- [Catálogo do PvPoke](https://github.com/pvpoke/pvpoke/blob/bc28b41e766db0597e6c2b4d5e01ca9dd4ab2471/src/data/gamemaster/pokemon.json), revisão de 18/09/2026.

São as revisões publicadas consultadas; não uma consulta ao servidor do jogo. A presença de um template não prova que a forma foi lançada ou pode participar de uma liga.

## Resultado

- 1115 entradas existentes verificadas: 1111 correspondências exatas de espécie/forma no Game Master; 1079 também encontradas no PvPoke.
- 23 entradas corrigidas; 6 formas separadas e a espécie Slither Wing adicionadas. Basculegion macho e fêmea também adicionados via PvPoke. Total final: 1124 entradas.
- Todos os nomes canônicos cadastrados possuem stats. Isso não significa que todas as formas do jogo estejam cadastradas.
- Ordenação por National Dex, agrupando aliases e formas no mesmo número. NIDORAN genérico foi mantido junto ao #29, sem inferir seu sexo.
- Templates NORMAL prevalecem sobre os genéricos (Dugtrio). Pumpkaboo/Gourgeist sem qualificador mantêm tamanho médio. Sombras e purificados não alteram estes atributos base.

## Correções

| Entrada | Antes | Atual | Template |
|---|---|---|---|
| RAICHU | 201/154/155 | 193/151/155 | V0026_POKEMON_RAICHU_NORMAL |
| SANDSHREW | 125/129/137 | 126/120/137 | V0027_POKEMON_SANDSHREW_NORMAL |
| SANDSLASH | 177/195/181 | 182/175/181 | V0028_POKEMON_SANDSLASH_NORMAL |
| NINETALES | 170/193/177 | 169/190/177 | V0038_POKEMON_NINETALES_NORMAL |
| DIGLETT | 108/81/67 | 109/78/67 | V0050_POKEMON_DIGLETT_NORMAL |
| DUGTRIO | 201/142/111 | 167/136/111 | V0051_POKEMON_DUGTRIO_NORMAL |
| MEOWTH | 99/78/120 | 92/78/120 | V0052_POKEMON_MEOWTH_NORMAL |
| PERSIAN | 158/136/163 | 150/136/163 | V0053_POKEMON_PERSIAN_NORMAL |
| SLOWBRO | 182/156/216 | 177/180/216 | V0080_POKEMON_SLOWBRO_NORMAL |
| EXEGGUTOR | 230/153/216 | 233/149/216 | V0103_POKEMON_EXEGGUTOR_NORMAL |
| MRMIME | 183/169/137 | 192/205/120 | V0122_POKEMON_MR_MIME_NORMAL |
| ARTICUNO | 250/197/207 | 192/236/207 | V0144_POKEMON_ARTICUNO_NORMAL |
| ZAPDOS | 252/189/207 | 253/185/207 | V0145_POKEMON_ZAPDOS_NORMAL |
| MOLTRES | 202/231/207 | 251/181/207 | V0146_POKEMON_MOLTRES_NORMAL |
| WOOPER (PALDEA) | 120/59/146 | 75/66/146 | V0194_POKEMON_WOOPER_PALDEA |
| SLOWKING | 190/180/216 | 177/180/216 | V0199_POKEMON_SLOWKING_NORMAL |
| CORSOLA | 116/182/155 | 118/156/146 | V0222_POKEMON_CORSOLA_NORMAL |
| MINIOR | 218/131/155 | 116/194/155 | V0774_POKEMON_MINIOR |
| NECROZMA | 277/220/200 | 251/195/219 | V0800_POKEMON_NECROZMA_NORMAL |
| ZAMAZENTA | 250/292/192 | 254/236/192 | V0889_POKEMON_ZAMAZENTA |
| FARFETCH D | 174/114/141 | 124/115/141 | V0083_POKEMON_FARFETCHD_NORMAL |
| MR MIME | 183/169/137 | 192/205/120 | V0122_POKEMON_MR_MIME_NORMAL |
| PALAFIN | 322/196/225 | 143/144/225 | V0964_POKEMON_PALAFIN |

## Formas adicionadas

| Forma | Atributos |
|---|---|
| Palafin (Hero) | 322/196/225 |
| Minior (Core) | 218/131/155 |
| Necrozma (Juba do Crepúsculo) | 277/220/200 |
| Necrozma (Asas da Alvorada) | 277/220/200 |
| Necrozma (Ultra) | 337/196/200 |
| Zamazenta (Coroado) | 250/292/192 |

Palafin sem qualificador representa Zero; o alias Hero agora resolve uma entrada independente. Minior sem qualificador representa Meteor; Core preserva os atributos de núcleo. As formas de Necrozma e Zamazenta existentes nas famílias agora possuem nomes e stats próprios.

## Valores preservados sem confirmação

| Entrada | Valor atual | Motivo |
|---|---|---|
| NIDORAN | Removido | Mantidas apenas as entradas feminina e masculina e seus aliases. |
| SLIGGOO (HISUI) | 153/190/151 | Sem template nem entrada correspondente no PvPoke consultado. |
| GOODRA (HISUI) | 211/255/190 | Sem template nem entrada correspondente no PvPoke consultado. |
| GIMMIGHOUL CHEST | 140/76/128 | A fonte possui Roaming; não foi usada para confirmar Chest. |

## Formas inicialmente ausentes — cadastradas nesta atualização

Os templates abaixo identificaram os conjuntos de atributos ausentes na auditoria inicial. Agora foram cadastrados por autorização do usuário, sem condicionar o cadastro à elegibilidade. Templates internos e formas ainda não lançadas também podem aparecer. Formas com atributos idênticos não aparecem nesta lista; isso não atesta sua seleção independente no app. Mega/Primal usam tempEvoOverrides e não foram convertidas em espécies elegíveis de PvP.

| Template | Atributos |
|---|---|
| V0150_POKEMON_MEWTWO_A | 182/278/214 |
| V0386_POKEMON_DEOXYS_ATTACK | 414/46/137 |
| V0386_POKEMON_DEOXYS_DEFENSE | 144/330/137 |
| V0386_POKEMON_DEOXYS_SPEED | 230/218/137 |
| V0413_POKEMON_WORMADAM_TRASH | 127/175/155 |
| V0479_POKEMON_ROTOM_FAN | 204/219/137 |
| V0479_POKEMON_ROTOM_FROST | 204/219/137 |
| V0479_POKEMON_ROTOM_HEAT | 204/219/137 |
| V0479_POKEMON_ROTOM_MOW | 204/219/137 |
| V0479_POKEMON_ROTOM_WASH | 204/219/137 |
| V0483_POKEMON_DIALGA_ORIGIN | 270/225/205 |
| V0484_POKEMON_PALKIA_ORIGIN | 286/223/189 |
| V0487_POKEMON_GIRATINA_ORIGIN | 225/187/284 |
| V0492_POKEMON_SHAYMIN_SKY | 261/166/225 |
| V0555_POKEMON_DARMANITAN_GALARIAN_ZEN | 323/123/233 |
| V0555_POKEMON_DARMANITAN_ZEN | 243/202/233 |
| V0641_POKEMON_TORNADUS_THERIAN | 238/189/188 |
| V0642_POKEMON_THUNDURUS_THERIAN | 295/161/188 |
| V0645_POKEMON_LANDORUS_THERIAN | 289/179/205 |
| V0648_POKEMON_MELOETTA_PIROUETTE | 269/188/225 |
| V0681_POKEMON_AEGISLASH_BLADE | 272/97/155 |
| V0710_POKEMON_PUMPKABOO_LARGE | 120/122/144 |
| V0710_POKEMON_PUMPKABOO_SMALL | 122/124/127 |
| V0710_POKEMON_PUMPKABOO_SUPER | 118/120/153 |
| V0711_POKEMON_GOURGEIST_LARGE | 179/206/181 |
| V0711_POKEMON_GOURGEIST_SMALL | 171/219/146 |
| V0711_POKEMON_GOURGEIST_SUPER | 182/200/198 |
| V0718_POKEMON_ZYGARDE_COMPLETE | 184/207/389 |
| V0718_POKEMON_ZYGARDE_COMPLETE_TEN_PERCENT | 205/173/144 |
| V0718_POKEMON_ZYGARDE_TEN_PERCENT | 205/173/144 |
| V0720_POKEMON_HOOPA_UNBOUND | 311/191/173 |
| V0746_POKEMON_WISHIWASHI_SCHOOL | 255/242/128 |
| V0875_POKEMON_EISCUE_NOICE | 173/139/181 |
| V0876_POKEMON_INDEEDEE_FEMALE | 184/184/172 |
| V0890_POKEMON_ETERNATUS_ETERNAMAX | 251/505/452 |
| V0898_POKEMON_CALYREX_ICE_RIDER | 268/246/205 |
| V0898_POKEMON_CALYREX_SHADOW_RIDER | 324/194/205 |

## Cobertura por National Dex

Slither Wing (#988) estava ausente e foi adicionado com 261/193/198, conforme V0988_POKEMON_SLITHERWING_NORMAL. Basculegion (#902) também estava ausente. As formas macho (217/144/260) e fêmea (199/144/260) foram adicionadas a partir de basculegion_male e basculegion_female no PvPoke. Não possuem template no Game Master consultado, portanto a confirmação é apenas do PvPoke. O nome sem qualificador resolve o macho; a fêmea é independente. Todos os números #1 a #1025 agora possuem ao menos uma entrada em names/stats.

## Confirmação direta no celular — 27/09/2026

Foi possível consultar o cache externo do Pokémon GO no aparelho 2412DPC0AG via ADB, sem root e sem modificar o jogo:

`/sdcard/Android/data/com.nianticlabs.pokemongo/files/remote_config_cache/v2_GAME_MASTER`

- Arquivo: 2.940.291 bytes; modificação exibida pelo aparelho: 27/09/2026 às 14:26. Essa é a data do arquivo, não uma versão oficial do Game Master.
- SHA-256: `796201da9b6eaf20c05fd6cada0b1c7fdff50a3b954d54a5080636d2d325026e`.
- Foram extraídos 2.477 templates com ataque, defesa e stamina base. Templates incluem formas e trajes; não são 2.477 espécies distintas.
- Os 2.472 templates presentes também no Game Master publicado conferem exatamente nos três atributos; nenhuma divergência.
- Cinco templates adicionais: Charmander/Charmeleon/Charizard GOGGLES_2026 e Pikachu K_2026_A_01/GLASS_HELMET_2026. Seus atributos são iguais aos das espécies normais.
- Sliggoo/Goodra de Hisui e Basculegion continuam sem templates de atributos neste cache. Gimmighoul não confirma a forma Chest. O Nidoran genérico continua ambíguo.

A extração leu a estrutura binária: registros de templates no campo 2, payload no campo 2, Pokémon no campo 2 e stats no campo 8; campos internos 1/2/3 correspondem a stamina/ataque/defesa. A interpretação foi conferida contra os 2.472 templates compartilhados, incluindo Bulbasaur 118/111/128 e Raichu normal 193/151/155.

O armazenamento privado não permite `run-as` (pacote não depurável), mas isso não impediu a leitura do cache externo. Não foram alterados os atributos do app nesta consulta.

[Extração dos atributos e comparação](../reference-assets/game-master-phone-2026-09-27/stats.json). O resultado confirma o conteúdo do cache naquele momento; não garante que ele inclua toda atualização disponível no servidor.
