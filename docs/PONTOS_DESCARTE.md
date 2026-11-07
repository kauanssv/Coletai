# Pontos de descarte pre-instalados de Sao Paulo

O APK inclui `app/src/main/assets/disposal_points_sao_paulo.json`, um catalogo fixo de **135 locais reais**. Os registros sao carregados com o mapa, inclusive na primeira abertura, sem cache anterior e sem consulta a Overpass. Todos permanecem na camada de referencia ao abrir pedidos, limpar marcadores ou desenhar trajetos.

## Cobertura e pesquisa

- **127 ecopontos**: coordenadas do Google My Maps publicado pela Prefeitura de Sao Paulo, com enderecos e horarios cruzados com a listagem municipal. Inclui Astarte, Viaduto Engenheiro Alberto Badra, Tatuape, Condessa, Vila Luisa, Agua Rasa e Mooca II.
- **6 pontos de eletronicos**: Matriz Sucata Digital na Vila Formosa e parceiros Shopping Patio Paulista, Parque Luis Carlos Prestes, Crystal Tower, Villa Lobos Office Park e Amplifique Coworking.
- **1 ponto de pilhas e baterias**: Shopping Analia Franco, com coletor informado no Piso Tulipa, corredor ao lado da loja Khelf.
- **1 ponto de medicamentos**: Drogasil da Avenida Celso Garcia, 4076, Tatuape, identificada pela LogMed e pela divulgacao atual da Drogasil para a unidade. A coordenada foi conferida no cadastro publico da unidade indicado em `coordinate_source`.

As fontes foram consultadas em **31/10/2026**. A lista de enderecos e coordenadas instalada esta no asset; cada registro preserva fonte, referencia da coordenada, materiais, horario quando informado e observacoes. O aplicativo oferece acesso a fonte e informa as restricoes de recebimento.

O mapa da Prefeitura contem 128 marcadores de ecopontos e 11 poligonos de lotes de varricao. Os poligonos nao sao estabelecimentos e foram descartados. O Ecoponto General Flores foi excluido porque a listagem atual o informa temporariamente fora de operacao. Nao foram adicionados como pontos permanentes os coletores de campanhas do Metro encerradas em 2025.

O mapa antigo da Sucata Digital foi cruzado com a lista atual da operadora. A coordenada antiga da matriz nao foi reutilizada: o endereco atual da Rua Templarios, 192, foi confirmado pelo link Google Maps do rodape do site. Os outros parceiros incluidos constam da lista atual e do mapa publicado pela operadora.

## Funcionamento

O catalogo fica no APK e nao expira nem desaparece com a limpeza do cache de consultas. Na cidade e entorno imediato de Sao Paulo, a busca usa diretamente esses registros e nao depende do servidor publico. Em outras regioes, as consultas Overpass continuam opcionais e seus resultados sao somados ao catalogo instalado. A fonte de cada ponto distingue Prefeitura, operadores e OpenStreetMap.

O mapa inicia no entorno Carr?o/Tatuape quando ainda nao ha posicao disponivel; a preferencia existente de GPS ou local manual continua sendo aplicada quando disponivel. A lista de pontos e ordenada pela distancia ao centro do mapa.

Ao iniciar o deslocamento e apos recolher os materiais, a sugestao automatica usa o GPS real do coletor para selecionar o ponto compativel mais proximo dentro de 15 km. Distancias sao geodesicas em linha reta; rotas viarias sao calculadas separadamente pelo OpenRouteService. O destino escolhido manualmente e preservado. A selecao exige compatibilidade com todos os materiais do pedido.

Ecopontos nao sao classificados como receptores de medicamentos, pilhas ou eletronicos. O recebimento segue as informacoes publicadas por cada operador. Limites de volume, horario, acesso e a posicao do coletor dentro de um shopping aparecem nas observacoes. A base inclui locais confirmados em fontes publicas; ela nao pretende enumerar todos os pontos existentes na cidade.

## Manutencao

Fontes e criterios: `docs/data/disposal_catalog_sources.json`. Pontos especiais revisados: `docs/data/disposal_points_extras.json`.

Para atualizar a base municipal, baixe o KML do Google My Maps da Prefeitura e revise a operacao atual dos locais. Depois execute:

```powershell
python tools/build_disposal_catalog.py arquivo-ecopontos.kml --verified-on AAAA-MM-DD
```

A data do catalogo e a constante `BundledDisposalPoints.VERIFIED_AT` devem acompanhar uma nova revisao. Uma atualizacao do APK distribui o catalogo revisado.

## Fontes

- [Lista municipal e mapa oficial Google My Maps](https://prefeitura.sp.gov.br/web/subprefeituras/w/ecoponto-esta%C3%A7%C3%A3o-de-entrega-volunt%C3%A1ria-de-inserv%C3%ADveis)
- [Mapa oficial da Prefeitura no Google](https://www.google.com/maps/d/viewer?mid=1Kok7L5JyjXN758y3vy3kMsKYdJylrQE)
- [Materiais recebidos em ecopontos e PEVs](https://prefeitura.sp.gov.br/web/secretaria_executiva_de_limpeza_urbana/w/350659)
- [Sucata Digital: pontos e enderecos atuais](https://sucatadigital.com.br/ponto-de-descarte/)
- [Shopping Analia Franco: pilhas e baterias](https://www.shoppinganaliafranco.com.br/o-shopping/multiplique-o-bem/)
- [LogMed: lista de pontos](https://www.logmed.org.br/arquivos/atuais/pontos-de-descarte-21-10-2024.pdf)
- [Drogasil: unidade Tatuape](https://www.drogasil.com.br/blog/saude/medicamentos/como-descartar-corretamente-suas-canetas-injetaveis)

Os testes JVM verificam integridade, fontes, coordenadas, cobertura dos bairros e selecao sem rede. O teste Android verifica a camada nativa com o cache vazio e gera uma captura da tela inicial com os pontos reais do catalogo.
