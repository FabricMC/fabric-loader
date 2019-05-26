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

package net.fabricmc.loader.game;

import java.nio.file.Path;

public class MappingStep {
	private final String from, to;
	private final Path path;

	MappingStep(String from, String to) {
		this.from = from;
		this.to = to;
		this.path = null;
	}

	MappingStep(String from, String to, Path path) {
		this.from = from;
		this.to = to;
		this.path = path;
	}

	public String getFrom() {
		return from;
	}

	public String getTo() {
		return to;
	}

	public boolean usesClasspathMappings() {
		return path == null;
	}

	public Path getPath() {
		return path;
	}

	@Override
	public String toString() {
		return "MappingStep{" + from + " -> " + to + "}";
	}
}
