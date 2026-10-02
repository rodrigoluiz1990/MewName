# Calibração do Total de PE — dois aparelhos

Capturas reais em 26/09/2026:

| Aparelho | Resolução | Treinador | Total de PE |
|---|---|---|---|
| 2412DPC0AG | 1220×2712 | RodWhite001 | 243.850.350 |
| Redmi Note 9 Pro Max | 1080×2400 | RodSarah | 236.013.402 |

O OCR foi executado com ML Kit no 2412DPC0AG usando as imagens originais de ambos os aparelhos. Os resultados de OCR e coordenadas estão nas fixtures trainer/xp-rodin.json e trainer/xp-redmi.json. O parser atualizado foi validado por testes usando esses resultados, sem depender de nomes ou valores fixados no código de produção.

Correções:
- Aceitar o rótulo “PE Total de PE” (ícone e título unidos pelo OCR).
- Encontrar o valor em outro bloco OCR, alinhado à direita do título, com coordenadas proporcionais à tela.
- Não interpretar textos de outras regiões como nome quando há coordenadas disponíveis. Na imagem rolada do Redmi, “COMPANHEIRO” era selecionado pelo fallback textual.
- Permitir leitura em duas etapas: nome no topo e, em até dois minutos, Total de PE na parte inferior. A identificação recente fica somente na memória, vem de uma leitura e deve coincidir com o perfil atual. Nome digitado manualmente não inicia esse vínculo. Expirado o prazo, é solicitada nova leitura do topo.

A leitura parcial não cria identidade nova. O total permanece sem edição manual. Frações de progresso para o próximo nível não são usadas como Total de PE. O ranking online continua adiado.

Validação: TrainerExperienceTest (capturas dos dois celulares, leitura parcial, expiração e alteração manual de identidade), TrainerProfileParserTest, TrainerScreensTest e ProfileReadDiagnosticsTest. APK debug compilado; esta calibração não foi instalada nos aparelhos durante a validação.