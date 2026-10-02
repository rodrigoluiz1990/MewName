# Dados e atualização de raids

Verificado em 30/09/2026.

- Base embutida: `assets/raids/metadata.json` contém atributos, tipos e golpes; `assets/catalogs/raid_history.json` mantém o histórico local. Esses arquivos mudam com atualizações do app.
- Cache privado: `files/raid-counters-v1/`. `catalog.json` guarda o catálogo baixado; `metadata.json` guarda atributos/golpes atualizados. Cada relatório usa `{chefe}_{categoria}_{nivel}.json`.
- Relatórios: chefe, categoria, nível dos atacantes, data da consulta, golpes possíveis e até 12 counters (espécie, golpes e Estimator). O nível 20 serve à estimativa de pessoas; 40/50 continuam servindo à lista de Pokémon sugeridos. Os arquivos são independentes.
- Pessoas: consulta própria de nível 20, sem clima, mantendo os filtros da simulação do app. Faixa do menor ao maior Estimator arredondado para cima. Não é conversão dos resultados de nível 40/50. Falta de dados não usa outro nível como substituto. A fase de escudos continua sem estimativa suportada.
- Ao abrir o catálogo: primeiro lê o cache. Baixa se não há catálogo ou se o usuário pede atualização. Atualização manual do catálogo também tenta atualizar os metadados; não recalcula todos os chefes.
- Ao abrir detalhes no app: primeiro lê o relatório salvo para chefe/categoria/nível. Se ausente, busca online. Atualizar detalhes busca novamente o relatório selecionado 40/50 e a estimativa fixa 20.
- Na sobreposição: os counters 40/50 priorizam os salvos e dependem de atualização manual quando ausentes; a estimativa nova de nível 20 também busca online na primeira consulta quando ainda não salva.
- Não há prazo automático de expiração dos relatórios ou do catálogo de raids. A data de consulta é registrada, mas não força atualização por idade.
- Falhas de rede preservam os arquivos salvos e permitem continuar exibindo-os. A estimativa de pessoas tem carregamento e falha independentes dos counters. Gravação via AtomicFile evita substituir um arquivo íntegro por uma gravação incompleta.
- Arquivos sobrevivem a reinício e atualização normal do app. Limpar dados ou desinstalar remove o armazenamento privado local (eventual restauração de backup do sistema é um mecanismo separado).

Esta descrição cobre raids convencionais/Mega em `RaidCounterRepository` e `RaidDetailsScreen`; batalhas Max possuem repositório próprio.
