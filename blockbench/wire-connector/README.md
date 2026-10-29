# Wire Connector — latão, ponto de dados do Avionics Wire

Guia: [visual-design-guide.md](../../docs/visual-design-guide.md). Uso:
[avionics-wire.md](../../docs/avionics-wire.md). Referências: rope pulley e smart chute do Create (latão
pequeno sobre a face), rope connector do Simulated (nó preso na face).

> **Estado:** base gerada pelo bootstrap em 2026-10-28, ainda sem refino no
> Blockbench nem conferência no jogo.

## Identidade

- **Latão** (rampa `1`–`8`): marca onde o dado entra no bloco, como a luva do
  cabo antigo. Flange de 1 unidade, colar de 2 unidades e prensa-cabo de metal
  escuro (`w x y z`) onde o fio preto entra (motivo "bocal escalonado").
- Sem redstone, sem transparência, sem emissão.

## Peças

Modelado com FACING = UP (bloco hospedeiro embaixo, y = 0 é a face dele). O
blockstate gira como uma end rod.

| Elemento | Volume | Material |
| --- | --- | --- |
| `flange` | 5–11 × y 0–1 × 5–11 | latão |
| `body` | 6–10 × y 1–3 × 6–10 | latão |
| `gland` | 7–9 × y 3–4 × 7–9 | metal escuro |
| `stem` | 6–10 × y −2–1 × 6–10 | latão sombreado, dentro do hospedeiro |

4 cubos, coordenadas inteiras, textura própria 16×16 (`wire_connector`).

- **Haste (`stem`)**: entra 2 px no bloco hospedeiro. Num bloco cheio fica
  escondida; num bloco com recuo (Avionics Computer, 1 px) fecha o vão. É a
  única peça fora de 0..16, e fica fora da forma de colisão
  (`WireConnectorBlock.STEM`).
- **Variante `slim`** (`wire_connector_slim.json`, gerada pelo export sem a
  flange): usada na borda de uma Modular Table Base (`SLIM=true`). O corpo de
  4 px fica centrado na borda de 4 px da mesa, e só 1 px da haste fica, no
  lugar da flange. Dentro da mesa a haste teria faces nos mesmos planos do
  tampo e do fundo (z-fighting).
Contratos com o código: `WireConnectorBlock.PARTS` (forma) e
`WireAttachPoints.CONNECTOR_MOUTH = 4` (boca do fio no topo do `gland`).
`WireAssetsTest` confere os dois.

Num sensor com `CableSocketProvider`, o modelo é deslocado no plano da face
para o ponto do socket (`WireConnectorModels`, no bake do chunk); o fio sai do
mesmo ponto deslocado.

## Pipeline

```powershell
node blockbench/wire-connector/create-base-model.mjs   # só uma vez
node blockbench/wire-connector/export-assets.mjs
```

O export grava o modelo, a variante `slim`, o PNG, o blockstate (`facing` ×
`slim`) e o modelo de item (`_tools/wire-nodes.mjs`).

## Pendente

- Refino no Blockbench e render de conferência ao lado do Create.
- Conferir no jogo: as seis faces, o encaixe no socket do Radar Altimeter e dos
  scanners, de dia e de noite.
