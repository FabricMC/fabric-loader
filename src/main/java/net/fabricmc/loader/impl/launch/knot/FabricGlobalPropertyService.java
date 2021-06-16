/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.impl.launch.knot;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import net.fabricmc.loader.impl.launch.FabricLauncherBase;

public class FabricGlobalPropertyService implements IGlobalPropertyService {
	@Override
	public IPropertyKey resolveKey(String name) {
		return new MixinStringPropertyKey(name);
	}

	private String keyString(IPropertyKey key) {
		return ((MixinStringPropertyKey) key).key;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getProperty(IPropertyKey key) {
		return (T) FabricLauncherBase.getProperties().get(keyString(key));
	}

	@Override
	public void setProperty(IPropertyKey key, Object value) {
		FabricLauncherBase.getProperties().put(keyString(key), value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getProperty(IPropertyKey key, T defaultValue) {
		return (T) FabricLauncherBase.getProperties().getOrDefault(keyString(key), defaultValue);
	}

	@Override
	public String getPropertyString(IPropertyKey key, String defaultValue) {
		Object o = FabricLauncherBase.getProperties().get(keyString(key));
		return o != null ? o.toString() : defaultValue;
	}
}
