# Logo

A logo é um horizonte artificial em pixel art com o Avionics Computer em alerta na frente,
saindo pela borda de baixo do aro. É gerada por script, não por PSD:

```powershell
python blockbench/_logo/compose-logo.py
```

- `render-logo-computer.mjs` renderiza `avionics_computer_warning` (yaw 210°, pitch 30°) com
  `../_tools/render-scene.mjs`, a 32 px por texel.
- `compose-logo.py` desenha o mostrador numa grade de 128 unidades ampliada 8× (4 unidades =
  1 texel do computador), monta o aro, a sombra e os contornos, e grava
  `docs/gallery/logo-large.png` (1200×1200) e `docs/gallery/logo.png` (512×512).
- Precisa de Node e Pillow (`pip install pillow`). Inclinação, pitch, cores e raios ficam
  no topo do `.py`.

`docs/gallery/logo.psd`, `docs/gallery/logo_scanner_scen.png` e a cena abaixo são da logo
anterior (Radar Altimeter num círculo azul).

## Cena dos sensores

`logo_scene.bbmodel` (Generic Model) reúne os três sensores para a arte da logo. Cada
sensor é um grupo raiz com pivô no centro da base: selecione o grupo e mova ou gire
livremente, sem afetar as fontes dos blocos.

| Grupo | Posição inicial | Giro (Y) |
| --- | --- | --- |
| `terrain_scanner_basic` | `[22, 0, -3]` | 30° |
| `radar_altimeter` | `[0, 4, 5]` | 45° |
| `terrain_scanner_advanced` | `[-22, 0, -3]` | -30° |

A frente dos scanners aponta para -Z (norte). Olhando por esse lado, +X fica à
esquerda.

A textura de latão do Create no topo do altímetro não vai embutida (licença). Ela é
carregada de `reference/create-textures/brass_casing.png`, fora do git.

Para regenerar a cena a partir dos modelos atuais (isso sobrescreve as posições):

```powershell
node blockbench/_logo/compose-logo-scene.mjs
```
