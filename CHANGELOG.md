# Changelog

All notable changes to Aeronautics: Fly by Instruments are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Added the Attitude Reference Unit: a cable endpoint that reads pitch, roll and heading from the aircraft's own pose, with Nose and Up faces set by the wrench or the screen, a level calibration (up to 15° per axis) and an optional damping mode.
- Added the `attitude_reference_unit` CC:Tweaked peripheral (`getAttitude`, `getRates`, `getConfig`, `setOrientation`, `calibrateLevel`, `resetCalibration`, `setDamping`).
- Added the `attitude.*` channels to the avionics network: pitch, roll, heading, turn rate, vertical speed and slip.

- Added the Attitude Indicator module (Experimental, creative only): a 2×2 table module with a slanted housing and upright screen on floor tables and a flat plate on wall and ceiling tables.
- The Attitude Indicator display is live: it draws the pitch and roll of the nearest Attitude Reference Unit on the table's cable network and, on an optional altitude tape, the height of the lowest Radar Altimeter. Both must be on the same aircraft as the table. Without a usable unit the screen goes black with a red ATT flag; near vertical it shows a small amber ATT.
- Added the Attitude Indicator screen (empty-hand click): the live attitude, the state of each source on the network, and the altitude tape switch. The speed tape is listed as coming soon.
- Added three Ponder tutorials in English and Brazilian Portuguese for the Experimental attitude devices: setting the aircraft frame (Attitude Reference Unit), reading the artificial horizon (installing the Attitude Indicator) and sources, tapes and ATT flags. They open from the Attitude Reference Unit, the Attitude Indicator, the Modular Table and the Radar Altimeter.

### Changed

- The Attitude Reference Unit is now a glass cube with slim industrial-iron corner posts and a looping gimbal inside; two reference arms (copper-tipped for Nose, brass-tipped for Up) show the configured frame, replacing the face markings. The unused `powered` blockstate property was removed.
- The GPWS bank angle now prefers the roll of a valid Attitude Reference Unit on the same aircraft, and falls back to the computer's own pose when there is none; `getStatus()` reports the source as `bankSource`.

## [0.3.0] - 2026-11-01

### Added

- Added eleven Ponder tutorials in English and Brazilian Portuguese for sensors, wiring, cockpit panels and FMS navigation.

### Changed

- Removed the Experimental label from the avionics blocks and modules; the Network Bridge remains Experimental and creative-only.

### Fixed

- Fixed wires remaining connected or visible after a connector or support was removed.
- Fixed duplicate wire refunds when removing wire nodes.
- Fixed the Advanced Terrain Scanner sending redstone signals through the wrong faces.
- Fixed buzzers on the same panel channel sounding out of sync.
- Fixed lit panel lamps being covered by sky and clouds with shaders.
- Fixed the buzzer tooltip to explain silencing and sneak-click configuration.

## [0.2.0] - 2026-10-28

An experimental avionics suite: a ground proximity warning computer, a cockpit panel and physical wires.

### Added

- Added the Avionics Computer, an experimental ground proximity warning system that reads your sensors and warns about terrain ahead, pull up, sink rate, altitude loss, flying too low and steep banks.
- Added a status screen to the Avionics Computer with sensor roles, a sensitivity setting, per-function inhibit and a self-test button.
- Added the experimental Avionics Wire, with brass Wire Connectors and iron Wire Supports, that hangs between devices to link them.
- Added the Wiring View, toggled with V, which shows every wire through blocks coloured by network state.
- Added cutting a single wire with a Create Wrench in the Wiring View.
- Added a "Looks at" setting to Terrain Scanners to tell the Avionics Computer which side of the ship they watch.
- Added joined Modular Tables: tables side by side form one panel, and modules can span several tables.
- Added docking: a Modular Table mounted on an Avionics Computer joins its network without any wire.
- Added lamp indications (GPWS warning, caution, INOP or a single function) and amber lamps.
- Added silencing the alarm by clicking a lit lamp or a sounding buzzer; sneak-click on a buzzer opens its settings.
- Added a heading compass to the FMS screen that marks the selected waypoint.
- Added one-click linking of an FMS Compass from the FMS screen.
- Added block light from lit table lamps.
- Added crafting recipes for the experimental avionics suite: Avionics Computer, Modular Table, lamps, buzzer, FMS, FMS Compass, Avionics Wire, Wire Connector and Wire Support. The Network Bridge stays creative-only.
- Added Create-style "Hold [Shift] for Summary" tooltips to every block and module.

### Changed

- Changed the sensor screen to a new layout where changes apply immediately, with a new Redstone Link frequency picker.
- Changed the look of the experimental Modular Table, lamps, buzzer, FMS and avionics blocks to match the Create style.
- Changed the FMS screen into one clear panel: the destination with distance and bearing on top, and waypoints you click to fly direct to (right click to edit).
- Changed new FMS waypoints to start at your position with the name ready to type, and deleting a waypoint now asks for a second click.
- Renamed the Flight Management System Module item to FMS.
- Changed the Modular Table Base to 4 pixels thick, so modules sit 1 pixel higher.
- Changed the required R-UI Lib version to 0.8.0 or newer.

### Removed

- Removed the GPWS Computer, which now loads as the Avionics Computer and keeps only its sensitivity.
- Removed GPWS profiles, modes and custom alert rules.
- Removed the `gpws` CC:Tweaked peripheral; programs must use `avionics_computer`.
- Removed `cableCount` from the Network Bridge and Avionics Computer status in CC:Tweaked.
- Removed the experimental Avionics Cable block; link devices with the Avionics Wire instead. Cables already placed in a world disappear when it loads.

## [0.1.0] - 2026-10-25

First release, focused on flight sensors for Create Aeronautics.

### Added

- Added the Radar Altimeter, which measures how high your ship is above the ground below it.
- Added the Terrain Scanner, which detects obstacles in front of your ship.
- Added the Advanced Terrain Scanner, which watches front, left and right separately with an adjustable field of view.
- Added a redstone signal to each sensor that rises with altitude, or as obstacles get closer.
- Added a sensor screen to choose the signal range and send it wirelessly through Redstone Link.
- Added CC:Tweaked support so computers can read what each sensor sees.
- Added crafting recipes for the three sensors using Create parts.
- Added support for sensors mounted on moving Aeronautics ships.
- Added server settings for how far sensors can see and how often they update.
- Added work-in-progress instruments, marked Experimental, to the creative tab without crafting recipes.
