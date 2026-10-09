# Otimizacao das barras de IV - 2026-10-08

## Ponto de retorno

Backup local: `backups/iv-reader-20261008-222433/`.

- `workspace-source.zip`: 886 arquivos de codigo, configuracao, assets do app e documentos, incluindo alteracoes anteriores e fontes novos. ZIP verificado; hashes SHA-256 em `manifest.json`.
- `OcrPokemonParser.kt`: copia exata do detector antes desta tarefa.
- `git-status.txt` e `git-head.txt`: estado Git de referencia.
- `restaurar-detector.ps1`: restaura somente o detector anterior, sem desfazer outras alteracoes do projeto. Recompilar o APK depois de executar.
- `reference-assets/` e caches Python nao estao no ZIP; permanecem intactos no projeto.

## Alteracoes

1. Os pixels da parte inferior da captura sao carregados de uma vez com `getPixels`, compartilhados entre os detectores de barras. Coordenadas fora dessa area conservam o acesso original. Nao ha redimensionamento nem mudanca de limiares.
2. Mantida a prioridade original: barras ancoradas nos textos, layout esperado e busca ampla. As buscas posteriores deixam de executar quando todas as posicoes ja possuem candidatos geometricamente compativeis que elas nao poderiam substituir.
3. Cache de geometria por instancia do parser, resolucao e retangulo pesquisado. Reutiliza apenas a classificacao das linhas da imagem que continuam exatamente iguais, comparadas pixel a pixel. Qualquer linha alterada e examinada novamente. As medidas de preenchimento e os IVs sao sempre calculados na captura atual. Limite de 12 regioes e 1 milhao de pixels de referencia (aproximadamente 4 MB, mais metadados). Nao ret?m Bitmap e nao persiste dados entre sessoes.

O cache e deliberadamente conservador: animacoes, mudancas de layout e deslocamentos podem reduzir seu aproveitamento, mas nao permitem herdar IVs de outro Pokemon. Os logs `MewNameReadTiming: IV geometry rows=...` indicam quantas linhas foram reaproveitadas.

## Evidencia e validacao

Antes da alteracao, 16 leituras bem-sucedidas de Pokemon no aparelho conectado:

| Etapa | Mediana |
|---|---:|
| Total ate solicitar janela | 2574 ms |
| Captura | 353 ms |
| OCR principal | 206 ms |
| IVs e PC | 1534 ms |
| Fundo e marcadores | 300 ms |
| PvP | 8 ms |

Nao sao medidas desta implementacao nem de outras rotas, como raid e perfil.

A comparacao diferencial temporaria executou o detector salvo e o novo sobre Psyduck e Scatterbug em 1080x2400 e 1220x2712, repetindo a resolucao inicial, leituras quentes, alteracoes nos pixels, ausencia de textos e desaparecimento do painel. Comparou IVs, ratios, confianca e retangulos; nenhum resultado divergiu. A copia de referencia e os XMLs do teste estao no backup para reproduzir a verificacao, sem duplicar o parser no codigo permanente.

Testes permanentes: `PhoneReadingRegressionTest`, `IvReadOptimizationTest` e `CpIvEvidenceTest`. Verificam inclusive que mudar os pixels mantendo o mesmo OCR nao permite reutilizar uma leitura anterior.

O ganho de tempo no aparelho ainda precisa ser medido apos instalar o novo APK. A compilacao nao instala automaticamente no celular.

## Validacao inicial no celular

APK instalado em 08/10/2026 as 22:34 no 2412DPC0AG, preservando os dados. Coletadas 16 leituras completas; nenhum erro de execucao registrado. A primeira leitura apos reiniciar levou 8543 ms, com custo alto de inicializacao em varias etapas.

Nas outras 15 leituras, a mediana da etapa IVs/PC foi 510 ms (antes: 1534 ms), aproximadamente 67% menor. O total ficou em 2458 ms (antes: 2574 ms); OCR principal subiu de 206 para 728 ms nesta sessao. Sao amostras diferentes em uso real, nao um benchmark controlado: nao atribuir causalidade a mudanca do OCR, nem prometer reducao equivalente no tempo total. Dados por leitura em `backups/iv-reader-20261008-222433/initial-device-readings.json`.

Durante a validacao, o usuario relatou XXL persistente ao trocar para Scatterbug PC 205 normal. Nova leitura confirmou visualmente o nome sugerido com XXL. A mesclagem de sessao tinha duas regras problematicas: permitia mesma especie/IVs mesmo com PC conflitante, e sempre herdava tamanho anterior quando o atual era normal.

Corrigido `PokemonReadSessionMerger`: conflitos explicitos de PC ou IVs confiaveis impedem mesclagem; resultado atual de tamanho com diagnostico prevalece, inclusive NORMAL. Sem diagnostico, leituras complementares conservam o comportamento anterior. A regra e compartilhada pelo app e sobreposicao. Copia anterior em `PokemonReadSessionMerger-before-size-fix.kt` no backup; essa correcao e independente da otimizacao das barras.

Testes `PokemonReadSessionSizeTest` cobrem PC diferente, IVs diferentes, tamanho normal atual, selo atual confirmado e preservacao dos campos ausentes numa leitura complementar. Executados tambem os testes existentes de formas regionais, falso Dynamax e reconhecimento. Novo APK gerado e instalado. Validacao da sequencia XXL -> normal concluida no aparelho: o usuario confirmou que XXL desapareceu no Scatterbug normal. A ultima leitura acompanhada terminou sem erro em 2035 ms. Os 20 testes da correcao de sessao passaram.
