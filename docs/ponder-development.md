# Ponder development

The optional integration targets the supplied PonderJS 1.21.1-2.4.0 and KubeJS 2101.7.2-build.377 on NeoForge 1.21.1 / Java 21. Place the JARs in libs, plus Rhino 2101.2.7-build.81 for the client, or override ponderjs_jar, kubejs_jar and rhino_jar with Gradle properties. None is bundled. Create supplies the Ponder API on the compile classpath.

KubeJS discovers the client-only plugin from kubejs.plugins.txt only with ponderjs present. The beforeScriptsLoaded hook adds fbi_client_scripts directly from the mod resource path with directory creation disabled. tutorials.js registers the 11 typed storyboards and three categories through PonderJS. It reloads with client scripts; it never installs files in a user script directory.

Normal build: gradlew.bat build. For the ordinary gradlew.bat runClient, copy PonderJS, KubeJS and Rhino into run/mods. Keep the libs copies for compilation and the isolated validation client. Do not add -PponderTutorials to the normal client when these JARs are already in run/mods.

The opt-in src/ponderValidation harness is excluded from normal builds. To use it, copy a disposable development world to run-ponder/saves/Ponder Validation, then run gradlew.bat runPonderValidationClient -PponderTutorials -PponderValidation. It executes every registered scene twice, checks completion, captures previews in run-ponder/screenshots, and exits. Inspect the FBI_PONDER_ALL_PASSED or FBI_PONDER_FAILED marker in the log. It never accepts a dedicated server EULA.

The reusable tutorial_base.nbt is a nine-by-nine andesite base. Storyboards create demonstration blocks through scheduled world instructions. All scene state is in the Ponder level; the real ship simulation and server networks are not involved. Captions use ponder.aeronautics_fly_by_instruments.* keys in en_us and pt_br. Test replay, language/resource reload, and absent/partial optional dependency combinations before release.

Dedicated isolation gate: gradlew.bat runPonderValidationServer -PponderServerValidation runs a disposable GameTest without KubeJS/PonderJS. Client absence gates use runPonderValidationClient -PponderValidation, with -PponderKubeOnly to test KubeJS without PonderJS. The client harness also checks the actual item tooltip for the Ponder prompt.
