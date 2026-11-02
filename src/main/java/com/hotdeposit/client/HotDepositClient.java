package com.hotdeposit.client;

import com.hotdeposit.HotDeposit;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entry point: adds the "Config" button for this mod in the Mods list.
 */
@Mod(value = HotDeposit.MOD_ID, dist = Dist.CLIENT)
public class HotDepositClient {
	public HotDepositClient(ModContainer container) {
		container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
	}
}
