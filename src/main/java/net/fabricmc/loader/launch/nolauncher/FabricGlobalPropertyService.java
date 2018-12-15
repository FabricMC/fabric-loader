package net.fabricmc.loader.launch.nolauncher;

import net.fabricmc.loader.launch.common.CommonLauncherUtils;
import org.spongepowered.asm.service.IGlobalPropertyService;

public class FabricGlobalPropertyService implements IGlobalPropertyService {
	@SuppressWarnings("unchecked")
	@Override
	public <T> T getProperty(String key) {
		return (T) CommonLauncherUtils.properties.get(key);
	}

	@Override
	public void setProperty(String key, Object value) {
		CommonLauncherUtils.properties.put(key, value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getProperty(String key, T defaultValue) {
		return (T) CommonLauncherUtils.properties.getOrDefault(key, defaultValue);
	}

	@Override
	public String getPropertyString(String key, String defaultValue) {
		Object o = CommonLauncherUtils.properties.get(key);
		return o != null ? o.toString() : defaultValue;
	}
}
