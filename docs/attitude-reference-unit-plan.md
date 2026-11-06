# Plano: Attitude Reference Unit e Attitude Indicator

**Estado:** fase 1 (ARU), fase 2 (display Attitude Indicator ligado à rede, com tela de configuração; sem Ponder) e fase 4 (derivados) implementadas; GPWS da fase 3 implementado. Pendentes: o slip no display, o Ponder e as saídas de redstone por faixa (resto da fase 3). O display **não** recalcula a atitude no cliente: usa a leitura do ARU enviada a 10 Hz e suavizada no cliente (ver [modular-table.md](modular-table.md)). Decisões em aberto na última seção.

## Objetivo

Criar uma fonte de atitude própria da rede aviônica, o **Attitude Reference Unit**
(ARU), e um display de horizonte artificial para a mesa modular, o **Attitude
Indicator**. O ARU guarda a configuração da aeronave (faces Nariz e Acima, calibração de nível) e
publica pitch, roll e dados derivados como canais tipados. O display, o GPWS e o
CC:Tweaked consomem esses canais.

## Por que não usar o Gimbal Sensor do Simulated

O `simulated:gimbal_sensor` já oferece gimbal animado, bússola, redstone por
inclinação, peripheral CC (`getAngles`) e Display Source. O ARU **não repete** isso:

| | Gimbal Sensor (Simulated) | ARU (este mod) |
|---|---|---|
| Princípio | Pêndulo físico: inércia, amortecimento, torque de gravidade | Referência estável: lê a pose do sublevel Sable |
| Curva coordenada | Indica nível (a força centrípeta engana o pêndulo) | Indica o bank verdadeiro |
| Faixa | Limitada (`MAX_ANGLE_X/Z`) | Roll ±180°, pitch ±90°, voo invertido |
| Eixos | X/Z do bloco | Pitch/roll relativos às faces Nariz e Acima |
| Saída | Redstone proporcional por face, CC, Display Link | Canais `attitude.*` com status/INOP, CC, redstone por faixa |
| Visual | Gimbal exposto e animado | Caixa de aviônica, sem gimbal exposto |

O redstone proporcional por inclinação **não será copiado**. Quem só quer isso usa o
Gimbal Sensor. A saída de redstone do ARU segue o padrão dos nossos sensores (faixas
configuráveis, faces ou Create Redstone Link).

## Bloco: Attitude Reference Unit

- Id `attitude_reference_unit`; endpoint da Avionics Cable Network (tipo
  `attitude_reference_unit`), com soquetes de cabo como os sensores atuais.
- **Nariz e Acima por face:** duas propriedades de blockstate, `nose` e `up`, cada
  uma com as 6 direções. Juntas definem o referencial da aeronave em qualquer
  montagem (piso, teto, parede, deitado). Ver [Orientação por faces](#orientação-por-faces).
- **Calibração de nível:** offsets de pitch e roll persistidos no block entity.
  "Calibrar nível" grava a atitude atual como 0°/0° (útil para naves paradas
  inclinadas); "Zerar" remove os offsets. Limite de ±15° por eixo.
- **Amortecimento:** filtro exponencial opcional (desligado, leve, forte) só nas
  leituras publicadas; o display continua suave porque interpola no cliente.
- Tela de configuração no padrão do guia visual §12 (nariz, acima, calibração,
  amortecimento, saídas de redstone, leitura ao vivo).
- Modelo no Blockbench partindo da receita dos sensores (guia §8): caixa de latão e
  andesito, sem rotor visível, com marcação de face dinâmica (ver
  [Visual](#visual-faces-marcadas)). Colisão e `*AssetsTest` atualizados.

### Orientação por faces

- Propriedades `nose` e `up` (`DirectionProperty`, 6 valores cada). Só 24 das 36
  combinações são válidas (`nose ⟂ up`); o bloco nunca fica num estado inválido.
- **Colocação:** `nose` = direção horizontal para onde o jogador olha; `up` = `UP`
  local. Cobre o caso comum sem configurar nada.
- **Wrench, clique direito numa face:** essa face vira o **Nariz**. Se ela era o
  Acima, o Acima passa a ser o nariz antigo (continua perpendicular).
- **Wrench + scroll olhando para uma face:** essa face vira o **Acima**, com rótulo
  flutuante "Acima" (padrão de scroll com wrench do mod). Se ela era o Nariz, o Nariz
  passa a ser o Acima antigo. Nenhuma das duas ações gira o bloco.
- **Tela:** planificação do cubo com as 6 faces clicáveis, mostrando Nariz e Acima;
  escolher uma face colinear com a outra seleção aplica a mesma troca acima.
- O peripheral expõe `setOrientation(nose, up)`, que recusa pares não
  perpendiculares.
- `rotate`/`mirror` do bloco transformam `nose` e `up` juntos, para schematics,
  contraptions do Create e montagem do Sable preservarem a orientação.
- Sneak + wrench segue sendo a retirada padrão do Create (`AvionicsWrenchable`).

### Cálculo

A única fonte é a pose do sublevel Sable, sem sensor físico. A pose é a rotação
completa da nave desde a montagem, e o grid local é sempre alinhado aos eixos. Por
isso, as faces `nose` e `up` formam o referencial completo da aeronave
(`right = forward × up`) em qualquer montagem. Pitch, roll e heading saem direto
dele; as taxas da fase 4 exigem também o histórico de pose entre ticks.

Com `forward` = vetor da face `nose` transformado pela `logicalPose()` e `up` =
vetor da face `up` transformado, já com os offsets aplicados:

- `pitch = asin(forward · WORLD_UP)`, em graus, positivo com nariz para cima.
- `roll` = o cálculo atual de `GpwsAttitude.bankDegrees(forward, up)`.
- `heading` = direção de `forward` projetada no plano horizontal, 0° = norte,
  sentido horário (mesma convenção do `FmsCompass`).
- Perto de pitch ±90° o roll fica indefinido: publicar o último valor válido e
  marcar o status como `degraded` até sair da zona (±85°).
- Bloco fora de um sublevel: atitude 0/0 e status `ground` (válido).

`GpwsAttitude` passa a ser `AttitudeMath`, com pitch, roll e heading testados nas
24 orientações válidas (nível, inclinado, invertido, perto da vertical).

### Visual: caixa de vidro com hastes de referência (v3, 2026-11-05)

Substitui os arcos nas faces (v2.1). O bloco é um horizonte artificial visto por dentro: um
cubo de vidro, quatro postes verticais slim de ferro industrial nas quinas, um gimbal no
centro e duas hastes de referência que mostram o frame configurado.

- **Hastes (blockstate, sem animação):** a haste do **Nariz** tem bulbo de cobre na
  ponta da frente e tampa lisa atrás; a haste do **Acima** tem bulbo de latão em cima e
  tampa lisa embaixo. Um modelo-fonte por haste (`arm_nose` com a frente em NORTH, `arm_up`
  com a frente em UP), girado por `x`/`y` do blockstate conforme `nose` e `up`.
- **Gimbal (BER, não reage a nada):** um anel que balança ±14° em Y e um volante que gira
  em Z, em loop fixo (`AttitudeReferenceRenderer`). Os modelos `gimbal_ring` e `gimbal_rotor`
  são registrados como modelos adicionais e não entram no blockstate.
- **Sockets de dados:** 6 anéis 4×4×1 no centro de cada parede, por fora do vidro; o contrato
  de `CableSocketProvider` não muda.
- **Sem `powered`:** o indicador de redstone foi removido (a propriedade nunca era ligada).
- Testes de assets: volume dos modelos estáticos igual ao `SHAPE`; nas 24 orientações a ponta
  de cada haste, depois das rotações do blockstate, cai na face de `nose` e de `up`.
- Convenção registrada no [guia visual §8.7](visual-design-guide.md#87-faces-configuráveis-por-blockstate).

## Canais na rede

Publicados pelo próprio ARU (`AvionicsSignalProvider`), a cada tick do servidor:

| Canal | Unidade | Fase |
|---|---|---|
| `attitude.pitch` | graus | 1 |
| `attitude.roll` | graus, positivo = asa direita baixa | 1 |
| `attitude.heading` | graus, 0–360 | 1 |
| `attitude.turn_rate` | graus/s, pela variação de heading | 4 |
| `attitude.vertical_speed` | blocos/s, pela variação de Y da pose | 4 |
| `attitude.slip` | graus, pela força lateral no referencial da nave | 4 |

Cada canal leva `SensorReading` com status (`valid`, `degraded`, `unavailable`).
Com mais de um ARU na rede, vale o mais próximo do consumidor, como o altímetro no
Computer; os outros aparecem como redundantes no diagnóstico.

## Display: Attitude Indicator (módulo da mesa)

- Módulo `attitude_indicator_module`, footprint 2×2. Modelo segue o guia §9.
- Renderização só no cliente, no mesmo caminho do FMS Compass em
  `ModularTableRenderer`: usa `ClientSubLevel.renderPose(partialTick)` da mesa para
  ficar suave a cada frame.
- O servidor sincroniza no módulo apenas o que o cliente não sabe: as faces Nariz e Acima e os
  offsets do ARU ligado, o status e o motivo de INOP. O cliente recalcula a atitude
  com a pose interpolada.
- ARU e mesa precisam estar no mesmo sublevel; caso contrário, INOP
  `other_vessel`.
- Desenho: disco céu/terra cortado pela linha do horizonte (polígono do recorte do
  círculo, sem stencil), escada de pitch a cada 5°/10°, escala de bank (10, 20, 30,
  45, 60°) com índice, símbolo fixo da aeronave. Animação de alinhamento ao ligar,
  como o `intro` do compasso.
- **Bandeira ATT** (vermelha) quando não há ARU na rede, rede incompleta ou status
  `unavailable`.
- Fase 4: bolinha de slip sob o disco, lida de `attitude.slip`.

## GPWS

- Na fase 3, o BANK ANGLE passa a preferir `attitude.roll` de um ARU válido.
- Sem ARU, mantém o comportamento atual (pose + `FACING` do Computer). Nenhum
  mundo existente perde o alerta.
- `getStatus().bankAngle` informa a fonte (`aru` ou `computer`).

## Redstone

- `SensorOutputController` com canais novos: `NOSE_UP`, `NOSE_DOWN`, `ROLL_LEFT` e
  `ROLL_RIGHT`. Cada um tem uma faixa em graus (0–90 para pitch, 0–180 para roll),
  em modo faces ou Create Redstone Link.
- `SensorOutputConfig.Range` hoje valida contra o alcance do sensor em blocos; será
  generalizada para aceitar o limite angular do canal.
- Atitude `unavailable` zera todas as saídas.

## Peripheral CC:Tweaked

Tipo `attitude_reference_unit`:

| Método | Retorno |
|---|---|
| `getAttitude()` | `pitch`, `roll`, `heading`, `status`, `time` |
| `getRates()` | `turnRate`, `verticalSpeed`, `slip` (fase 4) |
| `getConfig()` | `nose`, `up`, `pitchOffset`, `rollOffset`, `damping` |
| `setOrientation(nose, up)` | troca as faces; erro se não forem perpendiculares |
| `calibrateLevel()` / `resetCalibration()` | grava ou zera os offsets |

## Fases

1. **ARU:** bloco com `nose`/`up` por face, modelo multipart com faces marcadas, `AttitudeMath`, canais pitch/roll/heading, tela,
   peripheral, receita, lang, docs, testes.
2. **Attitude Indicator:** módulo, renderer, sincronização, bandeira ATT, Ponder.
3. **Integrações:** GPWS preferindo o ARU, redstone por faixa.
4. **Derivados:** turn rate, vertical speed, slip; bolinha no display.

## Decisões em aberto

- **Rotação do Create:** exigir RPM para o rotor (spin-up, perda de leitura sem
  rotação, como falha de vácuo). Proposta: opção de servidor, desligada por padrão.
- **Gestos da wrench:** clique = Nariz (implementado). Scroll = Acima ainda não existe; o Acima é definido só pela tela.
- **Hastes de referência:** validar no jogo a leitura das pontas (cobre = Nariz, latão = Acima)
  a ~5 blocos, através do vidro.
- **Nome no jogo:** "Attitude Reference Unit" ou "Attitude Gyro".
- **Footprint do display:** 2×2 (proposto) ou 3×3.
- **Vertical speed do ARU no GPWS:** substituir a estimativa pelo histórico do Radar
  Altimeter ou só complementá-la (a do altímetro mede AGL, a do ARU mede altitude).
