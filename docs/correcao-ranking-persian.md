# Ranking riscado na Copinha

Caso observado no aparelho 2412DPC0AG: Meowth PC 307, n?vel 17, IV 0/14/12. Persian teria 737 PC; mant?m ranking te?rico 4, por?m ineleg?vel por ultrapassar 500 PC.

O OCR passou a enviar esp?cie, n?vel e PC ao c?lculo. Mesmo com a sele??o autom?tica j? evitando Persian, a captura continuava sem risco vis?vel. CompactSelectableField agora desenha uma linha expl?cita sobre os limites dos caracteres quando struckThrough ? verdadeiro, em vez de depender da decora??o tipogr?fica. A sem?ntica tamb?m informa a inelegibilidade.

Valida??o: assembleDebug e PersianEligibilityTest passaram. RankStrikeInstrumentation executado no celular com o componente real em estilo glass: Persian rank=4/eligible=false, risco confirmado na captura; controle 13 eleg?vel sem risco. Esse teste valida o componente renderizado, n?o a captura OCR completa. APK instalado com adb install -r, preservando os dados.
