package com.hotdeposit;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(HotDeposit.MOD_ID)
public class HotDeposit {
	public static final String MOD_ID = "hotdeposit";

	private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
	private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

	// Same hardness/resistance/sound as a vanilla chest so it breaks just as fast by hand or with any tool.
	// Light level 10 (like a soul lantern) while the eyes are open, none while they're closed.
	public static final DeferredBlock<HotDepositBlock> HOT_DEPOSIT = BLOCKS.register("hot_deposit",
			() -> new HotDepositBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.instrument(NoteBlockInstrument.BASS)
					.strength(2.5F)
					.sound(SoundType.WOOD)
					.lightLevel(state -> state.getValue(HotDepositBlock.OPEN) ? 10 : 0)
					.noOcclusion()));

	public static final DeferredItem<BlockItem> HOT_DEPOSIT_ITEM = ITEMS.registerSimpleBlockItem(HOT_DEPOSIT, new Item.Properties());

	@SuppressWarnings("DataFlowIssue") // the data fixer type may be null
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HotDepositBlockEntity>> HOT_DEPOSIT_BLOCK_ENTITY = BLOCK_ENTITY_TYPES
			.register("hot_deposit", () -> BlockEntityType.Builder.of(HotDepositBlockEntity::new, HOT_DEPOSIT.get()).build(null));

	public HotDeposit(IEventBus modBus, ModContainer container) {
		BLOCKS.register(modBus);
		ITEMS.register(modBus);
		BLOCK_ENTITY_TYPES.register(modBus);
		container.registerConfig(ModConfig.Type.COMMON, HotDepositConfig.SPEC);
		modBus.addListener(HotDeposit::addToCreativeTabs);
		modBus.addListener(ModConfigEvent.Loading.class, HotDepositConfig::onConfigChanged);
		modBus.addListener(ModConfigEvent.Reloading.class, HotDepositConfig::onConfigChanged);
	}

	private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
			event.accept(HOT_DEPOSIT_ITEM);
		}
	}
}
