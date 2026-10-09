# Otimizacao da leitura - 2026-10-02

## Alteracoes

- Ranking PvP: busca binaria do maior meio-nivel permitido pelo limite de PC.
  Mantidos CPMs, arredondamento de PC/HP, ordem das multiplicacoes e empates.
  O cache em memoria existente continua em uso.
- OCR: um reconhecedor por instancia de OcrEngine, reutilizado entre leituras e
  recortes. Servico e ViewModel fecham seus reconhecedores ao encerrar.
- Cancelamento: nao fecha o reconhecedor compartilhado nem recicla um recorte
  antes de a tarefa nativa terminar. Falha de recorte conserva o OCR original.
- Sobreposicao: roteamento usa o OCR completo antes das releituras de tamanho.
  Perfil, catalogo, batalha e filtros nao executam verificacoes de XS/XXS/XL/XXL.
  Pokemon e telas desconhecidas mantem as verificacoes anteriores e a resolucao original.

## Medicao

Filtrar logcat por OverlayService. Cada leitura tem identificador `Read[xxxxxxxx]`,
versao, resolucao, destino e tempos em milissegundos usando relogio monotono.
Os intervalos incluem captura (com espera para esconder a bolha), OCR principal,
roteamento, recortes de tamanho (com quantidade), etapas do parser, nomes/sugestoes,
abertura da janela e total, com resultado success/error/cancelled.
Os tempos do parser tambem permanecem no diagnostico exportavel da leitura.
Abertura mede a chamada de criacao da janela, nao o primeiro frame efetivamente desenhado.
Para rotas que encerram antes do parser, Finalizacao inclui o tratamento da rota.

`OCR text` mede apenas a primeira leitura: nao comparar diretamente com o antigo
`OCR full`, que incluia recortes. Somar a etapa de tamanho para uma comparacao equivalente.
O total inclui roteamento e outras etapas que nao existiam na medicao antiga.

## Validacao

PvpRankingOptimizationTest compara com o algoritmo exaustivo anterior todos os
4096 IVs de Magikarp, Shuckle, Chansey, Mewtwo, Slaking, Persian, Arcanine e
Arcanine de Hisui: limites 500/1500/2500/sem limite, niveis 40/41/50/51.
Sao 524288 comparacoes exatas de produto, PC e nivel, preservando tambem os empates
que determinam o ranking. Inclui casos sem nivel elegivel.

OcrEngineLifecycleTest verifica reutilizacao, encerramento idempotente,
cancelamento com outra leitura em andamento, vida util dos bitmaps e falha de recorte.
A suite existente tambem verifica as regras de reconhecimento e rankings.

Nao foi feita instalacao nem afericao de velocidade desta versao nos celulares.
Os tempos coletados anteriormente nao representam o desempenho apos estas alteracoes.
