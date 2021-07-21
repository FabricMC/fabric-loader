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

public final class StringUtil {
	public static String capitalize(String s) {
		if (s.isEmpty()) return s;

		int cp = s.codePointAt(0);
		int cpUpper = Character.toUpperCase(cp);
		if (cpUpper == cp) return s;

		StringBuilder ret = new StringBuilder(s.length());
		ret.appendCodePoint(cpUpper);
		ret.append(s, Character.charCount(cp), s.length());

		return ret.toString();
	}

	public static String[] splitNamespaced(String s, String defaultNamespace) {
		int i = s.indexOf(':');

		if (i >= 0) {
			return new String[] { s.substring(0, i), s.substring(i + 1) };
		} else {
			return new String[] { defaultNamespace, s };
		}
	}
}
