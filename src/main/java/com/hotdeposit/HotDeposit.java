package com.hotdeposit;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(HotDeposit.MOD_ID)
public class HotDeposit {
	public static final String MOD_ID = "hotdeposit";

	private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
	private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
	private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);

	// Same hardness/resistance/sound as a vanilla chest so it breaks just as fast by hand or with any tool.
	// Light level 10 (like a soul lantern) while the eyes are open, none while they're closed.
	public static final RegistryObject<HotDepositBlock> HOT_DEPOSIT = BLOCKS.register("hot_deposit",
			() -> new HotDepositBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.instrument(NoteBlockInstrument.BASS)
					.strength(2.5F)
					.sound(SoundType.WOOD)
					.lightLevel(state -> state.getValue(HotDepositBlock.EYES_OPEN) ? 10 : 0)
					.noOcclusion()));

	public static final RegistryObject<BlockItem> HOT_DEPOSIT_ITEM = ITEMS.register("hot_deposit", () -> new BlockItem(HOT_DEPOSIT.get(), new Item.Properties()));

	@SuppressWarnings("DataFlowIssue") // the data fixer type may be null
	public static final RegistryObject<BlockEntityType<HotDepositBlockEntity>> HOT_DEPOSIT_BLOCK_ENTITY = BLOCK_ENTITY_TYPES
			.register("hot_deposit", () -> BlockEntityType.Builder.of(HotDepositBlockEntity::new, HOT_DEPOSIT.get()).build(null));

	public HotDeposit() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		BLOCKS.register(modBus);
		ITEMS.register(modBus);
		BLOCK_ENTITY_TYPES.register(modBus);
		// 1.20.1 has no built-in config screen; the settings live in config/hotdeposit-common.toml
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, HotDepositConfig.SPEC);
		modBus.addListener(HotDeposit::addToCreativeTabs);
		modBus.addListener((ModConfigEvent.Loading event) -> HotDepositConfig.onConfigChanged(event));
		modBus.addListener((ModConfigEvent.Reloading event) -> HotDepositConfig.onConfigChanged(event));
	}

	private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
			event.accept(HOT_DEPOSIT_ITEM);
		}
	}
}
