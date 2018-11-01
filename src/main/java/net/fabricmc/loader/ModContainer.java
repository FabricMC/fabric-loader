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

package net.fabricmc.loader;

import net.fabricmc.loader.language.ILanguageAdapter;

import java.io.File;

public class ModContainer {
	private ModInfo info;
	private File originFile;
	private ILanguageAdapter adapter;

	public ModContainer(ModInfo info, File originFile, boolean instantiate) {
		this.info = info;
		this.originFile = originFile;
		if (instantiate) {
			this.adapter = createAdapter();
		}
	}

	public ModInfo getInfo() {
		return info;
	}

	public File getOriginFile() {
		return originFile;
	}

	public ILanguageAdapter getAdapter() {
		return adapter;
	}

	private ILanguageAdapter createAdapter() {
		try {
			return (ILanguageAdapter) Class.forName(info.getLanguageAdapter()).newInstance();
		} catch (Exception e) {
			throw new RuntimeException(String.format("Unable to create language adapter %s for mod %s", info.getLanguageAdapter(), info.getId()), e);
		}
	}
}
