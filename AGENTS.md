# Pendência de validação no aparelho

O usuário pediu para lembrar de validar o OCR quando o aparelho Android for conectado.
Em uma próxima sessão, ao detectar um aparelho disponível ou receber a informação de que ele foi conectado, retome essa pendência e lembre o usuário.
O roteiro e o estado da validação estão em `docs/validacao-ocr-aparelho.md`.
Testes JVM/Robolectric e capturas salvas não substituem essa validação física; registre apenas as verificações realmente executadas.

# Instalação para teste

O usuário pediu para, sempre que possível, instalar no celular as alterações validadas. Após compilar e executar as verificações adequadas, confira a conexão ADB e instale o APK atualizado no aparelho disponível, preservando os dados. Não é necessário pedir novamente autorização para essa instalação. Informe separadamente a instalação e os testes realmente executados; se não houver aparelho disponível, registre essa limitação na resposta.
