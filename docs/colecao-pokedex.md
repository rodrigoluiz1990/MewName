# Coleção por espécie

Grade em National Dex e modal rolável de formas e categorias. O modal preserva a posição da grade e suporta espécies com muitas variantes por renderização preguiçosa.

Fonte consultada diretamente no telefone: /sdcard/Android/data/com.nianticlabs.pokemongo/files/remote_config_cache/v2_GAME_MASTER. SHA-256: 796201da9b6eaf20c05fd6cada0b1c7fdff50a3b954d54a5080636d2d325026e. 1514 registros de templates Pokémon (incluindo espécies base), sem duplicatas NORMAL/SHADOW/PURIFIED, exportados em collection_game_forms.json. O catálogo anterior auxilia na classificação de trajes; os IDs são confirmados pelo cache do aparelho. As marcações existentes mantêm suas chaves.

Limites: templates não confirmam disponibilidade pública de captura. Trajes legados que não possuem template próprio e diferenças cosméticas de gênero podem exigir um catálogo adicional. Não inventar sprites para registros sem correspondência; nesses casos mostrar imagem indisponível. A revisão automática de nomes sugeridos mantém o catálogo de formas já reconhecidas; novas variantes de catálogo podem ser marcadas manualmente aqui.

Validação: assembleDebug e 17 testes de coleção aprovados. Modal de Venusaur conferido no aparelho, incluindo a captura 98% preexistente preservada e silhuetas das categorias não marcadas. Imagens via Coil e resolução de catálogo em Dispatchers.IO. As antigas categorias Mega X/Y permanecem com a marcação agregada anterior da categoria Mega; o sprite de cada transformação é apresentado quando disponível.

## Sprites GO e Fantasiados

A coleção usa arquivos originais extraídos do jogo e publicados em https://github.com/PokeMiners/pogo_assets. O índice `go_sprite_index.json` fixa a revisão e os caminhos existentes, priorizando 256x256; não usa mais ilustrações PokeAPI. Brilhantes têm seu próprio arquivo `.s.icon.png`; Mega e Gigamax usam formas próprias. XXS e XXL não têm arquivos dedicados no índice consultado: são representados pelo sprite original com escala 48/76 dp em um bloco 80 dp. Sombroso usa o sprite base e uma prévia estática da textura original ShadowFlame4x4, colorida em roxo; não é um render completo dos efeitos 3D/olhos do jogo.

O catálogo de trajes foi complementado por 208 arquivos de traje legado `.c...`, incluindo 34 de Pikachu. Assets não comprovam liberação de captura. Fantasiados mostra um bloco por traje, sem replicar categorias. Marcações anteriores em outras categorias do traje contam como coletado; desmarcar explicitamente o traje remove essas marcações.
