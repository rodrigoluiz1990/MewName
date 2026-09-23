# Leitura de IV e tamanho — Redmi Note 9 Pro Max

Capturas de diagnóstico: 1080 × 2400, com avaliação aberta.

- Psyduck: esperado 12/15/15. O reconhecimento do rótulo PS aceitava também o início de "Psyduck" no diálogo inferior e sobrescrevia a posição da barra de HP. A busca agora exige PS/HP como palavra inteira.
- Scatterbug: esperado XXS, OCR original XS. Uma releitura ampliada e localizada do selo de altura recupera XXS. O apelido não é usado para essa confirmação. A segunda leitura só ocorre para selos pequenos XS/XL na região da altura, mantém a leitura original se falhar e rejeita resultados conflitantes.

Validação:
- OCR completo no emulador Android com as duas capturas reais: Psyduck 12/15/15 e NORMAL; Scatterbug 10/10/10 e XXS.
- Regressões das barras a 1080 × 2400 e capturas redimensionadas para 1220 × 2712.
- Testes de confirmação de selo, descarte de apelidos/doces e regressões existentes de imagem/reconhecimento.
- A validação em 1220 × 2712 usa imagens redimensionadas; não substitui uma conferência no aparelho anterior.

O Xiaomi bloqueia instalação e interação por ADB. A assinatura pública do app instalado difere da assinatura debug local. Uma atualização do app existente deve ser gerada pelo fluxo de release com a assinatura original; não é necessário remover a instalação existente.

Diagnóstico Android reutilizável: PhoneOcrInstrumentation lê files/capture.png do pacote de teste e grava files/ocr.json com as linhas e caixas do OCR.