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
