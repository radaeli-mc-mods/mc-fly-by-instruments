# TODOs de longo prazo

Backlog central de melhorias e integrações ainda pendentes, consolidado em
2026-10-03. Os itens abaixo registram trabalho futuro; os componentes podem
continuar funcionando no estado atual enquanto não forem tratados.

## Renderização e conexão física

- [x] **CABO-02 — Levar o socket do Radar Altimeter para o centro.** Feito no
  redesign v2 (2026-10-21): sockets centrados em todas as faces.
  Reposicionar o socket/ponto físico de conexão na face e manter o modelo, o
  encaixe visual do cabo e sua forma de seleção coerentes com essa posição.
- [ ] **LÂMPADA-01 — Investigar os glitches das lâmpadas com shaders.**
  Há cintilação visual aleatória relatada com Complementary Reimagined Ultra;
  mudar para o perfil Basic não resolveu. Identificar a causa e conferir que
  módulos ligados e desligados continuam distinguíveis individualmente.
  Causa provável do sumiço/nuvem na frente vista de baixo para cima (Complementary,
  Solas): o passe aceso usava `entityTranslucentEmissive`, que não escreve
  profundidade, e o pack desenha céu e nuvens por cima. Corrigido com
  `LampRenderTypes.EMISSIVE` (mesmo shader, escreve profundidade); **falta
  confirmar no jogo** com os dois packs.

## Revisão dos sensores (2026-10-21)

- [ ] **SENSORES-01 — Executar o [plano de ação da revisão](plano-acao-sensores.md).**
  18 achados em 4 fases; críticos: faces do Advanced (SEN-01) e salvamento em
  sublevel (SEN-02).

## Funcionalidades e integração

- **BUZZER-01 — Implementado; validação de voo pendente.** Bipes por severidade,
  volume individual com alcance proporcional, teste, mute e posição sonora
  global para aviões/sublevels. Falta comprovar a espacialização no jogo durante
  movimento.
- **PAINEL-01 — Integração implementada; validação funcional pendente.**
  Módulos ligados aos canais GPWS por severidade, agregação de fontes,
  configuração R-UI e setup básico automático. Falta validar alertas em voo e
  multiplayer antes de encerrar a fase.
- [ ] **GPWS-01 — Validar em voo os alertas GPWS dependentes do Terrain Scanner.**
  A integração lógica já existe: contatos conhecidos podem sustentar `TERRAIN`
  e `PULL_UP`, enquanto cobertura parcial sem contato não significa caminho
  livre. Exercitar em voo cobertura completa/parcial, scanner ausente ou
  indisponível e orientação relativa; ajustar comportamento somente se os
  testes revelarem problemas. A lógica implementada está descrita em
  [Avionics Computer](avionics-computer.md) e [Terrain Scanner](terrain-scanner.md).
- [ ] **LÂMPADA-02 — Avaliar variantes de cor das lâmpadas.**
  Usar as mesmas proporções e materiais da variante vermelha; validar cada
  combinação acesa/apagada no jogo antes de fixar uma paleta adicional.

## Validação antes da release

- [ ] **QA-01 — Registrar validação visual in-game dos modelos e conexões.**
  Conferir colocação, inventário, orientação, sockets, shaders e leitura visual
  em mundo estático e em sublevels. Builds e testes automatizados, sozinhos, não
  substituem essa verificação.
- [ ] **QA-02 — Medir desempenho de scans e rede em condições representativas.**
  Avaliar scanners repetidos, Advanced no limite de raios, redes extensas,
  carregamento de chunks e contraptions ativas; manter scans sem forçar a carga
  de chunks.
- [ ] **QA-03 — Fazer testes de voo e multiplayer de ponta a ponta.**
  Exercitar aeronaves pequenas e grandes, alertas básicos e avançados, cobertura
  parcial, montagem do painel, eventos e peripherals antes da release inicial.

## Referências

- [Identidade visual e validação de shaders](visual-design-guide.md)
- [Mesa modular e módulos](modular-table.md)
- [Terrain Scanner e seus estados](terrain-scanner.md)
- [Critérios macro e roadmap](../PRD.md)
