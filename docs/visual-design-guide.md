# Guia visual v2.1 — linguagem Create / Create Aeronautics

> v2 (2026-10) substituiu a direção "aviónica industrial" da v1, que continua no
> histórico do git. A **v2.1** (2026-10-27) consolida o que os três sensores
> entregues (Radar Altimeter, Terrain Scanner e Advanced Terrain Scanner)
> estabeleceram na prática (seção 8) e define as regras dos módulos da mesa
> (seção 9). Em 2026-11-04 entrou a convenção de faces configuráveis por
> blockstate (seção 8.7); em 2026-11-05 ela ganhou as hastes de referência e a
> caixa de vidro do ARU.
> Ferramenta oficial de modelagem e pintura: **Blockbench**.
>
> **Leitura obrigatória antes de qualquer pedido de modelagem ou texturização**
> neste mod (bloco, item, módulo, variante ou ajuste de modelo existente).

## 0. Por que mudar

Uma análise de 1.294 modelos de bloco do Create 6.0.10, 335 do Create
Aeronautics (Simulated) 1.3.2 e 77 do Aeronautics 1.3.2, comparados com os
nossos modelos v1, mostrou onde estava a diferença. A última coluna é o
resultado medido dos três sensores v2 (exports em `src/main/resources`).

| Critério | Create / Simulated / Aeronautics | Nossos modelos v1 | Sensores v2 (medido) |
| --- | --- | --- | --- |
| Densidade de textura | **1 texel por unidade** em 94–98% das faces | Mista; até 32 texels numa face de 0,5 | 1:1 em todas as faces (o exportador falha se não for) |
| Coordenadas | Inteiras; menor dimensão típica 2, às vezes 1 | Frações de 0,25/0,5/0,75 | Inteiras, sem rotação |
| Complexidade | Mediana de 2–5 elementos, p90 8–15 | 13–43 elementos | 3, 4 e 5 cubos |
| Paleta | Simulated reutiliza cores exatas do Create (90–95%) | 0–7% | **100%** dos pixels na paleta; 9–18 cores por textura |
| Origem das texturas | Pintadas à mão | Ruído procedural | Mapas de pixel escritos à mão + refino no Blockbench |
| Temperatura | Quente: latão, madeira, cobre, andesito | Fria: prata, azul-petróleo | Latão, madeiras, andesito, ferro industrial |

O altímetro v1 parecia um equipamento real de aviação. Um bloco do Create
parece uma **máquina montada com as peças do próprio Create**.

Pranchas de referência (locais, fora do git): `reference/visual/`.

![Nossos sensores vs. análogos do Create e do Simulated](../reference/visual/compare_ours_vs_create.png)

## 1. Princípio

**Todo bloco deve parecer algo que a equipe do Create poderia ter feito.**

1. **Feito de materiais do Create.** Carcaça de andesito, latão ou cobre,
   madeira, ferro industrial. Nada de materiais novos inventados.
2. **Função mecânica à vista.** Mostrador, lente, ponteiro, hélice ou antena
   grande o bastante para ler à distância de uso. Esconder a "eletrônica".
3. **Robusto e em camadas.** Base, corpo e tampa bem separados por volume e
   material, nunca uma caixa lisa com adesivos.
4. **Quente e saturado na medida.** Rampas com variação de matiz, contorno
   escuro e luz forte no topo, como as texturas do Create.
5. **Cor com significado.** Vermelho redstone é estado de saída. Gemas azul
   e vermelha indicam frequência. Pergaminho é leitura. Ver a seção 6.
6. **Pouco e bem resolvido.** Poucos cubos grandes com textura rica valem mais
   que muitos cubos pequenos. O detalhe mora no pixel, não na geometria.

## 2. Contexto de uso (builds da comunidade)

Fontes: galeria oficial do Create Aeronautics no Modrinth (Cloud Skipper,
Airships, Blue Plane) e cerca de 47 schematics do createmod.com (dirigíveis,
zeppelins, aviões, biplanos, helicópteros).

- **Dirigíveis:** cascos de madeira escura (spruce/dark oak), envelopes de
  lona branca ou colorida com cintas de madeira clara/latão, maquinário do
  Create exposto (cobre, casings de andesito/latão, engrenagens), propellers
  de vela.
- **Aviões e biplanos:** fuselagem de madeira, asas de vela/lona, pintura de
  cores fortes (vermelho/amarelo, ciano/verde-água, xadrez), cockpit de vidro
  com moldura escura.
- **Helicópteros e builds "modernos":** mais ferro industrial, metal escuro e
  concreto, mas o maquinário continua sendo do Create.

Duas distâncias de leitura:

| Peça | Onde vive | Distância típica | Consequência |
| --- | --- | --- | --- |
| Sensores | Embutidos no casco (altímetro na barriga, scanner no nariz), ao lado de casings do Create | 3–10 blocos | Silhueta e face funcional leem de longe; detalhe fino some |
| Mesa e módulos | Painel do cockpit, de frente para o piloto | 0,5–2 blocos | Cada pixel é visto; a peça é pequena, então cada texel precisa ter função (seção 9) |

Ambos precisam combinar com madeira e com metal. Por isso as carcaças seguem as
famílias de casing do Create (andesito/latão/cobre/ferro industrial).

## 3. Geometria (Blockbench)

| Regra | Valor |
| --- | --- |
| Grade | Snap de **1 unidade** (16 = 1 bloco). Frações só com motivo forte, registrado no README do bloco, e nunca para "afinar" uma peça |
| Elemento mínimo | 1 unidade; preferir 2 ou mais em peças visíveis |
| Elementos por bloco | Sensor ou bloco simples: 2–8 (os sensores v2 usam 3–5). Instrumento: até ~15. Acima de 20, justificar (Create chega a 25, Simulated a 39) |
| Rotação | Só 22,5° e 45°, e só em peças mecânicas (ponteiro, alavanca, aleta). Carcaça sempre alinhada à grade. **Exceção (2026-11-05):** o tampo da cunha do Attitude Indicator (`housing_slope`) é um cubo girado 22,5° em X; o exportador só aceita rotação num cubo nomeado no `rotatable` do exportador. Cubo girado não pode deixar fresta: suas faces que não ficam à vista (baixo, trás, ponta) terminam **dentro** de cubos sólidos, e ele fica recuado 1 dos vizinhos para não ter face coplanar |
| Camadas | Base/pé (2–3 de altura) + corpo + tampa ou placa (2 de altura). Corpo costuma recuar 1 unidade em relação à tampa ou base |
| Peça funcional | Grande e saliente: lente 6–10 unidades, mostrador com moldura, antena com volume. É o item que define a silhueta |
| Partes móveis | Modeladas como elemento ou modelo separado (como as partials do Simulated: `hand`, `needle`, `fan`), para animar no futuro sem refazer a carcaça |
| Faces | Sem `cullface` em faces recuadas ou salientes (somem contra o vizinho). Sem faces coplanares sobrepostas nem cascas duplicadas |

### Vocabulário formal recorrente

- **Moldura de casing:** borda de 2 px de metal ao redor de um miolo de madeira
  ou pedra (andesite/brass/copper casing).
- **Placa de topo com quadrados concêntricos:** content observer, threshold
  switch, laser sensor, optical sensor, navigation table.
- **Faixa de redstone:** fenda vermelha na lateral (stockpile/threshold switch)
  ou nos cantos (laser sensor), escura sem sinal e acesa com sinal.
- **Cantoneiras e pés:** pequenos blocos nos cantos (navigation table, docking
  connector).
- **Base de andesito com tábua de madeira:** altitude sensor, physics assembler.
- **Bocal escalonado:** laser pointer, anéis sucessivos de 1–2 unidades.

## 4. Texturas

### Densidade: 1 texel = 1 unidade, sempre

- Material isolado: PNG **16×16**.
- Peça com várias faces diferentes: **atlas 32×32** (64×64 só para modelos
  grandes, com justificativa), em que **cada face ocupa no atlas o tamanho real
  dela em pixels**. Uma face de 14×11 unidades usa 14×11 px.
- No Blockbench (formato Java Block): **um espaço de UV único para todas as
  texturas**, igual à resolução do projeto (32). O Blockbench 5.2 ignora o UV
  size de cada textura ao exportar Java e usa a resolução do projeto. Com
  projeto 32×32, um atlas de 32 px fica 1:1 com Auto UV, mas uma textura de 16
  px precisa de UVs dobradas (uma face de 8 unidades usa 16 de UV; em script,
  `px(16, uv)` do `bbtools.mjs`). **Nunca** mapear `[0,0,32,32]` em todas as
  faces.
- Conferir com a grade de pixels do Blockbench ligada: os texels de faces
  vizinhas precisam ter o mesmo tamanho na tela. O exportador confere de novo.

### Pintura

- **Paleta fechada:** só as rampas da seção 5 (`blockbench/_palette/create-aeronautics.gpl`,
  ou `.hex` se a versão do Blockbench não importar `.gpl`). Meta de entrega:
  ≥ 90% dos pixels; os sensores v2 têm 100%.
- **Poucas cores:** 7–20 por textura. Cada material usa uma rampa de 5–8 tons.
- **Luz:** topo e esquerda mais claros, base e direita mais escuras. Aresta
  externa com 1 px do tom mais escuro da rampa; aresta interna da moldura com
  1 px do tom mais claro.
- **Variação de matiz:** no latão, a sombra puxa para marrom-vinho (`#592424`)
  e a luz para amarelo claro (`#FFEB8C`). A madeira sombreia para marrom quente,
  nunca para cinza.
- **Textura de material:** veio de madeira em linhas de 1 px, tábuas separadas
  por uma linha escura, rebites de 1 px nos cantos das molduras. Nada de ruído
  aleatório. Padrões repetitivos (tábuas, nós) usam uma regra determinística
  escrita à mão, como em `planks()` do Terrain Scanner.
- Sem antialiasing, sem gradiente suave, sem texto legível (letras viram ruído
  a 16 px por bloco). Sem alpha parcial, exceto em vidro translúcido declarado: o da
  lâmpada (seção 9) e a caixa de vidro do Attitude Reference Unit (§8.7), com alpha baixo e uniforme.
- **Assets do Create são *All Rights Reserved*** (só o código é MIT). Podemos
  **referenciar** as texturas pelo id, porque elas são carregadas do jar do
  Create em runtime. Não podemos **copiar** PNGs, pixels ou trechos deles para o
  nosso repositório, nem embuti-los no `.bbmodel`. Para ver a textura no
  Blockbench, usar uma cópia local em `reference/create-textures/` (fora do git)
  via `relative_path`. Paleta (HEX) e estilo podem ser seguidos livremente.
- **Reutilizar antes de pintar.** Quando a peça é literalmente um material do
  Create, referenciar a textura dele no modelo, como o Simulated e o
  Aeronautics fazem: `create:block/andesite_casing`, `create:block/brass_casing`,
  `create:block/industrial_iron_block`, `create:block/axis`. O Create é
  dependência obrigatória do mod. Exemplo: topo do Radar Altimeter.

## 5. Paleta (HEX extraídos dos jars)

Use as rampas como estão. Ao estender, interpolar dentro da mesma rampa.
Arquivos: `blockbench/_palette/create-aeronautics.gpl` (Blockbench) e
`PALETTE` em `blockbench/_tools/bbtools.mjs` (mapas de pixel; ver seção 8.4).

| Material | Rampa (escuro → claro) | Onde aparece |
| --- | --- | --- |
| Latão | `#592424` `#724731` `#9E6947` `#BF8E55` `#CEA05A` `#E4B763` `#F7CB6C` `#FFEB8C` | brass casing/block, molduras "smart", optical sensor |
| Madeira escura | `#301E0E` `#3A2411` `#442B15` `#4F3118` `#53381A` `#5A3E1E` | miolo do brass casing, corpo dos instrumentos de latão |
| Madeira média | `#553A1F` `#5A4424` `#614B2E` `#70522E` `#7A5A34` `#82613A` `#886539` | miolo do andesite casing, laser sensor, velocity sensor |
| Andesito | `#505351` `#60635F` `#686C68` `#707570` `#828784` `#8F9691` `#9AA49D` `#A8B3AB` | andesite casing, altitude sensor (cinza levemente esverdeado) |
| Cobre | `#904931` `#9A5038` `#A75A40` `#B26247` `#C26B4C` `#C87456` `#D67B5B` `#E3826C` | copper casing, steam vent, potato cannon |
| Ferro industrial | `#353438` `#3F3E42` `#444444` `#494848` `#4F4F4F` `#595858` `#676161` `#757171` | bearings do Aeronautics, peças pesadas |
| Metal escuro | `#2B2B2B` `#353535` `#3B3B3C` `#464647` `#4A4A4B` `#525255` | aros, bocais, molduras escuras, sockets |
| Pergaminho | `#676562` `#949188` (marcas) + `#D4C2A4` `#E0D2AE` `#EEE4CE` `#F6EDD8` `#F9F2DE` | mostradores (altitude sensor radial/linear) |
| Redstone desligado | `#460000` `#500101` `#580101` | indicador sem sinal |
| Redstone ligado | `#970000` `#AE0000` `#CD0000` (+ brilho `#FD0000`) | indicador com sinal |
| Lente escura | `#13161D` `#171B23` `#20232A` `#282C37` `#333846` `#464C5F` | lente do laser sensor, vidros ópticos |
| Gema azul | `#5059AB` `#5371C6` | gimbal, receivers (par com a gema vermelha); brilho de lente |
| Lona | `#868480` `#9B9892` `#B6AEA1` `#CEC4B5` `#E4E4DF` `#F1F1F0` | envelopes, velas |
| Fio isolado | `#141416` `#1B1B1E` `#232326` (+ `#2B2B2B` do metal escuro) | **só** o fio do Avionics Wire (aprovada em 2026-10-28) |

**Fora da paleta:** azul-petróleo de identidade (`#31657E`) e amarelo de
sinalização (`#E4A92F`), que não aparecem em nenhuma textura do Create ou do
Aeronautics. Também a prata fria (`#BABDBF` e vizinhas) como cor dominante de
carcaça: ela existe pontualmente no Create, mas nunca como material principal.

## 6. Cor com função

| Sinal visual | Significado no Create/Aeronautics | Uso no mod |
| --- | --- | --- |
| Faixa ou ponto **vermelho redstone** | Saída de redstone; escuro sem sinal, aceso com sinal (o modelo troca para `block_powered`) | Fendas dos sensores. Acende com potência > 0 |
| **Gemas azul + vermelha** lado a lado | Par de frequência do Redstone Link | Opcional, só onde o jogador configura frequências |
| **Pergaminho** com marcas cinza | Leitura/medição | Mostrador de altura ou distância, se houver |
| **Lente escura** com reflexo azul | Emissor/receptor óptico | Face de medição (raycast) dos scanners e do altímetro |
| **Latão** | Tier "smart"/avançado | Variante avançada; instrumentos de cockpit |
| **Cobre** na ponta de uma haste de referência | — (convenção do mod, 2026-11-05) | Haste do **Nariz** num bloco orientável por faces ([§8.7](#87-faces-configuráveis-por-blockstate)) |
| **Latão** na ponta de uma haste de referência | — (convenção do mod, 2026-11-05) | Haste do **Acima** |

## 7. Famílias de material por tier

Segue a lógica do Create: andesito é o básico, latão é o avançado ou "smart",
cobre é fluidos e vapor, ferro industrial é a parte pesada do Aeronautics.

| Bloco | Família | Referências diretas |
| --- | --- | --- |
| Radar Altimeter | **Latão + madeira escura** (v2 entregue), lente para baixo | optical sensor, stockpile/threshold switch, content observer, laser pointer |
| Terrain Scanner | **Andesito + madeira média** (v2 entregue), bocal escalonado frontal | andesite casing, laser sensor, laser pointer |
| Advanced Terrain Scanner | **Latão + ferro industrial** (v2 entregue), barra com três lentes | content observer, smart observer, optical sensor |
| Mesa modular e módulos | Ver seção 9.3 (base v2.1 em 2026-10-27) | navigation table, nixie tube, display link, gauges |
| Avionics Computer (ex-GPWS Computer) | **Ferro industrial + latão, sem madeira** (v2.1 em 2026-10-28): ferro dominante, latão no acabamento, tela frontal com um estado por textura | navigation table, stockpile switch, industrial iron (Aeronautics) |
| Network Bridge (experimental) | **Liga de andesito + metal escuro e latão** (v2.1 em 2026-10-28): caixa de metal lisa, placa de metal escuro no topo (56% da face) com grade fina de ranhuras de latão (só no topo); laterais lisas com rebites e, em duas opostas, um socket de dados 4×4 discreto (eixo X/Z ao colocar). Diferenciada do Avionics Computer por material, silhueta lisa e grade de latão no topo ([README](../blockbench/network-bridge/README.md)) | andesite alloy, andesite casing |
| Avionics Wire | Fio **preto** (rampa Fio isolado), **Wire Connector em latão** (ponto de dados), **Wire Support em ferro industrial** (braçadeira: o fio passa por dentro). Nós podem ter uma haste de até 2 px abaixo de Y = 0, dentro do bloco hospedeiro, para encostar em blocos com recuo. Base de 2026-10-28 | rope connector do Simulated, bearings do Aeronautics |

### Diferenciação entre os sensores

Regra: cada sensor precisa ser reconhecível **num relance**, inclusive reduzido a ~76 px
(um bloco visto a uns 10 blocos de distância). Use pelo menos quatro pistas
independentes, e não dependa de uma só:

| Pista | Radar Altimeter | Terrain Scanner | Advanced Terrain Scanner |
| --- | --- | --- | --- |
| Tier e material (cor dominante) | Latão + madeira escura (marrom-dourado) | Andesito + madeira média (cinza e marrom claro) | Latão + ferro industrial (dourado sobre grafite) |
| Face de medição | Embaixo | Frente | Frente |
| Peça óptica (silhueta) | Lente quadrada rente, num aro | Bocal escalonado com lente pequena | Barra larga com três lentes em arco |
| Fenda de redstone | Horizontal, nas 4 laterais | Vertical, nas laterais | Horizontal, só nas faces que emitem |
| Topo | `create:block/brass_casing` | Tampa de andesito com tábuas | Placa de latão concêntrica |

Novos sensores devem escolher combinações que não colidam com estas. Variantes de um
mesmo bloco (básico e avançado) mudam o tier de material e a peça óptica, e mantêm o
volume do corpo e os sockets.

## 8. Receita dos sensores v2

Esta é a receita que funcionou nos três sensores e o ponto de partida para
qualquer bloco novo. Fontes: `blockbench/radar-altimeter/`,
`blockbench/terrain-scanner-basic/`, `blockbench/terrain-scanner-advanced/`.

### 8.1 Anatomia: corpo + peça funcional

- **Um cubo de corpo** com um atlas 32×32. Toda a riqueza de material (moldura,
  tábuas, rebites, fenda, socket) está pintada no atlas, não modelada.
- **Dois a quatro cubos de peça funcional** com textura própria (`*_optics` 32×32
  ou `*_lens` 16×16), salientes 1–2 unidades em relação ao corpo. Ex.: aro + lente
  (altímetro); anel + ponta + lente (scanner); barra + três lentes (advanced).
- O corpo recua para dar espaço à peça funcional, que fica rente ao limite do
  bloco: o scanner tem corpo em `z 4–16` e bocal em `z 0–4`; o altímetro tem corpo
  em `y 2–16`, aro em `y 1–2` e lente em `y 0–1`.
- **Grupos** no Blockbench: `body` e o conjunto funcional (`nozzle`,
  `lens_assembly`), com origem no centro da face funcional.

### 8.2 Orientação e contratos com o código

- Fonte com a frente em **NORTH** (z = 0). O blockstate gira o modelo; a fonte
  nunca embute rotação.
- A peça funcional fica centrada na **origem do raycast**: `(8, 8, 0)` para a
  frente, `(8, 0, 8)` para baixo. Os testes conferem isso.
- **Nomes de cubo são contrato** com testes e código (`forward_antenna`,
  `downward_antenna`, `body`). Renomear exige atualizar os testes.
- Sockets de cabo usam o mesmo volume de corpo entre variantes, para que
  `housingSocket`/`cableSocket` não mudem.
- Colisão e outline (`SHAPE`, `NORTH_SHAPE`) acompanham a geometria em todas as
  orientações, inclusive no teto.

### 8.3 Motivos de pixel padronizados

Reutilize estes desenhos antes de inventar outros:

| Motivo | Desenho | Onde |
| --- | --- | --- |
| Moldura de casing | Canto = tom mais escuro; borda externa de 1 px no 2º tom; bisel interno claro no topo/esquerda e escuro na base/direita | Todas as faces de corpo |
| Tábuas | Tábua de 3 px + junta de 1 px no tom escuro; nó determinístico esparso | Miolo de madeira (scanner, altímetro) |
| Rebite | 1 px claro a 2 px do canto da moldura | Frente do scanner, fundo do altímetro |
| Fenda de redstone | Moldura escura 4×8 (vertical) ou 8×4 (horizontal) com miolo 2×6 de redstone; aceso usa `R/T/U` com brilho alternado, apagado `r/s/t` | Laterais que emitem |
| Socket de dados | Anel 4×4 de metal escuro, centro mais escuro | Traseira, onde o cabo encaixa |
| Lente | Borda no tom médio da lente; reflexo claro em cima à esquerda; 1 px de brilho azul (`#5371C6`) | Toda peça óptica |
| Placa concêntrica | Retângulos concêntricos de latão alternando claro/escuro | Tampa do advanced |
| Bocal escalonado | Cada degrau 2 unidades mais estreito e 1 à frente; anel médio, ponta clara, lente escura | Frente do scanner |

### 8.4 Pipeline (script de bootstrap → Blockbench → exportador → testes)

1. **Bootstrap** (`blockbench/<bloco>/create-base-model.mjs`): escreve o `.bbmodel`
   inicial com os helpers de `blockbench/_tools/bbtools.mjs`. As texturas são
   **mapas de caracteres escritos à mão**, um caractere por pixel, com as chaves de
   `PALETTE` (seção 5); `checkMap` valida as dimensões de cada face e `blit` monta o
   atlas. Todo cubo lista as **seis faces**; as escondidas vão como `texture: null` (o
   `cube()` do `bbtools` preenche). O Blockbench 5.2 falha ao abrir um `.bbmodel` com face
   faltando e mostra o projeto pela metade; o exportador recusa a fonte nesse caso. Ruído procedural é proibido. O bootstrap roda **uma vez**: rodá-lo de novo
   sobrescreve o refino feito no Blockbench.
2. **Refino no Blockbench:** silhueta, contraste e pixels finais, com a paleta `.gpl`
   carregada e a grade de pixels ligada. Salvar com texturas incorporadas.
3. **Export** (`blockbench/<bloco>/export-assets.mjs`): chama `exportBlockModel` do
   `bbtools.mjs`, que grava o JSON do bloco, as variantes (`_powered`) e os PNGs do
   nosso namespace. Falha com cubo rotacionado, coordenada fracionária, face fora de
   1:1 ou textura não incorporada. Não exportar pelo menu do Blockbench.
4. **Testes de assets** (`src/test/.../*AssetsTest.java`, regras comuns em
   `SensorModelChecks`): densidade 1:1, coordenadas inteiras, PNG 16/32 opaco,
   `powered` só troca o atlas, colisão segue o modelo, face funcional no lugar certo.
5. **Item:** o modelo de item herda o de bloco e só ajusta `display` quando a face
   funcional não aparece no inventário (o altímetro mostra a lente de baixo).

### 8.5 Variante `powered`

- Um segundo atlas idêntico, em que **só os pixels da fenda mudam**. Mesmos
  elementos, mesmas UVs, mesma lista de texturas; o teste
  `assertPoweredOnlySwapsAtlas` garante isso.
- Uma variante acende todas as fendas juntas. Acender por canal exigiria um estado
  por canal no bloco; só fazer se o jogo precisar.

### 8.6 README por bloco

Cada `blockbench/<bloco>/README.md` segue o mesmo esqueleto:

1. Título com versão e família de material; link para este guia e referências diretas.
2. **Identidade:** material, peça funcional, redstone, traseira, tampa (as pistas da
   seção 7).
3. **Peças:** tabela `elemento | volume | material`.
4. **Texturas:** cada PNG com tamanho e regiões do atlas.
5. **Exportação:** comando do exportador e aviso sobre o bootstrap.
6. **Mudou a geometria?** classes de colisão/sockets e testes a rodar.
7. **Pendente:** o que não foi verificado ou falta ligar no código.

### 8.7 Faces configuráveis por blockstate

Convenção de 2026-11-04 (origem: [plano do Attitude Reference Unit](attitude-reference-unit-plan.md)).
Vale para qualquer bloco em que o jogador escolhe o papel de uma face (Nariz,
Acima, entrada, saída) em vez de só girar o bloco.

- **A marcação segue o blockstate, não um renderer.** A face marcada aparece igual no
  mundo, no item, no Ponder, em schematics e dentro de sublevels, sem custo por frame.
  Block entity renderer só se a marcação precisar animar.
- **Duas formas de marcar o papel de uma face** (escolha por bloco):
  1. *Marca na face* (convenção de 2026-11-04): corpo exportado em 6 partes multipart, uma por face, cada
     uma um único elemento com o volume do corpo e **só aquela face desenhada**, com um modelo por papel
     (`plain`, `nose`, `up`) escolhido pelo blockstate; Nariz = arco de 1 px em cobre, Acima = o mesmo arco em
     latão. Sem renderer; símbolos simétricos, sem rotação de UV (evite seta: lê como "para a frente").
     Foi usada no Attitude Reference Unit v2.1 e saiu dele.
  2. *Hastes de referência* (Attitude Reference Unit v3, 2026-11-05): uma haste por papel, um único modelo-fonte
     por haste girado por `x`/`y` do blockstate. Cada haste tem **uma ponta diferente da outra** (bulbo 4×4×2 na
     frente, tampa lisa 2×2 atrás): cobre = Nariz (fonte com a frente em NORTH), latão = Acima (fonte com a frente
     em UP). As hastes terminam em peças do gimbal para nunca se sobreporem, e as faces do bloco ficam todas
     iguais.
- Um papel novo ganha material e forma distintos dos existentes antes de entrar no guia; nunca reutilize
  vermelho redstone, gema azul ou lente escura (§6) como marcação.
- **Parte decorativa animada** (só no v3): o gimbal do ARU é desenhado por um block entity renderer em loop fixo
  (relógio do jogo), com os modelos registrados como adicionais e fora do blockstate; ele não lê nem reage ao
  frame. A marcação do papel continua estática no blockstate. O modelo do item leva o gimbal em repouso.
- **Caixa de vidro** (v3): vidro de 1 cubo `[1,1,1]–[15,15,15]` em modelo próprio com `render_type: translucent`
  (os postes e sockets ficam no modelo cutout), 4 postes verticais de ferro industrial 2×16×2 nas quinas, sem
  aros horizontais.
- **Socket de dados** fica no centro de toda face onde o cabo encaixa, inclusive
  sobre a placa marcada; o contrato de `CableSocketProvider` não muda com o papel.
- **Fenda de redstone** (marca na face) fica só nas faces neutras; a variante `powered` (§8.5) troca
  apenas os pixels da fenda. O Attitude Reference Unit v3 não tem indicador de redstone.
- **Legibilidade:** a marcação precisa ser reconhecida a 16 px, a ~5 blocos, sem
  tooltip. Validar no Blockbench e no jogo antes de fechar o desenho de um bloco.
- **Código:** `rotate`/`mirror` transformam todas as propriedades de face juntas;
  combinações inválidas (ex.: Nariz colinear com Acima) nunca chegam ao blockstate.
- **Testes de assets:** marca na face: as 6 partes cobrem exatamente o volume do corpo, cada papel usa a mesma
  textura em todas as faces e toda combinação válida resolve para um modelo. Hastes: a ponta de cada haste,
  depois das rotações do blockstate, cai na face do papel em todas as orientações válidas. Sempre: o volume dos
  modelos estáticos igual ao `SHAPE`.

## 9. Mesa modular e módulos (v2.1)

Os módulos da mesa foram redesenhados na linguagem v2 (estado na seção 9.5). Esta seção
define as regras. As propostas abaixo foram aprovadas em 2026-10-27.

### 9.1 Escala e grade

Fatos do código (`ModularTableRenderer`, `PanelCoordinates`):

- A base tem 4 unidades de espessura (`PanelCoordinates.SURFACE`; o corpo de 4 px do Wire Connector encaixa na borda). A face exposta tem **3×3 células de 16/3
  unidades** (≈ 5,33).
- O módulo é renderizado **em escala de mundo**: 1 unidade do modelo = 1/16 de
  bloco. O renderer leva o ponto `(8, 0, 8)` do modelo ao centro do footprint, na
  superfície da mesa, e gira para mesas de parede/teto.

Regras:

- **Mesma densidade do resto do mod:** 1 texel = 1 unidade. Não modelar o módulo em
  escala maior para o renderer encolher: os pixels ficariam menores que os da mesa e
  dos blocos vizinhos.
- Modelar com a mesa voltada para cima, **apoio em Y = 0** e centro em **X/Z = 8**.
  Não embutir offsets nem a espessura da mesa.
- Coordenadas inteiras. Para ficar centrado em 8, a largura e a profundidade de
  cada peça visível do conjunto são **pares**.
- Como a célula não é inteira, os texels do módulo não se alinham com os da base.
  É esperado; não tentar compensar.

### 9.2 Footprint, altura e orçamento

Tamanho máximo da carcaça por número de células, com folga mínima de 1 unidade
entre módulos vizinhos:

| Células no eixo | Espaço da célula | Carcaça | Volume (eixo) |
| --- | --- | --- | --- |
| 1 | 5,33 | **4** | 6–10 |
| 2 | 10,67 | **10** | 3–13 |
| 3 | 16 | **14** | 1–15 |

| Footprint | Carcaça (X × Z) | Exemplos |
| --- | --- | --- |
| 1×1 | 4×4 | lâmpada, buzzer |
| 2×1 | 10×4 | lâmpada longa |
| 2×3 | 10×14 | FMS |
| 2×2 | 10×10 | Attitude Indicator (cunha na mesa de chão, placa fina em parede e teto) |
| 3×3 | 14×14 | — |

- **Altura:** 2–4 unidades acima da mesa. **Exceção "módulo de display de pé" (2026-11-05, revista na v2 2×2):** a cunha do Attitude Indicator tem 12, e o topo da tela encosta no topo do bloco (4 da mesa + 12); seu volume de seleção vai até o topo do bloco (`PanelCoordinates.moduleTop`). Tela de pé é **um cubo sólido** com a moldura pintada, nunca barras soltas em volta de uma lente de uma face: o display é desenhado sem cull e apareceria por qualquer fresta. Em módulo grande (FMS), detalhes sobre a carcaça
  sobem só 1 unidade: relevos de 2 ou mais ficaram estranhos no jogo. Até 6 só para peça funcional que precisa
  ser vista de lado (alavanca, knob). Base/aro de 1 unidade + corpo/face.
- **Elementos:** 1×1 com 2–5 cubos; 2×1 até 8; módulo de tela (2×3) até ~15.
- **Texturas:** um atlas 16×16 por módulo pequeno, 32×32 para o de tela. Materiais
  compartilhados entre módulos (aro, carcaça) vivem numa textura comum, para que as
  variantes troquem só a peça funcional.
- **Orientação:** Z baixo é o topo do painel (longe do piloto), como no FMS: a tela
  ocupa as linhas de cima e o teclado, a de baixo. Módulos 2×1 são modelados no eixo
  X; o renderer gira para 1×2.

### 9.3 Família de material

Segue a navigation table do Simulated e os instrumentos de latão:

| Peça | Material | Motivo |
| --- | --- | --- |
| Base da mesa | Moldura de latão + tampo de madeira escura (motivo de casing) | Mesma família do altímetro e do GPWS; lê como "painel de instrumentos" |
| Carcaça dos módulos | Metal escuro com aro de latão de 1 px (o FMS usa ferro industrial: é o módulo grande da mesa) | Contrasta com o tampo de madeira, sem competir com a peça funcional |
| Peça funcional | Lente, mostrador de pergaminho, tecla ou grade de buzzer | Cor com função (seção 6) |

**Blocos que formam superfície contínua** (a base da mesa, e qualquer bloco futuro que se
emende com vizinhos iguais) usam **textura conectada no layout CTM compacto**: cinco tiles de
16×16 (moldura, horizontal, vertical, canto interno, miolo), escolhidos por quarto de face no
modelo do cliente, sem estados de bloco extras. O miolo é função só do pixel e a moldura não
tem arremate nas pontas, para emendar sem costura. Referência de implementação:
[base da mesa](../blockbench/modular-table-base/README.md).

### 9.4 Peças funcionais dos módulos

- **Lâmpada:** inspirada no **nixie tube** do Create (`create:block/nixie_tube`) e
  na lâmpada de sinal de trem (`create:block/signal_glow`): soquete opaco, tubo de
  vidro translúcido sem face inferior e um filamento visível através do vidro. O
  vidro tem alpha baixo e uniforme, com as laterais mais claras e o miolo mais
  fundo, para ler como cilindro. O filamento é uma malha: cinza de metal escuro
  quando apagado, xadrez de redstone quando aceso. Vidro, soquete e filamento são
  texturas separadas (o renderer classifica as faces pelo nome do sprite); aceso e
  apagado mantêm a geometria; a emissão pertence à lâmpada, não à mesa
  (`docs/lamp-emission.md`). O vidro é a **única** peça que pode ser translúcida (o vidro de bloco do Attitude Reference Unit, §8.7, é o outro caso declarado).
- **Cores de alerta:** Warning = rampa de redstone. Caution = âmbar feito com os
  tons claros do latão (`#E4B763`–`#FFEB8C`), porque o amarelo de sinalização saiu
  da paleta. Na lâmpada (entregue em 2026-10-28), o âmbar é o mesmo mapa do vermelho
  com a troca de chaves R→5, S→6, T→7, U→8 (o `5`, `#CEA05A`, mantém a borda escura do
  vidro). O vidro mantém a cor apagado, e o filamento aceso também fica âmbar, para o
  vermelho não virar laranja atrás do vidro âmbar.
- **Buzzer:** grade de furos pintada (pixels escuros alternados) numa tampa de metal
  escuro. As 16 facetas a 22,5° do v1 saem: uma carcaça quadrada de 4×4 com tampa
  em degrau lê como "alto-falante do Create" sem rotação.
- **Tela (FMS):** área de lente escura 1:1 dentro de moldura de latão; teclas como
  cubos de 2×2 ou pintadas na tampa, nunca texto.
- **Conteúdo animado de tela** (exceção aprovada em 2026-10-28): o que a tela *exibe* não é
  textura, é um display aceso. Pode sair da paleta e da grade de pixels: traços vetoriais
  finos, desenhados pelo renderer em tempo real, sem iluminação do mundo, sobre a lente
  apagada em preto. Grafia e marcas em branco (`#F2F2EA`); latão (`#E4B763`) só para o
  marcador que importa. Texto só com a fonte do Minecraft. Tamanhos fixos em unidades do
  modelo, proporcionais à lente. Primeiro uso: bússola do FMS
  ([README](../blockbench/fms-module-2x3/README.md)).
- **Estado em tela de bloco** (decisão de 2026-10-28, Avionics Computer): um indicador simples
  de estado numa tela de bloco (cursor em operação normal, `!` âmbar ou vermelho, `x` de INOP) é
  **textura animada vanilla**
  (faixa de quadros quadrados + `.mcmeta`), dentro da paleta, trocada por uma propriedade de
  blockstate com a mesma geometria. Sem renderer nem emissão. É a técnica do CC:Tweaked.
- **Display de horizonte artificial** (2026-11-05, Attitude Indicator): mesma exceção, também geometria em um passe plano e sem luz do mundo (como a bússola), com fonte 5×7 própria em polígonos. Fundo preto, traços de pelo menos 0,07 unidade, cores saturadas (céu e terra fora da paleta), brasão do símbolo da aeronave em latão (`#E4B763`). Layout puro e testável: `AttitudeDisplayLayout`. A estratégia de render-to-texture do plano não foi usada neste estágio ([README](../blockbench/attitude-indicator-module/README.md)).
- **Legibilidade de perto:** a 1 bloco, um módulo 1×1 ocupa poucos texels por face.
  Cada pixel precisa de função: contorno, aro, lente, reflexo. Nada de textura de
  material em peça de 4 unidades além do contorno.

### 9.5 Estado dos módulos v1 frente à v2.1

| Módulo | Divergências a corrigir no redesign |
| --- | --- |
| Lâmpada 1×1 | **v2.1 em 2026-10-27** (tubo nixie, 3 cubos, 4×4×4); faltam refino no Blockbench e validação no jogo: [README](../blockbench/lamp-module-1x1/README.md) |
| Lâmpada 2×1 | **v2.1 em 2026-10-27** (tubo nixie horizontal, 3 cubos, 10×4×4, texturas da 1×1); faltam refino no Blockbench e validação no jogo: [README](../blockbench/lamp-module-2x1/README.md) |
| Buzzer 1×1 | **v2.1 em 2026-10-29** (carcaça de metal escuro com aro de latão, tampa em degrau com grade pintada; 2 cubos, 4×4×3, atlas 16×16); faltam refino no Blockbench e validação no jogo: [README](../blockbench/buzzer-module-1x1/README.md) |
| FMS 2×3 | **v2.1 em 2026-10-28** (carcaça de ferro industrial, tela em moldura de latão, 2 knobs de latão e 2 teclas, relevo de 1 unidade; 6 cubos, 10×3×14); faltam refino no Blockbench e validação no jogo: [README](../blockbench/fms-module-2x3/README.md) |
| FMS Compass (item) | **v2.1 em 2026-10-28** (bússola de bolso redonda em degraus: aro de ferro, bezel de latão, lente com índice e ponteiro, alça de latão; 6 cubos; variante `_linked` acende a ponta do ponteiro e o reflexo da lente); faltam refino no Blockbench e validação no jogo: [README](../blockbench/fms-navigator/README.md) |
| Attitude Indicator 2×2 | **v2 visual em 2026-11-05** (cunha de 5 cubos 10×12×10: caixa de ferro, tampo a 22,5° recuado 1, tela sólida com moldura de latão pintada e lente 8×8, viseira de latão, sem controles; placa de 2 cubos; atlas 32×32; display por geometria com valores mockados; só criativo, sem receita). Substitui a v1 3×3 (11 cubos, frestas atrás da tela): [README](../blockbench/attitude-indicator-module/README.md) |
| Base da mesa | **v2.1 em 2026-10-27** (latão + madeira escura, tampo rente, texturas conectadas); faltam refino no Blockbench e validação no jogo: [README](../blockbench/modular-table-base/README.md) |

Variantes de tamanho de um módulo (como a lâmpada 2×1) são modelos próprios, nunca
esticamentos: mesma linguagem, mesmas texturas, com regiões novas para as faces longas.

## 10. Fluxo de entrega

1. **Novo projeto:** *Java Block/Item*, nome = id do bloco, resolução 32×32,
   *Box UV* desligado. Na prática, gerado pelo bootstrap (seção 8.4).
2. **Referência lado a lado:** importar no mesmo workspace o JSON do modelo
   análogo do Create ou do Simulated, extraído do jar, para comparar escala e
   densidade. Não exportar a referência.
3. **Geometria:** snap de 1 unidade e camadas (seção 3 ou 9), sem textura.
   Validar a silhueta em cinza.
4. **Texturas:** mapas de pixel com a paleta; refino no Blockbench Paint.
   Reaproveitar `create:block/...` onde couber.
5. **Variantes:** `block` e `block_powered` (ou aceso/apagado) só trocam
   textura, nunca geometria.
6. **Exportar** com o `export-assets.mjs` do bloco; manter o `.bbmodel`, com
   texturas incorporadas, em `blockbench/<bloco>/`.
7. **Testes** de assets e de colisão; README do bloco (seção 8.6).
8. **Comparar no jogo:** colocar o bloco entre casings de andesito/latão e num
   casco de madeira, de dia e de noite, com e sem shader. Módulos: na mesa, a 1
   bloco, em mesa de chão, parede e teto.

### Checklist de entrega

- [ ] Densidade 1:1 em todas as faces (pixel grid uniforme; exportador verde)
- [ ] Coordenadas inteiras (ou fração justificada no README do bloco)
- [ ] Elementos dentro do orçamento (seção 3 para blocos, 9.2 para módulos)
- [ ] ≥ 90% dos pixels com cores da paleta (seção 5) ou texturas do Create
- [ ] Motivos da seção 8.3 reutilizados onde couberem
- [ ] Silhueta em camadas; peça funcional legível na distância de uso (seção 2)
- [ ] Face funcional coerente com a direção real do código
- [ ] Indicador de redstone escuro/aceso, se houver; variante só troca textura
- [ ] Faces configuráveis, se houver: multipart por face, símbolos da seção 8.7
      legíveis a ~5 blocos e corretos em todas as orientações válidas
- [ ] Ao lado de casings do Create, parece da mesma família
- [ ] Colisão/outline e testes de assets atualizados e verdes
- [ ] `.bbmodel` e README do bloco atualizados; o que não foi verificado no
      jogo está em "Pendente"

## 11. Regras técnicas gerais

Valem independentemente da estética:

- **Face funcional inequívoca** e coerente com a direção do código, inclusive
  em sublevels rotacionados.
- **Sem dependência de shader:** a leitura é sem shader. Testar também com
  shaders (os glitches das lâmpadas com Complementary seguem em aberto, ver
  LÂMPADA-01).
- Colisão acompanha a geometria relevante.
- O `.bbmodel` é a fonte. O JSON/PNG em `src/main/resources` é o asset de
  runtime e precisa ficar coerente com ele.

## 12. Telas e HUD (decisão de 2026-10-28)

As telas do mod (Avionics Computer, mesa, módulos) seguem a HUD fixa do inspector do
Exotic Pets (`../pets/.../client/inspector/PetInspectorHud.java`): **limpas, sem ruído**.

- Painel escuro da paleta `UiTheme.DARK` da R-UI Lib, quase opaco, contorno de 1 px e
  **faixa de acento de 2 px** à esquerda. A faixa toma a cor do estado quando há um
  (âmbar em atenção/INOP, vermelho em alerta).
- Cabeçalho: título à esquerda, metadado discreto à direita. Logo abaixo, uma linha de
  destaque (`»`) com o estado que importa agora.
- Seções com título curto em maiúsculas, cor apagada, separadas por divisor fino.
- Linhas: rótulo apagado à esquerda, valor alinhado à direita, cortado com reticências.
- Valor que acabou de mudar acende na cor de aviso e esmaece.
- Cores só com função: sucesso, aviso, erro. Nada de ícones, caixas decorativas ou texto
  de ajuda permanente; dica só em tooltip.
- Ações são poucas, no rodapé, e aplicam na hora: sem Salvar/Cancelar.
- Escolhas de lista usam o radio da R-UI Lib (`UiPaint.radio`, `Radio.INDICATOR` = 8 px),
  nunca os glifos `●`/`○`, que têm larguras diferentes e fazem o texto pular. A marca tem o
  mesmo tamanho nos dois estados; a opção marcada só preenche o anel de branco. Opção reservada
  para o futuro aparece apagada com "em breve" e não faz nada.
- Rótulos flutuantes no mundo (configuração com a wrench) usam o mesmo painel, menor.
- Itens (frequências do Redstone Link) usam slots de 18 px, ícone de 16 px com moldura de
  1 px; o slot marcado ganha contorno claro. Clicar num slot abre uma tela própria com os
  slots e o inventário + hotbar (9 colunas). Arrastar um item até o slot ou clicar nele
  preenche; clique direito limpa. Valores numéricos e escolhas de uma linha (como o lado do scanner) usam o stepper
  `‹ valor ›` da R-UI Lib (`Stepper`; em telas imediatas, `Stepper.paint`/`Stepper.layout`),
  nunca slider, e respondem à roda do mouse; radio fica para listas que mostram
  as opções lado a lado. A tooltip de uma linha com valor só aparece sobre o rótulo, nunca
  sobre o valor ou as setas.
- Texto livre (nome, coordenadas) usa o campo do painel (`HudPanel.field`, caixa
  `UiPaint.input`): rótulo apagado à esquerda, valor na caixa, cursor no fim, erro em
  vermelho. Formulários de criação são a exceção ao "sem Salvar": abrem numa tela própria
  sobre a principal, com Adicionar/Voltar; uma edição vale ao sair (Concluir ou ESC).
  Ação destrutiva pede um segundo clique no mesmo botão ("Confirmar exclusão").
- Uma escolha de lista pode ter ação secundária no clique direito (editar o item), sempre
  explicada na tooltip da linha. Listas longas têm altura limitada e rolam (`HudPanel.scroll`): linha a linha pela roda do
  mouse, barra de 2 px à direita que se arrasta ou clica.

## 13. Fontes

- Jars locais: `create-1.21.1-6.0.10.jar` e
  `create-aeronautics-bundled-1.21.1-1.3.2.jar` (aeronautics, simulated,
  offroad), em `../sable-horses/run/mods/`.
- [Galeria do Create Aeronautics no Modrinth](https://modrinth.com/mod/create-aeronautics/gallery)
- [Create Aeronautics no CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-aeronautics)
- [Busca "aeronautics" no createmod.com](https://createmod.com/search?q=aeronautics),
  mais as buscas airship, zeppelin, aeronautics plane, aeronautics helicopter e biplane.
- [Create Aeronautics (Simulated) no createmod.com](https://createmod.com/de/mods/simulated)
- Registro do brief original do Radar Altimeter v2: histórico do git deste arquivo
  (commit `529f835`).
