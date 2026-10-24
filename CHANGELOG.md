## Não lançado

### 🇧🇷 Gerenciamento de conexão RFCOMM
- Adicionado cancelamento explícito da descoberta pelo canal nativo, com conclusão da busca pendente.
- Adicionados limite de tempo para conexão e proteção contra substituição de uma sessão ativa.
- Corrigidos o fechamento do socket e o tratamento de falhas de conexão e escrita.
- A escrita agora falha quando não existe uma conexão ativa, em vez de informar sucesso sem enviar dados.
- Adicionada notificação nativa de desconexão para atualização do estado no aplicativo.
- Mantido o suporte a apenas uma conexão ativa; conexões simultâneas ainda não são suportadas.

## 1.1.3

### 🇧🇷 Correções de compatibilidade com Android
- Corrigida a verificação de permissões de descoberta conforme a versão do Android: localização precisa até o Android 11; permissões de varredura e conexão Bluetooth no Android 12 ou superior.
- Corrigida a leitura do dispositivo nos eventos de descoberta em versões anteriores ao Android 13.
- Ajustado o registro do receptor de eventos Bluetooth no Android 13 ou superior, com indicação explícita de exportação.
- Cancelada a descoberta anterior antes do registro do novo receptor.
- Adicionada a declaração de que a varredura Bluetooth não é utilizada para determinar a localização (`neverForLocation`).
- Mantida a compatibilidade com a API pública Dart existente.

## 1.1.2

### 🇺🇸 Minor fixes
- Minor improvements to package description and documentation, translation of the documentation into English

### 🇧🇷 Correções menores
- Pequenas melhorias na descrição do pacote e documentação, tradução da documentação para o inglês

## 1.1.1

### 🇧🇷 Correções menores
- Pequenas melhorias na descrição do pacote e documentação.

### 🇺🇸 Minor fixes
- Minor improvements to package description and documentation

## 1.1.0

### 🇧🇷 Novidades
- Suporte a **UUID customizado** no método `connect()`.
- Adicionado parâmetro **`timeoutMs`** em `connect()` para controlar tempo de espera da conexão.
- Novo método **`readLine(delimiter)`** com buffer interno para leitura por linha.
- Buffer de leitura é limpo automaticamente ao desconectar.
- Melhorias internas na lógica de leitura e tratamento de exceções para evitar travamentos.

### 🇺🇸 What's New
- Added support for **custom UUID** in the `connect()` method.
- Added **`timeoutMs`** parameter to `connect()` to control connection timeout.
- New **`readLine(delimiter)`** method with internal buffer for line-based reading.
- Read buffer is now automatically cleared on disconnect.
- Internal improvements to reading logic and exception handling to prevent freezes.



## 1.0.1

### 🇧🇷 Correções menores
- Pequenas melhorias na descrição do pacote e documentação.

### 🇺🇸 Minor fixes
- Minor improvements to package description and documentation

## 1.0.0

### 🇧🇷 Versão inicial
- Primeira versão pública do plugin.
- Suporte completo ao Bluetooth Clássico (Serial RFCOMM) no Android.
- Permite listar dispositivos pareados, buscar novos dispositivos (scan), conectar, enviar e receber dados.
- Gerenciamento automático de permissões em tempo de execução.
- Exemplo Flutter incluso com leitura contínua e suporte a delimitadores de linha.

### 🇺🇸 Initial release
- First public release of the plugin.
- Full support for Classic Bluetooth (Serial RFCOMM) on Android.
- Allows listing paired devices, scanning, connecting, sending and receiving data.
- Automatic runtime permission management.
- Includes example Flutter app with continuous read and line-ending selection.
