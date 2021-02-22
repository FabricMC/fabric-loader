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

package net.fabricmc.loader.api.config.entrypoint;

import java.util.function.BiConsumer;

/**
 * Allows the definition of multiple config files with one entrypoint.
 * See {@link ConfigInitializer}
 */
public interface ConfigProvider {
	/**
	 * @param consumer consumes the modId under which to register a config file and the initializer to create it
	 */
	void addConfigs(BiConsumer<String, ConfigInitializer<?>> consumer);
}
