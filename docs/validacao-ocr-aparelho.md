# Validação do OCR no aparelho

Estado: **validação física parcial realizada em 11/10/2026**. Continuam pendentes os cenários indicados abaixo.

## Roteiro

- Confirmar o aparelho disponível e autorizado no ADB; registrar modelo, versão do Android e resolução.
- Validar captura pela sobreposição e importação pela galeria: nome, forma regional, família, CP, HP, nível e IVs.
- Comparar os resultados com os valores visíveis na tela; incluir barras 0/0/0, 15/15/15 e valores intermediários.
- Conferir capturas comuns, imagens grandes e imagens com rotação EXIF, verificando se a redução de resolução preserva nomes e barras.
- Conferir fundos especiais e estrela de favorito após a divisão dos detectores.
- Cancelar uma leitura e iniciar outra; verificar que o fluxo continua utilizável. Quando possível, exercitar timeout e nova tentativa.
- Encerrar e reabrir a sobreposição e observar falhas, uso de memória e tempo de leitura.

## Registro

Preencher após a execução: data, aparelho, versão do app, cenários executados, resultados e limitações. Manter itens não executados como pendentes.

A refatoração é verificada separadamente pela suíte local, incluindo capturas já salvas de Psyduck e Scatterbug. Isso não confirma o funcionamento no aparelho conectado.

## Execução de 11/10/2026

- Aparelho 2412DPC0AG, Android 16, 1220 × 2712; APK debug 1.0.29, versionCode 424508030, instalado preservando dados.
- Captura real de Ponyta pelo overlay e importação pela galeria: IVs 12/14/15 (91%); teste instrumentado confirmou PC 1521, favorito e sortudo.
- 14/14 imagens de referência no aparelho, incluindo 0/0/0 e 15/15/15; cancelamento e nova execução do validador funcionaram.
- Imagem ampliada (2440 × 5424) e JPEG com EXIF 6: IVs conferidos. O teste JPEG identificou e motivou uma correção do arredondamento de 14 para 15.
- Corrigidos também o contador de referências do validador, seu processamento na thread da interface, a navegação após importar imagem e o prefixo PC reconhecido como sC na região superior.
- Catálogos de ataques legados/efeitos de aventura, buscas e seletor de ataques exercitados. Coleção persistiu após encerrar o processo; marcação de teste desfeita e os 68 registros originais conferidos.
- Overlay encerrado e reaberto com compartilhamento restrito ao Pokémon GO. Últimas capturas: 4176 ms e 1146 ms; não é comparação com APK anterior.
- 377 testes JVM/Robolectric e lintDebug sem erros (32 avisos). Nenhum crash registrado no intervalo desta sessão. Compartilhamento encerrado ao terminar.
- Pendentes: validação física específica de formas regionais, todos os fundos especiais, timeout forçado e observação prolongada de memória. Os testes desta sessão não cobrem todos esses cenários.
## Revalidação de cancelamento — 11/10/2026

- Mesmo aparelho 2412DPC0AG; APK debug 1.0.29, versionCode 424510270, instalado com dados preservados.
- Validador de amostras: iniciado, cancelado durante a análise e reiniciado imediatamente; resultado final 14/14.
- Overlay com compartilhamento restrito ao Pokémon GO: leitura de Bulbasaur cancelada pelo botão da janela e nova captura iniciada imediatamente. Logs: leitura 54fb4075 terminou como cancelled (657 ms); leitura 336b291e terminou como success (1220 ms), exibindo nomes sugeridos. Os tempos não são comparação de desempenho.
- Suíte completa: 385 testes aprovados; lintDebug sem erros, com 39 avisos. Testes de regressão simulam finalização tardia, erro/resultado antigo, timeout, bloqueio de execução duplicada e progresso do overlay enfileirado.
- Compartilhamento encerrado ao terminar. Formas regionais, cobertura completa de fundos, timeout físico forçado e memória prolongada continuam pendentes.

## Progresso sem bloqueio — 11/10/2026

- APK debug 1.0.29, versionCode 424510984, instalado no mesmo aparelho. Confirmada rolagem da ficha de Bulbasaur enquanto o cartão mostrava análise em andamento; tela sem camada cinza e toques externos recebidos pelo jogo.
- Botão Cancelar do cartão conferido: leitura d1bfb32e terminou como cancelled. Cinco testes de regressão de cancelamento aprovados; assembleDebug e validação dos catálogos aprovados. Compartilhamento encerrado ao terminar.
