package net.fabricmc.loader.launch.knot;

import net.fabricmc.loader.launch.common.FabricLauncherBase;
import org.spongepowered.asm.service.IGlobalPropertyService;

public class FabricGlobalPropertyService implements IGlobalPropertyService {
	@SuppressWarnings("unchecked")
	@Override
	public <T> T getProperty(String key) {
		return (T) FabricLauncherBase.getProperties().get(key);
	}

	@Override
	public void setProperty(String key, Object value) {
		FabricLauncherBase.getProperties().put(key, value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getProperty(String key, T defaultValue) {
		return (T) FabricLauncherBase.getProperties().getOrDefault(key, defaultValue);
	}

	@Override
	public String getPropertyString(String key, String defaultValue) {
		Object o = FabricLauncherBase.getProperties().get(key);
		return o != null ? o.toString() : defaultValue;
	}
}
