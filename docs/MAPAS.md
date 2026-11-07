# Mapas reais do Coletaí

O Google Maps foi substituído por MapLibre Native Android 13.7.1, MapTiler e OpenRouteService, conforme solicitado. Não há tiles fictícios, pedidos inventados ou rotas simuladas no fluxo atual. As telas antigas de entrada encaminham para `WorkspaceActivity`.

## Configurar

Crie suas chaves nos painéis oficiais [MapTiler](https://cloud.maptiler.com/account/keys/) e [OpenRouteService](https://openrouteservice.org/dev/#/home). Adicione no `local.properties` da raiz, preservando a linha `sdk.dir` existente:

```properties
COLETAI_MAPTILER_API_KEY=SUA_CHAVE_MAPTILER
COLETAI_ORS_API_KEY=SUA_CHAVE_OPENROUTESERVICE
```

Esse arquivo está ignorado pelo Git. Também são aceitas propriedades pessoais em `~/.gradle/gradle.properties`, parâmetros Gradle `-P` ou variáveis de ambiente com os mesmos nomes. Propriedades Gradle têm precedência, seguidas pelas variáveis de ambiente e pelo arquivo local. Recompile o APK após configurar. Não publique as chaves. Chaves inseridas em aplicativos podem ser extraídas; configure restrições e limites nos painéis dos provedores. Para manter a chave ORS exclusivamente no servidor em produção, use um proxy autenticado.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Sem chave MapTiler, a interface informa indisponibilidade do mapa. O GPS ainda pode marcar coordenadas no cadastro de endereço. Sem chave ORS, o cálculo de trajetos informa indisponibilidade. Nenhum serviço é substituído por dados falsos ou por chaves públicas de terceiros.

## Fluxos

- Início: mapa interativo com pedidos autorizados, ativos e com coordenadas. Toque no marcador para abrir os detalhes; pedidos sem coordenadas continuam na lista. A câmera enquadra os pedidos, ou exibe o Brasil quando não há coordenadas.
- Endereços: marcar no mapa, buscar rua/número/cidade com seleção de resultados ou usar GPS. A posição é confirmada antes de voltar ao formulário; os campos do endereço permanecem editáveis.
- Detalhe da coleta: abre o mapa no próprio app. Endereço sem coordenadas pode ser buscado no MapTiler, com escolha explícita do resultado.
- Trajetos: solicite GPS e escolha carro, bicicleta ou caminhada; a geometria real vem do OpenRouteService, com distância, tempo estimado e instruções em português. Não inclui trânsito em tempo real, navegação por voz ou rastreamento contínuo do coletor.
- Permissões: localização precisa ou aproximada, apenas durante o uso. Não solicita localização em segundo plano. Atualizações de GPS são interrompidas ao sair da tela ou depois de 25 segundos sem posição.
- Conexão: busca por ação explícita, sem chamadas a cada tecla; botões evitam requisições duplicadas. Timeout, indisponibilidade, acesso inválido e limite de uso têm mensagens recuperáveis.

O renderizador preserva os créditos nativos das fontes e adiciona o logo oficial MapTiler com link. A geometria das rotas mantém o crédito OpenRouteService/OpenStreetMap. O logo vetorial foi convertido sem alterações visuais de `https://api.maptiler.com/resources/logo.svg`.

## Referências e validação

[MapLibre Android](https://maplibre.org/maplibre-native/android/examples/getting-started/), [geocoding MapTiler](https://docs.maptiler.com/cloud/api/geocoding/), [atribuição MapTiler](https://docs.maptiler.com/guides/map-design/attribution/add-attribution/) e [rotas OpenRouteService](https://giscience.github.io/openrouteservice/api-reference/endpoints/directions/requests-and-return-types).

Os planos gratuitos têm limites e condições definidos por cada provedor; não há garantia de gratuidade ilimitada. Consulte os painéis antes de publicar o app.

Os testes verificam a ordem longitude/latitude exigida pelo GeoJSON, validação das coordenadas, codificação de endereços, abertura/recriação da tela e carregamento de um estilo local pelo renderizador nativo no emulador. O estilo local existe apenas no teste, sem substituir o mapa real do app. As chaves fornecidas foram configuradas somente em `local.properties`, ignorado pelo Git. Foram validadas respostas HTTP 200 do estilo MapTiler, geocoding e trajetos reais de carro, bicicleta e caminhada. A API de rotas usa `https://api.heigit.org/openrouteservice/v2/directions/{profile}/geojson` com `Accept: application/geo+json` e autenticação no cabeçalho Authorization.


## Localização e tela inicial

O primeiro acesso solicita permissão de localização em primeiro plano. A posição do GPS é a preferência inicial para cliente, ambulante e empresa. Em Perfil > Configurações de localização, é possível escolher um ponto no mapa ou voltar ao GPS. A preferência é compartilhada entre as contas deste aparelho. A leitura é interrompida quando a tela sai do primeiro plano; não há rastreamento em segundo plano.

A tela inicial mantém o mapa amplo, localização e avisos no topo, transição em degradê e ação principal abaixo. Histórico e perfil continuam na navegação inferior. As coletas em andamento podem ser abertas pelo resumo abaixo do botão ou pelos marcadores.

Novos pedidos recebem o local selecionado. O endereço obtido das coordenadas precisa ser conferido antes do envio, principalmente número, CEP e complemento. Se GPS ou permissão estiverem indisponíveis, o aplicativo oferece explicitamente o uso de um endereço cadastrado.


## Pontos de referencia e destino para descarte

O mapa agora inclui pontos de coleta, reciclagem, medicamentos e residuos especificos cadastrados no OpenStreetMap. O GPS do coletor orienta a sugestao de destino compativel mais proximo ao iniciar o deslocamento. Consulte [PONTOS_DESCARTE.md](PONTOS_DESCARTE.md) para cobertura, fontes, cache e criterios de selecao.


### Catalogo fixo de Sao Paulo

O APK agora instala 135 pontos revisados de Sao Paulo, com prioridade a Carrao e Tatuape, usando o mapa oficial Google My Maps da Prefeitura e fontes dos operadores. Eles aparecem sem consulta online nem cache previo. Veja [PONTOS_DESCARTE.md](PONTOS_DESCARTE.md).
