# Indicador de Atitude 2×2 — fonte visual (v2 prévia, ferro industrial + latão)

Guia obrigatório: [guia visual v2.1](../../docs/visual-design-guide.md), seção 9. Plano completo:
[attitude-table-module-plan](../../docs/attitude-table-module-plan.md). Mesmas linhas do
[FMS](../fms-module-2x3/README.md): carcaça de ferro industrial, tela de lente escura numa moldura de latão de 1 px,
viseira de latão que sobe 1 unidade. Referências: navigation table do Simulated, gauges do Create.

**Estado: só visual.** Modelos, textura, registro do item, colocação na mesa e um display com valores
mockados. Nada lê o ARU nem o Radar Altimeter ainda (ver *Pendente*).

Dois modelos, escolhidos pela face da mesa (`FACING == UP` → cunha; parede e teto → placa). Fonte em
Blockbench (Java Block/Item, 1.21.1, *Box UV* desligado, resolução 32×32): abra `attitude_indicator_desk.bbmodel`
e `attitude_indicator_wall.bbmodel`. Modelados com a mesa voltada para cima: apoio em Y = 0, centro em X/Z = 8,
carcaça 10×10 (eixo de 2 células).

![Cunha (mesa de chão): vista do piloto](preview.png)
![Cunha: perfil](preview-side.png)
![Cunha: traseira](preview-rear.png)
![Placa (mesa de parede e teto)](preview-wall.png)

## Identidade

- **Material:** ferro industrial (`create:block/industrial_iron_block`, por id) + latão (moldura e viseira) +
  lente escura. Sem madeira.
- **Cunha:** caixa de ferro com tampo inclinado subindo da traseira (y 8) até a tela, que fica **de pé** na frente
  (lado do piloto, +Z). O tampo é 1 unidade mais estreito de cada lado: deixa um ressalto de 1 sobre a carcaça e
  nenhuma face coplanar. Viseira de latão 1 unidade à frente sobre a tela. Sem botões nem knobs: o módulo não tem controle
  físico.
- **Altura 12:** com a mesa de 4, o topo da tela encosta no topo do bloco. Não invade mais o bloco de cima (a v1 3×3
  tinha 16).
- **Placa:** casca fina e reta (3 de altura), tela no topo, para mesas de parede e teto, igual ao FMS.
- **Área ativa:** lente 8×8 nos dois modelos; o display é o mesmo.

### O que mudou da v1 (3×3)

A v1 tinha a moldura em 4 barras soltas e uma lente só com a face da frente; o tampo girado terminava antes da
tela e a escada debaixo dele não chegava até o tampo. Pela fresta, o display (desenhado sem cull) aparecia por trás
do bloco. Na v2 a tela é **um cubo sólido** com todas as faces e a moldura pintada, o tampo tem 4 de espessura (a
face de baixo e a traseira ficam enterradas na carcaça, a ponta da frente dentro da tela) e não há escadas.

## Peças

Cunha (5 cubos):

| Elemento | Volume | Material |
| --- | --- | --- |
| `housing` | `[3,0,3]–[13,8,11]` | ferro industrial (recortes 1:1) |
| `housing_foot` | `[3,0,11]–[13,2,13]` | ferro industrial; pé sob a tela |
| `housing_slope` | `[4,4,3]–[12,8,12]`, girado **−22,5° em X**, origem `[8,8,3]` | ferro industrial; sobe da traseira (y 8) até y ≈ 11,3 na tela; faces de baixo, de trás e da frente enterradas (desligadas) |
| `screen` | `[3,2,11]–[13,12,13]` | frente 10×10: moldura de latão de 1 px + lente 8×8 (`X 4–12`, `Y 3–11`, face em z 13); laterais e topo de latão |
| `visor` | `[3,11,13]–[13,12,14]` | latão; aba contra o sol |

Placa (2 cubos):

| Elemento | Volume | Material |
| --- | --- | --- |
| `housing` | `[3,0,3]–[13,2,13]` | ferro industrial |
| `screen` | `[3,2,3]–[13,3,13]` | topo 10×10: moldura de latão de 1 px + lente escura 8×8 |

## Texturas

`attitude_indicator_module.png`, atlas **32×32** (a v1 precisava de 64×64), compartilhado pelos dois modelos.
Regiões em `UV` do `create-base-model.mjs`: frente da tela (também o topo da placa), topo, laterais e traseira da
tela, faces da viseira, bordas da placa. A carcaça de ferro é recorte de
`create:block/industrial_iron_block`; faixas de 2 de altura usam as linhas de baixo (borda escura embaixo).

## O display

Não é textura: é geometria desenhada em tempo real pelo `ModularTableRenderer`
(`AttitudeDisplayRenderer`), unlit, em um passe plano como a bússola do FMS. Exceção de display da seção 9.4
do guia. Layout em `AttitudeDisplayLayout` (polígonos convexos puros, testáveis, com fonte 5×7 própria):
céu e terra com linha de horizonte, escada de pitch (longa e número a cada 10°, curta a cada 5°) numa janela
central, escala de bank fixa com ponteiro que gira com o roll, símbolo da aeronave em latão, fita de
altitude (AGL) opcional à direita; sem a fita o horizonte ocupa a tela toda. Bandeira `ATT` vermelha sem
dados e âmbar em degradado.

![Display: horizonte puro, com fita, ATT degradado e ATT sem dados](display-preview.png)

Os valores vêm de `AttitudeDisplayMock` (um voo lento e suave). Para gerar a imagem:

```powershell
$env:FBI_WRITE_PREVIEW = "$PWD\blockbench\attitude-indicator-module\display-preview.png"
.\gradlew.bat test --tests "*AttitudeDisplayLayoutTest"
```

## Exportação

```powershell
node blockbench/attitude-indicator-module/export-assets.mjs
node blockbench/attitude-indicator-module/render-preview.mjs
```

O exportador falha com cubo rotacionado (exceto `housing_slope`, só −22,5° em X), coordenada fracionária ou
densidade diferente de 1:1. A fonte da cunha é dona do PNG; a da placa precisa ter os mesmos pixels. **Não rode
o `create-base-model.mjs` sobre uma fonte já refinada no Blockbench.**

## Mudou a geometria?

`PanelCoordinates.moduleTop` / `DISPLAY_HEIGHT` (volume de seleção), as constantes da lente em
`AttitudeDisplayRenderer` (`LENS_FACE_WEDGE`, `WEDGE_CENTER_Y`, `LENS_FACE_PLATE`, `LENS`) e
`AttitudeIndicatorAssetsTest`. Ao mexer no tampo, confira que a face de baixo continua dentro da carcaça e a ponta
dentro da tela (perfil em `preview-side.png`): é o que impede frestas.

## Pendente

- Nenhum dado real: o display roda no mock. Falta ligar ao ARU mais próximo da rede e ao Radar Altimeter
  (AGL), com interpolação por `ClientSubLevel.renderPose` e o alinhamento de ~1 s ao ligar.
- Configuração (fita de altitude liga/desliga, velocidade "em breve") e rotação (`yaw`) da cunha: hoje a cunha
  olha sempre para o lado +Z do painel.
- Receita e Ponder. O item é Experimental, só no criativo.
- A transição de 0,25 s do reflow existe no layout (`altitudeTape` de 0 a 1), mas nada anima o valor ainda.
- Fita de velocidade: só reserva espaço; não desenha nada.
- Não verificado no jogo: a v2 2×2 inteira (ausência de frestas por trás, cunha nos 4 sentidos, mesa de parede e
  teto, colocação 2×2 atravessando mesas unidas, transformações do item na GUI e na mão), shaders (Complementary,
  BSL) e sol forte, FPS com vários displays. Geometria só conferida em render local de software.
- O display foi desenhado para a lente de 12 da v1 e é reduzido para 8 (`SCALE` = 2/3): traços de 0,05 viram ~0,03 e
  o texto da fita fica pequeno. Reavaliar no jogo se a escada de pitch e os números da fita ainda leem a 1 bloco;
  se não, engrossar no `AttitudeDisplayLayout`.
- Refino de pixels no Blockbench: a fonte atual é a do bootstrap e ainda não foi aberta no Blockbench.
