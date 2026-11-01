# Configuration

This reference applies to Fly by Instruments on Minecraft 1.21.1 / NeoForge.

## aeronautics_fly_by_instruments-server.toml

Format: TOML. The base file is generated in config/ in the instance or server directory. The world owner or server administrator edits it. A file with the same name in saves/&lt;world&gt;/serverconfig/ (singleplayer) or &lt;world&gt;/serverconfig/ (dedicated server) overrides the base configuration for that world. Server values apply to all connected players.

Close the world or stop the server before editing, then reopen/restart to apply the file. Keep values within the inclusive ranges below; NeoForge validates the configuration against this schema and corrects invalid entries during loading.

```toml
#Maximum cable and endpoint blocks visited while discovering one avionics network.
# Default: 4096
# Range: 1 ~ 1000000
maxAvionicsNetworkBlocks = 4096
#Ticks between server-side acquisition sensor scans.
# Default: 5
# Range: 1 ~ 1200
sensorScanIntervalTicks = 5
#Maximum Radar Altimeter range in blocks.
# Default: 128
# Range: 1 ~ 4096
radarAltimeterMaxRange = 128
#Maximum Terrain Scanner range in blocks.
# Default: 128
# Range: 1 ~ 4096
terrainScannerMaxRange = 128
#Block light emitted by a modular table with a fully lit lamp; 0 disables it.
# Default: 5
# Range: 0 ~ 15
lampLightLevel = 5
#Longest Avionics Wire span between two consecutive connectors/supports, in blocks.
# Default: 24
# Range: 2 ~ 64
wireMaxSpan = 24
#Most connectors and supports a single Avionics Wire may pass through, ends included.
# Default: 32
# Range: 2 ~ 128
wireMaxPoints = 32
#Blocks of wire made from one Avionics Wire item (costs round up).
# Default: 8
# Range: 1 ~ 64
wireBlocksPerItem = 8
```

| Key | Type | Default | Accepted values (inclusive) | Effect |
| --- | --- | --- | --- | --- |
| `maxAvionicsNetworkBlocks` | Integer | 4096 | 1–1000000 | Maximum visited nodes/devices in one network discovery. |
| `sensorScanIntervalTicks` | Integer | 5 | 1–1200 | Ticks between sensor scans (20 ticks = 1 second at 20 TPS). |
| `radarAltimeterMaxRange` | Integer | 128 | 1–4096 | Maximum altitude measurement distance, in blocks. |
| `terrainScannerMaxRange` | Integer | 128 | 1–4096 | Maximum scan distance for both terrain scanners, in blocks. |
| `lampLightLevel` | Integer | 5 | 0–15 | Maximum block light from a fully lit panel; 0 disables block light. |
| `wireMaxSpan` | Integer | 24 | 2–64 | Longest span between consecutive wire nodes, in blocks. |
| `wireMaxPoints` | Integer | 32 | 2–128 | Maximum points in a wire route, including both ends. |
| `wireBlocksPerItem` | Integer | 8 | 1–64 | Wire length per item, in blocks; item cost rounds up. |

All keys are top-level integers. There are no groups, lists, string identifiers or enum values in this file. No separate client/common configuration file is registered by the mod.

Per-device ranges, frequencies, aperture, sensitivity, panel indications and waypoints are saved with the devices, not in this file. Wiring View and Ponder keys are set in Minecraft Controls. See the [Ponder tutorials](ponder.md) and functional player guides.

---

# Configurações

Esta referência se aplica ao Fly by Instruments no Minecraft 1.21.1 / NeoForge.

## aeronautics_fly_by_instruments-server.toml

Formato: TOML. O arquivo base é gerado em config/ no diretório da instância ou servidor. O dono do mundo ou administrador edita o arquivo. Um arquivo de mesmo nome em saves/&lt;mundo&gt;/serverconfig/ (singleplayer) ou &lt;mundo&gt;/serverconfig/ (servidor dedicado) substitui a configuração base para esse mundo. Os valores do servidor valem para todos os jogadores conectados.

Feche o mundo ou pare o servidor antes de editar e reabra/reinicie para aplicar o arquivo. Respeite os limites inclusivos abaixo; o NeoForge valida a configuração contra este schema e corrige entradas inválidas durante o carregamento.

```toml
#Maximum cable and endpoint blocks visited while discovering one avionics network.
# Default: 4096
# Range: 1 ~ 1000000
maxAvionicsNetworkBlocks = 4096
#Ticks between server-side acquisition sensor scans.
# Default: 5
# Range: 1 ~ 1200
sensorScanIntervalTicks = 5
#Maximum Radar Altimeter range in blocks.
# Default: 128
# Range: 1 ~ 4096
radarAltimeterMaxRange = 128
#Maximum Terrain Scanner range in blocks.
# Default: 128
# Range: 1 ~ 4096
terrainScannerMaxRange = 128
#Block light emitted by a modular table with a fully lit lamp; 0 disables it.
# Default: 5
# Range: 0 ~ 15
lampLightLevel = 5
#Longest Avionics Wire span between two consecutive connectors/supports, in blocks.
# Default: 24
# Range: 2 ~ 64
wireMaxSpan = 24
#Most connectors and supports a single Avionics Wire may pass through, ends included.
# Default: 32
# Range: 2 ~ 128
wireMaxPoints = 32
#Blocks of wire made from one Avionics Wire item (costs round up).
# Default: 8
# Range: 1 ~ 64
wireBlocksPerItem = 8
```

| Chave | Tipo | Padrão | Valores aceitos (inclusivos) | Efeito |
| --- | --- | --- | --- | --- |
| `maxAvionicsNetworkBlocks` | Inteiro | 4096 | 1–1000000 | Máximo de nós/dispositivos visitados na descoberta de uma rede. |
| `sensorScanIntervalTicks` | Inteiro | 5 | 1–1200 | Ticks entre varreduras dos sensores (20 ticks = 1 segundo a 20 TPS). |
| `radarAltimeterMaxRange` | Inteiro | 128 | 1–4096 | Distância máxima de medição do altímetro, em blocos. |
| `terrainScannerMaxRange` | Inteiro | 128 | 1–4096 | Distância máxima dos dois scanners de terreno, em blocos. |
| `lampLightLevel` | Inteiro | 5 | 0–15 | Luz máxima de bloco do painel totalmente aceso; 0 desativa a luz de bloco. |
| `wireMaxSpan` | Inteiro | 24 | 2–64 | Maior vão entre nós consecutivos do fio, em blocos. |
| `wireMaxPoints` | Inteiro | 32 | 2–128 | Máximo de pontos num fio, incluindo as duas extremidades. |
| `wireBlocksPerItem` | Inteiro | 8 | 1–64 | Comprimento de fio por item, em blocos; custo arredondado para cima. |

Todas as chaves são inteiros no nível principal. Não há grupos, listas, identificadores de texto ou enums neste arquivo. O mod não registra arquivo separado de configuração de cliente/comum.

Faixas, frequências, abertura, sensibilidade, indicações e waypoints são salvos nos dispositivos, não neste arquivo. As teclas Wiring View e Ponder são ajustadas nos Controles do Minecraft. Veja os [Tutoriais Ponder](ponder.md) e os guias funcionais.
