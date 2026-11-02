# API Coletaí 1.4

API em Python/FastAPI, aplicação Android em Java e armazenamento relacional MySQL. Há propostas com valor e mensagem, aprovadas ou recusadas pelo cliente antes do deslocamento; a API não processa pagamentos. O esquema é derivado de `models.py`, com os nomes físicos e relacionamentos da documentação, além de campos de auditoria, sessão, revisão e exclusão lógica. O modelo lógico do documento também exige CNPJ e descrição da cooperativa; essas colunas foram incluídas, embora não apareçam no trecho de SQL físico do DOCX.

## Execução em servidor

1. Copie `.env.example` para `.env` e defina domínio e senhas. Use uma senha de banco formada por caracteres seguros em URL, por exemplo um valor hexadecimal aleatório. Não versione o arquivo `.env`.
2. Com Docker instalado, execute `docker compose up -d --build` nesta pasta. O MySQL é acessível apenas na rede interna; o Caddy publica HTTPS e obtém o certificado para o domínio configurado.
3. Crie o administrador com `docker compose exec api python api.py --create-admin`. A senha é solicitada sem eco e é persistida como PBKDF2 com salt individual. Não há administrador com senha padrão na API.
4. O DNS do domínio deve apontar para o servidor e as portas 80/443 precisam estar disponíveis. O app exige certificado HTTPS confiável e não aceita downgrade para HTTP.

Alternativamente, prepare um banco MySQL novo, instale `requirements.txt` em um ambiente virtual e defina `COLETAI_DATABASE_URL=mysql+pymysql://usuario:senha@host/coletai?charset=utf8mb4`. Execute `uvicorn api:create_app --factory` atrás de um proxy HTTPS. `create_all` inicializa uma base nova; não transforma automaticamente uma base preexistente com estrutura diferente. Não execute o `DROP DATABASE` do exemplo acadêmico sobre dados reais.

O volume `mysql_data` persiste os dados. Faça backups periódicos com `mysqldump --single-transaction coletai` e teste a restauração em uma base separada. A política de disponibilidade, retenção e recuperação depende da hospedagem escolhida.

## Configuração do Android

No arquivo pessoal `~/.gradle/gradle.properties`, configure:

```properties
COLETAI_API_BASE_URL=https://api.seu-dominio.example
COLETAI_MAPTILER_API_KEY=sua_chave_maptiler
COLETAI_ORS_API_KEY=sua_chave_openrouteservice
```

A URL é a raiz do serviço, sem `/v1`. Os provedores de mapas são MapTiler e OpenRouteService, renderizados por MapLibre Native. Veja `../docs/MAPAS.md` para configurar as chaves e seus limites. As chaves do APK podem ser extraídas; aplique as restrições disponíveis no provedor e use um proxy autenticado na produção caso precise manter a chave ORS exclusivamente no servidor.

Sem URL de API, o APK de depuração utiliza o ambiente local demonstrativo. O cache da API é separado dos dados anteriores do aparelho e excluído do backup automático. APK de produção sem API configurada não utiliza a senha universal de demonstração. Com API configurada, os atalhos de demonstração são ocultados, todas as mutações são autorizadas no servidor e falhas de comunicação não são convertidas em gravações locais.

## Contrato

- `POST /v1/register`: cadastro público de cidadão, coletor ou cooperativa, com senha própria.
- `POST /v1/login`: retorna token opaco, conta autenticada e estado autorizado.
- `GET /v1/state`: solicitações, materiais, endereços e notificações autorizados para a sessão. Somente administradores recebem todos os cadastros e a auditoria.
- `POST /v1/action`: corpo `{"action":"request.transition","payload":{"id":"1","revision":2,"action":"accept"}}`.
- `POST /v1/logout`: revoga o token atual.

Todas as operações autenticadas utilizam `Authorization: Bearer TOKEN`. Os tokens persistidos no servidor são hashes e expiram em 12 horas; contas bloqueadas/excluídas são recusadas mesmo com token anterior.

Operações: `profile.save`, `profile.status`, `profile.password`, `address.save`, `address.delete`, `accepted.save`, `material.save`, `info.save`, `request.save`, `request.transition`, `request.review`, `request.deleted`, `notification.read`. Transições: `accept` (abrir proposta), `propose` (enviar `price` e `message`), `approve`, `decline`, `reject`, `release`, `cancel`, `going`, `arrive`, `collect`, `complete`. Revisões evitam sobrescrita e o aceite utiliza bloqueio da linha no MySQL. Cada solicitação conserva o snapshot do endereço e seus itens individuais.

Fotos dos pedidos: `POST /v1/photos` recebe bytes JPEG com `Content-Type: image/jpeg` e autenticação Bearer. O retorno é uma referência `{id, source: "remote"}`, enviada no campo `photos` de `request.save` (até três referências distintas). Omitir `photos` preserva as fotos existentes; enviar `[]` remove as associações. `GET /v1/photos/{id}` exige autenticação e permite acesso ao proprietário, ao administrador e a quem pode visualizar o pedido associado. A resposta de estado contém apenas referências, sem os bytes das imagens.

Cada foto tem limite de 256 KB e 1600 pixels por lado. O Android copia a seleção para armazenamento interno, corrige a orientação, comprime e salva em JPEG sem EXIF. A API também remove os metadados opcionais do JPEG e armazena a imagem nas tabelas `foto_pedido` e `solicitacao_foto`, criadas automaticamente na inicialização junto das demais tabelas. O modo local usa arquivos privados do aplicativo com referências nos pedidos, sem exigir conexão.

## Testes e limites de validação

```powershell
.venv\Scripts\python.exe -m unittest discover -s . -p "test_*.py" -v
```

Os testes usam SQLite isolado, com chaves estrangeiras habilitadas, e validam os endpoints, transações e autorizações. Outro teste compila todo o DDL para o dialeto MySQL. Não substituem um teste de integração com o MySQL e o proxy HTTPS reais. MySQL/Docker não estavam instalados no ambiente de desenvolvimento; domínio, credenciais do banco e chave do Maps não foram fornecidos. Portanto, não houve implantação ou validação de tráfego entre celulares no servidor real.


## Atualização de uma base existente

O fluxo de propostas acrescenta colunas e amplia a restrição de estados de `solicitacao_coleta`. Para uma base MySQL já criada, configure `COLETAI_DATABASE_URL` e execute `python migrate_proposals.py` para revisar o SQL; `python migrate_proposals.py --apply` aplica a alteração aditiva sem apagar os pedidos. O aplicativo e a API precisam ser atualizados juntos. Bases novas usam `schema.mysql.sql` ou o modelo atualizado.

Avaliação pós-coleta: `request.review` recebe `id`, `score` inteiro de 1 a 5 e `comment` opcional (até 500 caracteres). Só o cliente do pedido pode avaliar, após a conclusão, uma única vez. Os perfis incluem `rating_average` e `rating_count`, calculados a partir das avaliações das coletas concluídas pelo responsável; esses valores não podem ser definidos pelo cliente. Sem avaliações, a API retorna média 0 e contagem 0, e o aplicativo exibe “Ainda não há avaliações”.
