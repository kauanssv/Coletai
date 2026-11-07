# Adequação à documentação Coletaí 1.4

> Registro histórico: por solicitação posterior do usuário, o aceite direto foi substituído pelo fluxo de propostas. Esta matriz descreve a versão anterior; o comportamento atual está em [FLUXO_PEDIDOS.md](FLUXO_PEDIDOS.md).

Referência: **Real Documentação Coletaí 1.4.docx**, fornecida pelo usuário. O documento define aceite direto de coleta, sem propostas, cobrança, compra/venda de materiais, cadastro de pontos fixos ou gestão interna das cooperativas.

## Requisitos funcionais

| Requisitos | Implementação |
| --- | --- |
| RF001, RF005, RF010 | Cadastro público por tipo, senha própria protegida por hash, manutenção do cadastro e troca de senha. |
| RF002 | Solicitação com data desejada, endereço cadastrado e um ou mais materiais, cada um com peso individual. |
| RF003, RF008, RF013 | Estados Aberta → Aceita → Coletor a caminho → Concluída; atualizações visíveis ao cidadão. |
| RF004 | Histórico por conta, incluindo conclusão/cancelamento; registros antigos preservados para migração e consulta administrativa. |
| RF006, RF011 | Solicitações disponíveis compatíveis com os materiais que o responsável aceita coletar. |
| RF007, RF012 | Aceite direto e recusa que mantém a solicitação disponível para os demais. |
| RF009, RF014 | Confirmação explícita da realização antes de concluir a coleta. |
| RF015, RF016 | Gerenciamento de contas e cooperativas, bloqueio, exclusão lógica e restauração. |
| RF017 | Consulta e gestão das solicitações, com histórico de alterações e recuperação de excluídos. |
| RF018 | Catálogo de materiais ativos/inativos e informações da plataforma editáveis pelo administrador. |
| RF019 | Autenticação por senha própria. Atalhos e credenciais de demonstração restritos ao APK debug sem API. API com sessões e autorização por perfil. |
| RF020 | MapLibre Native + MapTiler para mapa e busca de endereços; GPS e marcação de coordenadas; OpenRouteService para trajetos de carro, bicicleta e a pé, distância, duração e instruções dentro do app. Dependem das chaves externas; sem elas, o app informa indisponibilidade, sem simular mapa ou rota. A troca de provedor substitui o Google Maps citado no DOCX, por solicitação do usuário. |
| RF021 | Cidadão pode cancelar solicitações ainda não concluídas; encerramentos são preservados. |
| RF022 | Desistência do responsável remove a atribuição e reabre a solicitação para outros responsáveis. |
| RF023 | Cidadão pode editar somente enquanto a solicitação estiver aberta; o servidor também valida a regra e a revisão do registro. |

O modelo inclui endereços com apelido e campos estruturados, snapshot do endereço no pedido, catálogo e materiais aceitos pelo responsável, uma avaliação de 1 a 5 por coleta concluída e notificações de andamento com estado lida/não lida. O modelo físico fornecido usa `aberta`; esse é o valor canônico adotado para a situação também chamada de “solicitada” em outro trecho do documento.

## Aplicativo e armazenamento

`WorkspaceActivity`, `CollectionRepository`, `RequestFormDialog`, `AddressEditorDialog` e `RegistrationActivity` substituem o fluxo principal de propostas por solicitação gratuita e aceite direto. `LocationActivity` integra MapLibre Native, MapTiler e OpenRouteService; veja [MAPAS.md](MAPAS.md). As classes antigas permanecem para preservar compatibilidade e dados de versões anteriores; não são o destino do novo login.

`PlatformGateway` comunica-se com a API por HTTPS e `PlatformStore` mantém o cache remoto separado do ambiente local. No ambiente remoto, as mutações são autorizadas e persistidas no servidor. O modo local debug permite testar o fluxo funcional no mesmo aparelho, alternando as contas. Ele não é uma implantação multiusuário nem garante disponibilidade 24 horas.

O backend em `backend/` utiliza MySQL em execução normal, com modelo relacional, transações, snapshots, revisão das solicitações, controle de acesso e sessões com expiração. O estado retornado pela API respeita a conta autenticada. O cliente não decide as permissões de produção.

## Layout administrativo

- Mesmas cores de marca, texto, superfícies e bordas do Coletaí; removida a paleta roxa/azul.
- Cabeçalho mais curto e métricas com altura mínima, permitindo crescimento com a fonte.
- Margens horizontais de 16 dp e espaçamento consistente entre títulos, filtros e listas.
- Insets do Android consumidos uma única vez, evitando margem inferior duplicada na navegação.
- Diálogos com rolagem e ações fixas; busca/reposicionamento da lista e preservação de rascunhos.
- Validação visual em configuração de tela correspondente ao Samsung SM-S908E: 1080 × 2316, densidade 450, fonte 1.0.

## Dependências externas ainda necessárias

Não foram fornecidos servidor/API existentes, domínio HTTPS, credenciais MySQL ou chaves MapTiler e OpenRouteService. A aplicação, o cliente HTTPS, a API, o esquema e a configuração de execução estão preparados, mas a operação hospedada entre dispositivos depende desses dados e da implantação. A pergunta sobre essa infraestrutura foi enviada durante o trabalho.

Disponibilidade contínua, recuperação de backups, configuração da hospedagem, integração real com MySQL e validação de MapTiler/ORS com chaves não podem ser declaradas atendidas apenas pelo APK local. A implementação não constitui certificação de conformidade legal/LGPD. Dados apagados fisicamente antes da implementação não podem ser recuperados; a recuperação do painel refere-se à exclusão lógica.

Veja [a execução e o contrato da API](../backend/README.md) e os testes automatizados para os limites verificados.
