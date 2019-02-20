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

package net.fabricmc.loader.util;

import com.google.common.base.Strings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class FabricBranding {
	public static final String FABRIC = "fabric";
	public static final String VANILLA = "vanilla";
	
	private static final Logger LOGGER = LogManager.getLogger("FabricBranding");

	private FabricBranding() {
	}

	public static String apply(final String branding, final Object environment) {
		if (Strings.isNullOrEmpty(branding)) {
			LOGGER.warn("Null or empty branding given for {}", environment);
			return FABRIC;
		}
		return VANILLA.equals(branding) ? FABRIC : branding + "," + FABRIC;
	}
}
