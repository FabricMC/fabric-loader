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

package net.fabricmc.loader.api.metadata;

import java.nio.file.Path;
import java.util.List;

/**
 * Representation of the various locations a mod was loaded from originally.
 *
 * <p>This location is not necessarily identical to the code source used at runtime, a mod may get copied or otherwise
 * transformed before being put on the class path. It thus mostly represents the installation and initial loading, not
 * what is being directly accessed at runtime.
 */
public interface ModOrigin {
	/**
	 * Get the kind of this origin, determines the available methods.
	 *
	 * @return mod origin kind
	 */
	Kind getKind();

	/**
	 * Get the jar or folder paths for a {@link Kind#PATH} origin.
	 *
	 * @return jar or folder paths
	 * @throws UnsupportedOperationException for incompatible kinds
	 */
	List<Path> getPaths();

	/**
	 * Get the parent mod for a {@link Kind#NESTED} origin.
	 *
	 * @return parent mod
	 * @throws UnsupportedOperationException for incompatible kinds
	 */
	String getParentModId();

	/**
	 * Get the sub-location within the parent mod for a {@link Kind#NESTED} origin.
	 *
	 * @return sub-location
	 * @throws UnsupportedOperationException for incompatible kinds
	 */
	String getParentSubLocation();

	/**
	 * Non-exhaustive list of possible {@link ModOrigin} kinds.
	 *
	 * <p>New kinds may be added in the future, use a default switch case!
	 */
	enum Kind {
		PATH, NESTED, UNKNOWN
	}
}
