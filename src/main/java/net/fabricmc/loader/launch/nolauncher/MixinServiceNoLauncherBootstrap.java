package net.fabricmc.loader.launch.nolauncher;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

public class MixinServiceNoLauncherBootstrap implements IMixinServiceBootstrap {
	@Override
	public String getName() {
		return "NoLauncher";
	}

	@Override
	public String getServiceClassName() {
		return "net.fabricmc.loader.launch.nolauncher.MixinServiceNoLauncher";
	}

	@Override
	public void bootstrap() {
		// already done in NoLauncher
	}
}
