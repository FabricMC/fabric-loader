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

package net.fabricmc.loader.impl.game;

import net.fabricmc.api.EnvType;

enum LoaderLibrary {
	FABRIC_LOADER("net/fabricmc/loader/api/FabricLoader.class"),
	TINY_MAPPINGS_PARSER("net/fabricmc/mapping/tree/TinyMappingFactory.class"),
	SPONGE_MIXIN("org/spongepowered/asm/launch/MixinBootstrap.class"),
	TINY_REMAPPER("net/fabricmc/tinyremapper/TinyRemapper.class"),
	ACCESS_WIDENER("net/fabricmc/accesswidener/AccessWidener.class"),
	ASM("org/objectweb/asm/ClassReader.class"),
	ASM_ANALYSIS("org/objectweb/asm/tree/analysis/Analyzer.class"),
	ASM_COMMONS("org/objectweb/asm/commons/Remapper.class"),
	ASM_TREE("org/objectweb/asm/tree/ClassNode.class"),
	ASM_UTIL("org/objectweb/asm/util/CheckClassAdapter.class"),
	INTERMEDIARY("mappings/mappings.tiny"),
	SAT4J_CORE(true, "org/sat4j/specs/ContradictionException.class"),
	SAT4J_PB(true, "org/sat4j/pb/SolverFactory.class");

	final boolean shaded;
	final String path;

	LoaderLibrary(String path) {
		this(false, path);
	}

	LoaderLibrary(boolean shaded, String path) {
		this.shaded = shaded;
		this.path = path;
	}

	public boolean isApplicable(EnvType env, boolean shaded) {
		return !shaded || !this.shaded;
	}
}
