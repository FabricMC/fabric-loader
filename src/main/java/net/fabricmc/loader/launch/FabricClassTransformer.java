package net.fabricmc.loader.launch;

import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.transformer.PublicAccessTransformer;
import net.minecraft.launchwrapper.IClassTransformer;

public class FabricClassTransformer implements IClassTransformer {
	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if (FabricLauncherBase.getLauncher().isDevelopment()) {
			return PublicAccessTransformer.transform(name, basicClass);
		} else {
			return basicClass;
		}
	}
}
