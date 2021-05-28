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
 * An exception that occurs during a {@link LanguageAdapter}'s object creation.
 *
 * @see LanguageAdapter
 */
@SuppressWarnings("serial")
public class LanguageAdapterException extends Exception {
	/**
	 * Creates a new language adapter exception.
	 *
	 * @param s the message
	 */
	public LanguageAdapterException(String s) {
		super(s);
	}

	/**
	 * Creates a new language adapter exception.
	 *
	 * @param t the cause
	 */
	public LanguageAdapterException(Throwable t) {
		super(t);
	}

	/**
	 * Creates a new language adapter exception.
	 *
	 * @param s the message
	 * @param t the cause
	 */
	public LanguageAdapterException(String s, Throwable t) {
		super(s, t);
	}
}
