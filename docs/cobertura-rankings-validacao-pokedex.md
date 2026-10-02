# Cobertura de rankings e validação da Pokédex — 30/09/2026

## O que já existe

- 1142 registros de espécies/formas em names.json; todos possuem atributos resolvíveis em stats.json.
- 1159 chaves em stats.json, incluindo aliases; 1025 números distintos da National Dex (1–1025).
- O ranking de IV é calculado localmente pelo PvpRankCalculator, a partir dos atributos, CPM, IVs e configurações de liga/nível. Não é necessário baixar uma tabela de 4.096 IVs para cada espécie.
- A ficha nova mostra posições competitivas de GL/UL/ML do PvPoke e regras IV Master da família. Ela ainda não oferece um explorador completo das 4.096 combinações nem ranking competitivo de Copinha.

## Cobertura online atual

- 1106 registros têm uma correspondência única com o Game Master do PvPoke pelo algoritmo atual.
- Com posição publicada e correspondência encontrada: GL 746, UL 562, ML 273. Essas contagens não representam falhas do cálculo de IV: o ranking competitivo depende da seleção e simulação da fonte.
- 32 registros sem correspondência e 4 ambíguos precisam ser revisados. Nem todo caso é um Pokémon ausente: muitos são diferenças entre nomes genéricos e formas explícitas.

### Sem correspondência pelo algoritmo atual

- Tauros (Paldea)
- Burmy
- Wormadam
- Cherrim
- Giratina
- Shaymin
- Darmanitan
- Tornadus
- Thundurus
- Landorus
- Keldeo
- Aegislash
- Sliggoo (Hisui)
- Goodra (Hisui)
- Pumpkaboo
- Gourgeist
- Oricorio
- Lycanroc
- Minior
- Indeedee
- Morpeko
- Urshifu
- Dudunsparce Three-Segment Form
- Dudunsparce Two-Segment Form
- Gimmighoul Chest
- Gimmighoul Roaming
- Poltchageist Artisan Form
- Poltchageist Counterfeit Form
- Sinistcha Masterpiece Form
- Sinistcha Unremarkable Form
- Enamorus
- Calyrex (Shadow Rider)

### Correspondência ambígua

- Lanturn: lanturn, lanturnw
- Golisopod: golisopod, golisopodsh
- Palafin: palafin, palafin_zero
- Tatsugiri: tatsugiri_curly, tatsugiri_droopy, tatsugiri_stretchy

## O que falta para cobertura completa

1. Definir uma identidade estável por National Dex e forma, com mapeamento explícito de IDs de cada fonte. Hoje a associação online depende de nomes/aliases normalizados. Lanturn e Golisopod também encontram variantes alternativas de simulação do PvPoke; não devem desaparecer por isso.
2. Auditar todas as formas selecionáveis, inclusive as de atributos iguais, Mega/Primal e formas sombrosas. Ter todas as espécies base não demonstra cobertura de todas as formas.
3. Confirmar as pendências de atributos documentadas: Sliggoo/Goodra de Hisui, Gimmighoul Chest e Basculegion. A existência de um valor no arquivo não equivale a confirmação atual pelo Game Master do jogo.
4. Separar claramente ranking de IV, posição competitiva, elegibilidade da copa e disponibilidade no jogo. Um Pokémon pode ter IV calculável e não possuir posição no PvPoke.
5. Se o objetivo for consultar qualquer combinação na Pokédex, adicionar controles de IV/nível/piso e exibição dos resultados locais para CP/GL/UL/ML. O motor já existe, mas a ficha atual não expõe esse explorador.
6. Para posições competitivas que a fonte não publica, usar simulação própria ou outra fonte identificada. Não substituir a forma por outra nem inventar posição.

## Validação no aparelho

- Aparelho: 2412DPC0AG, resolução 1220 × 2712.
- APK debug compilado e instalado com adb install -r, preservando os dados do app.
- Build concluído; 4 testes PokedexDetailsTest passaram, sem falhas. Incluem separação dos atributos/PC de Arcanine de Hisui e forma normal, correspondência exata e cache.
- Conferida a ficha do Bulbasaur: imagens normal/brilhante, atributos 118/111/128, PC por nível, golpes com dados PvE/PvP, posição GL, ausência explícita de posição UL/ML, IV Master e aba Fontes.
- Cache no aparelho contém gamemaster.json e rankings-1500/2500/10000.json. A captura de PvP mostra consulta às 23:26.
- Sem erros AndroidRuntime/PokedexDetails nos logs consultados; isso não substitui teste exaustivo de todas as espécies ou falhas de rede.
- Capturas/XML em reference-assets/pokedex-validation-2026-09-30. Os nomes sequenciais 02/03/04 refletem o comando planejado; houve mudanças de tela entre comandos, portanto o conteúdo dos arquivos é a evidência, não seu nome.

Fontes online usadas nesta medição: https://raw.githubusercontent.com/pvpoke/pvpoke/master/src/data/gamemaster.json e rankings/all/overall/rankings-{1500,2500,10000}.json no mesmo repositório. Snapshots preservados na pasta da validação. Nenhum atributo ou regra IV Master foi alterado.

### Validação adicional com o aparelho livre

- Busca por Arcanine retornou duas entradas. A ficha abriu ao tocar no Pokémon.
- Seletor alternou Arcanine normal e Hisui corretamente.
- Normal: 227/166/207, Fogo, PC máximo nível 50 = 3425.
- Hisui: 232/165/216, Fogo/Pedra, PC máximo nível 50 = 3556; sprites normal e brilhante carregados.
- PvP de Hisui carregou GL #1035 e UL #811 nesta captura, correspondentes à forma específica. Esses são rankings competitivos, não posições de IV.
- Fechar a ficha preservou a busca Arcanine na lista.
- Evidências adicionais: arquivos 07 a 11 na pasta da validação.
