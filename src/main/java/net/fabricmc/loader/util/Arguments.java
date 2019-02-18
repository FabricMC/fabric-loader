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

import java.util.*;

public final class Arguments {
	private final Map<String, String> values;
	private final List<String> extraArgs;

	public Arguments() {
		values = new LinkedHashMap<>();
		extraArgs = new ArrayList<>();
	}

	public Collection<String> keys() {
		return values.keySet();
	}

	public List<String> getExtraArgs() {
		return Collections.unmodifiableList(extraArgs);
	}

	public boolean containsKey(String key) {
		return values.containsKey(key);
	}

	public String get(String key) {
		return values.get(key);
	}

	public String getOrDefault(String key, String value) {
		return values.getOrDefault(key, value);
	}

	public void put(String key, String value) {
		values.put(key, value);
	}

	public void addExtraArg(String value) {
		extraArgs.add(value);
	}

	public void parse(String[] args) {
		parse(Arrays.asList(args));
	}

	public void parse(List<String> args) {
		for (int i = 0; i < args.size(); i++) {
			String arg = args.get(i);
			if (arg.startsWith("--") && i < args.size() - 1) {
				values.put(arg.substring(2), args.get(++i));
			} else {
				extraArgs.add(arg);
			}
		}
	}

	public String[] toArray() {
		String[] newArgs = new String[values.size() * 2 + extraArgs.size()];
		int i = 0;
		for (String s : values.keySet()) {
			newArgs[i++] = "--" + s;
			newArgs[i++] = values.get(s);
		}
		for (String s : extraArgs) {
			newArgs[i++] = s;
		}
		return newArgs;
	}

	public String remove(String s) {
		return values.remove(s);
	}
}
