# Organização da sobreposição

`OverlayService` coordena a bolha, os fluxos de OCR, a navegação e o ciclo de vida Android. As operações de plataforma e a apresentação dos nomes sugeridos ficam em componentes internos:

- `OverlayCaptureSession`: possui `MediaProjection`, callback, `VirtualDisplay` e `ImageReader`; inicializa e libera os recursos e converte o frame em bitmap. O serviço continua responsável por ocultar a bolha, agendar a captura e iniciar o OCR.
- `OverlayWindowHost`: adiciona, atualiza e remove janelas; descarta a composição antes da remoção e encaminha a perda de permissão ao serviço. O conjunto de janelas rastreadas continua compartilhado com o encerramento do serviço.
- `OverlayResultsPresenter`: constrói o painel de nomes sugeridos e seus parâmetros de janela. Copiar, fechar e revisar são callbacks; o componente não possui o ciclo de vida do serviço.

O encerramento cancela tarefas e callbacks pendentes, remove janelas e fecha a sessão de captura. A sessão ignora callbacks atrasados de projeções já fechadas e remove seu callback antes de interromper a projeção deliberadamente.

Ao copiar frames, o bitmap retornado permanece válido mesmo quando o recorte retorna o próprio bitmap original. Quando existe espaço extra ao fim das linhas, ele é removido antes do OCR.

Os testes de encerramento, bolha e feedback continuam exercitando a integração com o serviço. A validação da captura real, permissões revogadas pelo Android e sobreposição no aparelho permanece no roteiro `validacao-ocr-aparelho.md`.

As demais telas e a exportação de diagnósticos continuam no serviço, podendo ser extraídas em etapas posteriores.
