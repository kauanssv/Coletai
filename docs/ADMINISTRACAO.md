# Administração Coletaí

O painel usa a paleta verde da marca, margens consistentes e navegação compacta. No APK debug sem API, **Abrir administração** entra na conta demonstrativa `admin@coletai.app`. O login convencional inicial usa a senha `12345`. Contas criadas pelo painel recebem uma senha própria de pelo menos oito caracteres.

Com a API configurada, não há atalho que dispense autenticação. O primeiro administrador do servidor é criado pelo procedimento em `backend/README.md`.

## Operações

- **Resumo:** contas ativas, solicitações abertas, concluídas e excluídas, com atalhos e últimas alterações.
- **Contas:** busca, edição de dados, criação de cidadão/coletor/cooperativa, bloqueio, exclusão reversível e restauração. A conta administrativa deve permanecer ativa.
- **Pedidos:** consulta e gestão das solicitações do modelo 1.4 e compatibilidade com registros anteriores. Cancelar não apaga o histórico. Somente solicitações encerradas são excluídas; restauração não reinicia a coleta.
- **Atividade:** autor, data, ação e valores anteriores/posteriores. Registros excluídos continuam auditáveis.
- **Catálogo:** tipos de material ativos/inativos e informações públicas da plataforma.

As telas comuns usam os mesmos cadastros e solicitações. Com API HTTPS, as autorizações e gravações ocorrem no servidor e os demais dispositivos consultam as alterações ao atualizar o estado. No modo debug local, os dados ficam restritos à instalação.

O painel não recupera dados fisicamente removidos antes da implementação, nem dados perdidos ao limpar o armazenamento ou desinstalar o aplicativo. A recuperação refere-se à exclusão lógica. Autenticação, disponibilidade, recuperação de backups e operação real em MySQL/Maps dependem da configuração e validação da infraestrutura descritas na [matriz de adequação](CONFORMIDADE_1_4.md).
