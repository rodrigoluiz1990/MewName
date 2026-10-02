# Categorias de fundos especiais

165 imagens organizadas por categoria. Os nomes dos arquivos existentes permanecem iguais, preservando as marcações de coleção (`background|arquivo`).

Abas: Todos, Localidades, Aniversário, Dia da Comunidade, GO Fest, GO Tour, Mega Evolução, Wild Area, Temporadas, Líderes de equipe e Eventos especiais. As categorias também estão disponíveis na tag Fundo especial em Definir nome e na tela de nome sugerido.

## Fundos adicionados e prévias

- Aniversário: 10º aniversário do Mewtwo, com globo terrestre e logotipo originais.
- Dia da Comunidade 2026: a prévia inclui a Poké Bola grande sobre a paisagem existente.
- Mega Evolução: `sb_GoTour2026_mega.png` foi movido de GO Tour para a pasta `mega_evolucao`, mantendo a chave da coleção. A prévia inclui o símbolo Mega.

As prévias são composições estáticas de texturas originais, renderizadas em segundo plano e mantidas em cache. Não reproduzem a animação do jogo e não são utilizadas como referências pelo reconhecimento. As camadas ficam em `app/src/main/assets/background_layers`.

Procedência: catálogo Addressables 926 do aparelho conectado. Efeito `bgfx_10thAnniGlobe`, bundle `6a862092731874c75f1283cefdb33524`; efeito `bgfx_Mega`, bundle `0d8607e5f34b5d48fd8f113bc89ec69a`. Extrações e manifesto em `reference-assets/background-effects-2026`. Comunidade: `bgfx_CommunityDay_2026`, bundle `50aa8253d1f7b2eaa5ee393bd6db892d`, documentado em `reference-assets/community-background-2026`.

## Reconhecimento

`SpecialBackgroundCatalog` centraliza a classificação. O prefixo `lc_` tem precedência sobre nomes de eventos. As demais imagens usam a pasta e, como alternativa, o nome do arquivo.

O comparador exige distância <= 1,8 e vantagem de 0,8 sobre a referência normal e sobre outra categoria. São limites iniciais, ainda sem calibração abrangente de todas as categorias. Uma referência especial forte pode coexistir com a indicação de sortudo.

Quatro fases do Vulpix foram capturadas. A fase 0 mostra o fundo normal; as demais mostram a Poké Bola. Fases 1 e 3 são referências, enquanto a fase 2 é usada apenas no teste: resultado COMMUNITY_DAY; fase 0 retorna categoria nula. A leitura ainda analisa um quadro e não garante identificar um fundo especial quando a captura mostra somente a fase normal. Mewtwo e Mega precisam de calibração com capturas reais para validar reconhecimento automático.

## Validação

Compilação debug aprovada; 14 testes passaram (7 de categorias/fundos e 7 de regressão de leitura). No telefone, as três prévias foram renderizadas pelo Android e a reutilização do cache foi confirmada. As imagens resultantes foram inspecionadas.
