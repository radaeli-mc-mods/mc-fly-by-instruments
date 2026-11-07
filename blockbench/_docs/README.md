# Previews da documentação

`render-docs.mjs` monta as cenas a partir dos modelos exportados em `src/main/resources` e grava
os PNGs em `docs/gallery/renders/`. O README do mod usa esses PNGs pelo raw do GitHub, e o portal
(`mod-docs`) usa cópias em `src/projects/flight-safety-instruments/assets/`.

```powershell
node blockbench/_docs/render-docs.mjs
Copy-Item docs/gallery/renders/*.png ../mod-docs/src/projects/flight-safety-instruments/assets/
```

| Arquivo | Cena |
| --- | --- |
| `avionics-computer.png` | Avionics Computer normal e em alerta |
| `avionics-wire.png` | Computador → dois Wire Supports → Terrain Scanner, num deck de toras de carvalho descascado |
| `wire-nodes.png` | Conector, suporte com o fio passando e conector `slim` na borda da mesa |
| `modular-table.png` | Mesa com FMS, lâmpada vermelha acesa, lâmpada âmbar e buzzer |
| `table-lamps.png` | Lâmpadas 1×1 e 2×1 apagadas e acesas, e o buzzer |
| `table-docked.png` | Mesa acoplada em cima do Avionics Computer |
| `fms-module.png` | Módulo FMS sozinho |
| `attitude-reference-unit.png` | Attitude Reference Unit, Nose = norte e Acima = cima, gimbal em repouso |
| `attitude-indicator.png` | Mesa com a cunha do Attitude Indicator (2×2), lâmpada acesa e buzzer; o display aparece apagado |
| `attitude-indicator-item.png` | Item de bolso do Attitude Indicator ao lado do FMS Compass, visto de cima |

- **Renderizador:** `../_tools/render-scene.mjs` é um rasterizador isométrico com luz direcional
  do Minecraft (topo 1, laterais 0,8/0,6, fundo 0,5), amostragem nearest e vidro translúcido por
  último. Não há AO, shader, texto de block entity nem brilho das lâmpadas.
- **Fio:** `../_tools/wire-scene.mjs` porta `WireGeometry`, `WireAttachPoints` e o tubo do
  `WireRenderer`. Se as constantes do Java mudarem, atualize esse arquivo.
- **Texturas de terceiros:** as do Create vêm de `reference/create-textures/` e as do Minecraft de
  `reference/minecraft-textures/` (copiadas do jar `client-extra` em `build/moddev/artifacts`).
  Ambas ficam fora do git. Uma textura ausente aparece cinza, com um aviso no console.
- **Cenas:** o deck é de stripped oak log deitada em X, para contrastar com o ferro e o latão dos
  modelos. Ligações mostram só o Avionics Wire (o cabo saiu da documentação) e usam os nossos sensores
  como exemplo, não a Network Bridge.
