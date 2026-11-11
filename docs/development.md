# Development

## Baseline

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.236
- Create 6.0.10+
- Create Aeronautics bundled 1.3+
- Sable 2.0.5
- CC:Tweaked 1.120.0+ (compile-time API; optional at runtime)
- R-UI Lib 0.10.0+

The Gradle properties default to the Create, Create Aeronautics, and Sable JARs
used by the sibling `sable-horses` workspace. The CC:Tweaked 1.120.0 JAR is
staged at `libs/cc-tweaked-1.21.1-neoforge-1.120.0.jar` and can be overridden
with `-Pcomputercraft_jar=<path>` if needed.

Aeronautics and Sable are supplied to Gradle as local development dependencies
and are not bundled into the addon JAR. Their nested JARs are extracted to
`build/local-mod-dependencies/` for compile-time symbols only.

## Commands

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

VS Code tasks for both commands are in `.vscode/tasks.json`, alongside
`Run NeoForge Client (without CC:Tweaked)` for the optional dependency check.

## Optional CC:Tweaked integration

CC:Tweaked is optional for players. Sensors, redstone outputs, the avionics network,
GPWS, and cockpit instruments work without it. When installed, CC:Tweaked adds
peripherals and Lua events. Its supported minimum remains 1.120.0.

The entrypoint registers `ComputerCraftIntegration` only when `computercraft` is
loaded. Block entities use CC-independent event interfaces and create a cached
peripheral only when its capability is queried. Keep CC API types and Lua conversion
code inside the integration and peripheral classes.

Use the development flag below to omit CC:Tweaked from the runtime dependencies
while retaining its API for compilation. Remove any manually installed CC:Tweaked
JAR from the selected development instance's `mods` directory as well.

```powershell
.\gradlew.bat -PwithoutComputerCraft test
.\gradlew.bat -PwithoutComputerCraft -PtableGameTests runGameTestServer
.\gradlew.bat -PwithoutComputerCraft runClient
```

With this flag, `test` runs the dedicated absence regression test. Run the ordinary
`.\gradlew.bat test build` command separately to check the full suite with CC:Tweaked.

## Release scope

The three sensors (`radar_altimeter`, `terrain_scanner` and
`terrain_scanner_advanced`) are the delivered content. The avionics suite
(Avionics Computer, Modular Table Base, Lamp 1x1/2x1, Buzzer, FMS, FMS Compass,
Avionics Wire, Wire Connector, Wire Support, Attitude Reference Unit and
Attitude Indicator) has recipes and no Experimental
mark in its names. The Network Bridge remains **Experimental**, has no recipe
and is only available from the creative tab or `/give`.

## Documentation

- Technical index: [docs/README.md](README.md)
- Product requirements: [PRD.md](../PRD.md)
- Visual design guide (read before any modeling): [visual-design-guide.md](visual-design-guide.md)
