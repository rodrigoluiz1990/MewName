# Leitura por PC/HP e análise do arco de nível

## Implementação

A resolução considera a espécie/forma identificada, PC exato, HP máximo (o segundo número de atual/máximo), IVs confirmados e nível explícito quando disponíveis. Enumera níveis de 1 a 51 em passos de 0,5 usando a mesma tabela de multiplicadores dos cálculos PVP. Exige HP ou nível adicional ao PC. Só preenche IVs ausentes quando todas as combinações compatíveis possuem os mesmos três IVs; nível só é confirmado por esse mecanismo quando todas concordam. Não escolhe a combinação mais próxima, não sobrescreve barras já aceitas e não presume piso 10 de raid/ovo. A precisão depende de espécie, forma, PC e HP terem sido lidos corretamente.

Sem avaliação aberta, o título próximo/acima do HP é prioridade. Doces identificam a família e restringem correções pequenas de OCR, mas não escolhem automaticamente uma evolução quando o apelido não identifica a espécie. A área de doces foi ampliada e os formatos português/inglês/espanhol são aceitos. Os diagnósticos registram ambiguidade ou ausência de combinação exata.

## Arco: análise, ainda sem conversão automática

O nome curva_cp anterior no código significa curva matemática de PC, não leitura de pixels do arco.

É viável detectar o semicírculo e o marcador, normalizar a geometria pela tela e usar a posição como intervalo de nível. Um leitor público de screenshots demonstra essa abordagem, exigindo nível do treinador e marcador visível: https://github.com/nneonneo/pogo-iv-reader . A implementação desse projeto é antiga e específica de iPhone; não serve como calibração direta do layout atual do GO.

Para ativar: coletar capturas com nível conhecido (incluindo meios níveis, 40+, limite máximo e melhor companheiro) nos dois aparelhos; medir a relação posição/multiplicador e o limite do arco para o treinador; mascarar Pokémon/fundo/partículas; rejeitar arco incompleto ou coberto; usar uma faixa de erro angular, cruzada com PC/HP. Não interpolar linearmente nível de 1 a 50 por ângulo sem validar a escala do jogo. Fundo claro e animações podem ocultar marcador/linha.

A conversão visual não foi ativada nesta mudança: falta uma série de capturas atuais com nível conhecido e limites do treinador confirmados. O novo resolvedor já aceita nível conhecido; uma leitura futura do arco deverá fornecer intervalo de candidatos, não um nível pontual arredondado.
