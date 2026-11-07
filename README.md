# Coletaí

## Informações acadêmicas

**Curso:** Análise e Desenvolvimento de Sistemas  
**Universidade:** Universidade Cidade de São Paulo — UNICID

### Integrantes

- Kauan de Souza dos Santos Vieira
- Kauê Sousa Miranda
- Keven Sousa Miranda
- Thiago Cavalcante Guimarães

## Sobre o projeto

O **Coletaí** é um aplicativo Android desenvolvido para facilitar o descarte correto de resíduos. A proposta é conectar pessoas que precisam descartar determinados materiais com ambulantes ou empresas responsáveis pela coleta.

O aplicativo possui perfis de Cliente, Ambulante, Empresa e Administrador. O fluxo de pedidos inclui busca por coletor, envio de proposta com valor e mensagem, aceite ou recusa pelo cliente, deslocamento, coleta e descarte.

## Principais funcionalidades

- Criação e acompanhamento de pedidos de coleta
- Perfis de Cliente, Ambulante e Empresa
- Envio, aceite e recusa de propostas
- Acompanhamento das etapas da coleta
- Histórico de pedidos e coletas
- Comunicação entre dispositivos via Bluetooth
- Painel administrativo demonstrativo
- Mapa local com pontos de descarte

## Tecnologias utilizadas

- Java
- XML
- Android Studio
- Git e GitHub
- MapLibre Native
- API HTTPS e MySQL

## Documentação

- [Fluxo de pedidos](docs/FLUXO_PEDIDOS.md)
- [Matriz de adequação](docs/CONFORMIDADE_1_4.md)
- [Arquitetura](docs/ARQUITETURA.md)
- [Painel administrativo](docs/ADMINISTRACAO.md)
- [Mapas](docs/MAPAS.md)
- [API e banco de dados](backend/README.md)
- [Apresentação offline por Bluetooth](docs/BLUETOOTH_OFFLINE.md)
- [Relatório de QA de 02/11/2026](docs/QA_2026-11-02.md)

## Desenvolvimento

Abra a pasta raiz do projeto no Android Studio com o SDK e o JDK indicados pelo Gradle. Configure o SDK local em `local.properties`.

Para executar as verificações e gerar o APK debug pelo PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest