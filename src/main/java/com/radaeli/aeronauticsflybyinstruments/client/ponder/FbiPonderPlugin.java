package com.radaeli.aeronauticsflybyinstruments.client.ponder;

import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.neoforged.fml.ModList;

/** Discovered only by KubeJS, on the physical client and with PonderJS installed. */
public final class FbiPonderPlugin implements KubeJSPlugin {
    @Override
    public void registerClasses(ClassFilter filter) {
        if (filter.scriptType == ScriptType.CLIENT) filter.allow(FbiPonderScenes.class);
    }

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType != ScriptType.CLIENT) return;
        var file = ModList.get().getModFileById("aeronautics_fly_by_instruments").getFile();
        manager.loadPackFromDirectory(file.findResource("fbi_client_scripts"),
                "aeronautics_fly_by_instruments", false);
    }
}
