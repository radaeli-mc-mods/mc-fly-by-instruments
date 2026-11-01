// Loaded directly from the mod's resources by FbiPonderPlugin, including on reload.
const FbiTutorials = Java.loadClass('com.radaeli.aeronauticsflybyinstruments.client.ponder.FbiPonderScenes');
Ponder.registry(event => FbiTutorials.register(event));
Ponder.tags(event => FbiTutorials.tags(event));
