# Nota estimada de combinação de golpes PvE

A tela de nomes sugeridos exibe PvE (est.) ao lado do ataque carregado. A nota é do par rápido + carregado selecionado para a espécie/forma atual. Tocar no campo mostra o significado da estimativa e a escala. Sem seleção, tipos ou estatísticas completos aparece —. O cálculo roda em Dispatchers.IO, sem rede, com os catálogos locais.

Para cada par, n = custo de energia do carregado / energia gerada pelo rápido. Pontuação = (n × potência rápida × STAB rápido + potência carregada × STAB carregado) / (n × duração rápida + duração carregada), com durações em segundos e STAB 1,2. O ciclo contínuo aproxima o uso de energia sem desperdício. A nota percentual é a pontuação dividida pela maior pontuação entre os pares disponíveis (incluindo legados) da mesma forma, multiplicada por 100 e arredondada. A ≥90%; B ≥80%; C ≥70%; D ≥60%; E <60%. Essa escala é uma convenção do MewName.

A pontuação não é DPS absoluto nem ranking competitivo PvP. Não considera adversário, efetividade, clima, vida/defesa, dano recebido gerando energia, desperdício no limite de energia, arredondamento de dano, esquivas, desmaios ou tempo de entrada. Não deve ser apresentada como reprodução exata da classificação de outro aplicativo. Se algum golpe da lista estiver sem dados, a comparação não é publicada para evitar atribuir 100% a um conjunto incompleto.

## Pesquisa

- Poke Genie declara na própria página do Google Play que seu ranking de movesets utiliza muitas simulações de batalhas para calcular DPS. A fórmula específica, os adversários de referência e os limites das letras não estão publicados ali: https://play.google.com/store/apps/details?id=com.cjin.pokegenie.standard&hl=en .
- GO Hub explica potência, duração, energia, DPS e Cycle DPS: https://pokemongohub.net/post/wiki/comprehensive-guide-moves-pokemon-go/ . Seu comparador e simulador consideram condições adicionais, incluindo adversário, clima e energia restante: https://pokemongohub.net/post/news/go-hub-guide-to-our-database/ .

A implementação atual é uma estimativa transparente de ciclo neutro, inspirada nesses conceitos; replicar um simulador completo requer outro modelo de combate e uma definição explícita dos cenários.