# Revisão IV Master — respostas aplicadas e propostas pendentes

Data: 04/10/2026. Base: nível 40; convenção 67% = 10/10/10 preservada.

## Resultado aplicado

- **29 combinações corrigidas em 21 registros existentes** (D-001 a D-029).
- **16 regras regionais adicionadas**, F-001 a F-016: Raticate, Raichu, Sandslash, Ninetales, Dugtrio, Persian, Golem, Muk, Exeggutor e Marowak de Alola; Arcanine e Electrode de Hisui; Rapidash, Slowking e Weezing de Galar; Tauros de Paldea.
- Cada nova regra recebeu os quatro percentuais aprovados e a convenção 67%. Foram 109 alterações de campos: 29 correções + 64 combinações novas + 16 convenções. A tabela passou de 565 para **581 registros**.
- Preferências explícitas de empate respeitadas: Arceus 93% = 13/14/15; Electrode de Hisui 98% = 15/14/15 e 93% = 14/13/15.
- A célula de Spidops continha o número Excel 42353, correspondente a 15/12/2015. Recuperado **15/12/15** porque coincide exatamente com a proposta marcada como aprovada. As novas colunas de IV usam formato de texto.
- Nenhuma alteração nas combinações pendentes, nos atributos base, nas famílias ou no resolvedor de formas. Mantidas GALARIANFARFETCHD e GLIMMET: as combinações já correspondem às evoluções Sirfetch’d e Glimmora, conforme solicitado nas observações.

A [planilha original](revisao-iv-master-nivel-40.xlsx) foi preservada. O [registro de aplicação](iv-master-aprovacoes-aplicadas-2026-10-04.json) contém ID da resposta, valor anterior, valor aplicado e hash da planilha lida.

## Arquivos para continuar a revisão

- [Nova planilha](revisao-iv-master-resultado-2026-10-04.xlsx): resumo, 109 alterações aplicadas, 34 linhas pendentes, 67 análises de formas/observações, comparação das fontes e cópia das 148 respostas originais.
- [CSV completo](iv-master-propostas-2026-10-04.csv): 117 linhas de comparação, com atributos, template do jogo, links dos sites e propostas por fonte.

As 34 linhas pendentes são F-017–F-046 e N-001–N-004. Sinistcha Comum aparece duas vezes na planilha de origem por causa das ramificações; portanto não são 34 espécies distintas. As 56 linhas de formas herdadas têm 35 combinações compatíveis e 21 que merecem revisão; há repetições de formas/ramificações, e compatibilidade matemática não garante resolução correta da forma no app.

## Fontes atuais e método

1. **Jogo no celular:** cache externo `remote_config_cache/v2_GAME_MASTER` do aparelho 2412DPC0AG, consultado via ADB somente leitura em 04/10. Foram extraídos **2.480 templates**. Os 2.477 compartilhados com a coleta de 27/09 mantêm os mesmos atributos. Novos: Sliggoo de Hisui, Goodra de Hisui e Machamp WILDAREA_2026_01. O cache observado não comprova que todas as atualizações do servidor já foram recebidas.
2. **[Stadium Gaming](https://www.stadiumgaming.gg/rank-checker):** [bundle público](https://www.stadiumgaming.gg/js/app.js) baixado novamente; mesmo hash da auditoria anterior, 1.500 registros. Dados e fórmula usados para reproduzir a comparação.
3. **[Dittobase](https://www.dittobase.com/pokemon-go/pvp-ivs):** [API do calculador](https://www.dittobase.com/api/pokemon-for-calc?v=3) baixada novamente, **1.838 registros**, oito sombras novas em relação à auditoria de 30/09 e nenhum atributo alterado nos registros compartilhados. Fórmula JavaScript novamente consultada: produto arredondado para inteiro; desempate da interface por ataque, PS, PC e IV de stamina.
4. **[Poke Genie](https://pokegenie.net/):** o site e a [descrição oficial do aplicativo](https://play.google.com/store/apps/details?id=com.cjin.pokegenie.standard) foram consultados. Não foi encontrado dataset/API público completo para confirmar numericamente estas propostas. A documentação pública descreve a calculadora PvP, mas não publica uma tabela IV Master por percentual. **Não apresentamos os cálculos locais como resultados verificados no Poke Genie.**

Os snapshots e hashes estão em [reference-assets/iv-master-review-2026-10-04](../reference-assets/iv-master-review-2026-10-04). A última alteração pública do arquivo Game Master consultada na API do PokeMiners continua sendo o commit `8e227be44f288d34463e23bf04e9b564d3c16f79`, de 29/08/2026; por isso a referência primária aqui é o cache do celular de hoje.

Para cada alvo, maximizar `(AtaqueBase + IVa) × (DefesaBase + IVd) × CPM² × floor((StaminaBase + IVs) × CPM)` no nível máximo 40, mantendo soma 44, 43, 42 ou 41. CPM de nível 40: 0,79030001. Limites internos reproduzidos: 9.999 PC no Stadium e 10.000 no Dittobase. Preservados todos os empates de maior produto; a interface pode exibir uma ordem diferente entre empatados. As colunas dos sites são resultados reproduzidos com seus atributos e fórmulas, não consultas manuais de cada posição da interface.

A comparação tem 115/117 linhas com template do celular, 93 com atributos Stadium e 114 com atributos Dittobase. As duas linhas sem template no celular são Basculegion macho/fêmea. Não usar relevância no meta ou disponibilidade em PvP como critério para apagar essas propostas.

## Respostas às observações

### Zygarde

**A regra atual está correta para a forma Completa/100% e foi mantida.**

| Forma | Template correto | Ataque/Defesa/Stamina | 98% | 96% | 93% | 91% |
|---|---|---|---|---|---|---|
| 10% | ZYGARDE_TEN_PERCENT | 205/173/144 | 14/15/15 | 15/15/13 | 14/15/13 | 13/15/13 |
| 50% | ZYGARDE_FIFTY_PERCENT | 203/232/239 | 15/14/15 | 15/15/13 | 15/14/13 | 15/13/13 |
| Completa/100% | ZYGARDE_COMPLETE | 184/207/389 | 15/15/14 | 15/15/13 | 15/15/12 | 15/15/11 |

`ZYGARDE_COMPLETE_FIFTY_PERCENT` e `ZYGARDE_COMPLETE_TEN_PERCENT` têm os atributos das formas 50% e 10%, respectivamente. A palavra COMPLETE nesses identificadores não os torna a forma de 100%. Se a intenção continua sendo avaliar todos os Zygarde pela transformação final, a regra familiar existente atende a isso; regras individuais por forma seriam uma decisão adicional.

### Kyurem, Minior e Slither Wing

- **Kyurem Preto e Branco:** ambos 310/183/245; sugestões 14/15/15, 13/15/15, 12/15/15 e 11/15/15. Diferem da regra de Kyurem normal em 93% e 91%. Propor regra específica e harmonizar Black/Preto e White/Branco na resolução.
- **Minior Core:** 218/131/155; sugestões 14/15/15, 13/15/15, 12/15/15 e 11/15/15. O MINIOR genérico do Stadium é a forma Meteor, 116/194/155. As sete cores Core têm os mesmos atributos; usado RED como referência explícita.
- **Slither Wing:** é espécie básica. A dúvida da planilha era uma ausência de associação/regra no cadastro, não uma evolução faltante. Jogo, Stadium e Dittobase coincidem em 261/193/198; propostas 14/15/15, 13/15/15, 12/15/15 e 11/15/15.

### Aegislash

O Stadium continua sem atributos no registro AEGISLASH. O Dittobase diferencia as formas e coincide com o jogo:

| Forma | Atributos | 98% | 96% | 93% | 91% |
|---|---|---|---|---|---|
| Escudo | 97/272/155 | 15/14/15 | 15/13/15 | 15/12/15 | 15/11/15 |
| Espada | 272/97/155 | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |

A tabela IV Master genérica atual coincide com **Espada**, enquanto `stats.json` usa **Escudo** para AEGISLASH genérico. Proposta: separar as duas regras e a seleção da forma. Permanece pendente, pois a resposta solicitou análise, não aprovou uma troca.

### Dugtrio e Gourgeist

- **Dugtrio:** o celular contém o template antigo genérico com defesa 134 e `DUGTRIO_NORMAL` com defesa 136. O app usa a forma NORMAL, 167/136/111. O Stadium genérico ainda usa 134. Os melhores IVs por percentual coincidem neste nível, apesar da diferença de atributo; não alterar o app para 134.
- **Gourgeist:** o genérico dos sites usa Small, 171/219/146; o app usa Average, 175/213/163. Os quatro tamanhos estão confirmados no cache. A regra Master genérica atual coincide com Small; para Average, sugerir 15/14/15, 15/15/13, 15/14/13 e 15/13/13. Large e Super têm propostas próprias na planilha.

### Goodra de Hisui e Basculegion

- **Goodra de Hisui:** cache atual e app: **211/255/190**. Dittobase: **220/242/207**, atributos da forma normal. As propostas pelo jogo são **15/14/15, 15/13/15, 15/12/15 e 15/11/15**; os dois últimos percentuais diferem dos calculados com o Dittobase. Stadium não oferece atributos para este alvo.
- **Basculegion:** macho 217/144/260 e fêmea 199/144/260 nos stats locais/Dittobase, mas sem confirmação no cache atual. Propostas marcadas provisórias. A regra herdada de Basculin merece revisão.

### Outras formas que merecem atenção

As formas de Rotom com aparelhos e Calyrex montado precisam de análise separada após as correções aprovadas para as formas genéricas. Também há divergências nas formas de Mewtwo Armadura, Wormadam Trash, Wishiwashi School, Eiscue Noice, Indeedee fêmea e Eternatus Eternamax. Todos os percentuais estão na aba Formas e observações.

Deoxys Defesa **já tem** uma regra `DEOXYSDEFENSE` correta. Entretanto, o resolvedor atual considera múltiplas regras da família e não isola sempre a forma selecionada. Não é necessário duplicar os IVs; a melhoria futura é resolver a forma corretamente. Para outras formas herdadas, apenas inserir uma nova linha no JSON pode não bastar: revisar a prioridade da forma selecionada e preservar a avaliação pelas evoluções para espécies básicas.

Slowbro de Galar foi calculado separadamente da aprovação F-010 (alvo Slowking): neste nível, as quatro combinações coincidem. A regra aprovada pode atender a essa ramificação sem inventar uma segunda aprovação.

## Propostas para as linhas ainda sem aprovação

Ordem dos IVs: Ataque/Defesa/PS. As propostas abaixo ainda não foram gravadas no aplicativo.

| ID | Alvo | 98% | 96% | 93% | 91% |
|---|---|---|---|---|---|
| F-017 | Articuno (Galar) | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| F-018 | Zapdos (Galar) | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| F-019 | Moltres (Galar) | 15/14/15 | 15/13/15 | 15/15/12 | 15/14/12 |
| F-020 | Typhlosion (Hisui) | 14/15/15 | 15/15/13 | 14/15/13 | 13/15/13 |
| F-021 | Overqwil | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| F-022 | Lilligant (Hisui) | 14/15/15 | 15/15/13 | 14/15/13 | 13/15/13 |
| F-023 | Darmanitan (Galar) | 15/15/14 | 14/15/14 | 13/15/14 | 12/15/14 |
| F-024 | Zoroark (Hisui) | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| F-025 | Stunfisk (Galar) | 15/15/14 | 15/15/13 | 15/15/12 | 15/15/11 |
| F-026 | Braviary (Hisui) | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| F-027 | Goodra (Hisui) | 15/14/15 | 15/13/15 | 15/12/15 | 15/11/15 |
| F-028 | Avalugg (Hisui) | 15/14/15 | 15/13/15 | 15/15/12 | 15/14/12 |
| F-029 | Walking Wake | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| F-030 | Iron Leaves | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| F-031 | Sinistcha (Comum) | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| F-032 | Sinistcha (Obra-Prima) | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| F-033 | Sinistcha (Comum) | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| F-034 | Okidogi | 15/15/14 | 14/15/14 | 13/15/14 | 12/15/14 |
| F-035 | Munkidori | 15/15/14 | 14/15/14 | 13/15/14 | 12/15/14 |
| F-036 | Fezandipiti | 15/15/14 | 15/14/14 | 15/13/14 | 15/12/14 |
| F-037 | Ogerpon | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| F-038 | Gouging Fire | 15/14/15 | 15/13/15 | 15/12/15 | 15/15/11 |
| F-039 | Raging Bolt | 14/15/15 | 13/15/15 | 15/15/12 | 14/15/12 |
| F-040 | Iron Boulder | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| F-041 | Iron Crown | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| F-042 | Terapagos | 15/14/15 | 15/13/15 | 15/15/12 | 15/14/12 |
| F-043 | Pecharunt | 15/15/14 | 15/14/14 | 15/13/14 | 15/12/14 |
| F-044 | Gourgeist (Small) | 15/14/15 | 15/13/15 | 15/12/15 | 15/11/15 |
| F-045 | Gourgeist (Large) | 15/15/14 | 15/14/14 | 15/13/14 | 15/12/14 |
| F-046 | Gourgeist (Super) | 15/14/15 | 15/13/15 | 15/12/15 | 15/15/11 |
| N-001 | Kyurem Black | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| N-002 | Kyurem White | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| N-003 | Minior (Core) | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| N-004 | Slither Wing | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |

## Validação

- 18 testes de IV Master/regiões/Lycanroc/Palafin passaram, incluindo as 16 novas ramificações e isolamento entre Raticate normal e de Alola.
- 93 combinações aprovadas de 98–91% conferidas com atributos do celular: nenhuma divergência.
- Enumeração independente de todas as 4.096 combinações em cada uma das 117 linhas: **479.232 combinações**, com conjuntos de melhores IVs iguais aos das propostas.
- Nova execução da importação resultou em zero mudanças: operação idempotente.
- Hash da planilha original conferido após gerar a nova planilha; respostas preservadas.
- `git diff --check` passou. APK não foi instalado nem gerada nova release nesta tarefa.

Scripts: `tools/apply_master_iv_review.py` (simula; só grava com `--apply`), `tools/review_master_iv_proposals.py` (CSV/análise a partir dos snapshots), `tools/package_master_iv_review.py` (nova planilha). Dependência Python: openpyxl. Não executar o antigo gerador `build_master_review_workbook.py` sobre a planilha respondida, pois ele a recria.
