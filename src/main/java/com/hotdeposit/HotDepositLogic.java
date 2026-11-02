package com.hotdeposit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedcore.inventory.ItemStackKey;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;
import net.p3pp3rf1y.sophisticateditemactions.common.IBlockItemActionHandler;
import net.p3pp3rf1y.sophisticateditemactions.common.IDepositHandler;
import net.p3pp3rf1y.sophisticateditemactions.common.IEntityItemActionHandler;
import net.p3pp3rf1y.sophisticateditemactions.common.ItemActionHandlerRegistry;
import net.p3pp3rf1y.sophisticateditemactions.common.ItemMatchResult;
import net.p3pp3rf1y.sophisticateditemactions.common.ItemTransferData;
import net.p3pp3rf1y.sophisticateditemactions.common.ItemTransferExtensionRegistry;
import net.p3pp3rf1y.sophisticateditemactions.common.StorageItemHandlerTarget;
import net.p3pp3rf1y.sophisticateditemactions.common.SubLevelCompatHelper;
import net.p3pp3rf1y.sophisticateditemactions.network.SyncItemTransfersPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side version of Sophisticated Item Actions' Shift + ' deposit (main inventory, matching items only),
 * centred on the Hot Deposit block with a configurable range and storage blacklist.
 * Storage access, item matching, the flying-item animation and status messages all come from Sophisticated Item Actions.
 * <p>
 * Finding storages is split in two: {@link #scanStorages} finds which storages are near a block (the expensive world scan,
 * the same for every player, so it can be cached), and {@link #resolve} turns that into deposit handlers for one player
 * (claim/permission checks and checking the storage is still there).
 */
public final class HotDepositLogic {
	// Shift + ' covers the main inventory but not the hotbar
	private static final int MIN_SLOT = 9;
	private static final int MAX_SLOT = 36;

	private HotDepositLogic() {
	}

	public static StorageScan scanStorages(ServerLevel level, BlockPos origin, int range) {
		Vec3 center = Vec3.atCenterOf(origin);
		double rangeSqr = (double) range * range;
		List<BlockStorage> blocks = new ArrayList<>();
		List<EntityStorage> entities = new ArrayList<>();

		Set<BlockPos> seen = new HashSet<>();
		for (BlockEntity be : SubLevelCompatHelper.getBlockEntitiesInRange(level, origin, range)) {
			Level storageLevel = be.getLevel() == null ? level : be.getLevel();
			BlockPos bePos = be.getBlockPos();
			if (Vec3.atCenterOf(bePos).distanceToSqr(center) > rangeSqr || HotDepositConfig.isBlacklisted(be.getBlockState())) {
				continue;
			}
			ItemActionHandlerRegistry.getBlockHandlerFor(storageLevel, bePos, be, IBlockItemActionHandler.Action.DEPOSIT).ifPresent(handler -> {
				// e.g. both halves of a double chest map to one position
				BlockPos actOn = handler.getInteractionPosToActOn(storageLevel, bePos, be, IBlockItemActionHandler.Action.DEPOSIT);
				if (seen.add(actOn)) {
					blocks.add(new BlockStorage(bePos.immutable(), actOn.immutable(), handler.id(), Vec3.atCenterOf(actOn).distanceToSqr(center)));
				}
			});
		}

		for (Entity entity : level.getEntities((Entity) null, new AABB(origin).inflate(range),
				e -> !(e instanceof Player) && e.isAlive() && e.position().distanceToSqr(center) <= rangeSqr)) {
			if (!HotDepositConfig.isBlacklisted(entity)) {
				ItemActionHandlerRegistry.getEntityHandlerIdFor(entity)
						.ifPresent(id -> entities.add(new EntityStorage(entity.getId(), id, entity.position().distanceToSqr(center))));
			}
		}

		// nearest first, so deposits fill the closest storage first and the "can deposit" check usually stops early
		blocks.sort(Comparator.comparingDouble(BlockStorage::distanceSqr));
		entities.sort(Comparator.comparingDouble(EntityStorage::distanceSqr));
		return new StorageScan(List.copyOf(blocks), List.copyOf(entities), center, rangeSqr);
	}

	public static Storages resolve(ServerLevel level, ServerPlayer player, StorageScan scan, boolean includeStorageTargets) {
		List<IDepositHandler> handlers = new ArrayList<>();
		List<StorageItemHandlerTarget> targets = new ArrayList<>();

		for (BlockStorage storage : scan.blocks()) {
			Optional<IBlockItemActionHandler> handler = ItemActionHandlerRegistry.getBlockHandler(storage.handlerId());
			if (handler.isEmpty() || !isStillStorage(level, storage, handler.get()) || !SubLevelCompatHelper.mayInteract(player, level, storage.pos())) {
				continue;
			}
			handler.get().getDepositHandler(player, storage.actOnPos()).ifPresent(handlers::add);
			if (includeStorageTargets) {
				targets.addAll(handler.get().getStorageItemHandlerTargets(player, storage.actOnPos()));
			}
		}

		for (EntityStorage storage : scan.entities()) {
			Entity entity = level.getEntity(storage.entityId());
			Optional<IEntityItemActionHandler> handler = ItemActionHandlerRegistry.getEntityHandler(storage.handlerId());
			// the entity may have moved or died since the scan
			if (entity == null || !entity.isAlive() || handler.isEmpty() || entity.position().distanceToSqr(scan.center()) > scan.rangeSqr()
					|| !WorldHelper.playerMayInteract(player, entity)) {
				continue;
			}
			handler.get().getDepositHandler(entity).ifPresent(handlers::add);
			if (includeStorageTargets) {
				targets.addAll(handler.get().getStorageItemHandlerTargets(entity));
			}
		}
		// blocks and entities were each sorted by the scan; interleave them by distance
		handlers.sort(Comparator.comparingDouble(h -> h.getPosition().distanceToSqr(scan.center())));
		targets.sort(Comparator.comparingDouble(t -> t.position().distanceToSqr(scan.center())));
		return new Storages(handlers, targets);
	}

	/** Cached scans can be a few seconds old; make sure the block there is still the same kind of storage. */
	private static boolean isStillStorage(ServerLevel level, BlockStorage storage, IBlockItemActionHandler handler) {
		if (!level.isLoaded(storage.pos())) {
			return false;
		}
		BlockEntity be = level.getBlockEntity(storage.pos());
		return be != null && !be.isRemoved() && handler.canActOn(level, storage.pos(), be);
	}

	public static boolean hasDepositableItem(Player player, List<IDepositHandler> handlers) {
		if (handlers.isEmpty()) {
			return false;
		}
		Set<ItemStackKey> checked = new HashSet<>();
		for (int slot = MIN_SLOT; slot < MAX_SLOT; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.isEmpty() || shouldSkipRegularDepositFromSlot(player, slot)) {
				continue;
			}
			ItemStackKey stackKey = ItemStackKey.of(stack);
			// several slots of the same item only need checking once
			if (!checked.add(stackKey)) {
				continue;
			}
			for (IDepositHandler handler : handlers) {
				if (handler.getItemMatch(stackKey) != ItemMatchResult.NO_MATCH) {
					return true;
				}
			}
		}
		return false;
	}

	/** Cheap value that changes whenever the deposited part of the inventory changes, so unchanged inventories can skip re-checking. */
	public static int inventoryFingerprint(Player player) {
		int hash = 1;
		for (int slot = MIN_SLOT; slot < MAX_SLOT; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			hash = 31 * hash + (stack.isEmpty() ? 0 : ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount());
		}
		return hash;
	}

	public static void deposit(ServerLevel level, ServerPlayer player, BlockPos pos) {
		// always a fresh scan here: a deposit must act on exactly what is there now
		Storages storages = resolve(level, player, scanStorages(level, pos, HotDepositConfig.range()), true);
		if (storages.depositHandlers().isEmpty() && storages.storageTargets().isEmpty()) {
			player.displayClientMessage(Component.translatable("gui.sophisticateditemactions.status.no_storage_in_range").withStyle(ChatFormatting.RED), true);
			playFailSound(player);
			return;
		}

		Map<Vec3, ItemTransferData> inserted = new HashMap<>();
		Set<Integer> depositedFromSlots = new HashSet<>();
		for (int slot = MIN_SLOT; slot < MAX_SLOT; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.isEmpty() || shouldSkipRegularDepositFromSlot(player, slot)) {
				continue;
			}

			// Same order as Sophisticated Item Actions: storages already holding this exact stack first, then ones holding the item
			List<IDepositHandler> sameItemHandlers = new ArrayList<>();
			ItemStackKey stackKey = ItemStackKey.of(stack);
			for (IDepositHandler handler : storages.depositHandlers()) {
				ItemMatchResult match = handler.getItemMatch(stackKey);
				if (match == ItemMatchResult.MATCHING_STACK) {
					stack = insert(handler, stack, player, slot, inserted, depositedFromSlots);
					if (stack.isEmpty()) {
						break;
					}
				} else if (match == ItemMatchResult.MATCHING_ITEM) {
					sameItemHandlers.add(handler);
				}
			}
			for (IDepositHandler handler : sameItemHandlers) {
				if (stack.isEmpty()) {
					break;
				}
				stack = insert(handler, stack, player, slot, inserted, depositedFromSlots);
			}
		}

		// Lets Sophisticated Backpacks' extension deposit from backpacks carried in the inventory, like the keybind does
		int extensionDeposited = ItemTransferExtensionRegistry.getExtension()
				.map(extension -> extension.depositFromInventorySources(player, MIN_SLOT, MAX_SLOT, true, storages.storageTargets(), inserted)).orElse(0);

		Vec3 playerPos = SubLevelCompatHelper.projectToWorld(level, player.getEyePosition().add(0, -0.1, 0));
		List<ItemTransferData> transfers = List.copyOf(inserted.values());
		SyncItemTransfersPayload payload = new SyncItemTransfersPayload(transfers, playerPos, true, false);
		PacketDistributor.sendToPlayer(player, payload);
		PacketDistributor.sendToPlayersTrackingEntity(player, payload);

		if (depositedFromSlots.isEmpty() && extensionDeposited == 0) {
			player.displayClientMessage(Component.translatable("gui.sophisticateditemactions.status.cannot_deposit_items"), true);
			playFailSound(player);
		} else {
			int count = depositedFromSlots.size() + extensionDeposited;
			player.displayClientMessage(Component.translatable("gui.sophisticateditemactions.status.deposited_items",
					Component.literal(String.valueOf(count)).withStyle(ChatFormatting.DARK_GREEN)), true);
		}
	}

	private static ItemStack insert(IDepositHandler handler, ItemStack stack, Player player, int slot, Map<Vec3, ItemTransferData> inserted,
			Set<Integer> depositedFromSlots) {
		ItemStack remaining = handler.insertItem(stack);
		if (remaining.getCount() < stack.getCount()) {
			inserted.computeIfAbsent(handler.getPosition(),
					k -> new ItemTransferData(handler.getPositionToOpen().orElse(null), handler.getPosition(), new ArrayList<>()))
					.itemsTransferred().add(stack.copyWithCount(stack.getCount() - remaining.getCount()));
			player.getInventory().setItem(slot, remaining);
			depositedFromSlots.add(slot);
		}
		return remaining;
	}

	private static boolean shouldSkipRegularDepositFromSlot(Player player, int slot) {
		return ItemTransferExtensionRegistry.getExtension().map(extension -> extension.shouldSkipRegularDepositFromSlot(player, slot)).orElse(false);
	}

	private static void playFailSound(Player player) {
		player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1, 0.7f);
	}

	/** Storages near a block, independent of any player. */
	public record StorageScan(List<BlockStorage> blocks, List<EntityStorage> entities, Vec3 center, double rangeSqr) {
		public boolean isEmpty() {
			return blocks.isEmpty() && entities.isEmpty();
		}
	}

	public record BlockStorage(BlockPos pos, BlockPos actOnPos, ResourceLocation handlerId, double distanceSqr) {
	}

	public record EntityStorage(int entityId, ResourceLocation handlerId, double distanceSqr) {
	}

	public record Storages(List<IDepositHandler> depositHandlers, List<StorageItemHandlerTarget> storageTargets) {
	}
}
