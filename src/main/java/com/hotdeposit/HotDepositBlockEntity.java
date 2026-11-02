package com.hotdeposit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Saves no data. Keeps the caches that let {@link HotDepositTracker} decide cheaply whether the eyes should be open:
 * <ul>
 * <li>which storages are near this block (re-scanned every {@link #STORAGE_RESCAN_INTERVAL} ticks, or when the config changes), and</li>
 * <li>per nearby player, the last answer and a fingerprint of their inventory; while neither the inventory nor the
 * storage scan changed, the answer is reused instead of matching items again.</li>
 * </ul>
 * Storage contents changing (someone emptying a chest) is picked up by the periodic re-scan, so the eyes can lag behind that
 * by up to {@link #STORAGE_RESCAN_INTERVAL} ticks; inventory changes are picked up within {@link HotDepositTracker#CHECK_INTERVAL} ticks.
 */
public class HotDepositBlockEntity extends BlockEntity {
	private static final int STORAGE_RESCAN_INTERVAL = 100;

	private long nextCheck;
	private HotDepositLogic.StorageScan scan;
	private long scanExpiry;
	private int scanConfigVersion = -1;
	private final Map<UUID, CachedAnswer> answers = new HashMap<>();

	public HotDepositBlockEntity(BlockPos pos, BlockState state) {
		super(HotDeposit.HOT_DEPOSIT_BLOCK_ENTITY.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if (level instanceof ServerLevel serverLevel) {
			// spread first checks of blocks loaded together (e.g. a chunk full of them) over different ticks
			nextCheck = serverLevel.getGameTime() + Math.floorMod(worldPosition.asLong(), HotDepositTracker.CHECK_INTERVAL);
			HotDepositTracker.add(this);
		}
	}

	@Override
	public void setRemoved() {
		HotDepositTracker.remove(this);
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		HotDepositTracker.remove(this);
		super.onChunkUnloaded();
	}

	boolean isCheckDue(long gameTime) {
		return gameTime >= nextCheck;
	}

	/** Forget cached answers and storages and check again on the next tick, e.g. right after a deposit. */
	void invalidate(long gameTime) {
		scan = null;
		answers.clear();
		nextCheck = gameTime;
	}

	/**
	 * Updates the eyes. Returns true when this did real work (scanned storages or matched items),
	 * false when everything was answered from the caches.
	 */
	boolean runCheck(ServerLevel level, long gameTime) {
		nextCheck = gameTime + HotDepositTracker.CHECK_INTERVAL;
		int range = HotDepositConfig.range();
		Vec3 center = Vec3.atCenterOf(worldPosition);
		double rangeSqr = (double) range * range;

		List<ServerPlayer> nearby = new ArrayList<>();
		Set<UUID> nearbyIds = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(center) <= rangeSqr) {
				nearby.add(player);
				nearbyIds.add(player.getUUID());
			}
		}
		answers.keySet().retainAll(nearbyIds);
		if (nearby.isEmpty()) {
			// nobody around: no scanning or matching at all
			setOpen(level, false);
			return false;
		}

		boolean didWork = false;
		if (scan == null || gameTime >= scanExpiry || scanConfigVersion != HotDepositConfig.version()) {
			scan = HotDepositLogic.scanStorages(level, worldPosition, range);
			scanExpiry = gameTime + STORAGE_RESCAN_INTERVAL;
			scanConfigVersion = HotDepositConfig.version();
			answers.clear();
			didWork = true;
		}
		if (scan.isEmpty()) {
			setOpen(level, false);
			return didWork;
		}

		boolean open = false;
		for (ServerPlayer player : nearby) {
			UUID id = player.getUUID();
			int fingerprint = HotDepositLogic.inventoryFingerprint(player);
			CachedAnswer cached = answers.get(id);
			boolean canDeposit;
			if (cached != null && cached.fingerprint() == fingerprint) {
				canDeposit = cached.canDeposit();
			} else {
				canDeposit = HotDepositLogic.hasDepositableItem(player, HotDepositLogic.resolve(level, player, scan, false).depositHandlers());
				answers.put(id, new CachedAnswer(fingerprint, canDeposit));
				didWork = true;
			}
			if (canDeposit) {
				// one player is enough to open the eyes; the others are checked once this one no longer can
				open = true;
				break;
			}
		}
		setOpen(level, open);
		return didWork;
	}

	private void setOpen(ServerLevel level, boolean open) {
		BlockState state = getBlockState();
		if (state.hasProperty(HotDepositBlock.OPEN) && state.getValue(HotDepositBlock.OPEN) != open) {
			level.setBlock(worldPosition, state.setValue(HotDepositBlock.OPEN, open), Block.UPDATE_ALL);
		}
	}

	private record CachedAnswer(int fingerprint, boolean canDeposit) {
	}
}
