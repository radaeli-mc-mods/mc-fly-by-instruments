# Plano — Avionics Wire (cabos físicos)

Status: **em implementação (2026-10-28)**. O Avionics Cable atual continua
como está; tudo aqui nasce como itens, blocos e classes novos.

### Decisões do usuário (2026-10-28)

1. Rampa **Fio isolado** aprovada para o guia.
2. **Fio pode atravessar blocos sólidos** por enquanto (sem raycast de obstrução).
3. Tecla do Wiring View: **`V`** (conflito conhecido com o Simple Voice Chat aceito; é
   configurável em Controles).
4. Fio navio↔chão **fora do escopo**.

Referência: vídeo "Wires" do Create Aviate (cabo mecânico que transmite rotação). Quadros
em `reference/visual/wires-video/` (fora do git): `sheet_01..05.png` a 1 quadro/s e
`detail_a.png`/`detail_b.png` com os momentos-chave.

## 1. O que o vídeo mostra (e o que copiamos da ideia)

| Momento | O que acontece | No nosso mod |
| --- | --- | --- |
| 0–3 s | *Small Wire Spool* preso na face do motor: o ponto onde o cabo entra no bloco | **Wire Connector** (latão) |
| 4–7 s | *Wire Hook*: pecinha presa no chão/em qualquer face, com caixa de seleção própria | **Wire Support** (ferro industrial) |
| 8–16 s | Com o item *Mechanical Wire*: clica no spool, nos hooks em ordem e no spool final. O cabo contorna o spool com curva suave e sai paralelo à face | Item **Avionics Wire**, roteamento clicando nos pontos |
| 17–21 s | *Wire Bearing*: bloco de mancal | Fora do escopo (não há rotação) |
| 24–28 s | Blocos colados com Super Glue perto do cabo | Cabo precisa acompanhar o sublevel/contraption |
| 29–34 s | Tecla liga um modo "raio X": mundo escurecido, cabos através dos blocos em amarelo, nós como quadrados dourados, trecho sob a mira em branco | **Wiring View** |
| 35–38 s | Visão normal do traçado final, com dobras nos hooks | Fio preto com caimento, dobras arredondadas |

Observação: no vídeo a tecla do raio X era `V`, a mesma do Simple Voice Chat (a mensagem
"Voice chat icons visible" aparece junto). Nossa tecla padrão precisa evitar esse conflito.

O que **não** copiamos: o cabo do vídeo é grosso (parece um eixo), translúcido e reto
entre os pontos. O nosso é um fio preto fino, com caimento.

## 2. Resultado esperado

1. O jogador prende **Wire Connectors** (latão) nas faces dos sensores, da mesa, da
   bridge, do Avionics Computer, ou de qualquer bloco que sirva de junção.
2. Prende **Wire Supports** (ferro industrial) em qualquer face firme pelo caminho.
3. Com o carretel **Avionics Wire** na mão, clica no conector inicial, nos suportes na
   ordem e no conector final. Vê uma prévia do fio com caimento real, o comprimento e o
   custo antes de fechar.
4. O fio aparece como um fio de verdade: preto, fino, caindo levemente entre os pontos,
   com curva suave nos suportes e saída perpendicular nos conectores.
5. A rede de dados é a mesma de hoje: `AvionicsNetworkManager.snapshot()`,
   `connectedSignals()`, Network Bridge, Avionics Computer e mesa não mudam de contrato.
6. Uma tecla liga o **Wiring View**: o mundo escurece e todos os fios, conectores e
   suportes próximos aparecem através dos blocos, coloridos pelo estado da rede.
7. Tudo continua funcionando dentro de um sublevel do Aeronautics em voo.

## 3. Decisões de arquitetura (com recomendação)

### 3.1 Conectores e suportes são blocos; o fio não é

Conector e suporte viram blocos pequenos presos na face (como botão/alavanca: ocupam a
célula de ar vizinha, `waterlogged`), com block entity. O **fio** não ocupa célula
nenhuma: é dado guardado nos block entities das pontas e renderizado.

Por quê blocos e não "anexos" sem bloco:

- **Sable/Aeronautics**: quando o navio é montado, blocos e block entities vão juntos para
  o sublevel. Dado em `SavedData` global ou em attachment de chunk ficaria para trás.
- **Create**: contraptions (bearing, glue) também copiam block entities; dá para girar o
  dado com `TransformableBlockEntity`.
- Colisão, seleção, quebra, drops, som e sincronização vêm de graça.
- O Simulated faz o mesmo com `RopeConnectorBlock`/`RopeWinchBlock` (referência de
  arquitetura no jar `simulated-neoforge-1.21.1-1.3.2`).

Custo: não dá para pôr um conector entre dois blocos encostados. Aceitável; nesse caso os
blocos já se tocam e o cabo antigo continua disponível.

### 3.2 Modelo de dados (sempre em offsets relativos)

```
WireRoute  { UUID id; List<WireAnchor> anchors }   // anchors[0] e o último são conectores
WireAnchor { BlockPos offset; int lane }           // offset relativo ao dono; lane só nos suportes
WireRef    { UUID id; BlockPos owner; BlockPos farEnd }   // offsets relativos a quem guarda
```

- O conector **inicial** é o dono e guarda a `WireRoute` inteira (é ele quem renderiza).
- O conector final e cada suporte guardam um `WireRef`. O conector final guarda também
  `farEnd`, para a busca de topologia atravessar o fio sem ler o dono.
- Offsets relativos sobrevivem à montagem do sublevel. Em contraptions do Create, o
  `transform()` gira os offsets.
- Capacidade: conector com até 4 fios (vira ponto de junção/ramificação, sem bloco extra);
  suporte com até 3 fios, cada um numa **lane** (esquerda/centro/direita, 2 px de
  distância), para fios paralelos não se sobreporem.

**Invariante:** um fio só existe com todos os seus pontos. Quebrar conector ou suporte
remove do mundo todo fio que passa por ele e devolve o item. Pontas em chunk descarregado
são limpas quando carregam (validação adiada no `onLoad`). **Chunk descarregado nunca
apaga fio.**

### 3.3 Topologia: a rede de hoje com uma aresta a mais

`NetworkDiscovery` passa a percorrer um grafo com três tipos de aresta:

| Aresta | Regra |
| --- | --- |
| cabo ↔ cabo / endpoint | adjacência nas 6 faces (como hoje) |
| conector ↔ bloco hospedeiro | o bloco onde o conector está preso (endpoint, cabo antigo ou bloco qualquer) |
| conector ↔ conector | via `WireRef.farEnd`; ponta descarregada → `INCOMPLETE` |

- `NetworkAccess` ganha `wireNeighbors(BlockPos)` e `isWireNode(BlockPos)`; o resto do
  algoritmo (limite de visitas, sem carregar chunks, `LIMIT_EXCEEDED`) fica igual.
- Interop: conector preso num **Avionics Cable antigo** liga as duas redes.
- `NetworkSnapshot` ganha `wireCount` (campo novo; `cableCount` mantém o significado). A
  Lua do `getNetworkStatus()` ganha `wireCount` sem quebrar scripts.
- Criar/remover fio chama `AvionicsNetworkManager.markDirty()`, como os eventos de bloco
  já fazem em `AvionicsNetworkEvents`.

### 3.4 Sublevels

- Na v1 todos os pontos de um fio ficam no **mesmo** contexto (mesmo sublevel ou todos no
  mundo), conferido com `Sable.HELPER.getContaining()`. A rede já não atravessa sublevels
  (docs/avionics-network.md); fio navio↔chão fica como evolução futura (o Simulated faz
  isso com cordas e física, o que é outro projeto).
- O renderer trabalha em coordenadas locais do bloco; o BER já é desenhado no espaço do
  sublevel. O Wiring View, que desenha fora dos BERs, usa
  `ClientSubLevel.renderPose(partialTick)` como o `ModularTableRenderer` já faz.

## 4. Visual

Seguir `docs/visual-design-guide.md` (v2.1) e o checklist da seção 10. Fontes em
`blockbench/wire-connector/`, `blockbench/wire-support/` e `blockbench/avionics-wire/`.

### 4.1 Peças

| Peça | Material | Forma (ponto de partida para o Blockbench) |
| --- | --- | --- |
| Wire Connector | Latão (rampa do guia) + metal escuro no furo | Flange quadrada 4×4 px, 1 px de espessura, colada na face; corpo cilíndrico-quadrado 2×2 px saindo 2 px; boca escura onde o fio entra. Lembra a luva do cabo antigo |
| Wire Support | Ferro industrial | Base 4×2 px presa na face, haste de 2 px e um gancho/sela em "U" aberto onde o fio passa. 3 lanes de 1 px separadas por 1 px |
| Fio | Preto (ver 4.3) | Seção quadrada de 1 texel; gate visual 1 vs 2 texel na Fase 4 |
| Item Avionics Wire | Fio preto enrolado num carretel de ferro industrial com abas de latão | Modelo de item próprio |

- O conector preso num sensor **encaixa no ponto de socket** de `CableSocketProvider`
  (mesmos pontos do cabo antigo, `CableSocketProvider`). No resto dos blocos,
  fica centrado na face.
> **Refino de 2026-10-28 (após o primeiro teste no jogo):** o suporte virou uma
> braçadeira (base, quatro colunas nos cantos e tampa) por onde o fio passa,
> aberta nos quatro lados, então não precisa girar. Caimento de 1,5% do vão
> (máx. 0,3 bloco), reto só o 1 px dentro do prensa-cabo e depois curva. Mesa
> modular com 4 px; na borda dela o conector perde a flange (`slim`). Haste de
> 2 px para dentro do bloco hospedeiro. Estado atual em
> [avionics-wire.md](avionics-wire.md).

- O suporte **gira a sela** para a direção do fio (bissetriz entre o trecho que entra e o
  que sai), calculada ao fechar a rota e guardada no block entity. Sem fio, fica na
  orientação de colocação.

### 4.2 Geometria do fio (`WireGeometry`, Java puro e testável)

- **Caimento**: parábola entre pontos (aproxima catenária). Flecha = `0,04 × vão
  horizontal`, máximo 0,75 bloco, zero em trecho vertical; vãos curtos (< 1,5 bloco) quase
  retos.
- **Saída do conector**: 3 px retos na normal da face antes de começar a cair (alívio de
  tensão), sem ponta coplanar nem penetração.
- **Dobras nos suportes**: canto arredondado (Bézier quadrática, raio 2 px) no ponto da
  sela, sem quina.
- **Subdivisão**: um ponto a cada 0,25 bloco, máx. 64 por vão; calculado uma vez quando o
  dado chega ao cliente, não por frame.
- **Luz**: por vértice, amostrada no bloco de cada ponto (como a guia/leash vanilla).
- **Render**: BER no conector dono, `shouldRenderOffScreen` e AABB cobrindo a rota.
  Textura de faixa 1 texel = 1 unidade, sem esticar. Se o custo aparecer em navios grandes,
  migrar para `VertexBuffer` em cache por fio.

### 4.3 Divergências do guia (precisam de decisão)

1. **Preto não está na paleta.** O mais próximo é a rampa *Metal escuro* (`#2B2B2B`…).
   Proposta: adicionar ao guia uma rampa curta **Fio isolado**, `#141416` `#1B1B1E`
   `#232326` `#2B2B2B`, uso exclusivo do fio, e uma linha "Avionics Wire" na tabela de
   famílias da seção 7.
2. A tabela da seção 7 diz que o cabo antigo é ferro industrial com latão só no conector.
   O sistema novo segue essa lógica (latão = ponto de dados, ferro = estrutura), então não
   há conflito, só uma linha nova.

## 5. Colocação e UX

### 5.1 Roteamento com o Avionics Wire

| Ação | Efeito |
| --- | --- |
| Clique direito num conector | Inicia a rota (som de clique, partícula no ponto) |
| Clique direito num suporte | Adiciona ponto; escolhe a lane livre mais próxima do lado do fio |
| Clique direito no conector final | Fecha, consome itens, recalcula a rede |
| Shift + clique direito no ar | Desfaz o último ponto |
| Trocar de item / Shift + clique no conector inicial | Cancela |

- A rota em andamento vive num **DataComponent** do item (`wire_route_in_progress`), com
  o servidor como autoridade. Sobrevive a troca de slot e reconexão.
- **Prévia**: fio fantasma do último ponto até a mira, já com caimento; encaixa no
  conector/suporte sob a mira. Branco = válido, vermelho = inválido.
- **Action bar**: comprimento, custo em itens e motivo da recusa.
- **Validação** (servidor, repetida no cliente para a prévia):
  - vão máximo `maxWireSpan` (padrão 24 blocos);
  - máximo de 32 pontos por rota;
  - mesmo contexto Sable;
  - sem repetir suporte;
  - capacidade de lanes e de fios por conector;
  - **trecho não atravessa bloco sólido** (raycast ignorando os blocos hospedeiros dos
    pontos). Isso força o uso de suportes, que é o espírito do sistema.
- **Custo**: 1 item = 8 blocos de fio, arredondado para cima (configurável); grátis no
  criativo. Quebrar devolve o mesmo valor.

### 5.2 Remover e inspecionar

- Quebrar conector/suporte: remove os fios que passam por ele e devolve os itens.
- **Wrench do Create com Shift no fio** (mira no fio, com pick no cliente): corta só aquele
  fio. Payload `CutWirePayload`, servidor revalida o alcance.
- **Engineer's Goggles** no conector: estado da rede, endpoints e fios ligados
  (`IHaveGoggleInformation`).

## 6. Wiring View (modo raio X)

- **Tecla** registrada em Controles, categoria do mod. Padrão a definir, evitando `V`
  (Simple Voice Chat). Alterna liga/desliga e mostra um indicador discreto no HUD.
- **Desenho** em `RenderLevelStageEvent` (`AFTER_LEVEL`):
  1. quad de tela escurecendo o mundo (~55%);
  2. fios como linhas lisas sem teste de profundidade;
  3. ícones billboard nos pontos: conector = quadrado de latão com miolo escuro, suporte =
     quadrado menor de ferro, endpoint = ícone do tipo do aparelho.
- **Cores por estado**: latão `#E4B763` = rede completa; cinza = sem endpoint;
  vermelho = `INCOMPLETE`/`LIMIT_EXCEEDED`. Trecho sob a mira em branco, com tooltip
  (comprimento, rede, endpoints).
- **Alcance** de 48 blocos. Fonte de dados: índice no cliente (`ClientWireIndex`) dos
  conectores carregados, preenchido no `onLoad`/`setRemoved` do block entity (padrão do
  `ClientLevelRopeManager` do Simulated).
- O estado da rede vem do servidor: ao ligar o modo, o cliente pede
  `WiringStatusRequestPayload` dos conectores próximos e recebe um resumo por conector,
  invalidado pelo `markDirty`.
- Sublevels: cada fio transformado pela `renderPose` do seu sublevel.

## 7. Classes e registros novos

| Pacote | Classes |
| --- | --- |
| `wire` (lógica, sem cliente) | `WireRoute`, `WireAnchor`, `WireRef`, `WireRouteValidator`, `WireCost`, `WireGeometry`, `WireLanes` |
| `block` | `WireConnectorBlock`, `WireConnectorBlockEntity`, `WireSupportBlock`, `WireSupportBlockEntity`, `WireNodeBlockEntity` (base comum: refs, validação adiada, `transform`) |
| `item` | `AvionicsWireItem`, componente `WireRouteInProgress` |
| `avionics` | `NetworkAccess` + `NetworkDiscovery` estendidos, `NetworkSnapshot.wireCount` |
| `network` | `WireRouteActionPayload`, `CutWirePayload`, `WiringStatusRequestPayload`, `WiringStatusPayload` |
| `client` | `WireRenderer` (BER), `WirePreviewRenderer`, `WiringViewRenderer`, `WiringViewKeys`, `ClientWireIndex`, `WirePicker` |
| `config` | `maxWireSpan`, `maxWireAnchors`, `wireBlocksPerItem`, `wiringViewRange` |

Nada no Avionics Cable antigo é removido ou renomeado.

## 8. Fases

Cada fase fecha com `.\gradlew.bat build` e `test` verdes. As fases 1 e 4 são Java puro
e podem andar em paralelo.

**Fase 0 — Spike de plataforma (½ dia, no cliente de dev).** Bloco provisório com BE e um
BER desenhando uma linha longa. Confirmar no jogo: (a) BE e NBT vão juntos na montagem do
navio Aeronautics; (b) o BER desenha no espaço do sublevel com AABB grande; (c) luz
amostrada dentro do sublevel; (d) Create bearing chama `transform()`. Se (a) ou (b)
falhar, revisar 3.1 antes de seguir.

**Fase 1 — Modelo e topologia (Java puro + testes).** `WireRoute`/`WireRef`, round-trip de
NBT, validador, custo, extensão do `NetworkDiscovery`. Testes:

- linha conector-fio-conector entre dois endpoints;
- junção com 3 fios num conector;
- conector no cabo antigo;
- ponta descarregada → `INCOMPLETE`;
- limite de visitas com fios;
- ciclos;
- `transform` com rotações de 90/180/270.

**Fase 2 — Blocos Connector e Support.** Registro como "(Experimental)", sem receita.
Bloco preso na face, formas, `waterlogged`, BE, sync, quebra com remoção em cascata e
refund, validação adiada, `markDirty`, encaixe no socket via `CableSocketProvider`.
Modelos no Blockbench (seções 8 e 10 do guia) e `*AssetsTest`.

**Fase 3 — Item e roteamento.** `AvionicsWireItem`, DataComponent, payloads, validação no
servidor, consumo/refund, prévia com caimento, action bar, sons. Modelo do carretel.

**Fase 4 — Render do fio.** `WireGeometry` (caimento, saída do conector, cantos, lanes)
com testes:

- pontas exatas;
- tangente na normal da face;
- vão vertical sem NaN;
- limite de subdivisão;
- lanes sem sobreposição.

Depois: BER, textura, luz por vértice, giro da sela do suporte. **Gate visual**: fio de 1
vs 2 texel, flecha e raio de dobra, revistos no jogo com o usuário.

**Fase 5 — Wiring View.** Tecla, overlay, ícones, hover/tooltip, payload de estado,
sublevels.

**Fase 6 — Polimento.** Corte com wrench, Goggles, partículas e sons, cena de Ponder
(opcional), entradas no JEI.

**Fase 7 — Documentação e entrega.**

- `docs/avionics-wire.md` (uso);
- atualizar `docs/avionics-network.md`, o PRD (4.5 diz que o cabo é o único componente
  de conexão) e o guia visual (rampa do fio, seção 7);
- validação no jogo (checklist abaixo);
- só então receitas e remoção do "(Experimental)".

## 9. Checklist manual (Fase 7)

- Rota sensor → 3 suportes → Avionics Computer; ler os canais pela bridge/CC:Tweaked.
- Junção num conector preso a um bloco comum, alimentando dois endpoints.
- Conector preso a um Avionics Cable antigo.
- Quebrar cada tipo de ponto e conferir refund e `avionics_network_changed`.
- Fio atravessando fronteira de chunk; descarregar e recarregar cada ponta.
- Montar o navio com o fio, voar, girar, desmontar; repetir num bearing do Create.
- Wiring View de dia, à noite, dentro de casco fechado e com o navio em movimento.
- Sodium e, separadamente, Iris com shader.
- Desempenho: navio com 100 fios de 20 blocos, comparando FPS com e sem eles.

## 10. Riscos

| Risco | Mitigação |
| --- | --- |
| BER cortado pelo frustum em fios longos | AABB da rota inteira + `shouldRenderOffScreen` |
| Dono em chunk descarregado esconde o fio | Aceito na v1; alternativa: render pelo ponto carregado de menor UUID |
| Montagem parcial (suporte fica fora do navio) | Validação adiada remove o fio e devolve o item; aviso no chat de quem montou, se possível |
| Iris/shaders no overlay sem profundidade | Desenhar depois do pipeline do shader; testar cedo na Fase 5 |
| Conflito de tecla | Padrão sem conflito conhecido; configurável |
| Custo de render em navios grandes | Malha em cache; `VertexBuffer` se medir problema |
