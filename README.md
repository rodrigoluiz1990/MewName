# MewName (MVP)

App Android em Kotlin + Compose para gerar apelidos de Pokemon no Pokemon GO com base em OCR de screenshot.

## O que ja funciona
- Selecionar screenshot da galeria.
- Extrair texto via ML Kit OCR.
- Tentar identificar nome, CP, IV e nivel.
- Configurar formato do nome (ordem, campos, separador, tamanho maximo).
- Preview imediato do apelido gerado.

## Estrutura
- `app/src/main/java/com/mewname/app/MainActivity.kt`: UI principal.
- `app/src/main/java/com/mewname/app/MainViewModel.kt`: estado e regras de tela.
- `app/src/main/java/com/mewname/app/ocr/OcrEngine.kt`: OCR.
- `app/src/main/java/com/mewname/app/domain/OcrPokemonParser.kt`: parser dos dados.
- `app/src/main/java/com/mewname/app/domain/NameGenerator.kt`: gerador do nome final.

## Proximos passos (estilo Pokegenie)
1. Implementar captura em tempo real com `MediaProjection` (com consentimento do usuario).
2. Rodar OCR continuamente com throttling (ex: 2 fps).
3. Sobrepor bolha/overlay para copiar nome com um toque.
4. Salvar presets de formato em `DataStore`.
5. Criar dicionario de nomes Pokemon para parser mais preciso.

## Observacao importante
Automacao direta de entrada no Pokemon GO pode conflitar com regras do jogo. Este MVP so gera sugestao de nome; a troca no jogo fica manual.
