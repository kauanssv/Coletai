# Fluxo de pedidos restaurado

Por solicitação do usuário, o fluxo de propostas volta a substituir o aceite direto da versão anterior.

1. O cliente envia o pedido e acompanha a busca pelo coletor, com cronômetro baseado na data persistida de criação e botão vermelho para cancelar.
2. O pedido disponível abre em tela cheia no perfil de ambulante ou empresa, reutilizando o layout original de detalhes. Sem filtro de materiais configurado, os coletores recebem os pedidos disponíveis.
3. Aceitar o pedido abre o layout original de envio de proposta. O coletor informa valor e mensagem. Sair sem enviar libera o pedido novamente.
4. O cliente recebe a proposta e pode aceitar ou recusar. A recusa retoma a busca, preservando o início do cronômetro e retirando a proposta recusada. Uma nova proposta pode ser enviada posteriormente.
5. Somente após o aceite do cliente, o coletor recebe o endereço completo e as coordenadas para o mapa e a rota.
6. O coletor inicia o deslocamento, confirma a chegada, confirma os materiais coletados e segue para o descarte. Pode escolher o destino do descarte no mapa e abrir a rota.
7. A confirmação do descarte encerra o pedido e libera a avaliação no histórico do cliente.

Estados: `aberta` → `negociando` → `proposta` → `aceita` → `a_caminho` → `coletando` → `descartando` → `concluida`. Cancelamentos usam `cancelada`. A revisão do registro impede avanços com uma tela desatualizada.

As configurações de localização agrupadas no Perfil e o mapa real permanecem. Os dados locais existentes são preservados; pedidos já aceitos pela versão anterior podem continuar a partir do deslocamento.

No modo local simples, a validação entre perfis usa o mesmo aparelho. Entre celulares, o fluxo usa a API HTTPS configurada ou a [apresentação offline por Bluetooth](BLUETOOTH_OFFLINE.md), com um celular coordenador e atualizações em primeiro plano a cada cinco segundos. O novo transporte Bluetooth atende o fluxo atual completo. O protocolo Bluetooth legado continua separado e usa outro UUID.

A suíte JVM verifica proposta, recusa, cancelamento, concorrência por revisão e a ordem das etapas. O teste Android `ProposalFlowTest` exercita as telas do fluxo no emulador, preservando seu armazenamento anterior. A API tem testes com SQLite; a migração e as transações MySQL precisam ser validadas na infraestrutura de implantação.
