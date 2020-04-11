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

package net.fabricmc.loader.guava;

import com.google.common.collect.UnmodifiableListIterator;

public class IteratorsFix {

	public static <T> UnmodifiableListIterator<T> emptyListIterator() {
		return new UnmodifiableListIterator<T>() {

			@Override
			public boolean hasNext() {
				return false;
			}

			@Override
			public T next() {
				return null;
			}

			@Override
			public boolean hasPrevious() {
				return false;
			}

			@Override
			public T previous() {
				return null;
			}

			@Override
			public int nextIndex() {
				return 0;
			}

			@Override
			public int previousIndex() {
				return 0;
			}
		};
	}

}
