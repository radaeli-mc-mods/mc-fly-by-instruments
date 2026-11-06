# Plano: módulo Attitude Indicator (horizonte artificial 3×3)

> **Revisão 2026-11-05 (v2 visual):** o footprint passou a **2×2** (carcaça 10×10, altura 12, lente 8×8,
> atlas 32×32) e a cunha foi refeita sem frestas: tela em um cubo sólido com moldura pintada e tampo de 4 de
> espessura recuado 1. As seções abaixo descrevem a v1 3×3 e valem pelo resto (render, dados, interação);
> a geometria atual está no [README do módulo](../blockbench/attitude-indicator-module/README.md).

## Contexto

O ARU já publica `attitude.pitch/roll/heading` (fase 1 entregue). Falta a fase 2 do
`docs/attitude-reference-unit-plan.md`: o display de horizonte artificial na mesa modular. O
pedido muda duas decisões em aberto daquele plano: o footprint passa a ser **3×3** (a mesa
inteira) e o módulo tem **dois modelos**, escolhidos pela face da mesa. A tela mostra o
horizonte e, opcionalmente, fitas de altitude relativa e de velocidade; sem elas, o horizonte
ocupa a tela toda. O display precisa ser nítido como uma tela real, inclusive com shaders e
sol forte.

Decisões já tomadas com o usuário:
- Mesa no chão: **cunha** (trapézio retângulo de lado). Base plana, traseira baixa, topo
  inclinado subindo até a frente alta, **tela vertical na face frontal alta**, sem acompanhar a
  inclinação.
- Mesa de parede: placa fina e reta, tela no topo.
- Velocidade: **reservada para um sensor futuro**. Agora só a fita de altitude (Radar
  Altimeter, AGL) é opcional e real; a de velocidade aparece na config como "em breve".
- Material: mesma linha do FMS (ferro industrial + moldura de latão de 1 px + lente escura).
  O display do FMS **não** é trocado (fora do escopo).

## Divergências do guia visual (apontar antes de seguir; propor atualização do guia)

1. **Altura (§9.2: 2–4, até 6):** a cunha tem tela 3×3 vertical, então ~15–16 de altura.
   Ela invade 4 px do bloco acima da mesa. Proposta: exceção "módulo de display de pé" no §9.2.
2. **Rotação (§3: carcaça sempre na grade):** o topo inclinado é um cubo girado **22,5°**
   (o Minecraft só aceita 0/±22,5/±45; 15–20° não existe). Exceção só para o tampo da cunha;
   o exportador passa a aceitar 22,5° num cubo de nome contratado (`housing_slope`).
3. **Cantos arredondados** do esboço: impossíveis com cubos. Viram bisel pintado (1 px claro
   na aresta de cima/esquerda, escuro embaixo/direita), como no motivo de moldura (§8.3).
4. **Atlas 64×64** (§4 pede justificativa): as faces da cunha (14×15, trapézios 14×16) não
   cabem em 32×32.
5. **Elementos:** cunha ~7 cubos, placa 2 cubos (dentro do orçamento de módulo de tela).

## Geometria (Blockbench, 1 texel = 1 unidade, centro X/Z = 8, apoio em Y = 0)

Fonte em `blockbench/attitude-indicator-module/` com dois `.bbmodel`, um `create-base-model.mjs`
(bootstrap) e um `export-assets.mjs`, a partir de `blockbench/fms-module-2x3/` e
`blockbench/_tools/bbtools.mjs`.

**`attitude_indicator_desk` (cunha, mesa UP), frente da tela em +Z (lado do piloto):**

| Cubo | Volume | Material |
| --- | --- | --- |
| `housing_lower` | `[1,0,1]–[15,10,13]` | `create:block/industrial_iron_block` |
| `housing_slope` | slab 14 de largura girada 22,5° em X, da traseira (z 1, y 10) até a frente (y ≈ 15), espessa o bastante para enterrar a borda inferior no `housing_lower` | ferro industrial |
| `bezel_top/bottom/left/right` | moldura de latão em `z 13–15`, X 1–15, Y 0–16; a barra de cima sai 1 unidade além da lente = **viseira contra o sol** | latão |
| `lens` | `[2,2,13]–[14,14,14]` (face da tela em z 14, recuada 1 da moldura) | lente escura |

Validar a silhueta em cinza no Blockbench antes de texturizar (o perfil trapezoidal depende
de a slab girada cobrir a escada do `housing_lower`).

**`attitude_indicator_wall` (placa, mesas de parede e teto):** `housing` `[1,0,1]–[15,2,15]`
ferro industrial; `screen` `[1,2,1]–[15,3,15]` com moldura de latão de 1 px pintada e lente
12×12 (X/Z 2–14), como o cubo `screen` do FMS.

**A área ativa é 12×12 nos dois modelos**, então o conteúdo da tela é o mesmo.

Escolha do modelo: `FACING == UP` → cunha; paredes e teto → placa (cunha de cabeça para
baixo não faz sentido). Na mesa de chão a cunha precisa olhar para o piloto: o módulo guarda um
`yaw` (4 valores), definido pelo olhar do jogador ao instalar e trocado com wrench + scroll,
como a orientação da lâmpada 2×1.

## Estratégia de render (o ponto central)

**Render-to-texture fora do pipeline do mundo + um único quad full-bright na lente.**

1. **Canvas 2D 512×512** por módulo visível (`TextureTarget` num pool LRU, máx. ~6), desenhado
   em `RenderFrameEvent.Pre`: antes do render do mundo, no mesmo contexto da HUD, onde os
   shaders (Iris) não substituem programas. Desenhar com:
   - quads `POSITION_COLOR` próprios para céu/terra, linhas, arcos e símbolos (linhas giradas
     como quads, sem textura esticada);
   - `RenderSystem.enableScissor` em pixels do framebuffer para recortar escada, horizonte e
     fitas (resolve o "recorte sem stencil" do plano antigo);
   - texto com a **fonte do Minecraft** em escala inteira (×2 nas marcas, ×3 nas leituras),
     com contorno preto. Dentro da textura não há briga de profundidade, que foi o motivo de o
     FMS ter usado glifos como geometria.
   - Restaurar o main target, projeção e scissor ao fim.
2. O BER da mesa só **registra** "módulo X visível neste frame" e desenha **um quad** com a
   textura do canvas sobre a lente (0,02 acima, polygon offset), usando
   `RenderType.textPolygonOffset(id)` com `LightTexture.FULL_BRIGHT`: o mesmo caminho do texto
   de placa com tinta brilhante, que os shaderpacks tratam como emissivo. A textura do target é
   registrada no `TextureManager` por um `AbstractTexture` que devolve o id de cor do target.
   Fallback atrás de uma constante/config de cliente: `RenderType.eyes(id)` (emissivo em todo
   pack), para o caso de algum pack escurecer o texto.
3. **Atitude suave:** no `RenderFrameEvent.Pre` o cliente calcula pitch/roll com
   `ClientSubLevel.renderPose(partialTick)` e o frame Nariz/Acima do ARU (blockstate já está no
   cliente; offsets de calibração vêm do sync do BE), reusando `AttitudeMath`. Mesma técnica do
   `renderFmsCompass` em `ModularTableRenderer.java:170`.
4. **Custo:** a ≤16 blocos redesenha todo frame; 16–32 blocos a ~10 Hz; além de 48, a lente
   fica com a textura do modelo. Um frame de latência na primeira aparição (lente preta).
5. **Legibilidade ao sol:** fundo preto, traços de ≥3 px no canvas (~0,07 unidade), cores
   saturadas e com contraste, texto com contorno, viseira física na cunha. Filtro linear no
   sampler, sem mipmap.

## Layout da tela (canvas 512, área 12×12 unidades)

Exceção de display do §9.4 (pode sair da paleta). Limpo, sem excesso:

- **Horizonte:** céu azul e terra marrom (tons do print de referência, um pouco mais escuros
  para contraste), linha do horizonte branca. Escada de pitch: linha longa a cada 10° com
  número dos dois lados, curta a cada 5°, sem marcas de 2,5°; ±20° visíveis; recortada numa
  janela central para não poluir.
- **Escala de bank** no topo: marcas em 10/20/30/45/60°, índice fixo em 0°, ponteiro que gira
  com o roll.
- **Símbolo da aeronave** fixo em latão `#E4B763` com contorno preto (barras de asa + ponto
  central), o "marcador que importa" do §9.4. Grafia e marcas em `#F2F2EA`.
- **Fita de altitude (direita, ~104 px):** fundo cinza escuro, marcas brancas, caixa de leitura
  rolante com o AGL inteiro em blocos. Fora do alcance do radar: `----`.
- **Fita de velocidade (esquerda):** reservada no layout, desligada até existir o sensor.
- **Reflow:** cada fita só aparece se estiver **ligada na config e com fonte na rede**. Sem
  fonte, some e o horizonte cresce para ocupar a tela, com transição de 0,25 s.
- **Bandeiras:** sem ARU, rede incompleta ou `unavailable` → tela preta com `ATT` vermelho;
  `degraded` (perto de ±90°) → `ATT` âmbar pequeno sobre o horizonte; ARU em outro sublevel →
  INOP como no plano do ARU.
- **Entrada:** alinhamento de ~1 s ao ligar, como o `intro` da bússola do FMS.

Pré-visualizar o layout (horizonte puro, com fita, bandeira ATT, roll 30°/pitch 10°) num
render local antes de ligar no jogo, como o `compass-preview.png` do FMS.

## Código

- **Registro do módulo:** tipo `attitude_indicator`, item `attitude_indicator_module`, footprint
  fixo 3×3 (só cabe numa mesa vazia ou atravessando mesas unidas). Seguir o caminho do FMS em
  `ModularTableBlockEntity`, `TableInteraction`, `FmsModuleItem` e `ModBlocks`.
- **Dados do módulo:** registro lateral por célula de origem (como `FmsModuleData`):
  `yaw`, `altitudeTape` (padrão ligado), `speedTape` (reservado). Persistência NBT e sync.
- **Fontes (servidor):** resolver o ARU válido mais próximo na rede, como o Computer resolve o
  altímetro em `AvionicsComputerBlockEntity` (~linha 240); exigir mesmo sublevel. Radar
  Altimeter: valor AGL e status, enviados no sync da mesa a ≤10 Hz e só com mudança; o cliente
  interpola.
- **Volume de seleção:** `PanelCoordinates.MODULE_TOP` é fixo em 4 px; passar a altura por
  tipo de módulo (cunha 16).
- **Renderer:** `ModularTableRenderer.render` ganha o ramo `attitude_indicator` (modelo por
  face + yaw + quad do canvas). Novas classes cliente: `AttitudeDisplayTargets` (pool, evento
  de frame, registro de textura), `AttitudeDisplayPainter` (desenho 2D), `AttitudeDisplayLayout`
  (geometria pura, testável).
- **Interação:** mão vazia → config no padrão §12 (Altitude liga/desliga, Velocidade "em
  breve", linhas de estado ARU/Radar); wrench + scroll na cunha → girar `yaw` com rótulo.
- Receita, lang en/pt, modelos de item. Ponder fica como follow-up.

## Docs a atualizar

- `docs/visual-design-guide.md`: §9.2 exceção de altura, §3 exceção de 22,5° no tampo da
  cunha, §9.4 "display por render-to-texture", §9.5 linha do novo módulo.
- `docs/attitude-reference-unit-plan.md`: footprint 3×3, dois modelos, estratégia de render,
  velocidade reservada.
- `docs/modular-table.md`: módulo, footprint, interação. `blockbench/attitude-indicator-module/README.md`
  no esqueleto do §8.6.

## Verificação

- `.\gradlew.bat test`: novo `AttitudeIndicatorAssetsTest` (1:1, coordenadas inteiras, só
  `housing_slope` girado a 22,5°, lente nas coordenadas que o renderer usa nos dois modelos);
  testes de `AttitudeDisplayLayout` (reflow com/sem fita, escada e bank por pitch/roll,
  recorte); persistência/sync do módulo; footprint 3×3 e `yaw` pela direção do jogador.
- `.\gradlew.bat build` e exportador verdes; `.bbmodel` aberto no Blockbench.
- No jogo (gate honesto, reportar o que foi feito): mesa de chão, parede e teto; cunha nos 4
  `yaw`; com e sem Radar Altimeter na rede (reflow); sem ARU (ATT); voo numa contraption
  (suavidade, roll ±180°, invertido); dia/noite; **Complementary e BSL ao sol** (texto e
  horizonte legíveis a 1–2 blocos); vários displays ao mesmo tempo (FPS).
