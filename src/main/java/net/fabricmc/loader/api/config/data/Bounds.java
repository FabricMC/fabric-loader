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

import org.jetbrains.annotations.NotNull;

/**
 * Represents an inclusive bound on a number.
 */
public class Bounds<T extends Number & Comparable<T>> extends Constraint<T> {
	protected final T min;
	protected final T max;
	protected final T absoluteMin;
	protected final T absoluteMax;
	protected final String string;

	protected Bounds(String name, @NotNull T min, @NotNull T max, @NotNull T absoluteMin, @NotNull T absoluteMax) {
		super(name);
		this.min = min;
		this.max = max;
		this.absoluteMin = absoluteMin;
		this.absoluteMax = absoluteMax;
		this.string = min.equals(absoluteMin)
				? "max =" + max
				: max.equals(absoluteMax)
				? "min = " + min
				: "bounds[" + this.min + ", " + this.max + "]";
	}

	@Override
	public final String toString() {
		return this.string;
	}

	public @NotNull T getMin() {
		return this.min;
	}

	public @NotNull T getMax() {
		return this.max;
	}

	public @NotNull T getAbsoluteMin() {
		return this.absoluteMin;
	}

	public @NotNull T getAbsoluteMax() {
		return this.absoluteMax;
	}

	@Override
	public boolean passes(T value) {
		return value.compareTo(this.min) >= 0 && value.compareTo(this.max) <= 0;
	}
}
