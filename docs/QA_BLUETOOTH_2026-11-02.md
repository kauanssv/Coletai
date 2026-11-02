# Validação da integração Bluetooth offline — 02/11/2026

## Entrega

APK debug: `build/distributions/Coletai-Bluetooth-Offline.apk`.

SHA-256: `F515C913482B142D936BD315F8C1D7B4CA4B490780585FFC0C0873E395DFCC17`.

Instruções de instalação, pareamento, contas, mapa offline e limites: [BLUETOOTH_OFFLINE.md](BLUETOOTH_OFFLINE.md).

## Resultados

| Verificação | Resultado |
| --- | --- |
| Suíte JVM completa | 108 testes, zero falhas e zero erros |
| Compilação debug e instrumentação | Aprovadas |
| Lint | Aprovado; permanecem avisos não impeditivos |
| Gateway Bluetooth no Android | Aprovado: autenticação, proposta pela interface, aprovação pelo cliente, coleta, avaliação, auditoria e ruas do APK |
| Origem manual sem GPS | Aprovada: a tela de acompanhamento calculou o trajeto offline a partir da posição manual do coletor |
| RFCOMM com dois Androids emulados | Aprovado, com Wi-Fi e dados móveis desligados |
| RFCOMM com três Androids emulados | Aprovado nos três processos: coordenador e dois participantes conectados simultaneamente |

No ensaio de três instâncias, o cliente enviou uma foto e um pedido, reconectou mantendo a sessão, recebeu e aprovou a proposta do coletor, acompanhou a conclusão e avaliou. O coordenador baixou a foto recebida pelo rádio e concluiu todas as etapas. O terceiro Android, autenticado como administrador, consultou a conclusão, a avaliação e a auditoria. O transporte utilizado foi a API Bluetooth RFCOMM do Android sobre NetSim, sem HTTP/TCP substituto na implementação.

As primeiras tentativas do ensaio exigiram ajuste da automação da confirmação de pareamento do Android. O ensaio também identificou uma topologia de teste inválida: abrir dois sockets SPP para o mesmo serviço a partir do mesmo rádio não representa dois celulares. A verificação de múltiplos participantes foi realizada com três dispositivos emulados e endereços Bluetooth diferentes.

Na regressão geral das telas anteriores, houve uma falha de fixture: o teste de criação de pedido pressupunha que o GPS responderia imediatamente. A fixture foi alterada para usar posição manual determinística, preservando a preferência anterior. A rodada completa e o reteste específico são registrados nos arquivos abaixo; o teste de rádio só roda mediante parâmetros explícitos, e sua suposição é intencionalmente não satisfeita na execução comum.

## Cobertura adicional

- Sessões de cliente, ambulante, empresa e administrador no mesmo coordenador.
- Dois coletores concorrentes: somente um consegue assumir o pedido.
- Repetição da mesma mensagem depois de reiniciar o servidor: um pedido e um registro de auditoria.
- Operação inválida não publica alterações parciais.
- Bloqueio administrativo impede acesso da sessão de outro aparelho; restauração conserva os pedidos.
- Cadastro realizado por uma conexão permite autenticação por outra e troca de senha.
- Fotos somente para contas autorizadas, com limite de tamanho e sem bytes nas atualizações de pedidos.
- Quadros grandes, UTF-8, múltiplos quadros no mesmo fluxo e leitura fragmentada.
- Ruas reais incluídas no APK, cálculo nos dois sentidos para carro/bicicleta/caminhada, mão única e rejeição de destinos sem cobertura.

## Evidências locais

Os arquivos em `build/qa/` são artefatos locais ignorados pelo Git:

- `bluetooth-unit-summary.json` e `app/build/reports/tests/testDebugUnitTest/`.
- `bluetooth-android.txt`: teste específico do gateway e rota manual.
- `bluetooth-radio-host.txt`, `bluetooth-radio-client.txt`, `bluetooth-radio-observer.txt`: ensaio de três dispositivos, cada um com `OK (1 test)`.
- `bluetooth-regression.txt`: rodada das telas existentes com o teste específico Bluetooth incluído.
- `bluetooth-final-retest.txt`: repetição da criação de pedido com fixture corrigida e do fluxo offline.

Foram usadas instâncias Android 17/API 37. Nenhum dado do celular físico conectado foi alterado. A configuração temporária de endereço do segundo emulador foi restaurada e o terceiro emulador temporário foi removido depois do ensaio.

## O que esta validação não certifica

Não houve ensaio com dois ou três celulares físicos, fabricantes diferentes ou distância/interferência no local da apresentação. A capacidade máxima de seis participantes é um limite da implementação, não um número certificado em hardware real. O coordenador permanece necessário; sua ausência interrompe a confirmação de novas alterações até a reconexão.

O mapa e o grafo offline cobrem a região instalada de São Paulo. Não fornecem trânsito, orientação curva a curva, busca mundial de imóveis ou todas as restrições de conversão. As informações detalhadas e os créditos estão no guia da apresentação.
