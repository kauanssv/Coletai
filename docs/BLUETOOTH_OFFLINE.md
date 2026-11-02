# Apresentação offline por Bluetooth

## Preparação dos celulares

1. Instale **o mesmo APK debug atualizado** em todos os aparelhos. O APK está em `app/build/outputs/apk/debug/app-debug.apk`.
2. Escolha um aparelho como **coordenador**. Ele guarda a base da apresentação e também pode usar cliente, coletor, empresa ou administrador.
3. Nas configurações Bluetooth do Android, pareie cada participante com o coordenador. Não é necessário parear os participantes entre si.
4. No login do Coletaí, abra **Apresentação offline por Bluetooth** e permita o acesso a dispositivos próximos. No coordenador, toque em **Ser o coordenador**.
5. Nos outros aparelhos, selecione o coordenador na lista de pareados e toque em **Conectar ao coordenador selecionado**. Aguarde a indicação de conexão e toque em **Entrar no sistema**.
6. Use uma conta diferente em cada aparelho. Novas contas podem ser criadas e utilizadas nos outros celulares da mesma sessão.

Contas iniciais, todas com senha `12345`:

| Perfil | E-mail |
| --- | --- |
| Cliente | cliente@gmail.com |
| Ambulante | ambulante@gmail.com |
| Empresa | empresa@gmail.com |
| Administrador | admin@coletai.app |

A base é persistente: se essas contas forem editadas ou suas senhas forem alteradas, os valores novos continuam valendo. A senha inicial só é destinada à apresentação no APK debug. Não use dados pessoais reais nesse ambiente de demonstração.

Os dados de apresentações anteriores no modo local e o cache HTTPS ficam separados da base Bluetooth. Encerrar a sessão não apaga a base do coordenador. Voltar a ser coordenador no mesmo aparelho recupera os dados salvos.

## Roteiro sugerido

- Celular A: coordenador e cliente. Celular B: ambulante. Opcionalmente, celular C: empresa e celular D: administrador.
- Desative Wi-Fi e dados móveis, mantendo o Bluetooth ativo. Em modo avião, reative o Bluetooth manualmente.
- Na configuração Bluetooth, **Localização para a apresentação** oferece pontos manuais distintos para cliente e coletor na região **Carrão/Tatuapé, São Paulo**. Também é possível escolher outra posição em **Configurações de localização** no Perfil. GPS funciona sem internet, mas pode demorar ou não ter sinal dentro do local; na modalidade Bluetooth, as rotas e a sugestão de descarte respeitam o ponto manual selecionado para a demonstração.
- O cliente cria um pedido com materiais, peso, endereço/localização e fotos opcionais.
- O ambulante ou a empresa recebe o pedido, envia valor e mensagem. O cliente aceita ou recusa a proposta.
- Após o aceite, o responsável recebe a localização completa, inicia o deslocamento, confirma chegada, retirada e descarte.
- O cliente avalia a coleta. O coletor vê a média no perfil e o administrador acompanha o pedido e a auditoria.
- No administrador, crie/edite contas e materiais, bloqueie e restaure um participante e consulte a auditoria. As operações passam pelas mesmas autorizações usadas pelo restante da plataforma local.

As telas em primeiro plano consultam o coordenador a cada cinco segundos. Uma alteração pode levar esse intervalo para aparecer nos demais celulares.

## Conexão e reconexão

Há uma sessão em estrela: até **seis conexões de participantes** mais o coordenador na implementação. A capacidade efetiva depende do Bluetooth de cada fabricante e precisa ser verificada com os celulares da apresentação. Não há promessa de qualquer quantidade de aparelhos.

O coordenador mantém o servidor aberto enquanto atende os participantes. Um serviço Android em primeiro plano mantém a sessão independente da tela de login ou perfil. A configuração também está acessível pelo Perfil e pela administração. É possível permitir a notificação para acompanhar o estado sem abrir a tela.

O cliente tenta se reconectar automaticamente. Sem o coordenador, não confirma alterações e não converte o erro em uma gravação local. Após reconectar, atualiza o estado do coordenador. **Retomar conexão** permite reabrir o servidor; **Encerrar Bluetooth neste celular** sai dessa modalidade. Trocar de coordenador significa trocar de base, não unir duas bases existentes. Não há eleição automática de um novo coordenador.

Pedidos repetidos por perda de resposta usam identificadores estáveis e confirmação persistente no coordenador. Há um histórico limitado das últimas 32 mutações confirmadas; após uma interrupção longa, atualize e confira a lista antes de repetir uma criação. Revisões dos pedidos impedem dois coletores de avançar a mesma solicitação com uma tela antiga.

## Mapas, busca e trajeto sem internet

O APK inclui **13.113 trechos OSM** da área Carrão/Tatuapé e arredores, aproximadamente latitude -23,575 a -23,515 e longitude -46,610 a -46,515. Nenhuma chave MapTiler ou OpenRouteService é necessária nessa modalidade.

- Ruas são desenhadas a partir do arquivo instalado no APK, sem baixar tiles ou estilos.
- A busca offline encontra nomes de ruas e pontos do catálogo local; não é um serviço mundial de busca de endereços ou de números de imóveis.
- O grafo local calcula trajetos para carro, bicicleta e caminhada, considerando acessos básicos e mão única. Tempos usam velocidades demonstrativas; restrições de conversão, trânsito ao vivo e orientação curva a curva não estão incluídos. A interface identifica o trajeto como **offline de demonstração**.
- Há 135 pontos reais de descarte já instalados, com materiais, endereços e observações. A localização de fora da região pode ser registrada em um pedido, mas o mapa não inventa ruas ou rotas onde não há cobertura instalada.
- O geocodificador do Android, as consultas Overpass e os provedores online não são chamados para concluir um pedido nessa modalidade. A posição manual de cada celular é independente; a posição da coleta é enviada no pedido.

Para ampliar a área, revise `BBOX` e execute `python tools/build_offline_map.py` durante o desenvolvimento, com internet, e distribua um novo APK. O mapa e o grafo são derivados da mesma extração.

Dados de ruas: **© OpenStreetMap contributors**, sob [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/), obtidos pela [Overpass API](https://overpass-api.de/api/interpreter). A extração distribuída está integralmente disponível em `app/src/main/assets/offline_streets_sao_paulo.geojson`, incluindo origem, licença e data. [Créditos e copyright do OpenStreetMap](https://www.openstreetmap.org/copyright). Fontes dos pontos de descarte: `docs/PONTOS_DESCARTE.md`.

## Implementação e limites de validação

`BluetoothPlatform` implementa servidor RFCOMM com múltiplos sockets, correlação de respostas, timeouts e reconexão. Usa um UUID próprio e protocolo versão 1, separado do Bluetooth legado.

`BluetoothEngine` autentica sessões independentes por aparelho, verifica autorização e executa todos os comandos atuais de perfil, endereço, catálogo, informações, pedidos, propostas, avaliação e notificações. Cada comando trabalha em uma cópia isolada e só publica os dados e o recibo da operação em uma gravação final. Credenciais e a base completa não são transmitidas aos participantes: o estado de cada conta é filtrado. O administrador autenticado pode consultar os registros administrativos.

Fotos comprimidas são enviadas ao coordenador e baixadas pelos aparelhos autorizados, em mensagens com tamanho limitado. Os dados de imagem não são replicados junto de cada atualização de pedidos.

`PlatformGateway` escolhe Bluetooth quando a modalidade está ativa. Assim, as telas atuais usam o mesmo contrato de login, mutação e atualização já usado pela API, sem manter um segundo fluxo de pedidos incompatível.

Os testes automatizados cobrem comandos de quatro sessões, concorrência de coletores, deduplicação após reiniciar o servidor, isolamento e bloqueio de contas, fotos, quadros fragmentados e rotas reais do catálogo offline. O teste Android específico exercita o gateway, serviço, negociação, administração e carregamento nativo das ruas.

**Ensaio RFCOMM aprovado com três instâncias Android 17/API 37 emuladas**, um coordenador e dois participantes conectados simultaneamente, com Wi-Fi e dados móveis desligados: criação de pedido com foto, envio e aceite de proposta, reconexão do cliente mantendo a sessão, deslocamento/retirada/descarte, avaliação e auditoria consultada pelo terceiro aparelho. Os três processos usam os sockets Bluetooth reais da API Android sobre o rádio emulado NetSim, sem um transporte TCP substituto no aplicativo. Também passou o ensaio anterior com dois Androids.

Evidências locais: `build/qa/bluetooth-radio-host.txt`, `bluetooth-radio-client.txt`, `bluetooth-radio-observer.txt` e `bluetooth-android.txt`. A classe `BluetoothRadioTest` é opt-in (`radio_side=host|client|observer`, `radio_host=MAC`, e `radio_three=true` no coordenador) e só permite emuladores. Não certifica todos os rádios, fabricantes ou distâncias de uso: o ensaio final deve usar os celulares e o local da apresentação.
