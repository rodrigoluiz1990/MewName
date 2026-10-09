# Leitura por PC/HP e análise do arco de nível

## Implementação

A resolução considera a espécie/forma identificada, PC exato, HP máximo (o segundo número de atual/máximo), IVs confirmados e nível explícito quando disponíveis. Enumera níveis de 1 a 51 em passos de 0,5 usando a mesma tabela de multiplicadores dos cálculos PVP. Exige HP, nível ou pelo menos dois IVs já conhecidos como evidência adicional ao PC. Cada IV ausente só é preenchido quando todas as combinações compatíveis concordam naquele atributo. Os demais permanecem desconhecidos; o percentual só é calculado com os três IVs. Nível só é confirmado por esse mecanismo quando todas concordam. Não escolhe a combinação mais próxima, não sobrescreve barras já aceitas e não presume piso 10 de raid/ovo. A precisão depende de espécie, forma, PC e HP terem sido lidos corretamente.

Sem avaliação aberta, o título próximo/acima do HP é prioridade. Doces identificam a família e restringem correções pequenas de OCR, mas não escolhem automaticamente uma evolução quando o apelido não identifica a espécie. A área de doces foi ampliada e os formatos português/inglês/espanhol são aceitos. Os diagnósticos registram ambiguidade ou ausência de combinação exata.

## Arco: análise, ainda sem conversão automática

O nome curva_cp anterior no código significa curva matemática de PC, não leitura de pixels do arco.

É viável detectar o semicírculo e o marcador, normalizar a geometria pela tela e usar a posição como intervalo de nível. Um leitor público de screenshots demonstra essa abordagem, exigindo nível do treinador e marcador visível: https://github.com/nneonneo/pogo-iv-reader . A implementação desse projeto é antiga e específica de iPhone; não serve como calibração direta do layout atual do GO.

Para ativar: coletar capturas com nível conhecido (incluindo meios níveis, 40+, limite máximo e melhor companheiro) nos dois aparelhos; medir a relação posição/multiplicador e o limite do arco para o treinador; mascarar Pokémon/fundo/partículas; rejeitar arco incompleto ou coberto; usar uma faixa de erro angular, cruzada com PC/HP. Não interpolar linearmente nível de 1 a 50 por ângulo sem validar a escala do jogo. Fundo claro e animações podem ocultar marcador/linha.

A conversão visual não foi ativada nesta mudança: falta uma série de capturas atuais com nível conhecido e limites do treinador confirmados. O novo resolvedor já aceita nível conhecido; uma leitura futura do arco deverá fornecer intervalo de candidatos, não um nível pontual arredondado.


## Complementos de 08/10/2026

- Consenso individual por ataque, defesa e stamina: um atributo pode ser determinado mesmo quando os outros continuam ambiguos. Caso de regressao: Psyduck PC 1077, nivel 50 e PS maximos correspondentes a stamina 15 admite 0/8/15, 3/3/15 e 5/0/15. Somente stamina e preenchida.
- PC sem HP pode ser cruzado com dois IVs conhecidos. PC sozinho continua sem confirmar IVs; a tela de captura ja fornece combinacoes possiveis para esse caso.
- A enumeracao ignora niveis cujo intervalo minimo/maximo de PC nao comporta o PC observado, preservando todas as combinacoes exatas.
- Nome sem painel de avaliacao: titulo sobre a barra de PS continua prioritario; um erro de um caractere em titulo com pelo menos seis caracteres pode ser corrigido sem doces apenas se houver uma unica especie candidata.
- Doces em linhas separadas: suporte a DOCES DE / BULBASAUR e BULBASAUR / CANDY, respeitando proximidade e coluna. Rotulo completo nao incorpora texto posterior de megaenergia. Fragmentos DE, GG e XL nao viram nomes de especie.
- Backup incremental dos fontes anteriores em `backups/cp-name-evidence-20261008-232046/`; preserva as otimizacoes de IV e a correcao anterior de XXL.

### Analise do arco visual

Reconsultado o [codigo de referencia read_level](https://github.com/nneonneo/pogo-iv-reader/blob/master/read_screenshot.py). Ele procura o marcador branco ao longo do arco usando o multiplicador CPM e o limite associado ao treinador, em vez de interpolar linearmente o numero do nivel. Essa regra e o layout antigo do projeto nao comprovam a escala atual do jogo.

Para integrar com seguranca ao MewName: detectar centro/raio e marcador, produzir um intervalo angular com confianca, calibrar sua correspondencia com CPM nas duas resolucoes e nos limites atuais do treinador, e intersectar o intervalo de niveis com as combinacoes exatas de PC/PS. Rejeitar marcador encoberto, multiplos candidatos ou fundo indistinguivel. Capturas de Pokemon com IVs confirmados e PC/PS permitem montar os exemplos de calibracao, desde que o nivel calculado seja unico. Incluir meios niveis, 40+, melhor companheiro e mudancas no limite do treinador.

A leitura visual do arco continua desativada ate essa calibracao. Os niveis calculados nesta entrega vem das evidencias numericas; nao sao uma leitura da posicao do marcador.

Validacao: 21 testes passaram (CpIvEvidenceTest, PhoneReadingRegressionTest e PokemonReadSessionSizeTest); APK debug gerado. Sem nova instalacao nesta etapa.
