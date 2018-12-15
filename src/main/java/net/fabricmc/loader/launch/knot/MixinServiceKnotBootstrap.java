package net.fabricmc.loader.launch.knot;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

public class MixinServiceKnotBootstrap implements IMixinServiceBootstrap {
	@Override
	public String getName() {
		return "Knot";
	}

	@Override
	public String getServiceClassName() {
		return "net.fabricmc.loader.launch.knot.MixinServiceKnot";
	}

	@Override
	public void bootstrap() {
		// already done in Knot
	}
}
