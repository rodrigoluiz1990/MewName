# Validação: Growlithe de Hisui e IV Master

Consulta: 27/09/2026. Nenhuma configuração do app foi alterada.

Os atributos locais coincidem com os usados pelo Stadium Gaming: Growlithe de Hisui 142/92/155 e Arcanine de Hisui 232/165/216 (Ataque/Defesa/Stamina).

A família está cadastrada com as formas normais e de Hisui. Porém, `master_iv_table.json` só tem `ARCANINE`, sem `HISUIANARCANINE`/`ARCANINEHISUI` nem entrada para Growlithe de Hisui. `MasterIvBadgeCatalog.entriesFor` filtra a ramificação Hisui; portanto, para essa seleção não encontra regra e retorna `familia_sem_json`. Os stats separados de PvP não preenchem automaticamente a tabela IV Master.

A auditoria geral comparou somente as chaves existentes na tabela; não identificou todas as famílias regionais sem cadastro.

## Combinações calculadas

Resultados obtidos executando `calculator.calc` do [bundle público do Stadium](https://www.stadiumgaming.gg/js/app.js), Liga Master, mesma soma de IVs, sem piso de captura. Ordem Ataque/Defesa/PS.

| Espécie | Nível | 98% | 96% | 93% | 91% |
|---|---:|---|---|---|---|
| GROWLITHE_HISUIAN | 40 | 14/15/15 | 13/15/15 | 12/15/15 | 15/15/11 |
| GROWLITHE_HISUIAN | 50 | 15/15/14 | 14/15/14 | 13/15/14 | 12/15/14 |
| GROWLITHE_HISUIAN | 51 | 14/15/15 | 15/15/13 | 14/15/13 | 13/15/13 |
| ARCANINE_HISUIAN | 40 | 14/15/15 | 13/15/15 | 15/15/12 | 14/15/12 |
| ARCANINE_HISUIAN | 50 | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |
| ARCANINE_HISUIAN | 51 | 14/15/15 | 13/15/15 | 12/15/15 | 11/15/15 |

Para a regra de família visando a última evolução, usar a linha de **Arcanine de Hisui**, não a de Growlithe. Em nível 40, coincide com a regra atual de Arcanine normal, mas precisa de uma entrada regional explícita para que o código a encontre. Em 50/51, 93% e 91% diferem da regra normal atualmente salva. A convenção 67%=10/10/10 permanece separada desse cálculo.

Não foi escolhido um nível nem incluída uma regra nova nesta validação.
