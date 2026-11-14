package com.radaeli.aeronauticsflybyinstruments;

import com.mojang.logging.LogUtils;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import com.radaeli.aeronauticsflybyinstruments.config.ModConfig;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.radaeli.aeronauticsflybyinstruments.integration.ComputerCraftIntegration;
import net.neoforged.fml.ModList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import com.radaeli.aeronauticsflybyinstruments.client.FmsClient;
import com.radaeli.aeronauticsflybyinstruments.client.PanelClient;
import com.radaeli.aeronauticsflybyinstruments.client.ModularTableModels;
import com.radaeli.aeronauticsflybyinstruments.client.PanelInputHandler;
import com.radaeli.aeronauticsflybyinstruments.client.TerrainScannerDebug;
import com.radaeli.aeronauticsflybyinstruments.network.PanelNetwork;
import com.radaeli.aeronauticsflybyinstruments.network.TerrainScannerNetwork;
import com.radaeli.aeronauticsflybyinstruments.network.AvionicsComputerNetwork;
import com.radaeli.aeronauticsflybyinstruments.network.SensorOutputNetwork;
import com.radaeli.aeronauticsflybyinstruments.network.FmsNetwork;
import com.radaeli.aeronauticsflybyinstruments.sound.ModSounds;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsRegistries;
import net.neoforged.neoforge.common.NeoForge;

/** Main entry point for Aeronautics: Fly by Instruments. */
@Mod(AeronauticsFlyByInstruments.MOD_ID)
public final class AeronauticsFlyByInstruments {
    public static final String MOD_ID = "aeronautics_fly_by_instruments";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AeronauticsFlyByInstruments(IEventBus modEventBus, ModContainer modContainer) {
        FmsRegistries.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModBlocks.BLOCK_ENTITIES.register(modEventBus);
        ModBlocks.DATA_COMPONENTS.register(modEventBus);
        ModBlocks.registerAliases();
        ModCreativeTabs.TABS.register(modEventBus);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, ModConfig.SPEC);
        modEventBus.addListener(PanelNetwork::registerPayloads);
        modEventBus.addListener(TerrainScannerNetwork::registerPayloads);
        modEventBus.addListener(AvionicsComputerNetwork::registerPayloads);
        modEventBus.addListener(SensorOutputNetwork::registerPayloads);
        modEventBus.addListener(com.radaeli.aeronauticsflybyinstruments.network.AttitudeReferenceNetwork::registerPayloads);
        modEventBus.addListener(FmsNetwork::registerPayloads);
        modEventBus.addListener(com.radaeli.aeronauticsflybyinstruments.network.AttitudeIndicatorNetwork::registerPayloads);
        modEventBus.addListener(com.radaeli.aeronauticsflybyinstruments.network.WireNetwork::registerPayloads);
        if (ModList.get().isLoaded("computercraft")) {
            ComputerCraftIntegration.register(modEventBus);
        }
        NeoForge.EVENT_BUS.addListener(PanelNetwork::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(com.radaeli.aeronauticsflybyinstruments.panel.TableInteraction::onRightClick);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PanelClient.register(modEventBus);
            FmsClient.register(modEventBus);
            com.radaeli.aeronauticsflybyinstruments.client.AttitudeReferenceClient.register(modEventBus);
            com.radaeli.aeronauticsflybyinstruments.client.ItemTooltips.register(modEventBus);
            com.radaeli.aeronauticsflybyinstruments.client.WireClient.register(modEventBus);
            ModularTableModels.register(modEventBus);
            NeoForge.EVENT_BUS.addListener(com.radaeli.aeronauticsflybyinstruments.client.BuzzerAudio::onPlaySound);
            NeoForge.EVENT_BUS.addListener(PanelInputHandler::onMouseScroll);
            NeoForge.EVENT_BUS.addListener(com.radaeli.aeronauticsflybyinstruments.client.AttitudeDisplayClient::onLoggingOut);
            if (!FMLEnvironment.production) {
                modEventBus.addListener(TerrainScannerDebug::registerKeyMapping);
                NeoForge.EVENT_BUS.addListener(TerrainScannerDebug::onClientTick);
                NeoForge.EVENT_BUS.addListener(TerrainScannerDebug::onRenderLevel);
            }
        }
        LOGGER.info("Aeronautics: Fly by Instruments loaded");
    }
}
