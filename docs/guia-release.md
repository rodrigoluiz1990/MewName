# Guia de release do MewName

Leia este documento ao receber uma solicitação de preparar, gerar ou finalizar uma release. As instruções mais recentes do usuário prevalecem. Este guia define o procedimento; sua existência não autoriza publicar sem uma solicitação de release.

## Parâmetros padrão

- Idioma: português; datas em America/Sao_Paulo.
- Escopo: alterações destinadas à release, incluindo commits posteriores à última release e mudanças locais ainda não commitadas. Não presumir que todo arquivo local deve ser publicado.
- Versão: incrementar o patch da última versão publicada, salvo orientação diferente; verificar tags e releases remotas antes de escolher. Não fixar aqui o número da próxima versão.
- Histórico: uma descrição curta por mudança relevante, agrupada por funcionalidade, sem repetir tentativas intermediárias ou ajustes substituídos.
- Publicação: quando o usuário solicitar gerar/finalizar a release, concluir commit, push, tag e acompanhamento do workflow. Se pedir apenas preparação ou commit, respeitar esse limite.
- Não criar relatórios extras de análise, testes ou inventários. Atualizar os arquivos de release existentes. Logs e capturas ficam locais.
- Não pedir novamente autorização para passos já abrangidos pela solicitação. Resolver escolhas rotineiras autonomamente; pedir esclarecimento apenas se houver uma ambiguidade material que não possa ser resolvida pelo projeto e pela conversa.

## Exclusões persistentes

Até instrução explícita do usuário liberando a inclusão, excluir o efeito experimental Dynamax aplicado ao sprite do Squirtle. Pontos conhecidos para conferir:

- `app/src/main/java/com/mewname/app/DynamaxSquirtlePortrait.kt`
- `app/src/main/java/com/mewname/app/MaxPokemonPortrait.kt`
- `app/src/main/java/com/mewname/app/MaxBattleSuggestionsScreen.kt`
- `app/src/main/assets/raids/max/SQUIRTLE.png`
- `app/src/main/res/drawable-nodpi/dynamax_aura.png`
- Trecho experimental de `app/src/main/assets/raids/max/README.md`.

Essa lista é uma pista, não uma regra para excluir arquivos inteiros. Revisar os trechos e dependências: outros ajustes nesses arquivos podem pertencer à release. Preservar o experimento local, sem apagá-lo nem publicá-lo acidentalmente. Se necessário, preparar e testar uma árvore isolada com o conteúdo exato a publicar.

Também excluir temporários, APKs gerados localmente, logs, capturas, caches, extrações brutas do jogo, credenciais e imagens de referência não usadas pelo aplicativo. Assets efetivamente usados, código, testes e ferramentas necessárias podem ser incluídos. Não excluir arquivos somente pela extensão.

## Roteiro econômico de análise

1. Ler `AGENTS.md`, este guia, `.github/workflows/release.yml`, `.github/workflows/verify.yml` e os trechos de versionamento em `app/build.gradle.kts`.
2. Conferir branch, remotes, estado do Git, última release publicada e tag correspondente. Não usar apenas a última tag local nem alterar automaticamente a branch de destino.
3. Começar por `git status --short`, `git diff --stat`, `git diff --cached --stat` e o resumo dos commits desde a release anterior. Inspecionar diffs por funcionalidade, incluindo arquivos não rastreados relevantes.
4. Ler só os trechos necessários. Para JSONs grandes, usar scripts que resumam diferenças sem imprimir o arquivo inteiro; para assets, conferir arquivos usados, referências e procedência. Não tratar mudança volumosa de dados como automaticamente segura.
5. Manter resultados de comandos limitados. Guardar logs locais e consultar o resumo ou erros; ampliar a leitura apenas se surgir uma dúvida ou falha.
6. Preservar alterações preexistentes e exclusões. Preparar o stage explicitamente, revisar seu diff e não usar `git add .` indiscriminadamente.

## Versão e histórico

- Atualizar `CHANGELOG.md` com todas as mudanças da release em relação à última publicada, sem duplicar funcionalidades já lançadas.
- Atualizar `RELEASE_NOTES.md`, usado pelo workflow, com a versão e o resumo destinado ao usuário. Não criar outro relatório equivalente.
- Atualizar README/ajuda somente se a release tornar suas instruções desatualizadas.
- No fluxo atual, `versionName` vem de `MEWNAME_RELEASE_TAG` sem o prefixo `v`; builds locais usam o fallback em `app/build.gradle.kts`. Atualizar esse fallback para a nova versão e usar a tag correspondente `vX.Y.Z`.
- `versionCode` é calculado a partir do tempo UTC. Não substituir esse mecanismo por incremento manual.
- Confirmar que versão, tag, changelog e notas concordam. Revalidar estas regras se o build/workflow tiver mudado.

## Validação antes de publicar

- Verificar a árvore exata que será commitada, especialmente se houver experimentos excluídos. Um build da árvore local com mudanças extras não comprova o conteúdo da release.
- Usar JDK 17 e o Gradle Wrapper. Atualmente o CI exige validação semântica dos catálogos, testes JVM, compilação debug, lint debug/release e build release. Conferir os workflows, pois são a referência atual.
- Checks atuais: `:app:validateCatalogSemantics`, `:app:testDebugUnitTest`, `:app:compileDebugKotlin`, `:app:lintDebug`, `:app:lintRelease`, `:app:assembleRelease`.
- Reaproveitar resultados locais apenas se corresponderem ao mesmo código, dados e configuração; não dispensar checks exigidos pelo CI. Não repetir testes aprovados sem mudanças ou motivo concreto.
- Após validar e compilar, conferir ADB e instalar um APK compatível com a assinatura instalada, preservando os dados. Não desinstalar o app para contornar incompatibilidade de assinatura.
- Informar separadamente instalação e testes físicos realmente executados. Ao detectar aparelho, lembrar a pendência de OCR e consultar `docs/validacao-ocr-aparelho.md`; não declarar que testes JVM ou capturas substituem a validação física.
- Não publicar como release concluída com falhas obrigatórias pendentes. Informar impedimentos reais, sem inventar validações.

## Commit, push e publicação

1. Revisar conteúdo staged, exclusões, dependências, versão e notas. Fazer commit com mensagem curta e representativa do escopo final.
2. Enviar o commit para a branch/remoto apropriados, sem force push. Se houver divergências remotas, resolver preservando o trabalho existente e revalidar o que mudou.
3. Criar a tag inédita `vX.Y.Z` no commit validado e enviá-la explicitamente. Não mover ou reutilizar tag publicada e não enviar outras tags por acidente.
4. O workflow atual `Release APK` é disparado pelo push de tags `v*`; ele publica `RELEASE_NOTES.md` e o asset `MewName-vX.Y.Z.apk`. Não duplicar a publicação manualmente.
5. Acompanhar os workflows de verificação e release até o resultado. Confirmar que a release e o APK pertencem à tag/commit esperados. Conferir a situação da assinatura; o workflow pode produzir APK sem assinatura de produção quando os secrets estão ausentes.
6. Conferir o estado local final. Mudanças experimentais excluídas podem continuar pendentes; não limpá-las.

## Resposta final

Informar de forma breve: versão, hash do commit, checks executados, instalação/testes no celular e link da release. Mencionar exclusões e limitações relevantes. Se a publicação falhar ou ainda estiver em andamento, dizer isso claramente em vez de afirmar que terminou.
