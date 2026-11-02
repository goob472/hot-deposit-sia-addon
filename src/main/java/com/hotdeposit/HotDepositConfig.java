package com.hotdeposit;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

public final class HotDepositConfig {
	public static final ModConfigSpec SPEC;

	private static final ModConfigSpec.IntValue RANGE;
	private static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		RANGE = builder.comment("How far (in blocks) from the Hot Deposit block storages are searched for.",
						"Sophisticated Item Actions' own Shift + ' range is 10.")
				.defineInRange("range", 10, 1, 32);
		BLACKLIST = builder.comment("Storages the Hot Deposit block never deposits into. Each entry is one of:",
						"  a block or entity id, e.g. \"minecraft:furnace\" or \"minecraft:armor_stand\"",
						"  a whole mod, e.g. \"somemod:*\"",
						"  a block or entity type tag, e.g. \"#c:chests\"")
				.defineListAllowEmpty("blacklist",
						List.of("minecraft:armor_stand", "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker", "minecraft:brewing_stand"),
						() -> "minecraft:", HotDepositConfig::isValidEntry);
		SPEC = builder.build();
	}

	private static List<Entry> parsedBlacklist = List.of();
	// bumped on every config load/reload so blocks know to drop their cached storage scans
	private static int version = 0;

	private HotDepositConfig() {
	}

	public static int version() {
		return version;
	}

	public static int range() {
		return RANGE.get();
	}

	public static boolean isBlacklisted(BlockState state) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		for (Entry entry : parsedBlacklist) {
			if (entry.tag() ? state.is(entry.blockTag()) : entry.matches(id)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isBlacklisted(Entity entity) {
		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		for (Entry entry : parsedBlacklist) {
			if (entry.tag() ? entity.getType().is(entry.entityTag()) : entry.matches(id)) {
				return true;
			}
		}
		return false;
	}

	static void onConfigChanged(ModConfigEvent event) {
		if (event.getConfig().getSpec() != SPEC) {
			return;
		}
		List<Entry> entries = new ArrayList<>();
		for (String raw : BLACKLIST.get()) {
			Entry entry = parse(raw);
			if (entry != null) {
				entries.add(entry);
			}
		}
		parsedBlacklist = List.copyOf(entries);
		version++;
	}

	private static boolean isValidEntry(Object value) {
		return value instanceof String s && parse(s) != null;
	}

	private static Entry parse(String raw) {
		String value = raw.trim();
		boolean tag = value.startsWith("#");
		if (tag) {
			value = value.substring(1);
		}
		if (!tag && value.endsWith(":*")) {
			String namespace = value.substring(0, value.length() - 2);
			return ResourceLocation.isValidNamespace(namespace) && !namespace.isEmpty() ? new Entry(null, namespace, false, null, null) : null;
		}
		ResourceLocation id = ResourceLocation.tryParse(value);
		return id == null || id.getPath().isEmpty() ? null : new Entry(id, null, tag, tag ? TagKey.create(Registries.BLOCK, id) : null, tag ? TagKey.create(Registries.ENTITY_TYPE, id) : null);
	}

	/**
	 * Either an exact id / tag ({@code id} set) or every id from a mod ({@code namespace} set).
	 * Tag keys are built once here rather than on every check.
	 */
	private record Entry(ResourceLocation id, String namespace, boolean tag, TagKey<Block> blockTag, TagKey<EntityType<?>> entityTag) {
		boolean matches(ResourceLocation other) {
			return namespace != null ? namespace.equals(other.getNamespace()) : id.equals(other);
		}
	}
}
