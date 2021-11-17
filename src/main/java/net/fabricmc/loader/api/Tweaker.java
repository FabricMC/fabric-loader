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

package net.fabricmc.loader.api;

/**
 * Helper class for instrumenting game without Mixins.
 * An alternative to launchwrapper tweakers.
 */
public interface Tweaker {

	/**
	 * Initializes this tweaker.
	 */
	void initialize();

	/**
	 * Returns an array of class bytes.
	 *
	 * @param name
	 * 		name of the class.
	 *
	 * @return byte array of the class or {@code null}.
	 */
	byte[] getClassBytes(String name);

	/**
	 * Called after applying mixins to the class.
	 *
	 * @param name
	 * 		name of the class.
	 * @param classBytes
	 * 		class bytecode.
	 */
	void postApply(String name, byte[] classBytes);
}
