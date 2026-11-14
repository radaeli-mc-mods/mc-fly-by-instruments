# Avionics Computer

O Avionics Computer (antigo GPWS Computer, renomeado em 2026-10-28) é o ponto
central de uma aeronave: lê todos os sensores da rede de aviônicos, atribui
um papel a cada um, roda a função GPWS sobre as leituras que eles já produziram
e é o gateway CC:Tweaked da rede inteira. Não dispara raycasts próprios, não
carrega chunks e avalia na mesma cadência configurável dos sensores. Leituras
com mais de dois intervalos de amostragem são consideradas obsoletas.

A disponibilidade do sensor é independente da leitura: cada bloco informa se
continua operante mesmo sem conseguir medir. O altímetro mantém a última
leitura válida. Se ela estava nos 10% finais do alcance e os retornos somem,
o GPWS considera **sem terreno abaixo** enquanto o sensor continuar respondendo.
Não usa o Y absoluto, nem transforma a leitura antiga em altitude atual. Quando
o terreno reaparece, o cálculo de tendência recomeça para evitar uma falsa queda.
Sem histórico próximo do limite, a função informa **SEM DADOS**; isso, por si
só, não dispara GPWS INOP. Falha de aquisição, falta do sensor, perda da
confirmação de funcionamento ou rede incompleta continuam indicando INOP.

Princípio: **configura uma vez, depois só funciona**. Como num GPWS real, não há
armar, perfis nem limites editáveis: o computador fica ativo enquanto estiver no
mundo, os envelopes são fixos e o jogador só escolhe a sensibilidade e inibe
funções. O raciocínio e o histórico das decisões estão no
[plano](avionics-computer-plan.md).

## Uso no jogo

Conecte o computador e os sensores ao mesmo cabo. O gabinete (v2.1, ferro
industrial com latão) tem na frente uma tela no estilo do CC:Tweaked e o botão de
teste:

- **Tela do bloco:** cursor piscando em operação normal; `!` âmbar piscando em
  atenção; `!` vermelho piscando em alerta ou autoteste; `x` âmbar fixo em GPWS
  INOP. Um computador recém-colocado mostra INOP até a primeira avaliação.
- **Botão de teste:** clicar nele roda o autoteste (3 s com todas as funções em
  `WARNING`). Clicar no resto do bloco abre a tela.
- O cabo conecta em qualquer face. A fonte editável fica em
  [Blockbench](../blockbench/avionics-computer/README.md).

A tela segue a linguagem visual das telas do mod (guia, seção 12) e se atualiza a
cada meio segundo:

- **Cabeçalho:** sensibilidade atual e o estado que importa agora: `NORMAL`,
  `ATENÇÃO · <função>`, `ALERTA · <função>`, `GPWS INOP` (com o motivo de rede,
  quando é o caso) ou `AUTOTESTE`.
- **Sensores:** cada sensor com o papel atribuído e a leitura ao vivo, ou o
  problema ("sem leitura recente", "outra aeronave",
  "varredura parcial", "lado não definido").
- **Funções:** cada função `ativa`, em atenção/alerta, `SEM DADOS: <motivo>`, `INOP: <motivo>` ou
  `inibida`. Clicar numa função alterna a inibição (GPWS INOP não pode ser
  inibido).
- **Rodapé:** `Sensibilidade` cicla baixa → normal → alta; `Testar` roda o autoteste.

### Papéis dos sensores

Atribuídos a cada avaliação:

| Sensor | Papel |
|---|---|
| Radar Altimeter com a menor altura válida | Altímetro principal |
| Outros Radar Altimeters | Altímetro secundário |
| Terrain Scanner | O lado escolhido na tela do próprio scanner: frontal, traseiro, lateral E ou lateral D |
| Terrain Scanner sem lado escolhido | `Scanner` com o aviso "lado não definido" |

**O lado é sempre manual.** Nada é deduzido da direção dos blocos. Ao colocar um
Terrain Scanner, abra a tela dele e escolha para qual lado da aeronave ele olha
(**Olha para**: frente, traseira, lado esquerdo, lado direito). O lado fica salvo
no scanner e o computador só o lê.

**Nariz da aeronave.** O computador tira o nariz dos scanners com lado escolhido:
o nariz fica para onde olha um scanner frontal, oposto a um traseiro, e a 90° de
um lateral. Havendo vários, vale o frontal, depois traseiro, esquerdo e direito,
cada grupo por posição. Esse nariz orienta o cone do `PULL UP` e o `BANK ANGLE`.
Sem nenhum scanner com lado, nenhum contato conta como "à frente" para o `PULL UP`
(ele usa só o tempo até o solo) e o `BANK ANGLE` usa a frente do computador como
eixo, o único caso em que a direção de um bloco ainda entra.

Uma varredura frontal completa vale dentro da abertura escolhida, inclusive
30° ou 35°. Abertura menor reduz o campo observado, sem indicar falha ou INOP.
O `PULL UP` também pode usar o tempo até o solo pelo altímetro quando um scanner
instalado não fornece cobertura frontal completa.

### Terreno e trajetória

`TERRAIN` e o ramo de obstáculos do `PULL UP` avaliam os contatos em relação à
trajetória projetada pela velocidade mundial no ponto do scanner. Em voo
nivelado, retornos do chão abaixo dessa trajetória não disparam esses alertas.
Uma margem de um bloco abaixo da face do sensor continua protegida. Durante a
descida, contatos que alcançam a trajetória voltam a contar; paredes e terreno
elevado à frente continuam podendo alertar. Sem movimento horizontal, a
comparação usa a altura do sensor; em descida vertical, mantém os contatos de
solo como possíveis ameaças.

As leituras brutas do scanner e suas saídas de redstone continuam incluindo o
chão observado. `TOO LOW`, `SINK RATE` e o tempo até o solo usam suas próprias
regras; esta filtragem não desativa a proteção de baixa altura ou descida.

## Funções e envelopes

As fases de voo (`GROUND`, `TAKEOFF`, `CLIMB`, `CRUISE`, `DESCENT`, `APPROACH`,
`UNKNOWN`) continuam internas: só escolhem o envelope e suprimem alertas no solo.
A velocidade vertical é estimada pelo histórico do menor clearance válido, em
blocos por segundo. `GROUND` exige AGL ≤ 1,5 bloco e velocidade vertical absoluta
≤ 0,5 bloco/s durante 20 ticks; enquanto isso é confirmado, os canais de risco
ficam quietos.

Os valores abaixo são parâmetros de gameplay em blocos, blocos por segundo,
segundos e graus, não limites aeronáuticos reais.

| Função (canal) | Modo real | Comportamento |
|---|---|---|
| Terrain ahead (`terrain`) | FLTA | `CAUTION` com contato que ameaça a trajetória a ≤32 blocos; em aproximação, ≤8. |
| Pull up (`pull_up`) | 2 | `WARNING` imediato para contato que ameaça a trajetória a ≤8 blocos no cone frontal de 45°, ou tempo até o solo ≤2 s em descida. |
| Sink rate (`sink_rate`) | 1 | `CAUTION` a partir de 6 blocos/s de descida; `WARNING` a partir de 12. |
| Don't sink (`dont_sink`) | 3 | `WARNING` por perda ≥4 blocos frente ao pico recente na decolagem/subida (janela de 5 s). |
| Too low (`too_low`) | 4 | Fora do solo: `CAUTION` a ≤8 blocos AGL, `WARNING` a ≤3; em aproximação, ≤3 e ≤1. |
| Bank angle (`bank_angle`) | 6 | `CAUTION` a partir de 30°, `WARNING` a partir de 45°, pela pose do sublevel e o nariz da aeronave. |
| GPWS INOP (`sensor_fail`) | — | `CAUTION` se a rede não está completa, falta o Radar Altimeter ou uma fonte instalada falhou/está obsoleta. |

A sensibilidade desloca os limites em 20%: limites de distância crescem em
`alta`; limites de descida, perda de altitude e bank diminuem em `alta`. Há 10%
de histerese; as funções exigem duas amostras para ativar e duas para limpar,
exceto `PULL UP`, que ativa na primeira.

Prioridade (uma por vez, a mais urgente primeiro): Pull up > Terrain ahead > Too
low > Sink rate > Don't sink > Bank angle; alertas antes de atenções; GPWS INOP
só aparece sem alerta de risco.

**INOP com motivo:** uma função sem entrada utilizável fica `unavailable` e diz
por quê: `no_altimeter`, `no_scanner`, `no_front_scanner`, `partial_coverage`,
`no_attitude` ou `settling` (esperando a segunda amostra do altímetro). Cobertura
parcial sem contato nunca é tratada como caminho livre.

## Canais na rede

O computador publica `gpws.<canal>` (severidade 0–3, ou `unavailable`) para cada
função e `gpws.any` com a maior severidade. A mesa modular lê esses canais pelas
indicações das lâmpadas ([mesa](modular-table.md)).

## Peripheral CC:Tweaked

Tipo do peripheral: `avionics_computer`. Os sensores continuam com peripherals
próprios para fluxos que usam um único sensor; a [Network Bridge](avionics-network.md)
continua sendo a ferramenta de topologia do cabo.

| Método | Resultado/efeito |
|---|---|
| `getStatus()` | `state` (`normal`, `caution`, `warning`, `inop`, `test`), `alert` (canal), `testing`, `sensitivity`, `flightPhase`, rede (`networkStatus`, `networkComplete`, `endpointCount`), `sensorCount`, e `clearance`, `verticalSpeed` e `bankAngle` com status/unidade/tempo. |
| `getSensors()` | Lista de `{type, role, x, y, z, status, unit, value?, issue?}`; coordenadas relativas ao computador. Um Velocity Sensor aparece com papel `auxiliary`, em `blocks/s`, e não entra no GPWS. |
| `getAlerts()` | Tabela por canal com `state` (`inactive`, `active`, `unavailable`, `inhibited`), `severity`, `gameTime` e `reason` quando INOP. |
| `getConfiguration()` | `{sensitivity, inhibited}`. |
| `test()` | Roda o autoteste; `false` se já está em teste. |
| `setSensitivity(value)` | `low`, `normal` ou `high`. |
| `setInhibited(function, bool)` | Inibe ou reativa uma função; `sensor_fail` não pode ser inibido. |

Eventos, cada um com o nome da attachment e um snapshot:
`avionics_alert_changed` (canais que mudaram de estado/severidade),
`avionics_sensors_changed` (lista de sensores quando muda papel, saúde ou
composição; leituras sozinhas não disparam), `avionics_status_changed` (estado
geral) e `avionics_configuration_changed`.

```lua
local avionics = assert(peripheral.find("avionics_computer"), "Avionics Computer não encontrado")

for _, sensor in ipairs(avionics.getSensors()) do
  print(sensor.role, sensor.status, sensor.value or "-")
end

while true do
  local event, name, data = os.pullEvent()
  if event == "avionics_status_changed" then
    print("Estado:", data.state, data.alert)
  end
end
```

## Mundos antigos

O ID `gpws_computer` é um alias de `avionics_computer`: blocos e itens de mundos
antigos carregam como Avionics Computer. Das configurações antigas, só a
sensibilidade é mantida; perfis, modos, fase manual e regras por canal/fase são
descartados. Scripts que usavam o peripheral `gpws` precisam trocar para
`avionics_computer`.
