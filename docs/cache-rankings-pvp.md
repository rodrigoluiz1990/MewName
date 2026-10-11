# Cache de rankings PvP

`PvpRankCalculator` mantém até 36 tabelas por instância, em ordem de último acesso (LRU). Consultar uma tabela existente a torna recente; inserir a 37ª remove a menos recentemente utilizada.

Cada tabela contém no máximo 4.096 combinações de IV (16 × 16 × 16), além da lista ordenada de produtos usada para calcular os rankings e empates. Assim, o cache retém no máximo 147.456 registros de combinações por calculadora. O limite temporário permite manter nove espécies nas quatro ligas; não representa uma medição do heap em bytes. O consumo real depende dos objetos e do runtime Android.

A chave continua distinguindo nome, liga e nível máximo efetivo, incluindo melhor companheiro. As fórmulas, a precisão e os critérios de empate permanecem iguais. Uma tabela removida é recalculada quando necessária.

Leituras e gravações permanecem sincronizadas, pois um acesso altera a ordem LRU. O cálculo continua fora do bloqueio. Caso outra tarefa já tenha publicado a mesma chave, sua tabela é reaproveitada. Cálculos simultâneos podem manter tabelas temporárias além do limite do cache; o limite também é por instância, não global.

`PvpRankCacheTest` verifica descarte LRU, reutilização das entradas recentes, igualdade de todas as combinações e produtos após recálculo, e acessos concorrentes. A suíte existente verifica fórmulas, formas e rankings.

Na validação física, observar memória e tempo de resposta em navegação prolongada entre famílias e ligas para ajustar o limite se necessário.
