# Auditoria IV Master × Stadium Gaming

Consulta: 27/09/2026. Nenhuma combinação do app foi alterada.

## Escopo e método

Foram lidos 565 registros de `master_iv_table.json`; 564 têm correspondência com atributos utilizáveis no bundle público do Stadium. Aegislash existe lá, mas com `stats: {}`, e ficou pendente. Não foi presumida a forma Escudo ou Espada.

Fontes: [Rank Checker](https://www.stadiumgaming.gg/rank-checker), [bundle público do site](https://www.stadiumgaming.gg/js/app.js).
Foram extraídos os atributos e multiplicadores CPM usados pelo próprio site. O cálculo foi reproduzido localmente: `(AtaqueBase + IV) × (DefesaBase + IV) × CPM² × floor((StaminaBase + IV) × CPM)`, respeitando o limite Master do site (9999 PC). Não se trata de consultar um ranking externo de desempenho em batalhas.

Comparamos a melhor combinação **dentro da mesma soma de IVs**: 98%=44, 96%=43, 93%=42 e 91%=41. Isso adapta o ranking de produto de atributos ao formato da tabela do app. O site não publica uma tabela própria chamada “IV Master por percentual”. Empates exatos são aceitos e todas as alternativas constam no CSV. Não usamos porcentagem de produto de atributos como se fosse porcentagem de IV.

A tabela local não especifica nível. Foram comparados 40, 50 e 51, sem piso mínimo de captura. No site, XL e Melhor Companheiro alteram esse limite; numa sessão sem preferências gravadas, o código usa 40. A forte concordância em 40 indica que esse provavelmente foi o critério original da tabela local.

| Nível | Combinações comparadas (98–91) | Diferentes | Registros com diferença |
|---|---:|---:|---:|
| 40 | 2256 | 39 | 24 |
| 50 | 2256 | 1135 | 351 |
| 51 | 2256 | 1076 | 338 |

## Diferenças no nível 40

“Stadium” abaixo significa a melhor combinação com a mesma soma, calculada a partir dos dados e fórmula do site. IVs na ordem Ataque/Defesa/PS. A perda mede produto de atributos, não dano, taxa de vitórias ou relevância competitiva.

| Registro | IV % | App | Stadium nível 40 | Perda de produto |
|---|---:|---|---|---:|
| AMBIPOM | 91 | 13/14/14 | 12/15/14 | 0.1750% |
| ARCEUS | 96 | 15/15/13 | 14/14/15 | 0.2178% |
| ARCEUS | 93 | 14/15/13 | 13/14/15 ou 14/13/15 | 0.2162% |
| ARCEUS | 91 | 13/15/13 | 13/13/15 | 0.2162% |
| CALYREX | 96 | 14/15/14 | 15/15/13 | 0.5650% |
| CALYREX | 93 | 13/15/14 | 15/15/12 | 0.6012% |
| CALYREX | 91 | 12/15/14 | 15/15/11 | 0.6379% |
| CETITAN | 93 | 15/14/13 | 15/15/12 | 0.3738% |
| CETITAN | 91 | 15/13/13 | 15/15/11 | 0.7503% |
| CRUSTLE | 93 | 14/15/13 | 15/14/13 | 0.0276% |
| GALARIANFARFETCHD | 96 | 15/15/13 | 13/15/15 | 0.5739% |
| GALARIANFARFETCHD | 93 | 14/15/13 | 12/15/15 | 0.5682% |
| GALARIANFARFETCHD | 91 | 13/15/13 | 11/15/15 | 0.5625% |
| GLIMMET | 98 | 15/15/14 | 14/15/15 | 0.3614% |
| GLIMMET | 96 | 14/15/14 | 15/15/13 | 0.4950% |
| GLIMMET | 93 | 13/15/14 | 14/15/13 | 0.4975% |
| GLIMMET | 91 | 12/15/14 | 13/15/13 | 0.5000% |
| GOLURK | 96 | 15/15/13 | 14/15/14 | 0.1568% |
| JIRACHI | 91 | 13/15/13 | 14/14/13 | 0.0020% |
| KINGDRA | 93 | 13/15/14 | 14/14/14 | 0.0023% |
| LIEPARD | 96 | 15/15/13 | 14/15/14 | 0.2255% |
| MEW | 91 | 13/15/13 | 14/14/13 | 0.0020% |
| MIENSHAO | 91 | 11/15/15 | 13/15/13 | 0.0239% |
| MUK | 96 | 15/15/13 | 14/15/14 | 0.0251% |
| ORICORIO | 96 | 15/14/14 | 14/15/14 | 0.1518% |
| RAMPARDOS | 96 | 15/15/13 | 14/15/14 | 0.2216% |
| ROTOM | 98 | 15/14/15 | 14/15/15 | 0.0751% |
| ROTOM | 96 | 15/13/15 | 13/15/15 | 0.1509% |
| ROTOM | 93 | 15/12/15 | 12/15/15 | 0.2276% |
| ROTOM | 91 | 15/11/15 | 11/15/15 | 0.3049% |
| SHAYMIN | 91 | 13/15/13 | 14/14/13 | 0.0020% |
| SHAYMINCEU | 91 | 14/14/13 | 13/15/13 | 0.1895% |
| SPECTRIER | 91 | 13/15/13 | 12/15/14 | 0.2292% |
| SPIDOPS | 93 | 14/13/15 | 15/12/15 | 0.0912% |
| VICTINI | 91 | 13/15/13 | 14/14/13 | 0.0020% |
| ZEBSTRIKA | 96 | 15/15/13 | 14/15/14 | 0.2078% |
| ZYGARDE | 98 | 15/15/14 | 15/14/15 | 0.0955% |
| ZYGARDE | 93 | 15/15/12 | 15/14/13 | 0.0981% |
| ZYGARDE | 91 | 15/15/11 | 15/13/13 | 0.1969% |

## Cuidados antes de corrigir

- **Regra de família versus espécie:** o app usa `MasterIvBadgeCatalog` para aplicar regras às famílias/ramificações. O CSV compara cada chave à espécie/forma de mesmo nome; isso não prova que a regra de família esteja errada. Por exemplo, `GALARIANFARFETCHD` precisa ser interpretado considerando Sirfetch’d; `GLIMMET`, considerando Glimmora. Confirmei que as três divergências de Farfetch’d de Galar coincidem com os melhores IVs de Sirfetch’d e as quatro de Glimmet coincidem com os melhores de Glimmora no nível 40. Assim, 7 das 39 diferenças têm explicação pela evolução, restando 32 combinações em 22 registros para revisão; isso ainda não autoriza corrigir todas automaticamente. Decidir o alvo da regra vem antes de trocar IVs.
- **Formas:** aliases explícitos usados: GALARIANFARFETCHD → FARFETCHD_GALARIAN; GIRATINAORIGEM → GIRATINA_ORIGIN; HISUIANDECIDUEYE → DECIDUEYE_HISUIAN; LYCANROCNOTURNO → LYCANROC_MIDNIGHT; HISUIANSAMUROTT → SAMUROTT_HISUIAN; SHAYMINCEU → SHAYMIN_SKY. Chaves genéricas como Rotom e Zygarde foram comparadas ao registro genérico homônimo do site, não a todas as formas.
- **Dados do site também divergem:** Dugtrio tem defesa 134 no registro genérico do Stadium e 136 no app; Gourgeist genérico tem 171/219/146 no Stadium e 175/213/163 no app. No segundo caso, conferir tamanho/forma é indispensável. Não substituir os atributos locais auditados automaticamente pelos do site.
- **67%:** os 522 registros com essa regra usam 10/10/10. Ela não é a melhor combinação irrestrita de soma 30 no Stadium, em nenhum dos três níveis, mas é a única combinação de soma 30 com piso 10/10/10. Portanto, foi separada dos totais de divergências; é uma convenção de coleção/piso de captura, não 522 erros comprovados.
- **Aegislash:** pendente por ausência de atributos no registro consultado do Stadium.

## Arquivos e validação

- `auditoria-iv-master-stadium.csv`: todas as comparações nos três níveis, inclusive equivalentes e a convenção 67%.
- `../tools/audit_stadium_master.py`: reprodução da auditoria; baixa o bundle público caso `.tmp-stadium-app.js` não exista. Para atualizar a consulta, substituir esse cache por uma nova cópia do bundle.
- Validação cruzada: 147 comparações de 10 registros, nos três níveis, executadas também pelo módulo JavaScript original do Stadium (`calculator.calc`), sem divergências em relação ao cálculo da auditoria. Amostra: Ambipom, Arceus, Calyrex, Farfetch’d de Galar, Rotom, Zygarde, Slaking, Lycanroc Noturno, Samurott de Hisui e Absol.

SHA-256 do bundle usado: `23ddb89124fdc8e2e0cbb5a75019009784af3df4c7c39c89d8a16ed7abb31c41`.

## Critérios confirmados para revisão

O usuário confirmou nível 40 como base e 67%=10/10/10 como regra intencional. A planilha `revisao-iv-master-nivel-40.xlsx` reúne as propostas e pendências para validação manual antes de qualquer alteração. Famílias/ramificações sem regra e formas com regra herdada são tratadas separadamente. Nenhum cadastro foi corrigido automaticamente.
