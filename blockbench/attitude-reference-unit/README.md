# Attitude Reference Unit — fonte visual (v2.1, andesito + aros de latão)

Guia obrigatório: [guia visual v2.1](../../docs/visual-design-guide.md), com a convenção de
[faces configuráveis por blockstate (§8.7)](../../docs/visual-design-guide.md#87-faces-configuráveis-por-blockstate).
Plano do bloco: [attitude-reference-unit-plan.md](../../docs/attitude-reference-unit-plan.md).
Referências diretas: andesite casing e brass casing (Create), content observer.

Abra `attitude_reference_unit.bbmodel` no Blockbench (Java Block/Item, 1.21.1, *Box UV* desligado).

![preview](preview.png) ![traseira e baixo](preview-rear.png) ![a ~5 blocos](preview-small.png)

**Estado: no jogo (fase 1).** O exportador `export-assets.mjs` gera o blockstate multipart, os modelos de papel
(plain, nose, up × powered), o aro e o modelo do item a partir deste `.bbmodel`. Testes em
`AttitudeReferenceUnitAssetsTest`. Rode `node blockbench/attitude-reference-unit/export-assets.mjs` após editar a fonte.
`create-base-model.mjs` é só o bootstrap original: não rode sobre a fonte refinada.

## Identidade

Caixa de aviônica fechada, sem rotor nem gimbal à mostra. A fonte mostra a colocação padrão:
**Nariz = NORTH, Acima = UP**, as outras quatro faces neutras. Na fonte os arcos já estão pintados nessas
duas faces; no jogo eles aparecem dinamicamente, só na face escolhida, pelo blockstate.

| Pista | Attitude Reference Unit |
| --- | --- |
| Material dominante | Andesito (corpo) com **aros de latão finos** só nas arestas de cima e de baixo, sem madeira |
| Silhueta | Corpo recuado 1 unidade entre dois aros de latão (1 px de altura); sem arestas verticais |
| Face neutra | Moldura de casing, 2 rebites, socket de dados 4×4 no centro, fenda de redstone 8×3 embaixo |
| Face Nariz | Só um **arco de cobre** (círculo de 8 px, 1 px) em volta do socket |
| Face Acima | Só um **arco de latão**, no mesmo lugar. Sem seta: ela lia como "para a frente" |

Nariz e Acima têm o mesmo desenho e se distinguem pelo material (cobre avermelhado × latão amarelo).
Diferenciado do Network Bridge (andesito liso, uma só massa, placa escura no topo) pelos aros, e do
Avionics Computer (ferro) pelo material.

## Peças (14 cubos)

| Elemento | Volume | Material |
| --- | --- | --- |
| `face_north`, `face_east`, `face_south`, `face_west`, `face_up`, `face_down` (grupo `body`) | `[1,1,1]–[15,15,15]` cada um, **só uma face desenhada** | Andesito; `face_north` usa a textura do Nariz e `face_up` a do Acima |
| `edge_x_{down,up}_{n,s}` (grupo `edges`), 4 cubos | 16×1×2, nas bordas norte e sul de cada aro | Latão |
| `edge_z_{down,up}_{w,e}`, 4 cubos | 2×1×12, entre as barras X | Latão |

Coordenadas inteiras, sem rotação, sem sobreposição. Cada aro tem 1 unidade de altura (`y 0–1` embaixo,
`y 15–16` em cima) e 2 de profundidade, apoiado na face de cima/baixo do corpo, então 1 px da moldura dessas
duas faces fica coberto. Não há arestas verticais.

As seis partes do corpo são o desenho do §8.7: na fase 1 o exportador gera, para cada uma, os modelos
`neutral`, `nose` e `up` (trocando a região do atlas) e o blockstate multipart escolhe por `nose`/`up`.
As arestas são a parte fixa.

## Texturas (1 texel = 1 unidade)

- `attitude_reference_unit.png` (32×32): neutra 14×14 em `[0,0]`, Nariz 14×14 em `[16,0]`, Acima 14×14 em
  `[0,16]`, aros em `[16,16]` (16×1, lados), `[16,17]` (16×2, topo), `[16,19]` (12×1), `[16,20]` (2×1, pontas) e
  `[30,20]` (2×12). Também é a partícula.
- `attitude_reference_unit_powered.png` (32×32): igual, só a fenda acende (variante §8.5). Não está
  atribuída a nenhuma face no `.bbmodel`; é a troca de textura da variante.

Cores: 100% da paleta (andesito, latão, metal escuro, redstone) mais a **rampa de cobre do guia §5**, que
ainda não tinha chaves no `PALETTE` de `bbtools.mjs`; o bootstrap as define localmente.

## Gerar e renderizar

```powershell
node blockbench/attitude-reference-unit/create-base-model.mjs   # só o bootstrap; não rode sobre fonte refinada
node blockbench/attitude-reference-unit/render-preview.mjs      # renders direto do .bbmodel
```

O render usa `instance.json`/`instance.lookup` do `render-scene.mjs`, para desenhar um modelo ainda não exportado.

## Mudou a geometria?

Quando o bloco entrar no jogo: `SHAPE` (cubo cheio, os aros vão até o limite), `cableSocket` em
cada face (socket no centro de todas, §8.7) e `AttitudeReferenceUnitAssetsTest`.

## Pendente

- Refino no Blockbench (a fonte ainda é a saída do bootstrap) e abertura do arquivo lá para conferir.
- Validar a legibilidade dos arcos a 16 px e ~5 blocos **no jogo** (o render pequeno é só local); o arco de
  latão sobre as arestas de latão é o ponto de maior risco.
- Exportador, blockstate multipart, variante `powered`, colisão e testes (fase 1 do plano).
- Conferir ao lado de casings do Create, com e sem shader.
