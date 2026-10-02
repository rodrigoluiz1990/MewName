# Coleção pessoal

Acesso: Coleções. As abas Brilhante, Sortudo, Sombroso/Purificado, Tamanho, IV, PVP e Fundo especial listam Pokémon e formas para marcação direta. Use a busca e o filtro Mostrar somente coletados. No menu do Treinador, abra Coleção pessoal (antes de Cálculo PvP) para escolher os grupos visíveis na revisão. Todos os 19 grupos começam disponíveis. Ocultar um grupo preserva as marcações existentes.

Grupos: brilhantes, sortudos, sombrosos, purificados, IV, 100%, 98%, 96%, 93%, 91%, 67%, 0%, XXL, XXS, fundo especial e quatro ligas PVP (CP/Copinha, GL, UL, ML).

Na tela de nomes sugeridos, o bloco Coleção pessoal mostra o Pokémon/forma atualmente selecionado. Os grupos marcados indicam que já existe uma captura registrada. Tocar alterna o registro imediatamente; não depende de copiar um nome. A marcação não é inferida do OCR, de uma combinação de IV ou do ranking: é uma confirmação manual, inclusive para PVP.

Na tela principal da coleção, cada linha permite marcar os grupos da aba. Unown, Vivillon/Scatterbug/Spewpa e outras formas únicas são listados por subtipo. A aba IV contém o marcador geral e os percentuais, enquanto a aba PVP mantém as quatro ligas separadas. Desmarcar remove o registro daquele grupo. O catálogo anterior de brilhantes/trajes/fundos continua acessível pelo botão de catálogo; seus registros permanecem preservados.

Identidade: espécie canônica + forma no nome + forma única quando aplicável. Aliases de Hisui apontam para o mesmo registro, mas Hisui e normal não se misturam. Letras de Unown e padrões de Vivillon são separados. Cada espécie da evolução tem seu próprio registro. O grupo Fundo especial registra a posse de algum fundo, sem distinguir cada estampa; os demais grupos também indicam presença, não quantidade nem cruzamento de características.

Persistência local: SharedPreferences personal_collection_v1. IDs de grupos são estáveis e independentes da ordem visual. A tela principal e a sobreposição observam o mesmo armazenamento. As coleções antigas de brilhantes/trajes/fundos permanecem independentes e não são importadas automaticamente.

Validação: PersonalCollectionTest cobre aliases/formas regionais, persistência entre leituras, grupos independentes, ocultar sem apagar, desfazer marcações, ausência de inclusão automática e separação de formas únicas.
## Exibição nos nomes sugeridos

O bloco fica oculto por padrão, inclusive para quem já tem marcações salvas. Ative Treinador → Coleção pessoal → Mostrar coleção nos nomes sugeridos para exibir a linha de ícones acima das abas. Desativar preserva grupos e capturas; a tela Coleções continua disponível. Se nenhum grupo estiver selecionado, a linha também fica oculta.

## Controles compactos e transformações (26/09/2026)

A linha nos nomes sugeridos usa botões de 34 dp, imagens limitadas a 22 dp e rolagem horizontal. Decodificação e recorte das margens transparentes ocorrem fora da thread da interface. Os símbolos de GL/UL/ML, Mega, Dinamax e Gigamax ficam empacotados no APK. XXS/XXL usam os esquadros originais extraídos do APK 0.429.1; Copinha usa o emblema GlobalLeaguePennant solicitado pelo usuário. Ver assets/collection/SOURCES.md.

O grupo genérico MASTER foi removido. Dados antigos desse grupo são ignorados na leitura; os percentuais e demais marcações permanecem. O controle de seleção no treinador continua determinando quais ícones aparecem nos nomes sugeridos. Grupos novos podem ser ativados ali; preferências existentes não são substituídas.

As abas Dinamax, Gigamax e Mega têm marcação independente. Mega/Gigamax filtram espécies e formas exatas tanto na lista quanto no bloco de nomes sugeridos; a gravação também rejeita marcações incompatíveis. A lista CollectionTransformations foi extraída do Game Master consultado na auditoria de Hisui (24/09/2026), usando tempEvoOverrides com MEGA e breadOverrides com BREAD_DOUGH_MODE, cruzados com nomes/aliases locais. Representa formas configuradas no catálogo, não uma agenda de raids ou garantia de disponibilidade atual para captura. Reversões primais não são tratadas como Mega. Dinamax permanece um grupo de marcação manual geral.

Fonte dos metadados: https://github.com/PokeMiners/game_masters/blob/master/latest/latest.json . Quando novas formas forem adicionadas, atualizar o cruzamento de nomes e os testes. Não expandir compatibilidade automaticamente para toda a família evolutiva ou para formas regionais.
## Abas e imagens por categoria

A tela usa duas abas principais: Pokémon e Fundos especiais. Os tipos de coleção são subabas de Pokémon. Fundos especiais exibe o catálogo de imagens locais, com pesquisa, filtro de coletados, marcação e prévia; as chaves antigas background|arquivo continuam preservadas. O botão para o catálogo legado e os textos explicativos foram retirados dessa tela.

Brilhantes usam o sprite shiny da espécie/forma; sortudos, tamanho, IV, PVP e demais categorias comuns usam o sprite padrão sem traje. As espécies comuns usam PokeAPI official-artwork para evitar trajes indevidamente associados ao ID base no feed de raids. Formas regionais e únicas continuam resolvidas separadamente. Mega e Gigamax usam os sprites transformados; variantes Mega X/Y são exibidas juntas, mantendo a marcação por espécie e categoria. Chesnaught, Delphox e Greninja Mega usam os IDs PokeAPI 10292, 10293 e 10294, conferidos pela API e pela existência dos arquivos. Fontes: https://pokeapi.co/api/v2/pokemon/chesnaught-mega , https://pokeapi.co/api/v2/pokemon/delphox-mega e https://pokeapi.co/api/v2/pokemon/greninja-mega .

A resolução de URLs ocorre em Dispatchers.IO; Coil carrega as imagens assincronamente, com cache e tamanho limitado. A troca de categoria recria o estado da imagem para não mostrar o sprite da aba anterior. Testes cobrem padrão sem traje, shiny, formas regionais, compatibilidade e cobertura dos sprites de todas as Megas/Gigamax da lista local.

## Ordem dos grupos

Em Treinador > Cole??o pessoal, segurar um bloco e arrast?-lo muda sua posi??o. A lista rola automaticamente junto ?s bordas durante o gesto. Soltar salva a ordem; cancelar o gesto restaura a ?ltima ordem salva. A??es de acessibilidade permitem mover para cima/baixo.

A ordem ? persistida como lista de IDs em `personal_collection_v1/group_order` e usada tamb?m na faixa de ?cones de nomes sugeridos. Grupos ocultos conservam sua posi??o e suas capturas. IDs desconhecidos/duplicados s?o ignorados e grupos novos s?o adicionados ao final; instala??es sem ordem salva mant?m a ordem original.

Valida??o: assembleDebug e PersonalCollectionTest (incluindo persist?ncia, reordena??es consecutivas, grupos ocultos e recupera??o de configura??o inv?lida). O gesto ainda precisa de confer?ncia visual no aparelho.
