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

## Release no GitHub
O projeto já está preparado para criar uma `GitHub Release` automaticamente quando você publicar uma tag no formato `v*`, por exemplo `v1.0.0`.

Fluxo básico:
1. Commit e push das mudanças.
2. Criar a tag:
   `git tag v1.0.0`
3. Enviar a tag:
   `git push origin v1.0.0`
4. O GitHub Actions vai compilar a APK e criar a release com o arquivo anexado.

Secrets opcionais para assinar a APK de release:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Se esses secrets não forem configurados, a release ainda será criada, mas com a APK de release não assinada.

## Observacao importante
Automacao direta de entrada no Pokemon GO pode conflitar com regras do jogo. Este MVP so gera sugestao de nome; a troca no jogo fica manual.
