# Flight Management System (FMS)

**Estado:** implementação concluída; validação funcional in-game pendente.

## Objetivo

Adicionar à Modular Table uma memória simples de waypoints e permitir que um
Navigator use o destino ativo como alvo da Navigation Table do Aeronautics.
O FMS fornece canais de dados; não cria alertas nem altera o GPWS.

## Módulo e persistência

- `fms_module` ocupa um footprint virtual de 2 células de largura × 3 de altura (2×3) na mesa.
- O item se chama apenas **FMS** no jogo (id de registro `fms_module` mantido).
- A tela segue a linguagem do guia visual §12 (`FmsScreen`, um painel só): destino ativo
  no topo (distância, rumo, posição), waypoints como opções de radio (clique voa direto,
  clique no ativo limpa, clique direito edita; até 7 visíveis, o resto rola com a roda ou a barra; ao abrir, o destino fica à vista), estado da
  Bússola FMS com botão de vincular e, no rodapé, Novo waypoint / Limpar destino.
- `FmsWaypointScreen` cria e edita: um waypoint novo já vem com o bloco do jogador e o
  nome em foco (nome + Enter marca o ponto); Tab troca de campo; a edição vale ao sair
  com Concluir ou ESC; Excluir pede um segundo clique.
- Waypoints podem usar a posição atual ou coordenadas inseridas manualmente.
- Cada waypoint tem UUID, nome de 1–32 caracteres, dimensão e coordenadas XYZ.
  As coordenadas são salvas como posições inteiras de bloco; valores fracionários
  são convertidos para o bloco que contém a posição (floor, inclusive em negativos).
  Nomes duplicados são aceitos; não há limite artificial de quantidade.
- O UUID do módulo, os waypoints e o UUID selecionado ficam persistidos no módulo.
- Remover o FMS ou quebrar a mesa grava o estado no item `fms_module`; reinstalar
  restaura o mesmo módulo e mantém o vínculo do Navigator.
- Se uma cópia com UUID duplicado for instalada enquanto a instância original
  estiver carregada, a cópia recebe UUID novo, mantendo seus waypoints.

## Navigator e Navigation Table

- `fms_navigator` (FMS Compass) é uma bússola redonda em 3D, na linguagem do módulo FMS
  ([fonte visual](../blockbench/fms-navigator/README.md)) e é stack size 1. Ligado a um
  módulo, troca para o modelo `fms_navigator_linked` (propriedade de item `linked`): ponta do
  ponteiro e reflexo da lente acesos.
- O botão "Vincular bússola" da tela associa a primeira Bússola FMS do inventário ao
  UUID exato do módulo aberto; o item permanece no inventário.
- Se ela já estiver ligada a outro FMS, o botão vira "Vincular a este FMS": o rótulo
  explícito é a confirmação de revínculo que o servidor exige.
- O item é registrado como `NavigationTarget` do Aeronautics e pode ser inserido
  na Navigation Table nativa. A mesa aponta para o waypoint ativo do FMS ligado e
  reavalia o alvo a cada tick do servidor: trocar ou limpar o waypoint chega à mesa
  no tick seguinte.
- A resolução procura FMS carregados no mesmo `Level`, no mundo ou em qualquer
  sublevel Sable. Não força carga de chunks.
- Alcance (`FmsNavigatorRange`): sem limite quando mesa e módulo estão no mesmo
  sublevel (mesma contraption); 256 blocos entre contraptions diferentes ou quando
  um dos dois está no mundo.
- Waypoint não selecionado, UUID indisponível, dimensão diferente ou fora do
  alcance resultam em ausência de target; Y não influencia a direção horizontal
  do compasso.
- Na mesa, o FMS Compass é desenhado deitado sobre o tampo de latão
  (`FmsNavigatorTableRenderer`, via `RenderableNavigationTarget`) e gira com a seta
  da mesa, com o ponteiro pintado apontando para o waypoint.

## Canais de dados

O módulo publica dois `AvionicsDataChannel` na rede de aviônicos:

| Canal | Contrato |
| --- | --- |
| `waypoint_selected` | `VALID` booleano; `true` quando há waypoint selecionado, caso contrário `false` |
| `distance_to_waypoint` | Distância horizontal X/Z, em blocos; `UNAVAILABLE` sem alvo ou quando o alvo pertence a outra dimensão |

A posição do módulo e os waypoints usam coordenadas lógicas/globais calculadas
com a pose Sable. O estado é amostrado na cadência de sensores configurada
(padrão atual: 5 ticks); eventos só são publicados quando status ou valor muda.
No `modular_table`, os métodos de leitura consultam o estado atual e os eventos
informam alterações confirmadas no servidor.

### API CC:Tweaked

- `getFmsWaypoints(column, row)`
- `createWaypoint(column, row, name, x, y, z)`
- `updateWaypoint(column, row, waypointId, name, x, y, z)`
- `deleteWaypoint(column, row, waypointId)`
- `selectWaypoint(column, row, waypointId)`
- `clearWaypointSelection(column, row)`
- `getFmsSignals(column, row)`
- `getNetworkSignals()` para canais de endpoints conectados à rede física completa

A lista marca com `>` o waypoint selecionado. Editar coordenadas preserva a
dimensão original do waypoint; usar a posição atual cria um waypoint novo na
dimensão atual.

Eventos:

- `modular_table_fms_waypoint_changed(name, action, column, row, waypoint)`
- `modular_table_fms_signal_changed(name, column, row, channel, reading)`

Leituras usam `status`, `unit`, `gameTime` e `value` quando válida. `getNetworkSignals`
retorna tipo e deslocamento do endpoint mais canal e snapshot tipado. Canais de
módulos virtuais também incluem `module_column` e `module_row` para distinguir
múltiplos FMS no mesmo endpoint. A Network Bridge continua expondo topologia;
a UI/API do FMS não despacha alertas ao GPWS.

## Aparência nesta entrega

O FMS usa o modelo v2.1: carcaça de ferro industrial, tela de lente escura
sem conteúdo rebaixada numa moldura de latão na parte de cima, e dois knobs
de latão e duas teclas embaixo. Fonte e exportação:
[Blockbench FMS 2×3](../blockbench/fms-module-2x3/README.md).
O FMS Compass ainda usa a textura vanilla de bússola. Com um waypoint
selecionado, a tela mostra uma bússola que gira com a proa do FMS (ou da
contraption) e marca o rumo do waypoint com um dot de latão (só visual); o display funcional continua reservado para uma etapa posterior.

## Testes e aceite

- CRUD, seleção, limpeza, validação de nome/coordenadas e persistência NBT;
- item do módulo preserva dados ao remover, recolocar e quebrar/reinstalar mesa;
- footprint 2×3 (2 de largura × 3 de altura) e rejeição de sobreposição/borda;
- UUID duplicado gera identidade independente sem perder waypoints;
- leitura dos canais, estados unavailable, distância X/Z, eventos e API Lua;
- lookup do Navigator restringe dimensão e sublevel e não força chunk loading;
- integração com o registry `NavigationTarget` do Aeronautics;
- build automatizado; validar in-game o CRUD, inventário, vínculo, reload e compass.

O build/teste automatizado não substitui o teste manual da Navigation Table em
sublevel voador.
