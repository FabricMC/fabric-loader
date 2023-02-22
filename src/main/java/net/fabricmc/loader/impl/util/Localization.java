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

package net.fabricmc.loader.impl.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public final class Localization {
	public static final ResourceBundle BUNDLE = createBundle("net.fabricmc.loader.Messages", Locale.getDefault());
	public static final ResourceBundle ROOT_LOCALE_BUNDLE = createBundle("net.fabricmc.loader.Messages", Locale.ROOT);

	public static String format(String key, Object... args) {
		String pattern = BUNDLE.getString(key);

		if (args.length == 0) {
			return pattern;
		} else {
			return MessageFormat.format(pattern, args);
		}
	}

	public static String formatRoot(String key, Object... args) {
		String pattern = ROOT_LOCALE_BUNDLE.getString(key);

		if (args.length == 0) {
			return pattern;
		} else {
			return MessageFormat.format(pattern, args);
		}
	}

	private static ResourceBundle createBundle(String name, Locale locale) {
		if (System.getProperty("java.version", "").startsWith("1.")) { // below java 9
			return ResourceBundle.getBundle(name, locale, new ResourceBundle.Control() {
				@Override
				public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
						throws IllegalAccessException, InstantiationException, IOException {
					if (format.equals("java.properties")) {
						InputStream is = loader.getResourceAsStream(toResourceName(toBundleName(baseName, locale), "properties"));

						if (is != null) {
							try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
								return new PropertyResourceBundle(reader);
							}
						}
					}

					return super.newBundle(baseName, locale, format, loader, reload);
				};
			});
		} else { // java 9 and later
			return ResourceBundle.getBundle(name, locale);
		}
	}
}
