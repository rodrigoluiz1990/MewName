Versão 1.0.30

- Leitura na sobreposição com aviso centralizado, translúcido, no estilo glassmorphism, progresso e botão Cancelar; removidos o fundo cinza e o bloqueio de interação com o jogo fora do cartão.
- Cancelamento protegido contra resultados, erros, progresso e finalização de leituras antigas, permitindo iniciar uma nova análise com segurança.
- OCR e sobreposição divididos em componentes menores para facilitar manutenção e liberação de recursos.
- Melhorias na importação de imagens grandes e com rotação EXIF, processamento em segundo plano e tratamento de tempo limite.
- Correções na leitura de barras de IV em JPEG, reconhecimento do prefixo PC e navegação para a revisão após importar imagens.
- Cache PvP ampliado temporariamente para 36 tabelas por calculadora e busca de posição no ranking por pesquisa binária.
- Catálogo de ataques com índices reutilizados; consultas de ataques legados e efeitos de aventura carregadas em segundo plano.
- Coleção pessoal com reaproveitamento dos registros interpretados, migração automática do armazenamento anterior e serialização apenas das entradas alteradas.
- Ataques legados dos Dias Comunitários de 2026 atualizados até Zorua, em 10/10/2026, incluindo Soco Enganador para Zoroark e separação das listas por forma regional.
- Estatísticas PvE/PvP e traduções de Martelo Gigatônico e Fúria de Glaive adicionadas; tradução de Água Gelada atualizada.
- Correções na comparação de versões e geração do versionCode; publicação passa a exigir testes unitários, validação dos catálogos e Lint release.

Observações

- O limite de 36 tabelas é temporário; uma nova política de cache continua pendente.
- A validação física realizada é parcial. Formas regionais, fundos especiais, timeout forçado, uso prolongado de memória e conferência visual do cartão centralizado continuam pendentes.
- A leitura visual do arco de nível permanece desativada.
