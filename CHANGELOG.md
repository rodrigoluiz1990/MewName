# Histórico de alterações

## 1.0.35 - 2026-10-28

Alterações acumuladas desde a release 1.0.34.

### Fundos especiais

- Detecção revisada: o cartão é reconhecido quando fica claramente mais perto de uma referência especial ou longe do fundo do próprio tipo; nas capturas de referência passou de 9 para 37 de 41 cartões, sem novos falsos positivos.
- Pokémon sombrosos: o fundo de sombroso conta como comum e os cartões especiais deles (como o Ho-Oh) passam a ser reconhecidos.
- O teste visual de sortudo, que confundia cartões dourados e vermelhos, deixa de bloquear o fundo especial quando o custo de Fortalecer ou o sombroso descartam sortudo, ou quando a referência especial é muito próxima.
- Novas referências recortadas de capturas, com categoria: Ancients Recovered (Regis), Pokémon Gold do GO Tour (Ho-Oh), GO Wild Area 2025 e Líder Valor.
- O app aprende fundos no próprio aparelho: ao copiar um nome com a tag de fundo especial, o recorte daquela leitura é guardado com a categoria do nome, se não parecer o fundo comum. Os recortes entram no backup.
- O cartão visto numa leitura só passa para a seguinte quando é o mesmo Pokémon (mesmo PC, ou mesmos PS e IVs); antes, Pokémon da mesma família com o PC encoberto herdavam o fundo.

### Purificado, sombroso e sortudo

- Origem identificada pelo custo de Fortalecer (purificado, sombroso, sortudo), substituindo a detecção de brilhos que marcava purificado em excesso; custos ambíguos ficam sem marca.
- Texto "sombroso" reconhecido sem confundir com ataques como Bola Sombria.
- Corrigido o arredondamento dos doces de Fortalecer e os custos por origem de captura.

### Reconhecimento e ataques

- Apelido com nome impossível para o PC/PS escolhe o membro compatível da família.
- Lista de ataques completada com o Game Master: 86 combinações, incluindo Contra-atacar (Elite) do Mewtwo.

### Nomes

- Siglas padrão de fundo especial: ★AN (Aniversário), ★TP (Temporadas), ★LE (Líder de Equipe) e ★ME (Mega Evolução). Configurações já salvas mantêm as próprias siglas.

### Desempenho e tamanho

- Build release com R8 e Baseline Profile: APK de 246 para 129 MB e abertura a frio de ~360 para ~270 ms.
- Figurinhas e imagens de eventos recomprimidas.
- Tela de Raids monta a lista fora da linha principal, sem engasgos ao rolar.
- Detectores visuais leem os pixels por linha.

### Manutenção

- A release publica o `mapping.txt` do R8; build `benchmark` e módulo `baselineprofile` para medir e regenerar o perfil (`docs/baseline-profile.md`).

## 1.0.34 - 2026-10-26

Alterações acumuladas desde a release 1.0.33.

### Desempenho da leitura pela sobreposição

- Captura usa o primeiro quadro composto sem o botão (cerca de 60 ms) em vez da espera fixa de 260 ms; a espera completa é mantida quando uma janela do app acabou de ser fechada.
- Falas da Equipe GO Rocket preparadas ao ligar a sobreposição e guardadas por versão do app; a verificação de cada leitura não reprocessa mais as tabelas de texto do jogo, e cargas simultâneas da mesma tabela foram eliminadas.
- Assinaturas das referências de fundo compartilhadas entre leituras e guardadas por versão; modelo de OCR e referências preparados em segundo plano ao ligar a sobreposição.
- Ranking PvP, barras de IV, detecção de gênero e emblemas da revisão calculados com menos alocações e leituras de pixel, mantendo resultados idênticos.
- Catálogos de famílias e de IV Master compartilhados entre leituras; expressões de normalização de texto compiladas uma única vez.

### Telas do aplicativo

- Ficha da Pokédex não trava mais ao montar a família e as evoluções.
- Ícones de assets em cache compartilhado e logo reduzido na barra de navegação e no botão flutuante, com abertura do app mais rápida.

### Reconhecimento

- Tela de detalhe com o PC encoberto pelo Pokémon passa a identificar a espécie pelos doces, inclusive com apelido; antes o botão "Novo ataque" era tomado como avaliação e a leitura podia indicar outro Pokémon.

### Manutenção

- Removida a dependência ML Kit Language ID, sem uso no app.
- Logs de depuração detalham o tempo das subetapas de IV, nível e fundo.

## 1.0.33 - 2026-10-25

Alterações acumuladas desde a release 1.0.32.

### Coleções e sprites

- Nova aba Fantasias na coleção, separada das formas normais; Mewtwo de Armadura preserva seus atributos e seleção na sugestão de nomes.
- Coleção de adesivos com marcação persistente; cartões de Fantasias, Fundos especiais e Adesivos compartilham imagem, nome, margens e indicador de coletado.
- Fundos especiais e adesivos são marcados pelo toque no cartão; pressionar um fundo abre a prévia ampliada.
- Novo layout compacto da coleção na sobreposição, com uma espécie por linha, sprite e sequência de formas coletáveis.
- Alternância de layouts, busca em uma linha e painel recolhível até a alça para liberar os controles do jogo.
- Busca posiciona a lista na espécie encontrada, priorizando nome/número exato e prefixo; retorno dos detalhes preserva a posição.
- Formas regionais aparecem após a espécie principal; corrigidas duplicações e aliases Galar/Galarian, Meowth e Perrserker.
- Revisados sprites e nomes de formas, incluindo Keldeo Ordinary/Resolute, Minior e formas técnicas HOME_FORM_REVERSION.
- Sprites ausentes usam uma silhueta de Pikachu; imagens compartilhadas recebem recorte de margens transparentes e tamanhos consistentes.
- Corrigidas classificações de fantasias e clones, preservando as marcações existentes ao normalizar identificadores.

### Filtros e nomes

- Tela inicial de filtros mostra os salvos; criação pelo +, edição ao tocar no cartão e ações independentes de copiar e excluir.
- Tags personalizadas com nome/texto único, ícone opcional, edição por pressão longa e favorito configurável no editor.
- Primeira aba reúne tags favoritas; criação integrada à aba Minhas tags e catálogo de ícones compartilhado com missões.
- Tags selecionadas respeitam a ordem de inclusão e podem ser reorganizadas por arraste no filtro copiado e salvo.
- Editor rápido de filtros na sobreposição com janela móvel, preview destacado, ícones compactos e cópia sem bloquear o jogo.
- Toque na tag alterna inclusão, negação e desativação no preview do filtro rápido.
- Preview do apelido e padrão do nome com visual compartilhado; arraste dos campos do padrão aprimorado.
- Formatos de nome podem ser reordenados por pressão longa e arraste, com persistência da ordem.
- Exclusão de formatos exige confirmação e mantém a proteção do último formato; IV 0% aparece como ⁰⁰ no apelido.

### Missões e backup

- Missões iniciais são configuradas em novas instalações, sem adicionar uma tela separada de modelos.
- Ordem das missões editável por pressão longa e arraste dos cartões.
- Configuração no Treinador permite caixa livre ou vinculada ao botão de leitura, acima, abaixo ou dividida entre ambos.
- Barras compactas mantêm o estilo glassmorphism e acompanham o botão, com ajustes de sobreposição e alinhamento.
- Catálogo compartilhado de ícones ampliado com itens do jogo, favorito, recursos e opção sem ícone; removidas duplicatas.
- Confirmação de exclusão de missão preservada e padronizada.
- Backup automático diário em pasta escolhida, mantendo uma cópia válida e preservando o arquivo após desinstalação.
- Reinstalações podem localizar o backup ao selecionar a mesma pasta; gravação e restauração protegem os dados existentes.

### Equipe GO Rocket, raids e eventos

- Personagens Rocket com nome abaixo e fala ao lado; Pokémon em cartões com fraquezas por ícones, ordenadas por eficácia.
- Seleção dos adversários é independente por recruta/líder; corrigidas associações e casos sem sugestões suficientes.
- Sugestão de uma equipe de três Pokémon com ataque rápido e até dois carregados, considerando cobertura ofensiva conjunta.
- Sugestões usam sprites, tipos dos ataques e símbolo de legado configurado pelo treinador; botão Copiar equipe sugerida abaixo dos cartões.
- Raids de nível 5 e Mega seguem os intervalos da programação atual, evitando incluir chefes futuros do simulador.
- Identificação de raids aprimorada com estrelas, PC do chefe, candidatos compatíveis e catálogo local ordenado pela Pokédex Nacional.
- Quando a identificação é inconclusiva, chefes candidatos aparecem em cartões com sprite para seleção manual.
- Imagens locais de eventos ampliadas e janelas de eventos recorrentes ajustadas no carrossel da tela inicial.

### OCR, IV Master e dados

- Leitura alternativa de nome, família pelos doces, PC e PS quando a avaliação inferior não está disponível.
- Cruzamento de PC/PS/IVs e arco calibrado para auxiliar o nível; evidências ambíguas não são tratadas como confirmação.
- PC visível auxilia o desempate de nível; PC parcial pode ser cruzado com PS e IVs conhecidos quando a combinação é única.
- Arredondamento dos doces de purificados corrigido, incluindo Moltres do nível 35 ao 40: 106 doces.
- Ajustada a distinção visual entre purificado e sombroso e o roteamento de telas de tags para os filtros.
- Regras aprovadas do IV Master incorporadas, incluindo famílias de Darmanitan de Galar e Growlithe de Hisui; planilha reduzida às pendências atuais.
- Catálogos de Pokémon e famílias reorganizados pela Pokédex Nacional; ferramentas de ordenação e geração do catálogo de PC de raids incluídas.

### Interface e manutenção

- Ícones de ações padronizados em Font Awesome 7.3.1, preservando dimensões compactas e incluindo licença e atribuição.
- Modais de nomes, temperatura, introdução, calendário, backup e exclusão compartilham título centralizado, X e ações padronizadas.
- Seletores de missão, variantes de raid e ataques PvP com opção selecionada destacada e fundo corrigido.
- Seletor de Pokémon da Análise de Equipe com fundo opaco, título em negrito e separação entre sprite e nome.
- Toggle de troca automática e botão de exportação de registros padronizados; confirmação do seletor de nível mantida.
- Guia de release documentado e testes ampliados para persistência, filtros, coleções, missões, OCR, raids e equipes.

O efeito experimental Dinamax do Squirtle permanece fora desta release. A regressão física completa do OCR e a divergência de poeira no fortalecimento do Moltres purificado continuam pendentes.

## 1.0.32 - 2026-10-18

Alterações acumuladas desde a release 1.0.31.

### Missões diárias

- Nova tela para cadastrar, editar, ativar e concluir missões, com renovação diária e escolha de ícone.
- Caixa compacta na sobreposição: toque conclui a missão; ao concluir todas, a caixa desaparece.
- Arraste da caixa com posição salva e visual glassmorphism; ocultação durante a captura do OCR.
- Seleção ampliada com itens do jogo, PokéStop, pesquisa diária, recursos de fusão, maçãs, moedas, passes, armazenamento e incubadoras.
- Ícone próprio no menu; ícone da missão à esquerda do nome e remoção de ícones redundantes.
- Modal com título e Salvar centralizados, fechamento pelo X e controles de ativação padronizados.

### Backup, códigos promocionais e ajuda

- Backup e restauração dos dados pessoais, acessíveis pelo menu Treinador antes da Ajuda.
- Correções de leitura e gravação pelo seletor de arquivos Android, incluindo tamanho desconhecido informado como zero bytes.
- Botão único de códigos promocionais alterna entre Resgatar e Resgatado: abre o navegador e registra o resgate; novo toque desfaz a marcação.
- Abas para separar códigos resgatados e filtros integrados às abas, sem botão adicional de resgate.
- Ajuda reorganizada conforme o menu principal, com bloco separado para a sobreposição e instruções consolidadas de missões.
- Corrigido o acesso à política de privacidade; fontes de dados ampliadas.

### Filtros, navegação e aparência

- Painel compartilhado de filtros, Limpar à direita e remoção de Limpar quando há apenas busca textual.
- Busca recolhível em Coleções e filtros reorganizados nas telas de ataques.
- Removida a lupa de Ovos, Pesquisa de Campo, Equipe GO Rocket, Raids e Calendário; filtros ficam acessíveis na tela.
- Abas antes dos filtros em Ovos, Pesquisa de Campo e GO Rocket, incluindo as distâncias dos ovos.
- Ordem compartilhada dos tipos: Planta, Fogo, Água, Elétrico, Psíquico, Normal, Lutador, Pedra, Terrestre, Venenoso, Inseto, Voador, Aço, Sombrio, Fantasma, Gelo, Dragão e Fada.
- Controles de ativação padronizados como toggles; títulos dos modais de nomes centralizados, X no topo e ação principal centralizada.
- Indicadores de carregamento centralizados nas telas ajustadas.
- Novos ícones de menu e remoção do nome duplicado no modal de Pokémon da coleção.
- Telas com cache exibem dados salvos imediatamente e atualizam em segundo plano quando necessário.

### Ataques e efeitos de aventura

- Ordenação por combobox integrado aos filtros, com inversão de sentido e opções em ordem alfabética.
- Pokédex Nacional como ordenação inicial nas listas de ataques vinculados a Pokémon; lista geral de golpes mantém ordenação por nome.
- Catálogos PvE/PvP, traduções e vínculos de ataques atualizados com os dados disponíveis do jogo.
- Revisados valores e associações de golpes, incluindo Fissura, Chifre Broca e Martelo Gigatônico.
- Efeitos de aventura com retratos e banners do jogo, cartões translúcidos, ajustes de espaçamento e ícone mais visível.
- Incluídos efeitos de aventura de Mewtwo, descrições resumidas e dados de combate disponíveis.

### Pokédex e tipos

- Lista principal limitada aos 1025 números da Pokédex Nacional; formas especiais e fusões acessíveis pela família.
- Removidas duplicações de espécies e corrigidos vínculos de sprites e identificação de formas, incluindo Zacian Coroado e Raichu de Alola.
- Otimizada a resolução de nomes e famílias, corrigindo carregamento prolongado de fichas como Dipplin.
- Atualizar passa a recarregar também os dados locais da ficha.
- Abas padronizadas e novas abas Pokédex, Tamanho e Fantasia quando houver trajes disponíveis.
- Informações de altura, peso, região, companheiro, captura, troca, transferências e custos com ícones de doces, poeira e energia.
- Comparação visual de tamanhos, tabela de limites por categoria e Professor Willow como referência de escala.
- Revisados indicadores de disponibilidade e nomes de fantasias, com descrições em português, inglês e espanhol.
- Removido o bloco redundante de informações adicionais da aba Geral.
- PvP com Copinha, ícones e nomes completos das ligas; limite de PC entre parênteses e grafia Ultra Liga.
- Golpes separados em blocos de rápidos e carregados; melhores combinações PvE/PvP após os carregados, com rápidos à esquerda e carregados à direita.
- Títulos de blocos padronizados em negrito e nomenclatura Pontuação nas combinações.
- Tela de Tipos mostra exemplos da combinação exata selecionada e cartões de fraquezas/resistências em ordem decrescente de multiplicador.
- Corrigidas falha ao selecionar dois tipos, resolução de sprite de Scovillain e variações de tamanho do campo selecionado; toque no tipo selecionado permite removê-lo.

### Análise de Equipe PvP e fortalecimento

- Nova tela Análise de Equipe PvP para avaliar três Pokémon e seus golpes, com confrontos favoráveis, ameaças e cobertura da equipe.
- Análise de equipe baseada nos confrontos publicados pelo PvPoke; combinações sem dados equivalentes ficam como desconhecidas, sem simulação completa de batalhas.
- Ícone próprio de análise de equipe no menu principal.
- Corrigidos custos de doces e poeira nos níveis iniciais e meios níveis da ferramenta de ajuste de nível na sugestão de nomes.

### Raids, OCR e manutenção

- Detalhes de raids com subtítulo Movimentos, menos legendas e atualização no topo, inclusive sem chefe identificado.
- Atualização do catálogo quando faltam opções para exibir os dados da raid.
- Melhor identificação de formas regionais a partir do catálogo atual, incluindo Voltorb de Hisui.
- Recuperação de nomes fragmentados de megas, como Mega Dragonite, com correspondência única e nível compatível.
- Identificação alternativa pelo PC exato do chefe, nível e catálogo atual, incluindo distinção entre Dialga e Forma Origem.
- Contagem dos símbolos usa a posição dos dígitos do PC; tolerância a ruídos externos de OCR sem alterar dígitos.
- Logs debug detalham coordenadas, nível, candidatos e motivos de rejeição para investigar falhas intermitentes.
- Ampliados testes automatizados de dados, sprites, backup, missões, filtros, equipes e identificação de raids; compatibilidade do Robolectric com JDK 17 ajustada.

- Preparação da release: 490 testes JVM/Robolectric sem falhas e lint release sem erros (43 avisos); APKs debug e release compilados sem a prévia Dinamax.

A prévia visual Dinamax do Squirtle não faz parte desta release. O roteiro completo de validação física do OCR continua pendente; as verificações pontuais não cobrem todos os aparelhos e cenários.

## 1.0.31 - 2026-10-14

Alterações acumuladas desde a release 1.0.30.

### Pokédex e família

- Detalhes passam de janela modal para tela própria, com abas Geral, Golpes e PvP centralizadas e atualização dos dados online pelo ícone superior direito.
- Atributos ATK, DEF e HP apresentados em arcos, sem moldura ou divisórias; tipos centralizados.
- PC por nível em ordem decrescente, com indicação de pesquisas, raids/ovos, raids com clima favorável, fortalecimento, PC máximo e Melhor Companheiro. Removida a explicação do bloco; clima favorável transferido para esse bloco.
- Fraquezas e resistências em cartões compactos de tamanho uniforme, porcentagem e um único ícone colorido de tipo.
- Família completa com sprites de Pokémon GO, requisitos de evolução, doces e megaenergia; caminhos compartilham os ancestrais e ramificações usam setas diagonais. Custos acima das setas e rolagem horizontal.
- Megaevoluções incluídas nas famílias e Gigamax apresentada como forma alternativa, com ligação distinta de evolução. Adicionado catálogo local de evoluções.
- Ícones de doces com cores da espécie, megaenergia com textura e cores da forma e novos ícones de clima.
- Golpes e recomendações PvP usam os cartões compartilhados de ataques; indicação de legado mantida no cartão e legendas introdutórias removidas.
- Confrontos favoráveis e principais ameaças mostram sprites e nomes em listas horizontais. Sombrosos recebem aura roxa transparente atrás do sprite e indicação entre parênteses; Palkia Origem recebe identificação abaixo do nome.
- Corrigidos vínculos dos sprites de Florges, Mimikyu, Keldeo comum e Keldeo Resolute. Consulta por identificador exato no catálogo de formas, sem substituir forma desconhecida pela comum.
- Título de informações adicionais simplificado; removidas as explicações finais desse bloco e de IV Master · família.
- Aba Fontes retirada da Pokédex. Nova tela com fontes e links de consulta em Treinador → Ajuda → Fontes de dados; botão no final da Ajuda.

### Tela principal e eventos

- Imagens oficiais de eventos e temporadas no topo da tela principal, com continuação em gradiente de cores abaixo da imagem.
- Seleção dos eventos do período por prioridade, dando destaque a eventos como Dia Comunitário e Gigamax sobre eventos gerais e temporada.
- Carrossel com gesto horizontal, indicadores discretos em barras e troca automática configurável em 5, 10, 15 ou 30 segundos.
- Pausa automática de 30 segundos após navegação manual e retorno ao evento prioritário ao retomar a tela.
- Consulta às notícias e temporadas oficiais com cache e reaproveitamento dos últimos dados quando uma fonte falha; proteção do cancelamento e tratamento separado das fontes.

### Ataques e dados do jogo

- Busca de ataques, legados e efeitos de aventura recolhível pelo ícone no topo, com painel compartilhado de filtros e Limpar à direita.
- Cartões padronizados com cor do tipo, métricas PvE/PvP, efeitos, ícones de clima e ícone de tipo à esquerda do nome.
- Efeitos de aventura preservam suas descrições sem repetir o nome do ataque; filtro dessa tela não oferece rápido/carregado.
- Vínculo dos ataques legados passa a usar o nome canônico, corrigindo cartões incompletos como Feixe Meteórico.
- Corrigidos agrupamentos de traduções de Golem e Florges, nomes canônicos de Lock On, Wild Charge, Super Power e Natures Madness, variantes de Weather Ball/Techno Blast e chave de Pawmot.
- Removidas duplicatas e associações incorretas de ataques legados, incluindo Evolução Dracônica de Exeggutor de Alola e entradas inválidas de Moltres, Raikou, Meloetta e Hippowdon. Corrigida a grafia de Wildbolt Storm.
- Formas de Necrozma nos efeitos de aventura identificadas em português como Juba Crepúsculo e Asas Alvorada.
- Teste de integridade assegura que os ataques legados mantidos encontram dados de combate. Essa verificação não certifica todo o histórico de exclusividade dos golpes.

### Navegação, filtros e raids

- Corrigida a faixa opaca sob o botão central e o menu inferior nas telas secundárias. Conteúdo pode passar pelo recorte, com espaço de rolagem para alcançar o final das listas.
- Filtros salvos de Pokémon e amigos na sobreposição extraídos para componente compartilhado, com identidade visual de raids/Max, cartões, estado vazio, rolagem e cópia que fecha o painel.
- Removidos texto de ajuda redundante no painel e botões de filtros salvos nas telas que já exibem a lista; instruções de uso disponíveis na Ajuda.
- Atualização do catálogo de raids pelo ícone superior quando o chefe não é identificado, incluindo a sobreposição, com estados de progresso e falha.
- Identidade inequívoca do chefe no catálogo atual prevalece sobre contagem visual incorreta de símbolos da raid, corrigindo casos como Yveltal; não usa raids antigas como comprovação.

### Manutenção e validação

- Preparação da release: 418 testes JVM/Robolectric aprovados; catálogos validados, testes instrumentados compilados e APK release gerado. Lint release sem erros, com 40 avisos.

- Novos testes de eventos, carrossel, evoluções, vínculos de ataques, sprites e identificação de raids; instrumentos de teste no aparelho para atualização de raids e estilos dos filtros.
- Atualizado o registro existente de verificações físicas; downloads de referência, arquivos temporários e bytecode Python excluídos da lista de arquivos para commit.
- Mantidas as pendências de validação completa do OCR no aparelho, de memória em uso prolongado e de política definitiva para o cache PvP. As conferências visuais desta versão não substituem os cenários físicos de OCR ainda pendentes.

## 1.0.30 - 2026-10-11

Alterações desde a release 1.0.29.

### OCR, revisão e cancelamento

- Extraídos leitores de barras de IV, nomes, números, atributos visuais e geometria; `OcrPokemonParser` permanece como coordenador.
- Redução da memória usada na importação por decodificação com amostragem e orientação EXIF; processamento de CPU interrompível fora da thread da interface e tratamento específico de timeout.
- Reaproveitada a medição da estrela de favorito; corrigida a classificação de barras JPEG incompletas como IV 15.
- Reconhecimento de “sC”/“5C” como ruído do prefixo PC limitado à linha completa na região superior.
- Importação abre a tela de progresso/revisão; referências do validador são reconhecidas e seu parser executa em segundo plano.
- Proteção da importação contra conclusões de solicitações antigas. `CurrentReadTask` centraliza o cancelamento da sobreposição e do validador, invalida a leitura antes de cancelar e impede que callbacks antigos alterem resultados, progresso, erros ou estado de uma nova leitura.

### Sobreposição

- Captura, gerenciamento das janelas e apresentação de resultados separados em `OverlayCaptureSession`, `OverlayWindowHost` e `OverlayResultsPresenter`.
- Liberação dos recursos de captura e das composições ao encerrar; correção do descarte indevido do bitmap quando o recorte devolve a imagem original.
- Removida a janela de progresso que cobria e escurecia a tela inteira. Aviso compacto centralizado com gradiente claro translúcido, borda suave, progresso e Cancelar; interação com o jogo preservada fora do cartão.

### Desempenho e coleção

- Cache LRU PvP ampliado de 16 para 36 tabelas por calculadora, provisoriamente; busca binária da posição preserva o primeiro lugar entre produtos empatados.
- Catálogos de ataques indexados por fonte, com traduções reutilizadas por idioma e carregamento de arquivos fora do bloqueio global.
- Ataques legados e efeitos de aventura carregados em segundo plano, com estados de carregamento e falha.
- Coleção pessoal armazena entradas individualmente e migra o antigo array na primeira edição; reutiliza registros já interpretados e serializa apenas os alterados. Observação pela interface e marcações em segundo plano. SharedPreferences ainda pode regravar o XML completo.

### Dados do jogo

- Revisão dos Dias Comunitários de 2026 até Zorua (10/10): Rillaboom/Planta Mortal, Ninetales/Bola de Energia, Ninetales de Alola/Água Gelada, Tinkaton/Martelo Gigatônico, Oinkologne/Tapa de Lama, Baxcalibur/Fúria de Glaive, Inteleon/Hidro Canhão, Thievul/Vento Congelante e Zoroark/Soco Enganador. Cinderace/Queimadura Explosiva já constava no catálogo.
- Listas de golpes de Ninetales e Zoroark separadas por forma regional, com ajuste dos golpes de Zoroark de Hisui; catálogos de ataques atuais e legados sincronizados.
- Adicionados dados PvE/PvP e traduções PT-BR/EN/ES de Martelo Gigatônico e Fúria de Glaive; rótulos de Água Gelada atualizados. A revisão não certifica todos os legados históricos de outros eventos.

### Build, testes e documentação

- Comparação de versões extraída para `ReleaseVersion`; versionCode calculado por segundos decorridos desde uma referência UTC, acima da faixa dos códigos anteriores. Versão padrão atualizada para 1.0.30.
- Workflows passam a executar testes unitários e Lint; release exige validação dos catálogos, testes e Lint release antes da assinatura e publicação, sem upload de relatórios de testes.
- Novos testes de cancelamento concorrente, cache PvP, apresentação da sobreposição, propriedade de bitmap, versões e ataques comunitários; ampliadas regressões de importação, EXIF, barras de IV, coleção e repositório de ataques.
- Adicionado instrumento de diagnóstico no aparelho e amostra JPEG com EXIF para regressão. README atualizado; documentação dos componentes OCR/overlay, cache e roteiro de validação física incluída.
- Ajuste de formatação no código dos detalhes de eventos do calendário.

### Validação física e pendências

- Em builds debug 1.0.29 anteriores à publicação: 14/14 amostras, leitura de Ponyta, importação ampliada e EXIF, persistência da coleção, cancelamento seguido de nova leitura e interação com o jogo durante OCR verificados no aparelho.
- O comportamento não modal foi verificado antes do ajuste visual final para o centro; a conferência visual desse ajuste permanece pendente.
- Nova política de cache, uso prolongado de memória, formas regionais, fundos especiais e timeout forçado no aparelho continuam pendentes. Leitura visual do arco de nível permanece desativada.

## 1.0.29 - 2026-10-09

- Pokédex em grade com blocos individuais, sprites usados pela coleção e filtros recolhidos em um botão.
- Coleções disponíveis em sobreposição, com painel ajustável pela barra superior, título centralizado e interação com o jogo fora do painel.
- Atalhos da sobreposição reorganizáveis por arrastar no perfil do treinador; menu em arcos respeitando a ordem escolhida.
- Leitura durante a captura para consultar combinações possíveis de IV pelo PC, com filtro de nível e campos compactos.
- Leitura das barras de IV otimizada: pixels em lote, menos buscas redundantes e reaproveitamento de geometria validado em cada captura.
- OCR reutilizado entre leituras, cálculo de ranking PvP otimizado e diagnósticos de tempo por etapa.
- Melhor identificação de nome e família sem avaliação aberta, usando o título e os doces, inclusive rótulos separados em linhas.
- PC, PS e nível conhecido ajudam a preencher IVs ausentes apenas quando as combinações compatíveis concordam em cada atributo.
- Corrigida a persistência de XXL ao trocar de Pokémon; leituras com PC ou IVs conflitantes não são mescladas.
- Raids: identificação e consulta ao cache mais rápidas, seleção pelo número de símbolos, opções sem duplicatas e tratamento de nomes parcialmente encobertos.
- Atualizar raid ou batalha Max também tenta carregar imagens ausentes do chefe e das sugestões. Detalhes Max com ícone de atualizar e menos textos explicativos.
- IV Master: 29 combinações corrigidas em 21 registros e 16 regras regionais adicionadas conforme a revisão aprovada; convenção 67% preservada.

Observações

- Combinações de captura são possibilidades, não IVs confirmados. Valores ambíguos não são preenchidos automaticamente.
- A leitura visual do arco de nível ainda depende de calibração e não foi ativada nesta versão.

## 1.0.28 - 2026-10-02

Versão 1.0.28

- Nova ficha da Pokédex com atributos por forma, golpes, PC, fraquezas, rankings PvPoke e fontes de consulta.
- Novo visual dos ataques rápidos e carregados, com dados PvE/PvP, ícones próprios e detalhes de efeitos especiais.
- Coleção pessoal por espécie e forma, marcação de capturas, ordenação personalizada e novos ícones.
- Fundos especiais organizados por categoria, incluindo aniversário, Dia da Comunidade e Mega Evolução.
- Calendário semanal com navegação fluida, temporada e Passe GO antes dos eventos e ajustes nos detalhes da sobreposição.
- Correções nos filtros de Pokémon/pessoas e na ordem do texto copiado.
- Melhorias no cache das sugestões de raids e estimativa de treinadores baseada em Pokémon de nível 20.
- Revisão de atributos, formas regionais e elegibilidade de evoluções para as ligas PvP.
- Melhorias na leitura de IVs, nomes, ataques, tamanho e experiência do treinador.

## 1.0.27 - 2026-09-23

Versão 1.0.27

- Novo visual da tela inicial, com blocos compactos, saudação, data e temperatura, além da localização automática para o clima.
- Calendário integrado ao app com navegação por mês e semana, eventos por dia e correções no carregamento.
- Raids reorganizadas por nível, histórico ampliado e classificação de Mega e Super Mega revisada.
- Sugestões de batalhas Max com atacantes, escudo e cura, golpes possíveis, fraquezas, PC de captura e acesso ao Pokebattler.
- Atualização da tabela IV Master e correções na comparação de formas regionais, Samurott de Hisui e formas de Lycanroc.
- Melhorias na leitura de IV, tamanho XXS/XXL e identificação dos padrões de Vivillon em diferentes proporções de tela.
- Padronização glass da janela de nomes sugeridos, dos campos e modais de Forma Única, com títulos centralizados.
- Ajustes nos controles de PVP, fundo especial de Mega Evolução e apresentação do IV Master.
- Ícone ativo de sobreposição menor, menu por pressão prolongada mais rápido e correção da faixa inferior nas telas.
- Ajuda e apresentação inicial reorganizadas na tela de ajuda.

## 1.0.26 - 2026-09-20

Esta versão reúne melhorias de navegação, coleções, leitura de telas e sugestões de batalha:

- Raids mais precisas: identificação pelo nome parcial do chefe e pelos ícones de dificuldade, alternativas do mesmo nível quando o chefe não é reconhecido e atualização individual dos dados da raid.
- Organização de raids atuais e salvas por categoria, incluindo Dinamax e Gigamax, com nomes traduzidos e ordenação pela Pokédex Nacional.
- Nova tela de sugestões para batalhas Dinamax e Gigamax, seguindo o padrão visual das sugestões de raid.
- Pokédex simplificada: todos os Pokémon aparecem quando nenhum tipo está selecionado e as formas alternativas agora exibem seus próprios sprites.
- Coleções aprimoradas com carregamento progressivo, ordem da Pokédex, imagens de brilhantes, aba de Pokémon com traje e visualização ampliada dos fundos especiais.
- Revisão de Pokémon mais compacta, com IV Master e forma na mesma linha, melhores combinações de IV sempre disponíveis e seletores visuais para forma e fundo especial.
- Melhor identificação da equipe do Treinador e de fundos especiais durante a leitura de tela.
- Ajuda e atualização reunidas no menu do Treinador, introdução das principais funções na primeira abertura e terminologia padronizada para “modo sobreposição”.
- Telas de ataques legados e efeitos de aventura atualizadas com busca, tipos, dano e energia; ajustes de espaçamento e remoção dos elementos Unown restantes.
- Catálogo de espécies corrigido para usar os números oficiais da Pokédex Nacional, com ajustes adicionais nos dados de IV Master.
