package com.hotdeposit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the "should the eyes be open" checks for every loaded Hot Deposit block on the server.
 * <ul>
 * <li>Blocks don't tick on their own; they sit in one queue per dimension.</li>
 * <li>Each block is checked at most once per {@link #CHECK_INTERVAL} ticks.</li>
 * <li>At most {@link #MAX_EXPENSIVE_CHECKS_PER_TICK} checks that actually scan storages or match items run in one tick;
 * the rest wait in the queue for the next tick, so many blocks or players can never pile their work into the same tick.</li>
 * </ul>
 */
@EventBusSubscriber(modid = HotDeposit.MOD_ID)
public final class HotDepositTracker {
	static final int CHECK_INTERVAL = 20;
	private static final int MAX_EXPENSIVE_CHECKS_PER_TICK = 4;

	private static final Map<ServerLevel, ArrayDeque<HotDepositBlockEntity>> QUEUES = new HashMap<>();
	// per-player deposit cooldown (game time of the last deposit)
	private static final Map<UUID, Long> LAST_DEPOSIT = new HashMap<>();

	private HotDepositTracker() {
	}

	static void add(HotDepositBlockEntity blockEntity) {
		if (blockEntity.getLevel() instanceof ServerLevel level) {
			ArrayDeque<HotDepositBlockEntity> queue = QUEUES.computeIfAbsent(level, l -> new ArrayDeque<>());
			if (!queue.contains(blockEntity)) {
				queue.addLast(blockEntity);
			}
		}
	}

	static void remove(HotDepositBlockEntity blockEntity) {
		if (blockEntity.getLevel() instanceof ServerLevel level) {
			ArrayDeque<HotDepositBlockEntity> queue = QUEUES.get(level);
			if (queue != null) {
				queue.remove(blockEntity);
			}
		}
	}

	/** Returns true if the player may deposit now, and starts their cooldown. */
	static boolean tryStartDeposit(UUID player, long gameTime, long cooldownTicks) {
		Long last = LAST_DEPOSIT.get(player);
		if (last != null && gameTime - last < cooldownTicks && gameTime >= last) {
			return false;
		}
		LAST_DEPOSIT.put(player, gameTime);
		return true;
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (!(event.getLevel() instanceof ServerLevel level)) {
			return;
		}
		ArrayDeque<HotDepositBlockEntity> queue = QUEUES.get(level);
		if (queue == null || queue.isEmpty()) {
			return;
		}

		long gameTime = level.getGameTime();
		int budget = MAX_EXPENSIVE_CHECKS_PER_TICK;
		// walk the queue once, rotating each visited block to the back so no block is starved when the budget runs out
		for (int i = queue.size(); i > 0 && budget > 0; i--) {
			HotDepositBlockEntity blockEntity = queue.pollFirst();
			if (blockEntity == null) {
				break;
			}
			if (blockEntity.isRemoved() || blockEntity.getLevel() != level) {
				continue;
			}
			queue.addLast(blockEntity);
			if (blockEntity.isCheckDue(gameTime) && blockEntity.runCheck(level, gameTime)) {
				budget--;
			}
		}
	}

	@SubscribeEvent
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		LAST_DEPOSIT.remove(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel() instanceof Level level) {
			QUEUES.remove(level);
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		QUEUES.clear();
		LAST_DEPOSIT.clear();
	}
}
