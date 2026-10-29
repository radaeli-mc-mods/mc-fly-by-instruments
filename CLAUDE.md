# Aeronautics: Fly by Instruments — instruções do projeto

Addon NeoForge 1.21.1 para Create Aeronautics. Documentação técnica em
`docs/README.md`; roadmap em `PRD.md`.

## Modelagem e texturização

Sempre que o pedido envolver **modelar, texturizar, redesenhar ou ajustar o
modelo** de qualquer bloco, item ou módulo do mod:

1. **Leia antes `docs/visual-design-guide.md`** (guia visual v2.1: linguagem
   Create / Create Aeronautics) e siga o checklist de entrega da seção 10.
   Partir da receita dos sensores (seção 8); módulos da mesa seguem a seção 9.
2. **Use o Blockbench.** A fonte é o `.bbmodel` em `blockbench/<bloco>/`,
   com texturas incorporadas. JSON e PNG em `src/main/resources` são export.
3. Consulte as referências locais antes de desenhar:
   - pranchas em `reference/visual/` (fora do git);
   - paleta do Blockbench em `blockbench/_palette/create-aeronautics.gpl`;
   - modelos análogos do Create e do Simulated nos jars de `../sable-horses/run/mods/`.
4. **Densidade 1 texel = 1 unidade**, coordenadas inteiras, rampas de cor do
   Create (ou as próprias texturas `create:block/...`). Nunca esticar uma
   textura inteira numa face.
5. Se o pedido divergir do guia, aponte a divergência antes de seguir. Se
   surgir uma decisão de estilo que valha para o mod todo, proponha atualizar
   o guia.
6. Mudou a geometria? Atualize a forma de colisão do bloco e os testes de
   assets correspondentes (`src/test/.../*AssetsTest.java`).
7. Seja honesto nos gates: diga o que foi verificado de fato (render local,
   Blockbench, build, jogo).

## Escopo atual

Radar Altimeter, Terrain Scanner e Advanced Terrain Scanner são entregues
(saída de redstone + CC:Tweaked). A suíte aviônica (Computer, Mesa, Lamp 1x1/2x1,
Buzzer, FMS, FMS Compass, Wire, Wire Connector, Wire Support) tem receita mas
mantém "(Experimental)" no nome. Network Bridge segue sem receita.

## Build

```powershell
.\gradlew.bat build
.\gradlew.bat test
```
