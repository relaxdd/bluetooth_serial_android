## 1.1.4

### 🇧🇷 Gerenciamento de conexão RFCOMM
- Adicionado cancelamento explícito da descoberta pelo canal nativo, com conclusão da busca pendente.
- Adicionados limite de tempo para conexão e proteção contra substituição de uma sessão ativa.
- Corrigidos o fechamento do socket e o tratamento de falhas de conexão e escrita.
- A escrita agora falha quando não existe uma conexão ativa, em vez de informar sucesso sem enviar dados.
- Adicionada notificação nativa de desconexão para atualização do estado no aplicativo.
- Mantido o suporte a apenas uma conexão ativa; conexões simultâneas ainda não são suportadas.

### 🇧🇷 Leitura com tempo limite e cancelamento
- Adicionado o parâmetro nomeado opcional `timeoutMs` a `read()`, com valor padrão de 200 ms e independente do tempo limite de conexão. No Android, o valor é limitado ao intervalo de 1 a 5.000 ms; o esgotamento do prazo retorna `null`.
- Adicionado `cancelRead()` à API Dart, à interface de plataforma e ao canal nativo. O cancelamento conclui a leitura bruta pendente com `null`, sem desconectar o dispositivo.
- Adicionada proteção contra leituras brutas simultâneas (`READ_BUSY`) e retorno explícito de erro quando não há conexão ativa (`NOT_CONNECTED`).
- Adicionadas verificação da identidade do socket e do fluxo de entrada, além de sincronização com um contador de geração, para impedir que uma leitura cancelada consuma dados de uma nova operação.
- O encerramento da conexão agora cancela a leitura bruta pendente. As respostas de `read()` são entregues pela thread principal, sem depender de uma Activity anexada.
- Limitada cada leitura ao tamanho do buffer de 1.024 bytes e utilizado um relógio monotônico para medir o prazo, evitando leituras maiores que o buffer e interferência de alterações no relógio do sistema.

### 🇧🇷 Compatibilidade e escopo
- Chamadas existentes a `read()` sem argumentos continuam válidas. Implementações alternativas ou mocks que sobrescrevem `read()` precisam aceitar o novo parâmetro nomeado; para permitir cancelamento, também devem implementar `cancelRead()`.
- `read()` continua retornando fragmentos do fluxo RFCOMM: uma resposta pode chegar em várias leituras. A montagem de mensagens completas e os prazos de uma transação devem ser tratados pelo aplicativo.
- As novas garantias de cancelamento e exclusão mútua se aplicam a `read()`, não a `readLine()`. Não executar os dois métodos simultaneamente sobre a mesma conexão.

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
