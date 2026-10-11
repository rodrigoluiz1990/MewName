# Organização do parser OCR

`OcrPokemonParser` mantém as duas entradas `parse` e coordena a combinação das evidências, a revisão e o cálculo de rankings. A extração de componentes preserva as regiões, tolerâncias, ordem de prioridade e regras de fallback existentes.

| Componente | Responsabilidade |
| --- | --- |
| `OcrIvBarReader` | Localizar e medir barras de avaliação; manter o cache de geometria e produzir valores e diagnósticos de IV. |
| `OcrPokemonNameReader` | Resolver nomes, aliases, texto do rodapé e famílias de doces; manter o catálogo de nomes em cache. |
| `OcrNumericReader` | Extrair CP, HP, nível e percentual de IV nas regiões existentes, além de detectar contexto de avaliação. |
| `OcrVisualAttributeReader` | Reconhecer fundos especiais e estrela de favorito, mantendo referências visuais e assinaturas de textura. |
| `OcrTextGeometry` | Compartilhar normalização de texto, ordenação de linhas e conversão de coordenadas, sem estado. |

Os leitores com estado pertencem a cada instância do parser. O leitor de nomes recebe o mesmo `PokemonFamilySuggester` usado pelo coordenador. A extração não transforma caches por instância em caches globais.

Os componentes são internos ao módulo. `MainViewModel`, `ReviewOcrPipeline` e `OverlayService` continuam usando `OcrPokemonParser`. Regras de movimentos, formas, gênero e seleção PvP continuam no coordenador e podem ser extraídas em etapas posteriores.

## Verificação

Executar `./gradlew :app:testDebugUnitTest` (no Windows, `gradlew.bat`). O pré-build também valida os catálogos.

Os testes de reconhecimento por texto, nomes e formas continuam protegendo o fluxo. `IvReadOptimizationTest` e `PhoneReadingRegressionTest` exercitam o leitor de barras diretamente, incluindo mudanças de resolução e conteúdo da imagem. `CpIvEvidenceTest` exercita o leitor de nomes diretamente, sem reflexão nesses componentes.

A validação física continua pendente conforme `validacao-ocr-aparelho.md`.
