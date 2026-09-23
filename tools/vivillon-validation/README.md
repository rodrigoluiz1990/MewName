# Validacao dos icones Vivillon

Use esta pasta para validar screenshots reais de Scatterbug, Spewpa ou Vivillon pelo icone do botao **Evoluir**.

## Fluxo

1. Salve as screenshots completas em `tools/vivillon-validation/screenshots/`.
2. Preencha `samples.csv` com o arquivo e o padrao esperado.
3. Rode:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\extract-vivillon-icon-samples.ps1
```

O script cria crops em:

```text
tools/vivillon-validation/crops/
```

Para transformar um crop validado em referencia do app, copie o melhor crop para:

```text
app/src/main/assets/unique_pokemon_refs/vivillon/
```

Use o nome do padrao, por exemplo `marine-real-01.png`, `jungle-real-01.png` ou `sandstorm-real-01.png`.

## Padroes aceitos

`archipelago`, `continental`, `elegant`, `fancy`, `garden`, `high_plains`, `icy_snow`, `jungle`, `marine`, `meadow`, `modern`, `monsoon`, `ocean`, `pokeball`, `polar`, `river`, `sandstorm`, `savanna`, `sun`, `tundra`.

## Observacoes

- As imagens coladas no chat nao ficam disponiveis como arquivos no workspace. Elas precisam ser salvas na pasta `screenshots`.
- O crop padrao mira o selo circular do Vivillon no botao `EVOLUIR`, nao o Scatterbug/Spewpa na tela.
- Se o crop cortar o icone, ajuste `crop_left`, `crop_top`, `crop_right`, `crop_bottom` em `samples.csv`.

## Calibração com dois aparelhos

A localização do selo de evolução agora usa a borda circular clara junto ao botão verde, procurando entre 54% e 93% da altura. Não depende da posição fixa usada pelo script de extração antigo. A comparação prioriza a cor das asas; o segundo candidato de confiança deve ser de outro padrão, não outra imagem do mesmo padrão. Selos ausentes ou ambíguos retornam sem identificação.

Capturas reais de regressão em app/src/test/resources/vivillon/:
- phone-a.png: aparelho 2412DPC0AG, 1220 × 2712, Scatterbug PC 202. Resultado Jardim, consistente com a referência 05-jardim.jpg e com a indicação do usuário.
- phone-b.png: Redmi Note 9 Pro Max, 1080 × 2400, Scatterbug PC 312. Resultado Marinho, confirmado pelo usuário.

VivillonCalibrationTest verifica as duas capturas para Scatterbug e Spewpa, selos de Neve congelada, Jardim, Marinho, Deserto e Solar em duas resoluções/alturas, ausência de selo e tela de avaliação sem o botão de evolução. Os selos sintéticos usam as referências fornecidas e verificam o recorte/comparação; não substituem capturas independentes de todos os padrões.

As 56 referências existentes continuam disponíveis. A comparação da tela de Vivillon já evoluído permanece com a métrica anterior. Para validar outros padrões em aparelhos reais, adicionar capturas com o selo visível e confirmar o padrão esperado.