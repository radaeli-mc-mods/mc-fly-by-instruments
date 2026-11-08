# Plano: integração do Velocity Sensor (Simulated) à rede de aviônicos

## Contexto

O Create Aeronautics (bundle 1.3.2) traz o **Velocity Sensor** do Simulated (`velocity_sensor`), um
bloco direcional que mede a velocidade do ponto do sensor **ao longo da sua face**. Hoje a
Fase 4 do GPWS e o Attitude Indicator estimam velocidade pelo histórico do Radar Altimeter e a
fita de velocidade é um valor fictício (`AttitudeDisplayMock.speed`). O pedido: plugar o sensor na
rede pelo Avionics Wire, ler a velocidade, expô-la na rede e no CC (pela bridge) e ligá-la à fita de
velocidade do Attitude Indicator.

## Mapeamento da API do Simulated (decompilado com `javap`)

| Item | Fato verificado |
| --- | --- |
| Classe | `dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity` |
| Leitura | `public float getAdjustedVelocity()`, em blocos/s, **com sinal** |
| Cálculo | `dot(velocidade mundial do ponto do sensor, normal da face em mundo)`; `|v| ≤ 0,05` vira `0` |
| Velocidade do ponto | `(posição logical − posição last) × 20`, em `Sable`; fora de sublevel fica `0` |
| Atualização | todo tick de servidor (`tick()`) |
| Saída | redstone com força `clamp(v / maxSpeed × 15, −15, 15)` (`maxSpeed` 1–50, padrão 10) |
| CC | `VelocitySensorPeripheral` do Simulated: `getVelocity()` (peripheral próprio do bloco) |

Consequência: o sensor mede **a componente ao longo do eixo da face**. Para medir a velocidade para
a frente, ele deve apontar para o nariz da aeronave. Um sensor lateral lê ~0.

## Entrada na rede (wire)

O bloco não é nosso: não implementa `AvionicsEndpoint`. Os conectores de wire já se prendem em
qualquer face (`WireRules` não restringe o bloco hospedeiro), mas a descoberta de topologia
(`AvionicsNetworkManager.LevelAccess.endpointType`) só reconhece `AvionicsEndpoint`.

**Decisão:** um adaptador de endpoint por classe (`VelocitySensorEndpoint`), consultado por uma
fachada `AvionicsEndpoints` (tipo e sinais de um `BlockEntity`), sem tocar no bloco do Simulated.
Assim o sensor vira o tipo `velocity_sensor` sem mixin. Invalidação de cache já é genérica
(`NeighborNotify`, place, break), então nenhum evento novo é necessário.

## Exposição

| Destino | O que é | Formato |
| --- | --- | --- |
| Rede (canal) | `velocity.axial` no endpoint `velocity_sensor` | `SensorReading<Double>`, `blocks/s`, `VALID` ou `UNAVAILABLE` |
| Avionics Computer | entrada em `getSensors()` com papel `auxiliary` (não entra no GPWS) | `type`, `role`, `x/y/z`, `status`, `value`, `unit` |
| Network Bridge (CC) | `getSignals()`: todos os canais publicados pela rede inteira, com dispositivo e coordenadas relativas | lista de `{name, type, x, y, z, status, value?, unit, gameTime}`; vazia sem rede completa |
| Peripheral do bloco | o do próprio Simulated (`getVelocity`), inalterado | — |

A bridge deixa de ser "só topologia": é o gateway de **sinais** da rede, como já era a rede de
canais. O Computer continua sendo o lugar das leituras com papel e do GPWS.

## Attitude Indicator: fita de velocidade

- **Fonte:** o `velocity_sensor` mais próximo (distância a partir da origem do cabo) que esteja no
  **mesmo sublevel** da mesa e tenha leitura válida. Mesma regra do ARU e do altímetro.
- **Fita ligada na config** (padrão ligada, como a de altitude) **e** com fonte na rede. Sem fonte,
  a fita some e o horizonte cresce, igual à de altitude.
- **Valor:** arredondado e limitado a ±999, com `---` sem leitura. Velocidade negativa (a aeronave anda contra
  a face do sensor) aparece com sinal de menos, `-12`. O visor do próprio sensor mostra só o módulo; a fita mostra
  o sinal.
- **Sync:** `AttitudeDisplayPayload` ganha `speed` e `speedTape`; `AttitudeIndicatorScreenPayload`
  ganha `velocity` (estado do link), `speed` e `speedTape`; ação `speed_tape` no
  `AttitudeIndicatorActionPayload`.
- **Remoção do mock:** o `AttitudeDisplayMock.speed` sai do caminho do jogo. Continua só em
  prévias e testes de layout.
- **Ponder:** as cenas do Attitude Indicator passam uma velocidade fixa de demonstração.

## Limites e riscos

- O sensor lê o eixo da face. Cenário "sensor de cabeça para baixo" dá o sinal invertido; a
  documentação diz para apontar para a frente.
- Sensores em outra aeronave ou fora de sublevel entram como `other_vessel`/`stale`, igual ao altímetro.
- Sem validação em jogo nesta rodada: o plano é validado por testes unitários e build. A validação
  visual e em voo fica pendente.

## Fases

1. Adaptador `VelocitySensorEndpoint` + `AvionicsEndpoints`, usado por `LevelAccess` e
   `connectedSignals`. Testes do mapeamento de leitura.
2. Avionics Computer: entrada `auxiliary` com velocidade. Bridge: `getSignals()`. Testes de
   descrição do sinal e do contrato Lua.
3. Attitude Indicator: `AttitudeIndicatorData.speedTape`, sampler, feed, payloads, sync, tela
   (link + toggle), cliente (sem mock) e lang en/pt. Testes do feed e do layout.
4. Build (`gradlew.bat build`), docs (`avionics-network.md`, `attitude-table-module-plan.md`, este
   plano e `docs/README.md`).

## Estado

Implementado nas quatro fases:

- **Rede:** `VelocitySensorEndpoint` (leitura e canal `velocity.axial`) e `AvionicsEndpoints` (tipo e sinais),
  usados por `AvionicsNetworkManager`. Testes: `VelocitySensorEndpointTest`.
- **Computer e bridge:** entrada `auxiliary` em `getSensors()` (sem GPWS); `getSignals()` na bridge. Testes:
  `NetworkBridgePeripheralTest`.
- **Attitude Indicator:** `AttitudeIndicatorFeed.resolve` com fontes de velocidade, sampler, `AttitudeIndicatorData.speedTape`,
  payloads (`speed`, `speedTape`, `velocity`), ação `speed_tape`, tela com link e interruptor, cliente sem o mock
  e cenas do Ponder com velocidade fixa. Testes: `AttitudeIndicatorFeedTest`, `AttitudeDisplayFeedTest`,
  `AttitudeIndicatorDataTest`, `AttitudeIndicatorScreenTest`.

Verificado: `gradlew.bat test` passa com os testes novos. **Não verificado:** teste no jogo (sensor
montado, fio até a mesa, leitura no Computer e na bridge via CC, fita com o sensor apontado para o nariz).
