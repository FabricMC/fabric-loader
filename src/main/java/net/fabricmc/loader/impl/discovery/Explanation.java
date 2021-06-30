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

package net.fabricmc.loader.impl.discovery;

import net.fabricmc.loader.api.metadata.ModDependency;

class Explanation implements Comparable<Explanation> {
	private static int nextCmpId;

	final ErrorKind error;
	final ModCandidate mod;
	final ModDependency dep;
	final String data;
	private final int cmpId;

	Explanation(ErrorKind error, ModCandidate mod) {
		this(error, mod, null, null);
	}

	Explanation(ErrorKind error, ModCandidate mod, ModDependency dep) {
		this(error, mod, dep, null);
	}

	Explanation(ErrorKind error, String data) {
		this(error, null, data);
	}

	Explanation(ErrorKind error, ModCandidate mod, String data) {
		this(error, mod, null, data);
	}

	private Explanation(ErrorKind error, ModCandidate mod, ModDependency dep, String data) {
		this.error = error;
		this.mod = mod;
		this.dep = dep;
		this.data = data;
		this.cmpId = nextCmpId++;
	}

	@Override
	public int compareTo(Explanation o) {
		return Integer.compare(cmpId, o.cmpId);
	}

	@Override
	public String toString() {
		if (mod == null) {
			return String.format("%s %s", error.desc, data);
		} else if (dep == null) {
			return String.format("%s %s", error.desc, mod);
		} else {
			return String.format("%s %s %s", error.desc, mod, dep);
		}
	}

	enum ErrorKind {
		PRESELECT_HARD_DEP("presel: reqdep", true),
		PRESELECT_SOFT_DEP("presel: optdep", true),
		PRESELECT_NEG_DEP("presel: neg.dep", true),
		PRESELECT_ROOT("presel root", false),
		HARD_DEP_INCOMPATIBLE_PRESELECTED("incompatible presel reqdep", true),
		HARD_DEP_NO_CANDIDATE("reqdep without candidate", true),
		HARD_DEP("reqdep", true),
		SOFT_DEP("optdep", true),
		NEG_DEP("neg.dep", true),
		REQ_AUTO_LOAD_NESTED("req autoload nested", false),
		REQ_NESTED_PARENT("req nested parent", false),
		REQ_AUTO_LOAD_ROOT_SINGLE("req autoload root single", false),
		REQ_AUTO_LOAD_ROOT("req autoload root", false),
		UNIQUE_ID("unique id", false);

		ErrorKind(String desc, boolean isDependencyError) {
			this.desc = desc;
			this.isDependencyError = isDependencyError;
		}

		final String desc;
		final boolean isDependencyError;
	}
}
