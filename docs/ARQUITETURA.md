# Arquitetura Coletaí 1.4

O fluxo principal é Android Java/XML → API HTTPS → MySQL. Sem API configurada, o APK debug usa um ambiente local para demonstração das regras. O aplicativo de produção não assume a identidade dos atalhos de teste.

| Componente | Responsabilidade |
| --- | --- |
| `WorkspaceActivity` | Solicitações, histórico, avisos, cadastro e materiais atendidos. |
| `RequestFormDialog` | Data, endereço e materiais com peso individual. |
| `AddressEditorDialog` / `LocationActivity` | Endereços estruturados, coordenadas opcionais e MapLibre Native com MapTiler e OpenRouteService. |
| `CollectionRepository` | Regras de aceite direto, edição, cancelamento, reabertura, conclusão, avaliação e visibilidade. |
| `ProfileRepository` / `PasswordHashes` | Cadastros, situação do acesso e credenciais protegidas no ambiente local. |
| `PlatformGateway` | Login, estado autorizado e mutações via HTTPS. Não grava localmente para mascarar falhas de servidor. |
| `PlatformStore` | Separa o ambiente local do cache autenticado remoto. |
| `PlatformTasks` | Autenticação/rede fora da UI, com controle de término e sincronização dos testes. |
| `AdminActivity` / `AdminRepository` | Cadastros, cooperativas, solicitações, catálogo e informações da plataforma. |
| `AuditRepository` | Autor, data e cópias antes/depois, inclusive para exclusões lógicas. |
| `backend/api.py` / `backend/models.py` | Autorização, regras e transações MySQL no servidor. |

Status canônicos: `aberta` → `aceita` → `a_caminho` → `concluida`; cancelamento pelo cidadão encerra como `cancelada`. A desistência do responsável volta a `aberta`, removendo a atribuição. Recusar não encerra a solicitação. Apenas o cidadão proprietário edita enquanto aberta. O servidor valida essas regras independentemente do cliente.

O snapshot do endereço evita alterações retroativas quando o cidadão atualiza seu cadastro. Materiais têm catálogo ativo/inativo e relação com os responsáveis. A avaliação é única por solicitação concluída. Cada usuário acessa somente os registros autorizados; administradores consultam inclusive os excluídos.

## Compatibilidade

`PedidoRepository`, `EventStore`, `BluetoothColetai` e as telas antigas permanecem como compatibilidade com versões anteriores. O login direciona para `WorkspaceActivity`, com propostas e preços aprovados pelo cliente. A apresentação offline atual utiliza `BluetoothPlatform` e `BluetoothEngine`, separados do protocolo legado, para executar as mesmas operações entre celulares sem servidor HTTPS. O ambiente local pode migrar os registros antigos de cidadãos, preservando identificadores e os dados anteriores para consulta administrativa. Veja [BLUETOOTH_OFFLINE.md](BLUETOOTH_OFFLINE.md).

## Execução e limites

Veja [a configuração da API](../backend/README.md) e [a matriz de conformidade](CONFORMIDADE_1_4.md). Não foram fornecidos domínio HTTPS, credenciais MySQL ou chaves MapTiler e OpenRouteService. Portanto, os testes de API executam em base isolada SQLite, e o DDL também é compilado para MySQL. A implantação e a integração real com o servidor dependem dessas configurações.
