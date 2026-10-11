# Histórico de alterações

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
