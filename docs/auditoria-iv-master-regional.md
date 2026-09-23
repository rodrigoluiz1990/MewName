# Auditoria de IV Master por forma regional

A consulta recebia a família do doce, misturando formas comuns e regionais. Aceitava a combinação de qualquer membro, mesmo quando o nome selecionado indicava outra forma. Além disso, os nomes antigos GALARIANFARFETCHD e HISUIANDECIDUEYE não correspondiam aos nomes atuais entre parênteses.

A correção usa o nome selecionado no OCR, no cálculo de revisão e na lista de IVs esperados. Aliases regionais antigos e atuais são equivalentes. Uma regra explícita da espécie tem prioridade. Na ausência dela, somente evoluções do mesmo ramo regional podem fornecer a regra. Formas sem regra compatível ficam sem comparação, sem herdar os IVs da forma comum.

O catálogo master_iv_table.json não foi alterado nesta correção. Ausência abaixo significa ausência de regra regional identificável no catálogo atual; não significa que a forma não tenha IVs ideais em outras fontes.

## Farfetch'd

| Forma | 98% | 96% | 93% | 91% |
|---|---|---|---|---|
| Farfetch'd comum | 14/15/15 | 13/15/15 | 12/15/15 | 15/15/11 |
| Farfetch'd (Galar) / Sirfetch'd | 14/15/15 | 15/15/13 | 14/15/13 | 13/15/13 |

Também foi corrigido o alias de Decidueye (Hisui) e a separação de evoluções regionais: Perrserker, Sirfetch'd, Cursola, Obstagoon, Runerigus, Mr. Rime, Sneasler, Overqwil e Clodsire. Overqwil não tem regra cadastrada atualmente.

Leituras consecutivas com nomes de ramos regionais diferentes não são mais mescladas apenas por terem doce, CP ou IVs iguais.

## Cobertura

55 formas regionais verificadas; 10 com regra aplicável e 45 sem regra regional.

### Regras disponíveis

| Forma | IVs cadastrados |
|---|---|
| Corsola (Galar) | 98%: 14/15/15; 96%: 13/15/15; 93%: 12/15/15; 91%: 11/15/15 |
| Decidueye (Hisui) | 98%: 14/15/15; 96%: 13/15/15; 93%: 12/15/15; 91%: 11/15/15 |
| Farfetch'd (Galar) | 98%: 14/15/15; 96%: 15/15/13; 93%: 14/15/13; 91%: 13/15/13 |
| Linoone (Galar) | 98%: 15/14/15; 96%: 15/13/15; 93%: 15/15/12; 91%: 15/14/12 |
| Meowth (Galar) | 98%: 14/15/15; 96%: 15/15/13; 93%: 14/15/13; 91%: 13/15/13 |
| Mr. Mime (Galar) | 98%: 14/15/15; 96%: 13/15/15; 93%: 12/15/15; 91%: 11/15/15 |
| Sneasel (Hisui) | 98%: 14/15/15; 96%: 13/15/15; 93%: 12/15/15; 91%: 11/15/15 |
| Wooper (Paldea) | 98%: 15/15/14; 96%: 15/15/13; 93%: 15/15/12; 91%: 15/15/11 |
| Yamask (Galar) | 98%: 15/14/15; 96%: 15/13/15; 93%: 15/12/15; 91%: 15/11/15 |
| Zigzagoon (Galar) | 98%: 15/14/15; 96%: 15/13/15; 93%: 15/15/12; 91%: 15/14/12 |

### Pendentes de regra regional

- Arcanine (Hisui)
- Articuno (Galar)
- Avalugg (Hisui)
- Braviary (Hisui)
- Darmanitan (Galar)
- Darumaka (Galar)
- Diglett (Alola)
- Dugtrio (Alola)
- Electrode (Hisui)
- Exeggutor (Alola)
- Geodude (Alola)
- Golem (Alola)
- Goodra (Hisui)
- Graveler (Alola)
- Grimer (Alola)
- Growlithe (Hisui)
- Lilligant (Hisui)
- Marowak (Alola)
- Meowth (Alola)
- Moltres (Galar)
- Muk (Alola)
- Ninetales (Alola)
- Persian (Alola)
- Ponyta (Galar)
- Qwilfish (Hisui)
- Raichu (Alola)
- Rapidash (Galar)
- Raticate (Alola)
- Rattata (Alola)
- Samurott (Hisui)
- Sandshrew (Alola)
- Sandslash (Alola)
- Sliggoo (Hisui)
- Slowbro (Galar)
- Slowking (Galar)
- Slowpoke (Galar)
- Stunfisk (Galar)
- Tauros (Paldea)
- Typhlosion (Hisui)
- Voltorb (Hisui)
- Vulpix (Alola)
- Weezing (Galar)
- Zapdos (Galar)
- Zoroark (Hisui)
- Zorua (Hisui)

## Validação

Suíte completa: 197 testes, 0 falhas/erros. APK debug gerado. Inclui testes de troca de espécie na revisão, aliases, isolamento regional, evoluções exclusivas, ausência de regras e leituras consecutivas.
