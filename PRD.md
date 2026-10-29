# Product Requirements Document — Aeronautics: Fly by Instruments

## 1. Visão geral

**Aeronautics: Fly by Instruments** será um addon de segurança e instrumentação de voo para Create Aeronautics. O mod ajudará jogadores a detectar riscos durante o voo e construir sistemas de alerta físicos dentro das próprias contraptions.

O produto não será inicialmente um pacote geral de aviação. Seu foco será:

- consciência de terreno;
- avisos de proximidade e trajetória;
- instrumentação conectada fisicamente;
- painéis de alerta modulares;
- integração programática completa com CC:Tweaked.

Este PRD consolida as decisões de discovery e define um roadmap macro. O
documento de discovery continua como histórico e referência para ideias ainda
em aberto; em caso de divergência, prevalecem este PRD e os planos técnicos
mais recentes.

## 2. Objetivos do produto

### Objetivos

1. Adicionar um sistema GPWS funcional para contraptions voadoras.
2. Permitir que o jogador construa uma rede física de sensores usando cabos.
3. Oferecer uma experiência simples por padrão e uma configuração avançada opcional.
4. Transformar dados de voo em alertas físicos e programáveis.
5. Expor cada componente por sua API completa para CC:Tweaked no mesmo momento em que a funcionalidade for implementada.
6. Manter o custo de processamento previsível em servidores multiplayer.

### Fora do objetivo inicial

- motores ou sistemas de propulsão;
- autopilot completo;
- substituição do `Altitude Sensor` original;
- radar de tráfego ou armamento;
- logística de rotas;
- HUD genérico sem relação direta com segurança de voo.

### Princípios de desenvolvimento

- **Mesa modular primeiro:** a fundação funcional do Annunciator Panel é a
  primeira entrega de gameplay após a fundação técnica do projeto. Ela deve ser
  testável antes de o GPWS, os scanners e os alertas finais existirem.
- **Integração CC vertical:** CC:Tweaked não será uma fase posterior. Cada
  funcionalidade deverá nascer com seus periféricos, métodos, eventos,
  documentação e testes de API correspondentes.

## 3. Público e experiência desejada

O mod deve atender dois perfis:

### Jogador casual

O mod deve oferecer um caminho de segurança útil sem exigir conhecimento de
aviação ou configuração técnica. A meta mínima é:

1. colocar um GPWS e um Radar Altimeter;
2. conectá-los com Avionics Cable;
3. usar thresholds e fases automáticos;
4. receber alertas úteis de clearance/descida com uma configuração padrão ou
   rápida do painel.

Terrain Scanners ampliam os avisos de obstáculos, mas não são obrigatórios para
o caminho básico. Sensores opcionais ausentes não devem transformar um sistema
parcialmente montado em uma cascata de falhas. CC:Tweaked e os ajustes
detalhados também não serão pré-requisitos.

### Jogador avançado

Deve ser possível configurar thresholds, histerese, regras de arming/inhibit, sensores preferenciais, redundância, fases de voo e roteamento de canais.

### Princípio arcade-first

O mod pode usar componentes e linguagem visual de avionics, mas a camada padrão
deve priorizar diversão, leitura imediata e configuração mínima. A simulação
detalhada amplia a experiência para entusiastas; não pode ser pedágio para
receber proteção básica. Defaults tolerantes, presets, descoberta automática e
um caminho de painel pronto/rápido devem tornar o sistema útil em gameplay
arcade, inclusive para manter um airship em hover sem queda inesperada.

### Identidade visual dos componentes

Adotar uma linguagem de aviónica industrial em voxel: carcaças metálicas claras,
bases em grafite/preto e acentos de latão, azul técnico e cores funcionais.
Silhueta e marcações devem tornar a função e a face de medição intuitivas,
sem exigir conhecimentos de aviação. Módulos da mesa compartilham escala baixa,
apoio em Y=0 e centro horizontal em X/Z=8 na fonte; estados aceso/apagado devem
ser legíveis por módulo e sem shaders. O [guia de identidade visual](docs/visual-design-guide.md)
consolida paleta, materiais, proporções e checklist para os próximos modelos.
Cores futuras propostas no guia não criam funcionalidades ou vínculos obrigatórios
entre cores e canais do GPWS.

## 4. Escopo funcional inicial

### 4.1 GPWS Computer (hoje Avionics Computer)

> Atualização de 2026-10-28: o bloco virou o **Avionics Computer**, hub de sensores,
> função GPWS e gateway CC:Tweaked, sem perfis nem modos. Veja
> [docs/avionics-computer.md](docs/avionics-computer.md) e
> [docs/avionics-computer-plan.md](docs/avionics-computer-plan.md).

O GPWS Computer será o componente lógico central. Ele interpretará dados da rede de avionics e publicará os estados de alerta.

Modos e contextos iniciais:

- `OFF`/`STANDBY`;
- `ARMED`;
- `TEST`;
- decolagem;
- subida;
- cruzeiro;
- descida;
- aproximação/pouso.

No modo básico, a seleção de fase será automática. No modo avançado, poderá ser ajustada ou substituída manualmente.

Alertas iniciais:

- `TERRAIN`;
- `PULL UP`;
- `SINK RATE`;
- `DON'T SINK`;
- `TOO LOW`;
- `BANK ANGLE`;
- `SENSOR FAIL`.

Thresholds, tempos de confirmação, histerese e prioridade final de cada alerta serão definidos antes da implementação do motor de alertas.

### 4.2 Radar Altimeter

O Radar Altimeter mede clearance/AGL a partir do centro da face inferior do próprio bloco. Sua orientação é fixa no eixo inferior local e acompanha a inclinação/rotação do sublevel; ele também funciona quando colocado fora de uma contraption.

Características esperadas:

- bloco físico com a face emissora inferior visualmente identificável;
- nove casts: um central e oito em anel, com abertura de 20° em torno do eixo inferior local;
- consolidar os retornos válidos pela mediana; pelo menos três dos nove casts precisam atingir blocos com colisão;
- incluir terreno, construções e outras contraptions; ignorar o sublevel hospedeiro e entidades;
- medir distância decimal em blocos entre a face inferior e o primeiro hit;
- alcance máximo inicial de 128 blocos, configurável pelo servidor;
- amostragem inicial a cada 5 ticks, configurável pelo servidor;
- não carregar chunks: casts sem hit ou que alcancem uma fronteira descarregada não contam; menos de três retornos válidos produz `UNAVAILABLE`;
- erros de execução produzem `FAILED`;
- leitura relativa ao terreno, não à altitude global;
- peripheral CC:Tweaked independente `radar_altimeter`, com `getReading()` retornando estado, valor quando válido, unidade e tick da amostra;
- emitir `radar_altimeter_reading_changed` com o snapshot completo quando estado ou valor mudarem; mudança apenas do tick não emite evento.

### 4.3 Terrain Scanner

O Terrain Scanner será um bloco físico, normalmente colocado no nariz da aeronave.

Haverá duas versões funcionais, orientadas para a frente segundo a orientação local do bloco e acompanhando a atitude do sublevel:

- **Scanner simples:** nove raycasts em cone frontal de abertura total fixa de 90°;
- **Scanner avançado:** distribuição aproximadamente uniforme de 9 a 81 raycasts, com abertura total configurável de 30° a 180° (padrão 90°). A densidade cresce automaticamente com a área angular; o jogador não configura a contagem.

O scanner detectará como possíveis obstáculos:

- terreno;
- estruturas construídas;
- outras contraptions.

A abertura avançada será configurável por interface no jogo e por CC:Tweaked, validada e aplicada autoritativamente no servidor e salva por bloco. Ambos os scanners fornecem snapshots com contatos por raio, distância, direção relativa, quantidade de raios concluídos, cobertura e game time. Hits são genéricos, sem classificação do tipo de obstáculo. Uma varredura sem hits mas com cobertura retorna `VALID` e lista vazia; cobertura parcial mantém os contatos conhecidos; cobertura zero retorna `UNAVAILABLE`; erros retornam `FAILED`.

Os scans usam o intervalo global configurável (padrão 5 ticks), têm alcance máximo global configurável (padrão 128 blocos), não carregam chunks e tratam fronteiras descarregadas como cobertura incompleta. Ambas as variantes são endpoints da Avionics Cable Network e têm peripherals próprios `terrain_scanner` e `terrain_scanner_advanced`. **A integração das leituras ao GPWS foi implementada na Fase 4:** contatos conhecidos podem acionar `TERRAIN` e `PULL_UP`; cobertura parcial sem contato não é tratada como caminho livre. A validação final desses casos em voo permanece na estabilização.

### 4.4 Fontes de atitude e velocidade

O GPWS inicial não dependerá de blocos `Velocity Sensor` ou `Gimbal Sensor`
externos. Para manter o baseline e simplificar a montagem:

- a velocidade vertical será estimada pelo histórico de leituras do Radar
  Altimeter;
- o ângulo de bank será derivado da pose do sublevel Sable, usando a
  orientação do GPWS como eixo longitudinal.

Sensores externos poderão ser avaliados no futuro, mas não são requisito do
GPWS básico nem devem ser tratados como dependências do baseline atual.

### 4.5 Avionics Cable

O Avionics Cable será o único componente novo de conexão física na primeira versão.

Requisitos:

- conexão automática com sensores compatíveis, GPWS e módulos;
- rede formada por cabos adjacentes e ramificações naturais;
- descoberta automática dos componentes alcançáveis;
- desconexão detectável como sensor ausente ou falha;
- transporte apenas de dados;
- nenhum sistema wireless como mecanismo principal;
- sem blocos obrigatórios separados de junction, data port ou frequency link.

O cabo acompanhará a contraption dentro do sublevel. A topologia física deverá ser significativa para a construção, sem exigir configuração manual de frequências.

**Atualização (2026-10-28):** o Avionics Wire soma um segundo meio físico, sem substituir o cabo: fios pretos entre Wire Connectors (latão) apoiados em Wire Supports (ferro industrial), com o modo Wiring View. A rede é a mesma; ver [docs/avionics-wire.md](docs/avionics-wire.md).

**Atualização (2026-10-29):** o bloco Avionics Cable foi removido; o Avionics Wire é o único meio físico da rede.

### 4.6 Network Bridge

A Network Bridge será o gateway CC:Tweaked para consultar a topologia completa alcançável pela rede de Avionics Cable. Cada instrumento e componente continuará expondo sua própria peripheral independente; a bridge não substitui nem representa os métodos/eventos locais desses dispositivos.

- peripheral `avionics_network_bridge` com status, lista de endpoints e evento de mudança da rede;
- a `modular_table` expõe grid, módulos e eventos próprios; módulos também podem publicar canais consultáveis pela rede física;
- o Radar Altimeter continua acessível diretamente por sua própria peripheral, mesmo conectado à rede.

### 4.7 Annunciator Panel

O Annunciator Panel será uma mesa modular inspirada nas mesas do Aeroworks.

O contrato físico vigente está em [Mesa modular](docs/modular-table.md): somente
a mão principal instala/remove módulos e controla preview/rotação. Clique com
essa mão vazia abre o módulo atingido ou o painel numa célula vazia; Shift +
clique com a mão principal vazia abre sempre o painel geral na face exposta ou
num módulo, mesmo com o grid cheio e a secundária ocupada. Laterais do volume de
seleção dos módulos permitem abertura/remoção; bordas e traseira da base não.
Falhas de colocação não consomem itens nem abrem telas. Recolhimento com
Shift + Wrench e quebra/perda do suporte devolvem os módulos uma única vez,
preservando dados do FMS. Teste de som não salva o rascunho; Save aplica,
Cancel descarta e ESC aplica quando válido. Acesso às telas usa distância global
de oito blocos e mensagens de uma instalação substituída são rejeitadas.

Durante o design e a construção desse painel, o projeto deverá consultar
`reference/aeroworks-1.5.0.jar` como referência local para a experiência de
mesa modular, slots predefinidos, seleção de módulos e highlight visual. Esse
JAR é apenas material de referência; não é dependência de runtime ou compilação
do Aeronautics: Fly by Instruments, e nenhuma implementação ou asset deverá ser
copiado sem revisão de licença e necessidade explícita.

Requisitos:

- **Primeiro vertical slice:** criar as bases da mesa modular, o sistema de
  slots, a colocação e remoção de módulos, a persistência da configuração e o
  highlight visual do slot em edição antes da integração com os alertas finais.
- Deve existir pelo menos um item/módulo de teste que possa ser criado pelo
  jogador e usado para validar a ocupação, edição e leitura dos slots.
- A mesa e o módulo de lâmpada 1×1 devem funcionar com canal e saída simulados,
  configuráveis via CC:Tweaked, sem depender do GPWS ou dos sensores finais.
- slots predefinidos;
- highlight visual do slot em edição;
- módulos substituíveis, inicialmente incluindo lâmpadas e buzzers;
- configuração de cada módulo para um canal do GPWS;
- thresholds individuais de atuação;
- acesso aos estados via CC:Tweaked desde a primeira versão da mesa;
- eventos e métodos de API para ocupação, seleção, configuração e estado dos
  módulos aplicáveis.

Os módulos finais, acknowledge/mute, sons e integração com goggles/displays
serão definidos durante o design detalhado. A lâmpada 1×1 da Fase 1 é um
atuador de teste por canal simulado; não representa ainda o módulo final de
alerta GPWS.

### 4.8 Flight Management System (FMS)

O FMS é um módulo virtual de 2 células de largura × 3 de altura (2×3) da mesa modular que armazena waypoints dimensionais.
Sua interface permite criar, listar, editar, excluir e selecionar um destino.
Waypoints podem ser definidos na posição atual ou por coordenadas manuais.
As coordenadas são persistidas em blocos inteiros; valores fracionários são
convertidos para o bloco que contém a posição, inclusive em coordenadas negativas.
UUID, waypoints e seleção acompanham o item do módulo ao removê-lo ou quebrar a
mesa. Não há limite de contagem de waypoints definido pelo mod; nomes têm de 1 a
32 caracteres e nomes repetidos são permitidos.

O item `FMS Navigator` vincula-se ao UUID exato de um módulo FMS sem ser
consumido. Um vínculo anterior a outro módulo exige confirmação antes de ser
substituído. Na Navigation Table do Aeronautics, o alvo ativo é usado somente
quando Navigator, mesa e módulo estão no mesmo mundo e sublevel. Waypoints de
outra dimensão, módulo não carregado, vínculo inexistente ou destino não
selecionado não produzem alvo. Consultas não carregam chunks. O compasso usa
distância horizontal; Y permanece armazenado mas não direciona o compasso.

O FMS publica `waypoint_selected` (booleano válido, `true` somente quando há
alvo escolhido) e `distance_to_waypoint` (distância horizontal em blocos;
`UNAVAILABLE` sem alvo ou fora da dimensão). Os canais são expostos pelo
peripheral `modular_table`, consultáveis na rede Avionics Cable e notificados
por eventos. Não são alertas GPWS e não estão conectados à lógica GPWS nesta
fase. Placeholder: bloco de ferro para o módulo, ícone vanilla de bússola para o
Navigator; modelos/texturas próprios ficam fora do escopo inicial.

## 5. API CC:Tweaked e integração vertical

Todos os sensores, computadores, cabos relevantes e módulos deverão nascer com
API completa para **CC:Tweaked 1.120.0+**, junto com a própria funcionalidade.
Não haverá uma implementação provisória sem CC seguida de uma fase posterior de
integração.

A API deverá permitir, no mínimo:

- enumerar sensores conectados;
- consultar leituras atuais;
- consultar a saúde/conectividade dos sensores;
- consultar modos e fase de voo do GPWS;
- consultar estado, severidade e origem dos alertas;
- configurar thresholds permitidos;
- consultar e configurar módulos do Annunciator Panel;
- observar eventos de mudança de alerta;
- distinguir dados indisponíveis de leituras válidas.

A integração CC:Tweaked será requisito de primeira versão, não um recurso posterior.

Cada fase deverá entregar, quando aplicável:

- o peripheral correspondente;
- métodos de leitura e configuração;
- eventos de mudança de estado;
- distinção entre estado válido, indisponível e falho;
- documentação Lua e testes automatizados do contrato público.

Exemplos de escopo por funcionalidade:

- a mesa modular expõe slots, módulos e o item de teste;
- a rede de cabos expõe conectividade, componentes descobertos e falhas;
- cada sensor expõe sua leitura, validade e estado de conexão;
- o GPWS expõe modos, alertas, severidades, thresholds e eventos;
- os módulos finais do painel expõem seu canal, threshold e estado de atuação.

## 6. Requisitos não funcionais

### Performance

- scans executados a cada 5 ticks por padrão;
- intervalo configurável;
- nenhum carregamento síncrono de chunks provocado por um scan;
- alcance máximo configurável;
- resolução do scanner avançado configurável;
- custo proporcional e previsível ao número de scanners ativos;
- resultados reutilizáveis entre o scanner, GPWS, painel e CC:Tweaked quando possível.

### Multiplayer e autoridade

- leituras relevantes para alertas devem ser calculadas de forma server-authoritative;
- estados de alerta devem ser sincronizados para clientes e painéis;
- componentes desconectados devem produzir estado explícito de falha;
- sensores dentro de sublevels devem usar posição e orientação corretas da contraption.

### Manutenibilidade

- separar aquisição de dados, rede de cabos, motor de regras e apresentação de alertas;
- manter a API CC:Tweaked estável e documentada;
- evitar acoplamento direto entre o GPWS e um único tipo de painel;
- preservar possibilidade de adicionar instrumentos futuros sem criar uma segunda rede de comunicação.

## 7. Baseline técnico

- Minecraft `1.21.1`;
- NeoForge `21.1.236`;
- Java 21;
- Sable `2.0.5`;
- Create Aeronautics `1.3+`;
- CC:Tweaked `1.120.0+`;
- Create: baseline de desenvolvimento atualmente configurada como `6.0.10+`, ainda sujeita à confirmação da versão mínima oficial.

Identidade atual do projeto:

- mod id: `aeronautics_fly_by_instruments`;
- pacote base: `com.radaeli.aeronauticsflybyinstruments`;
- versão inicial do scaffold: `0.1.0`.

## 8. Requisito de testes automatizados

Testes automatizados são requisito obrigatório do desenvolvimento. Um build bem-sucedido sozinho não será considerado validação suficiente.

### Testes unitários

Devem cobrir, no mínimo:

- cálculo de clearance do Radar Altimeter;
- interpretação dos resultados do Terrain Scanner;
- seleção automática de fase de voo;
- thresholds de cada alerta;
- histerese e tempos de confirmação;
- prioridade e transição de estados de alerta;
- comportamento de `SENSOR FAIL`;
- perfis básico e avançado;
- serialização de configurações e thresholds.

Também devem cobrir a fundação da mesa modular:

- ocupação e liberação de slots;
- seleção e highlight do slot;
- colocação, remoção e serialização do item/módulo de teste;
- operação da mesa com canal simulado, sem GPWS real.

### Testes de rede de cabos

Devem cobrir:

- descoberta de sensores conectados;
- ramificações;
- desconexão e reconexão;
- múltiplos sensores do mesmo tipo;
- sensores ausentes ou indisponíveis;
- rede conectada a mais de um consumidor;
- persistência após salvar e recarregar o mundo.

### Testes de integração Minecraft/NeoForge

Devem validar:

- registro de blocos, itens, block entities e menus;
- carregamento do `neoforge.mods.toml`;
- montagem e leitura de contraptions/sublevels;
- transformação de posição e orientação dos sensores;
- exclusão da própria contraption dos scans;
- sincronização dos estados de alerta;
- compatibilidade com os JARs baseline.

### Testes de API CC:Tweaked

Devem validar automaticamente:

- presença dos tipos de periférico;
- nomes e assinaturas dos métodos;
- unidades e convenções dos valores retornados;
- leitura de sensores e alertas;
- configuração permitida por Lua;
- eventos de mudança de alerta;
- comportamento quando a rede está incompleta.

Esses testes devem acompanhar cada fase de funcionalidade. Não é aceitável
adiar a criação dos periféricos e eventos para uma fase de integração posterior.
O primeiro conjunto deve validar a mesa modular, seus slots e o item de teste.

### Testes de performance/regressão

Deve existir um cenário automatizado ou reproduzível que avalie:

- múltiplas contraptions com scanners ativos;
- frequência padrão de 5 ticks;
- scanner simples versus avançado;
- variação de alcance e resolução;
- ausência de carregamento inesperado de chunks;
- tempo de atualização do motor de alertas.

Os limites numéricos de performance serão definidos após existir um protótipo funcional e uma baseline medida.

Testes automatizados não substituem validação manual em mundo real. O comportamento de voo, a leitura visual do painel e a experiência de montagem ainda precisarão de testes in-game.

## 9. Roadmap macro

### Fase 0 — Fundação do projeto

Objetivo: deixar o projeto compilável e preparado para desenvolvimento.

Entregas:

- scaffold NeoForge funcional;
- metadados e dependências baseline;
- configuração de Java 21;
- estrutura de registries;
- harness inicial de testes;
- documentação de build e execução.

Saída da fase: projeto estruturalmente validado, com testes iniciais executando.

### Fase 1 — Annunciator Panel: fundação funcional (prioridade 1)

Objetivo: entregar primeiro uma mesa modular detalhada e funcional, mesmo sem
as funcionalidades finais de voo.

Primeira iteração, com escopo reduzido por decisão do jogador: apenas o bloco
`modular_table_base`, fino como um Copycat Panel (3/16 de bloco), com textura
placeholder de ferro, item no inventário criativo e colocação sobre superfícies.
Esta iteração permite testar a base no mundo.

Segunda iteração: grid virtual de 3×3 células por mesa, cada célula com
16/3 pixels de largura. A base pode ser montada em qualquer face sólida,
inclusive no teto. Os módulos de lâmpada 1×1 e 2×1 têm aparência placeholder e
não possuem comportamento de iluminação/alerta. O módulo 2×1 alterna entre
orientação horizontal e vertical pelo scroll enquanto o jogador mira na mesa.
O placement é escolhido pelo ponto de clique na face exposta, com highlight do
footprint e rejeição de sobreposição e de footprints fora dos limites. Uma
Wrench do Create retira o módulo. A ocupação persiste no mundo e é sincronizada
com os clientes. Cada mesa mantém seu próprio grid.

O peripheral `modular_table` acompanha essa entrega: consulta do grid, dos
módulos e da ocupação, preview de footprint, configuração de canal/threshold,
injeção de sinal simulado, consulta de saída e eventos de colocação/remoção,
configuração e mudança de sinal. Contratos e exemplos estão em
`docs/modular-table.md`.

Entregas:

- bases da mesa modular;
- slots predefinidos e regras de ocupação;
- colocação, remoção, seleção e highlight de slots;
- persistência da configuração;
- item/módulo de teste obtível pelo jogador;
- canal e saída simulados para validar a mesa isoladamente;
- peripheral, métodos e eventos CC:Tweaked da mesa e do item de teste;
- testes automatizados de slots, serialização, interação e API;
- consulta ao `reference/aeroworks-1.5.0.jar` durante o design da experiência
  modular.

Saída da fase: o jogador consegue criar uma mesa, inserir o item de teste,
editar slots e observar uma saída simulada, antes de existirem GPWS e sensores
finais.

### Fase 2 — Modelo comum e Avionics Cable Network

**Implementação entregue:** cabo físico, contratos comuns e descoberta de topologia estão implementados. O acesso agregado CC:Tweaked é responsabilidade da Network Bridge; a mesa expõe apenas seus próprios módulos e eventos.

Objetivo: estabelecer os contratos de dados e a conexão física que sustentarão
os instrumentos, o GPWS e o painel.

Entregas:

- tipos de leituras de sensores;
- estados de conectividade e falha;
- modelo de alerta e severidade;
- modos do GPWS;
- serialização de configurações;
- Avionics Cable;
- conexão automática e ramificações;
- descoberta de sensores e consumidores;
- persistência e sincronização da rede;
- peripheral, métodos, eventos e testes CC:Tweaked da rede.

Saída da fase: uma rede física funcional sem depender de wireless, com contrato
de dados comum e API pública de conectividade.

### Fase 3 — Instrumentos de aquisição

Objetivo: produzir leituras confiáveis para o GPWS.

#### 3.0 — Network Bridge

- adicionar bloco/peripheral `avionics_network_bridge` antes dos sensores;
- mover status, descoberta, enumeração e evento de topologia para a bridge;
- remover a API agregada da peripheral `modular_table`.

#### 3.1 — Radar Altimeter

- bloco fixo com eixo emissor na face inferior local, transformado com o sublevel;
- nove casts com cone de 20° de meia-abertura, mediana e quorum mínimo de três retornos;
- alcance máximo inicial 128 blocos e intervalo de amostragem inicial 5 ticks, ambos configuráveis;
- hits de colisão em terreno, estruturas e outras contraptions; ignorar host e entidades;
- `UNAVAILABLE` para ausência de superfície, chunk descarregado ou menos de três hits; `FAILED` para erro de execução;
- peripheral, snapshot, evento, documentação Lua e testes automatizados CC:Tweaked.

#### 3.2 — Fontes derivadas de voo

- estimativa da velocidade vertical a partir do histórico do Radar Altimeter;
- derivação do ângulo de bank pela pose do sublevel Sable e orientação do GPWS;
- testes automatizados de sinais derivados em mundo estático e em sublevels.

#### 3.3 — Terrain Scanner

**Estado atual: implementado, incluindo consumo dos snapshots pelo motor GPWS na Fase 4.**

- scanner simples fixo com nove raycasts em cone de 90°;
- scanner avançado com abertura total ajustável de 30° a 180° (padrão 90°) e contagem automática de 9 a 81 raycasts;
- interface de configuração no jogo e API CC:Tweaked autoritativas no servidor, com persistência por bloco;
- alcance máximo padrão 128 blocos, intervalo global de 5 ticks, ambos configuráveis; sem carregamento forçado de chunks;
- contatos de colisão genéricos para terreno, estruturas e outras contraptions, ignorando o sublevel hospedeiro e entidades;
- snapshot com status, contatos, cobertura, raios concluídos/total e game time; peripheral/eventos CC:Tweaked e inclusão na rede física;
- testes automatizados de geometria, estados/cobertura, configuração, persistência e API.

Saída da fase: sensores testáveis individualmente por jogo e por CC:Tweaked.

### Fase 4 — Motor GPWS

Objetivo: transformar leituras em alertas.

O plano técnico detalhado está em
[Fase 4 — GPWS Computer e motor de alertas](docs/gpws-phase-4-plan.md). Em
resumo, o GPWS consome snapshots existentes, calcula os sete canais aprovados,
detecta fases automaticamente e entrega perfis básico e avançado com
peripheral, eventos e testes CC:Tweaked. O básico deve produzir alertas de
clearance/descida usando apenas GPWS + Radar Altimeter; o scanner é opcional
para esse benefício. O plano também define fusão de sensores, expiração de
leituras, defaults de thresholds, histerese e critérios de falha.

**Estado atual: motor GPWS implementado, inclusive integração lógica com os
Terrain Scanners.** A verificação de comportamento em cenários reais de voo e
o acionamento dos módulos físicos do painel pertencem às fases de estabilização
e integração do Annunciator Panel, respectivamente.

Entregas:

- estados `STANDBY`, `ARMED` e `TEST`;
- seleção automática de fase de voo;
- alertas iniciais aprovados;
- thresholds padrão;
- histerese e tempos de confirmação;
- falhas e dados ausentes;
- perfil básico e perfil avançado;
- peripheral do GPWS;
- eventos de mudança de alerta e fase;
- métodos CC:Tweaked para leitura e configuração permitida;
- testes automatizados do contrato CC junto dos testes do motor.

Saída da fase: GPWS funcional sem depender ainda do painel final.

### Fase 5 — Annunciator Panel: integração com alertas reais

**Estado:** integração, configuração R-UI, peripheral/eventos CC:Tweaked, setup
básico, teste/mute e volume/alcance espacial dos buzzers implementados. Testes
automatizados focados passaram; a suíte completa ainda apresenta sete falhas
conhecidas nos testes de telas/assets dos sensores e contratos periféricos de
Altimeter/Terrain. Validação do áudio em avião/sublevel e testes funcionais em
voo/multiplayer continuam pendentes.

Objetivo: conectar a fundação da mesa aos alertas reais e completar os módulos
de apresentação.

Entregas:

- módulos de lâmpada;
- módulos de buzzer;
- ligação de módulos a canais;
- severidade mínima por módulo; thresholds de voo permanecem configurados no GPWS;
- buzzer espacial no referencial global da aeronave/sublevel e dos ouvintes;
  volume individual de 0–100% controla intensidade e alcance, até 16 blocos;
- cadência por severidade (Advisory 2 s, Caution 1 s, Warning 0,5 s a 20 TPS);
- teste geral de 3 s; mute encerra sem alertas ou reativa diante de alerta novo
  ou escalado;
- preset ou configuração rápida para o caminho básico (por exemplo, associar
  automaticamente lâmpada/buzzer aos alertas mais úteis sem configurar cada
  slot ou threshold manualmente);
- teste do painel e mute temporário dos buzzers; acknowledge permanece fora do escopo;
- testes de painel e estados de falha;
- eventos, métodos e testes CC:Tweaked dos módulos e canais reais.

Saída da fase: cabine funcional para jogadores básicos e avançados.

### Fase 6 — Flight Management System

**Estado:** implementação e testes direcionados concluídos; validação in-game
do CRUD e do compass da Navigation Table ainda pendente.

Objetivo: adicionar navegação por waypoints reutilizando a mesa modular, a rede
física de dados e o target nativo da Navigation Table.

Entregas:

- módulo FMS virtual 2×3 (2 de largura × 3 de altura) com CRUD de waypoints na UI R-UI;
- criação por posição atual e por coordenadas, com persistência por identidade;
- seleção e limpeza de target;
- item FMS Navigator vinculado ao módulo exato, com confirmação de rebind;
- integração com o target nativo da Navigation Table no mesmo mundo/sublevel;
- canais `waypoint_selected` e `distance_to_waypoint` tipados e eventos CC;
- consultas locais e de canais da rede física sem carregamento de chunks;
- testes automatizados dos dados, estados, canais, APIs e persistência.

Saída da fase: waypoints gerenciáveis, target utilizável pelo compass da
Navigation Table e sinais FMS disponíveis sem acoplamento aos alertas GPWS.

### Fase 7 — Estabilização e release inicial

Objetivo: preparar uma versão jogável e sustentável.

Entregas:

- testes automatizados completos;
- auditoria de cobertura dos contratos CC:Tweaked por funcionalidade;
- testes multiplayer;
- testes de performance;
- validação in-game de aeronaves pequenas e grandes;
- revisão de mensagens, Ponder e documentação;
- revisão de compatibilidade com as versões baseline;
- changelog e release notes.

Saída da fase: release inicial com escopo controlado e limitações conhecidas documentadas.

## 10. Critérios de aceite macro

O produto poderá ser considerado pronto para a primeira release quando:

1. o jogador conseguir conectar sensores por Avionics Cable sem wireless;
2. o GPWS detectar e publicar todos os alertas iniciais aprovados;
3. o modo básico funcionar sem configuração detalhada;
4. o modo avançado permitir thresholds e regras configuráveis;
5. o jogador conseguir criar a mesa modular, testar os slots e usar o item de
   teste antes da integração com o GPWS;
6. o Annunciator Panel exibir alertas através de módulos configuráveis;
7. o FMS preservar waypoints e direcionar a Navigation Table associada, expondo seus dois canais;
8. cada funcionalidade for entregue com seus periféricos, eventos, APIs e
   testes CC:Tweaked, sem uma etapa posterior de integração pendente;
9. os testes automatizados cobrirem regras, rede, painel, integração e API;
10. scanners e consultas de navegação não provocarem carregamento inesperado de chunks;
11. o comportamento funcionar em contraptions dentro de sublevels;
12. a validação manual confirmar leitura compreensível e operação útil em voo;
13. o jogador conseguir receber alerta útil de clearance/descida com GPWS,
    Radar Altimeter e o setup básico do painel, sem Terrain Scanner, CC:Tweaked
    ou ajuste individual de thresholds.

## 11. Riscos e decisões abertas

- definir thresholds iniciais sem tornar os alertas irritantes;
- definir a detecção automática das fases de voo;
- estabelecer o significado exato de estruturas e outras contraptions como obstáculos;
- escolher alcance e resolução padrão do scanner avançado;
- tratar chunks não carregados sem gerar falsos negativos perigosos;
- definir os nomes, receitas e comportamento final dos blocos/itens de teste da
  mesa modular;
- definir os módulos finais do Annunciator Panel;
- confirmar a forma de setup rápido/preset do painel básico;
- confirmar a versão mínima exata de Create;
- validar os contratos atuais de Sable e Aeronautics no runtime;
- estabelecer metas mensuráveis de TPS após o primeiro protótipo.

## 12. Princípio de produto

O mod deve fazer uma aeronave parecer mais segura e mais instrumentada sem exigir que todos os jogadores construam um simulador de aviação completo. A profundidade deve existir como camada opcional; o caminho básico deve continuar sendo: posicionar, conectar, configurar minimamente e voar.

### Arcade primeiro, simulação opcional

Realismo nos componentes não deve tornar realista/complexa a operação mínima.
O jogador deve conseguir montar uma proteção útil com poucos blocos, cabos e
defaults automáticos; os scanners e ajustes avançados aumentam a consciência e
o controle para quem quiser aprofundar. Quando um sensor opcional não estiver
instalado, o GPWS básico continua avaliando os canais suportados pelos dados
disponíveis e não declara falha apenas pela ausência desse opcional. A Fase 5
deve validar uma apresentação acessível — por exemplo, um setup padrão ou
assistente de configuração para lâmpada/buzzer — sem remover a montagem
modular para entusiastas.
