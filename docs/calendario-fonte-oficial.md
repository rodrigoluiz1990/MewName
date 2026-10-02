# Calendário: análise da fonte oficial (sem integração)

Consulta em 27/09/2026. A fonte atual do app continua sendo Laboratório do Sam.

## Observações verificadas

- https://pokemongo.com/pt-BR/events/september-2026 retornou uma solicitação de login. Isso não comprova que todo o calendário seja privado, mas impede tratar essa resposta como um feed público completo.
- https://pokemongo.com/pt-BR/news é público e lista publicações de eventos.
- https://pokemongo.com/pt-BR/news/communityday-october-2026-zorua contém título, imagem e datas/horários no texto. A imagem da publicação é servida por lh3.googleusercontent.com.
- A publicação distingue o intervalo principal do evento de janelas adicionais de bônus. Extrair qualquer data da página indiscriminadamente produziria eventos incorretos.
- Não foi confirmada nesta análise uma API pública documentada de calendário.

## Proposta para uma implementação futura

Manter um catálogo intermediário revisável com ID estável, categoria, título localizado, início/fim, regra de fuso horário, URL da publicação, URL da imagem e data da última revisão. Associar os eventos atuais à publicação oficial, em vez de tentar deduplicar apenas pelo título.

Um processo fora do app poderia consultar publicações públicas e preparar sugestões de atualização; datas e intervalos ambíguos precisam de revisão. Horários locais devem continuar locais e horários globais precisam de fuso explícito. A ausência de uma publicação numa consulta não deve apagar eventos já armazenados.

Para imagens, armazenar a URL de origem e usar carregamento assíncrono e cache com uma imagem alternativa em caso de falha. Selecionar o banner editorial, evitando sprites e imagens de bônus misturadas no artigo. A disponibilidade pública do arquivo não estabelece autorização de redistribuição; confirmar as condições aplicáveis antes de empacotar ou espelhar as imagens.

Esta é uma proposta técnica, não uma integração implementada. Nenhum login, endpoint privado ou troca da fonte atual foi adicionado.

## Interface

Semana é a visualização inicial. No modo semanal, a faixa de dias rola continuamente sem alterar a seleção ou os eventos. Somente tocar em uma data seleciona outro dia. As setas semanais deslocam a faixa sem selecionar uma data; Hoje retorna à semana atual e seleciona hoje. No modo mensal, deslizar continua avançando/voltando o mês. As datas não usam bolinhas de eventos. O atalho Calendário do menu ativo da sobreposição abre uma lista dos eventos do dia. Ele pode ser habilitado na configuração de atalhos existente.

AppModalHeader estabelece o componente reutilizável para título centralizado e fechamento X no canto superior direito. Aplicado aos detalhes do calendário; os demais modais legados ainda precisam adotar o componente ao serem revisados.

## Validação

Compilação debug e 8 testes de calendário aprovados. Teste no celular verificou semana inicial, lista compacta e abertura/fechamento dos detalhes. Navegação mensal conferida diretamente na interface: Setembro → Outubro → Setembro por gestos, sem setas. Versão instalada no aparelho conectado.

Os detalhes na sobreposição são uma camada na mesma janela, sobre o calendário ainda composto. A lista e sua posição de rolagem são preservadas ao abrir e fechar os detalhes.
