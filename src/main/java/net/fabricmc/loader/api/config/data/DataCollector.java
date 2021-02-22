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

package net.fabricmc.loader.api.config.data;

import java.util.Arrays;
import java.util.Collection;

public interface DataCollector {
	/**
	 * Adds any number of pieces of data of one specific type.
	 * @param type the type of data to add
	 * @param data zero or more data values
	 * @param <T> the type of data to add
	 */
	@SuppressWarnings("unchecked")
	default <T> void add(DataType<T> type, T... data) {
		this.add(type, Arrays.asList(data));
	}

	/**
	 * Adds any number of pieces of data of one specific type.
	 * @param type the type of data to add
	 * @param data zero or more data values
	 * @param <T> the type of data to add
	 */
	<T> void add(DataType<T> type, Collection<T> data);
}
