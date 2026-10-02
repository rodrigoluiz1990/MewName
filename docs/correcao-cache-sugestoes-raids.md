# Recupera??o de sugest?es de raids

Caso verificado: Buzzwole no aparelho 2412DPC0AG. Havia dois relat?rios ?ntegros, com 12 sugest?es cada, em RAID_LEVEL_4/40 e RAID_LEVEL_ULTRA_BEAST/40. O cat?logo atual identifica Buzzwole como RAID_LEVEL_ULTRA_BEAST. A contagem de ?cones detectada como 4 levava a uma chave diferente da tela principal.

N?o foi encontrada expira??o por tempo no reposit?rio. Os arquivos permanecem em files/raid-counters-v1. As buscas s?o separadas por chefe, categoria e n?vel dos atacantes (40/50).

Corre??es:
- Para chefes reconhecidos, priorizar categorias conhecidas do cat?logo, inclusive identificadores especiais como ULTRA_BEAST, em vez de inventar uma categoria pela contagem de ?cones.
- Quando o chefe n?o est? no cat?logo atual, consultar tamb?m o hist?rico e os relat?rios persistidos. A identifica??o inicial n?o precisa de rede.
- Preservar as categorias/formas distintas; n?o retornar um relat?rio de outra categoria como se fosse o solicitado.
- Persistir a prefer?ncia 40/50. Ao abrir, se apenas o outro n?vel possui relat?rio, selecionar esse n?vel explicitamente na tela.
- Carregar os relat?rios antes dos dados auxiliares de fraqueza, e preservar cache em falhas de atualiza??o.
- Serializar leitura dos relat?rios com grava??es AtomicFile para evitar disputa entre telas.

Valida??es cobrem Buzzwole, contagem incorreta de ?cones, chefes fora do cat?logo, cat?logo ausente/inv?lido, recria??o do reposit?rio e separa??o de n?veis/categorias.

Valida??o conclu?da: build aprovado; 22 testes unit?rios passaram. No aparelho, RaidCacheInstrumentation recuperou BUZZWOLE / RAID_LEVEL_ULTRA_BEAST / n?vel 40 / 12 sugest?es diretamente do disco. A consulta tamb?m foi repetida ap?s encerrar o processo do app, sem rede no caminho testado. Atualiza??o instalada com preserva??o dos dados.
