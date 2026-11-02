# Coletaí

Aplicativo Android em Java/XML para clientes, ambulantes, empresas e administradores. O fluxo de pedidos voltou a utilizar propostas: busca com cronômetro, pedido em tela cheia para o coletor, proposta com valor e mensagem, aceite ou recusa pelo cliente, deslocamento, coleta e descarte. Veja [o fluxo atual](docs/FLUXO_PEDIDOS.md).

Consulte [a matriz de adequação](docs/CONFORMIDADE_1_4.md), [a arquitetura](docs/ARQUITETURA.md), [o painel administrativo](docs/ADMINISTRACAO.md) e [a API HTTPS/MySQL](backend/README.md).

## Desenvolvimento

Última rodada de testes entre perfis, correções e limites de validação: [relatório de QA de 02/11/2026](docs/QA_2026-11-02.md).

Para apresentar o sistema entre celulares sem internet, use [a apresentação offline por Bluetooth](docs/BLUETOOTH_OFFLINE.md), disponível no login e no Perfil. Um celular coordena os pedidos, propostas, administração e fotos dos participantes; o APK inclui o mapa local de Carrão/Tatuapé.

Abra a raiz no Android Studio, com o SDK e JDK indicados pelo Gradle. Configure o SDK local em `local.properties`.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

O APK debug fica em `app/build/outputs/apk/debug/app-debug.apk`. Sem URL de API, utiliza o ambiente local de demonstração. Os atalhos usam as contas iniciais e a senha `12345`; contas novas têm senha própria. **Abrir administração** entra no console demonstrativo. O painel usa a mesma paleta verde do Coletaí.

Para uso entre celulares, configure a API HTTPS e o MySQL conforme `backend/README.md`. O mapa usa MapLibre Native, MapTiler e OpenRouteService. Configure as chaves conforme [MAPAS.md](docs/MAPAS.md). Sem essas configurações, não há implantação multiusuário em produção. Dados locais e cache remoto são separados.
