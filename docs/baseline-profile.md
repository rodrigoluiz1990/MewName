# Build de release: R8 e Baseline Profile

O build `release` é reduzido e otimizado pelo R8 (`isMinifyEnabled` e `isShrinkResources`) e leva o Baseline Profile `app/src/main/baseline-prof.txt`. Esse perfil lista o código usado ao abrir o app e as telas principais. O Android o compila já na instalação do APK, e a biblioteca `profileinstaller` o aplica em versões que não fazem isso sozinhas.

Medido no POCO X7 Pro (Android 16) em 27/10/2026, no estado logo após instalar:

| Build | Abertura a frio (mediana) |
|---|---|
| Release sem R8 | 360 ms |
| R8 sem perfil | ~317 ms |
| R8 com perfil | ~266 ms |

O pior quadro das telas leves e médias caiu pela metade. Pokédex, Raids e Análise PvP continuam limitadas pelo próprio carregamento de dados.

## Build `benchmark`

O tipo `benchmark` é o release assinado com a chave de debug local e marcado como `profileable`. Ele instala por cima do APK de debug sem perder dados e permite usar `atrace`, `simpleperf` e a coleta do perfil.

- `.\gradlew :app:assembleBenchmark`: build igual ao release, com R8.
- `.\gradlew :app:assembleBenchmark -Pmewname.benchmarkMinify=false`: sem R8, para coletar o perfil com os nomes originais das classes.

## Regenerar o perfil

Regenere o perfil quando as telas principais ou a abertura do app mudarem bastante. Um perfil desatualizado não quebra nada: o código que não estiver nele só deixa de ser pré-compilado.

1. Conecte um aparelho com Android 13 ou mais recente e o app configurado, na tela inicial e em português. O roteiro não apaga dados.
2. Gere o build sem R8 e o roteiro: `.\gradlew :app:assembleBenchmark :baselineprofile:assembleBenchmark -Pmewname.benchmarkMinify=false`.
3. Instale os dois APKs com `adb install -r`. No HyperOS, confirme a instalação do pacote `com.mewname.baselineprofile` na tela.
4. Rode o roteiro sem tocar no aparelho:
   `adb shell am instrument -w -e class com.mewname.baselineprofile.BaselineProfileGenerator com.mewname.baselineprofile/androidx.test.runner.AndroidJUnitRunner`
5. No Android 16, a biblioteca de benchmark acusa falha ao ler a saída de `pm dump-profiles`, mas o perfil é gravado. Copie-o direto:
   `adb pull /data/misc/profman/com.mewname.app-primary.prof.txt app/src/main/baseline-prof.txt`
6. Remova o roteiro (`adb uninstall com.mewname.baselineprofile`), gere o build com R8 e confira no APK o arquivo `assets/dexopt/baseline.prof`.

O roteiro está em `baselineprofile/src/main/java/com/mewname/baselineprofile/BaselineProfileGenerator.kt` e localiza as telas pelos rótulos em português da tela inicial.

## Cuidados com o R8

- O app grava nomes de enums nas preferências e nos backups. O R8 renomeia os campos, mas mantém o texto retornado por `name`; confirmado no APK em 27/10/2026.
- O código não usa reflexão nem `getIdentifier`. Se isso mudar, adicione regras em `app/proguard-rules.pro`.
- Erros do APK publicado mostram nomes encurtados. Para traduzi-los, use o `MewName-vX.Y.Z-mapping.txt` anexado à mesma release; localmente, ele fica em `app/build/outputs/mapping/release/mapping.txt`.
