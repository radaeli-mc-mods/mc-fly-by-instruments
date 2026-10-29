package com.radaeli.aeronauticsflybyinstruments.block;

import com.radaeli.aeronauticsflybyinstruments.AeronauticsFlyByInstruments;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import com.radaeli.aeronauticsflybyinstruments.item.LampModuleItem;
import com.radaeli.aeronauticsflybyinstruments.item.LampModule2x1Item;
import com.radaeli.aeronauticsflybyinstruments.item.BuzzerModuleItem;
import com.radaeli.aeronauticsflybyinstruments.item.FmsModuleItem;
import com.radaeli.aeronauticsflybyinstruments.item.FmsNavigatorItem;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsRegistries;
import dev.simulated_team.simulated.index.SimDataComponents;
import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.NetworkBridgeBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.sensor.RadarAltimeterBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.sensor.RadarAltimeterBlock;
import com.radaeli.aeronauticsflybyinstruments.sensor.TerrainScannerBlock;
import com.radaeli.aeronauticsflybyinstruments.sensor.AdvancedTerrainScannerBlock;
import com.radaeli.aeronauticsflybyinstruments.sensor.TerrainScannerBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsComputerBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.block.AvionicsComputerBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import com.radaeli.aeronauticsflybyinstruments.wire.AvionicsWireItem;
import com.radaeli.aeronauticsflybyinstruments.wire.WireConnectorBlock;
import com.radaeli.aeronauticsflybyinstruments.wire.WireNodeBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.wire.WireRouteInProgress;
import com.radaeli.aeronauticsflybyinstruments.wire.WireSupportBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(AeronauticsFlyByInstruments.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(AeronauticsFlyByInstruments.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeronauticsFlyByInstruments.MOD_ID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AeronauticsFlyByInstruments.MOD_ID);

    public static final DeferredBlock<ModularTableBaseBlock> MODULAR_TABLE_BASE =
            BLOCKS.register("modular_table_base", () -> new ModularTableBaseBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().dynamicShape()
                            .lightLevel(state -> state.getValue(ModularTableBaseBlock.LIGHT))));
    public static final DeferredItem<BlockItem> MODULAR_TABLE_BASE_ITEM =
            ITEMS.registerSimpleBlockItem(MODULAR_TABLE_BASE);
    public static final DeferredBlock<NetworkBridgeBlock> NETWORK_BRIDGE =
            BLOCKS.register("network_bridge", () -> new NetworkBridgeBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.0f).noOcclusion()));
    public static final DeferredItem<BlockItem> NETWORK_BRIDGE_ITEM =
            ITEMS.registerSimpleBlockItem(NETWORK_BRIDGE);
    public static final DeferredBlock<AvionicsComputerBlock> AVIONICS_COMPUTER =
            BLOCKS.register("avionics_computer", () -> new AvionicsComputerBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.0f)));
    public static final DeferredItem<BlockItem> AVIONICS_COMPUTER_ITEM = ITEMS.registerSimpleBlockItem(AVIONICS_COMPUTER);
    public static final DeferredBlock<RadarAltimeterBlock> RADAR_ALTIMETER =
            BLOCKS.register("radar_altimeter", () -> new RadarAltimeterBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.0f).noOcclusion()));
    public static final DeferredItem<BlockItem> RADAR_ALTIMETER_ITEM =
            ITEMS.registerSimpleBlockItem(RADAR_ALTIMETER);
    public static final DeferredBlock<TerrainScannerBlock> TERRAIN_SCANNER =
            BLOCKS.register("terrain_scanner", () -> new TerrainScannerBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OBSERVER).strength(2.0f).noOcclusion()));
    public static final DeferredItem<BlockItem> TERRAIN_SCANNER_ITEM = ITEMS.registerSimpleBlockItem(TERRAIN_SCANNER);
    public static final DeferredBlock<AdvancedTerrainScannerBlock> TERRAIN_SCANNER_ADVANCED =
            BLOCKS.register("terrain_scanner_advanced", () -> new AdvancedTerrainScannerBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OBSERVER).strength(2.0f).noOcclusion()));
    public static final DeferredItem<BlockItem> TERRAIN_SCANNER_ADVANCED_ITEM =
            ITEMS.registerSimpleBlockItem(TERRAIN_SCANNER_ADVANCED);
    public static final DeferredItem<LampModuleItem> LAMP_MODULE =
            ITEMS.register("lamp_module", () -> new LampModuleItem(new Item.Properties()));
    public static final DeferredItem<LampModule2x1Item> LAMP_MODULE_2X1 =
            ITEMS.register("lamp_module_2x1", () -> new LampModule2x1Item(new Item.Properties()));
    public static final DeferredItem<BuzzerModuleItem> BUZZER_MODULE =
            ITEMS.register("buzzer_module", () -> new BuzzerModuleItem(new Item.Properties()));
    public static final DeferredItem<FmsModuleItem> FMS_MODULE =
            ITEMS.register("fms_module", () -> new FmsModuleItem(new Item.Properties()));
    public static final DeferredItem<FmsNavigatorItem> FMS_NAVIGATOR = ITEMS.register("fms_navigator",
            () -> new FmsNavigatorItem(new Item.Properties()
                    .component(SimDataComponents.TARGET, FmsRegistries.NAVIGATOR_TARGET)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ModularTableBlockEntity>> MODULAR_TABLE_ENTITY =
            BLOCK_ENTITIES.register("modular_table", () -> BlockEntityType.Builder.of(
                    ModularTableBlockEntity::new, MODULAR_TABLE_BASE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkBridgeBlockEntity>> NETWORK_BRIDGE_ENTITY =
            BLOCK_ENTITIES.register("network_bridge", () -> BlockEntityType.Builder.of(
                    NetworkBridgeBlockEntity::new, NETWORK_BRIDGE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AvionicsComputerBlockEntity>> AVIONICS_COMPUTER_ENTITY =
            BLOCK_ENTITIES.register("avionics_computer", () -> BlockEntityType.Builder.of(
                    AvionicsComputerBlockEntity::new, AVIONICS_COMPUTER.get()).build(null));
    // Avionics Wire: physical wires between brass connectors, resting on industrial iron supports.
    public static final DeferredBlock<WireConnectorBlock> WIRE_CONNECTOR =
            BLOCKS.register("wire_connector", () -> new WireConnectorBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CHAIN).sound(SoundType.COPPER).strength(0.8f)
                            .noOcclusion()));
    public static final DeferredItem<BlockItem> WIRE_CONNECTOR_ITEM = ITEMS.registerSimpleBlockItem(WIRE_CONNECTOR);
    public static final DeferredBlock<WireSupportBlock> WIRE_SUPPORT =
            BLOCKS.register("wire_support", () -> new WireSupportBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CHAIN).sound(SoundType.NETHERITE_BLOCK).strength(0.8f)
                            .noOcclusion()));
    public static final DeferredItem<BlockItem> WIRE_SUPPORT_ITEM = ITEMS.registerSimpleBlockItem(WIRE_SUPPORT);
    public static final DeferredItem<AvionicsWireItem> AVIONICS_WIRE =
            ITEMS.register("avionics_wire", () -> new AvionicsWireItem(new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WireNodeBlockEntity>> WIRE_NODE_ENTITY =
            BLOCK_ENTITIES.register("wire_node", () -> BlockEntityType.Builder.of(
                    WireNodeBlockEntity::new, WIRE_CONNECTOR.get(), WIRE_SUPPORT.get()).build(null));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WireRouteInProgress>> WIRE_ROUTE =
            DATA_COMPONENTS.registerComponentType("wire_route", builder -> builder
                    .persistent(WireRouteInProgress.CODEC).networkSynchronized(WireRouteInProgress.STREAM_CODEC));
    /** Worlds saved with the old GPWS Computer (renamed 2026-10-28) load it as the Avionics Computer. */
    public static void registerAliases() {
        var old = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AeronauticsFlyByInstruments.MOD_ID,
                "gpws_computer");
        var current = AVIONICS_COMPUTER.getId();
        BLOCKS.addAlias(old, current);
        ITEMS.addAlias(old, current);
        BLOCK_ENTITIES.addAlias(old, current);
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarAltimeterBlockEntity>> RADAR_ALTIMETER_ENTITY =
            BLOCK_ENTITIES.register("radar_altimeter", () -> BlockEntityType.Builder.of(
                    RadarAltimeterBlockEntity::new, RADAR_ALTIMETER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerrainScannerBlockEntity>> TERRAIN_SCANNER_ENTITY =
            BLOCK_ENTITIES.register("terrain_scanner", () -> BlockEntityType.Builder.of(
                    TerrainScannerBlockEntity::new, TERRAIN_SCANNER.get(), TERRAIN_SCANNER_ADVANCED.get()).build(null));

    private ModBlocks() {}
}
