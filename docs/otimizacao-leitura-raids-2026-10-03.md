# Leitura de raids - 2026-10-03

## Evidencia anterior a esta alteracao

Observacao passiva no 2412DPC0AG, versao 1.0.28, em sete leituras de Mega
Victreebel: captura 332-422 ms; OCR 185-382 ms; roteamento 6919-11532 ms;
total ate solicitar a janela 7490-12359 ms. Mais de 92% no roteamento.
Os relatorios locais de Mega Victreebel nivel 20/40 foram confirmados apos a
atualizacao. Algumas leituras exibiram chefe nao identificado. O texto OCR
individual das falhas nao estava no log, portanto a causa exata dessa falha
nao pode ser deduzida somente das capturas de tela.

## Implementacao

- Sobreposicao pede apenas a identidade da raid a BattleAdvisor. As sugestoes
  genericas descartadas pelo modal e equipes Max nao sao calculadas nesse caminho.
  Outros consumidores mantem o contrato completo de adviceForRaw por padrao.
- Correspondencia exata de nome precede comparacoes aproximadas; o maior alias
  exato continua tendo prioridade. Busca aproximada permanece como alternativa.
- RaidMetadata indexa nomes normalizados e conserva ambiguidade de formas.
- Detector de nivel le os pixels da regiao em lote, sem alterar limiares nem
  contagem. So executa depois de identificar uma raid, nao para perfil/Max/Pokemon.
- Indice de relatorios por diretorio reutilizado entre instancias, com assinatura
  de nomes, tamanhos e datas. Escritas pelo repositorio invalidam o indice;
  adicoes, remocoes e alteracoes externas tambem sao detectadas. Limite 4 diretorios.
  Relatorios sao validados uma vez por reconstrucao, em vez de abertos duas vezes.
- Status muda para identificacao de tela/batalha depois do OCR.

## Diagnosticos

OverlayService: Read[id] Route/perfil, avaliacao, catalogo, filtros, falas Rocket,
catalogo Rocket, identificacao batalha e simbolos nivel. O chefe, modo e nivel
identificados tambem ficam registrados. Subtempos Route estao contidos no tempo
Roteamento; nao devem ser somados novamente ao total.
RaidReadTiming: resolucao da identidade, consulta ao cache (hit/miss e nivel),
atualizacao via rede (separada para niveis 20/40/50).

## Validacao e limites

RaidReadOptimizationTest cobre transcricao da tela de Mega Victreebel e variacoes
sinteticas de OCR, preservacao de recomendacoes Max, equivalencia dos nomes do
indice de metadados e invalidacao do indice de arquivos. Testes existentes cobrem
Mega X/Y, sombrosos, regionais, categorias Ultra Beast, relatorios offline e simbolos.

O ganho no celular e a identificacao nas mesmas capturas ainda precisam de nova
medicao com este APK. Nao atribuir os tempos anteriores ao desempenho novo nem
considerar a falha intermitente do chefe completamente resolvida por testes sinteticos.


## Validacao posterior com Xerneas

No aparelho, o OCR retornou somente `erne` na faixa do nome, encoberto pelos
chifres do modelo. O nivel detectado foi 5. O roteamento carregava falas Rocket
em 6241 ms antes de reconhecer a raid. Esse caminho agora tenta batalha antes
das traducoes Rocket (preservadas para telas que nao correspondem a batalha).

A leitura seguinte, apos reinstalacao, passou de 8459 ms para 1179 ms ate solicitar
a janela: OCR 252 ms e roteamento 539 ms. Sao observacoes individuais, nao uma
media de benchmark. A resolucao interna do modal nao esta incluida nesse total.

Adicionado fallback conservador para nomes encobertos: fragmento somente na faixa
central calibrada, pelo menos 4 letras e metade do nome, nivel numerico detectado
e um unico chefe compativel no catalogo atual salvo. Sem catalogo, nivel ou com
formas ambiguas, continua sem inferir. Nao usa PC como chave nem presume que toda
raid nivel 5 seja Xerneas. O caso real e os casos negativos estao cobertos em
RaidOccludedNameTest, incluindo a preservacao do roteamento Rocket.
