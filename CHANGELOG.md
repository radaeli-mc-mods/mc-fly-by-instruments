# Changelog

All notable changes to Aeronautics: Fly by Instruments are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2026-11-08

### Added

- Added the Attitude Reference Unit, which measures pitch, roll, heading, turn rate, vertical speed and slip, with level calibration and optional damping.
- Added the Attitude Indicator for Modular Tables, showing readings from a connected Attitude Reference Unit with optional altitude and speed tapes.
- Added access to Attitude Reference Unit settings and readings through CC:Tweaked and the avionics network.
- Added Velocity Sensor readings to the avionics network and the Attitude Indicator's optional speed tape.
- Added crafting recipes for the Attitude Reference Unit and Attitude Indicator.
- Added three Ponder tutorials in English and Brazilian Portuguese for the new instruments.

### Changed

- Updated the Avionics Computer's bank warning to use a connected Attitude Reference Unit when available.
- Updated the Attitude Indicator display with day and night colors, simpler pitch and bank markings, a larger altitude readout, and a painted iron housing with polished brass trim.
- Updated the Attitude Reference Unit recipe to use iron bars, an electron tube, precision mechanisms, a gyroscopic mechanism and glass.
- Removed the Experimental label from the Attitude Reference Unit and Attitude Indicator.

### Fixed

- Fixed the Attitude Indicator's selection box extending too far from wall and ceiling tables.

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
