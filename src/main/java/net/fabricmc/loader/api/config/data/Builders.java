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

import net.fabricmc.loader.api.config.value.ValueKey;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.function.Supplier;

public class Builders {
	public static class Number<T extends java.lang.Number & Comparable<T>> extends ValueKey.Builder<T> {
		private final java.lang.String boundsName;
		private final T absoluteMin;
		private final T absoluteMax;

		public Number(@NotNull Supplier<@NotNull T> defaultValue, @NotNull T absoluteMin, @NotNull T absoluteMax) {
			super(defaultValue);
			this.boundsName = defaultValue.get().getClass().getSimpleName().toLowerCase(Locale.ROOT);
			this.absoluteMin = absoluteMin;
			this.absoluteMax = absoluteMax;
		}

		/**
		 * @param min minimum value, inclusive
		 * @param max maximum value, inclusive
		 * @return this
		 */
		public Number<T> bounds(T min, T max) {
			this.with(new Bounds<>(this.boundsName, min, max, this.absoluteMin, this.absoluteMax));
			return this;
		}

		/**
		 * @param min minimum value, inclusive
		 * @return this
		 */
		public Number<T> min(T min) {
			this.with(new Bounds<>(this.boundsName, min, this.absoluteMax, this.absoluteMin, this.absoluteMax));
			return this;
		}

		/**
		 * @param max maximum value, inclusive
		 * @return this
		 */
		public Number<T> max(T max) {
			this.with(new Bounds<>(this.boundsName, this.absoluteMin, max, this.absoluteMin, this.absoluteMax));
			return this;
		}
	}

	public static class String extends ValueKey.Builder<java.lang.String> {
		public String(@NotNull Supplier<java.lang.@NotNull String> defaultValue) {
			super(defaultValue);
		}

		public String matches(java.lang.String regex) {
			this.with(new Matches(regex));
			return this;
		}
	}
}
