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

package net.fabricmc.test;

import net.fabricmc.loader.util.version.SemanticVersion;
import net.fabricmc.loader.util.version.SemanticVersionPredicateParser;
import net.fabricmc.loader.util.version.VersionParsingException;

import javax.annotation.Nullable;
import java.util.function.Predicate;

public class VersionParsingTests {
	private static Exception tryParseSemantic(String s, boolean storeX) {
		try {
			new SemanticVersion(s, storeX);
			return null;
		} catch (VersionParsingException e) {
			return e;
		}
	}

	private static void testTrue(@Nullable Exception b) {
		if (b != null) {
			throw new RuntimeException("Test failed!", b);
		}
	}

	private static void testFalse(@Nullable Exception b) {
		if (b == null) {
			throw new RuntimeException("Test failed!");
		}
	}

	private static void testTrue(boolean b) {
		if (!b) {
			throw new RuntimeException("Test failed!");
		}
	}

	private static void testFalse(boolean b) {
		if (b) {
			throw new RuntimeException("Test failed!");
		}
	}

	public static void main(String[] args) throws Exception {
		// Test: Semantic version creation.
		testTrue(tryParseSemantic("0.3.5", false));
		testTrue(tryParseSemantic("0.3.5-beta.2", false));
		testTrue(tryParseSemantic("0.3.5-alpha.6+build.120", false));
		testTrue(tryParseSemantic("0.3.5+build.3000", false));
		testFalse(tryParseSemantic("0.0.-1", false));
		testFalse(tryParseSemantic("0.-1.0", false));
		testFalse(tryParseSemantic("-1.0.0", false));
		testFalse(tryParseSemantic("", false));
		testFalse(tryParseSemantic("0.0.a", false));
		testFalse(tryParseSemantic("0.a.0", false));
		testFalse(tryParseSemantic("a.0.0", false));
		testFalse(tryParseSemantic("x", true));
		testTrue(tryParseSemantic("2.x", true));
		testTrue(tryParseSemantic("2.X", true));
		testTrue(tryParseSemantic("2.*", true));
		testFalse(tryParseSemantic("2.x", false));
		testFalse(tryParseSemantic("2.X", false));
		testFalse(tryParseSemantic("2.*", false));

		// Test: Semantic version creation (spec).
		testTrue(tryParseSemantic("1.0.0-0.3.7", false));
		testTrue(tryParseSemantic("1.0.0-x.7.z.92", false));
		testTrue(tryParseSemantic("1.0.0+20130313144700", false));
		testTrue(tryParseSemantic("1.0.0-beta+exp.sha.5114f85", false));

		// Test: comparator range with pre-releases.
		{
			Predicate<SemanticVersion> predicate = SemanticVersionPredicateParser.create(">=0.3.1-beta.2 <0.4.0");
			testTrue(predicate.test(new SemanticVersion("0.3.1-beta.2", false)));
			testTrue(predicate.test(new SemanticVersion("0.3.4+build.125", false)));
			testTrue(predicate.test(new SemanticVersion("0.3.7", false)));
			testFalse(predicate.test(new SemanticVersion("0.4.0", false)));
			testFalse(predicate.test(new SemanticVersion("0.3.4-beta.7", false)));
		}

		// Test: x-range.
		{
			Predicate<SemanticVersion> predicate = SemanticVersionPredicateParser.create("1.3.x");
			testTrue(predicate.test(new SemanticVersion("1.3.0", false)));
			testTrue(predicate.test(new SemanticVersion("1.3.99", false)));
			testFalse(predicate.test(new SemanticVersion("1.4.0", false)));
			testFalse(predicate.test(new SemanticVersion("1.2.9", false)));
			testFalse(predicate.test(new SemanticVersion("2.0.0", false)));
		}

		// Test: smaller x-range.
		{
			Predicate<SemanticVersion> predicate = SemanticVersionPredicateParser.create("2.x");
			testTrue(predicate.test(new SemanticVersion("2.0.0", false)));
			testTrue(predicate.test(new SemanticVersion("2.2.4", false)));
			testFalse(predicate.test(new SemanticVersion("1.99.99", false)));
			testFalse(predicate.test(new SemanticVersion("3.0.0", false)));
		}

		// Test: tilde-ranges.
		{
			Predicate<SemanticVersion> predicate = SemanticVersionPredicateParser.create("~1.2.3");
			testTrue(predicate.test(new SemanticVersion("1.2.3", false)));
			testTrue(predicate.test(new SemanticVersion("1.2.4", false)));
			testFalse(predicate.test(new SemanticVersion("1.2.2", false)));
			testFalse(predicate.test(new SemanticVersion("1.3.0", false)));
		}

		{
			Predicate<SemanticVersion> predicate = SemanticVersionPredicateParser.create("~1.2");
			testTrue(predicate.test(new SemanticVersion("1.2.0", false)));
			testTrue(predicate.test(new SemanticVersion("1.2.6", false)));
			testFalse(predicate.test(new SemanticVersion("1.1.9", false)));
			testFalse(predicate.test(new SemanticVersion("1.3.0", false)));
		}

		{
			Predicate<SemanticVersion> predicate = SemanticVersionPredicateParser.create("~1");
			testTrue(predicate.test(new SemanticVersion("1.0.0", false)));
			testTrue(predicate.test(new SemanticVersion("1.1.5", false)));
			testFalse(predicate.test(new SemanticVersion("0.9.9", false)));
			testFalse(predicate.test(new SemanticVersion("3.0.5", false)));
		}

		{
			Predicate<SemanticVersion> predicate = SemanticVersionPredicateParser.create("~1.2.3-beta.2");
			testTrue(predicate.test(new SemanticVersion("1.2.3-beta.2", false)));
			testTrue(predicate.test(new SemanticVersion("1.2.3-rc.7", false)));
			testTrue(predicate.test(new SemanticVersion("1.2.3", false)));
			testTrue(predicate.test(new SemanticVersion("1.2.5", false)));
			testFalse(predicate.test(new SemanticVersion("1.3.0", false)));
			testFalse(predicate.test(new SemanticVersion("1.2.2", false)));
			testFalse(predicate.test(new SemanticVersion("1.2.4-alpha.4", false)));
			testFalse(predicate.test(new SemanticVersion("1.2.3-alpha.4", false)));
		}

		// Test: caret-range.
		{
			Predicate<SemanticVersion> predicate = SemanticVersionPredicateParser.create("^1.2.3");
			testTrue(predicate.test(new SemanticVersion("1.2.3", false)));
			testTrue(predicate.test(new SemanticVersion("1.2.4", false)));
			testTrue(predicate.test(new SemanticVersion("1.3.0", false)));
			testFalse(predicate.test(new SemanticVersion("1.2.2", false)));
			testFalse(predicate.test(new SemanticVersion("2.0.0", false)));
			testFalse(predicate.test(new SemanticVersion("1.2.4-beta.2", false)));
		}

		{
			Predicate<SemanticVersion> predicate = SemanticVersionPredicateParser.create("^0.2.3");
			testTrue(predicate.test(new SemanticVersion("0.2.3", false)));
			testTrue(predicate.test(new SemanticVersion("0.2.4", false)));
			testFalse(predicate.test(new SemanticVersion("0.3.0", false)));
			testFalse(predicate.test(new SemanticVersion("0.2.0", false)));
			testFalse(predicate.test(new SemanticVersion("0.2.8-beta.2", false)));
		}

		{
			Predicate<SemanticVersion> predicate = SemanticVersionPredicateParser.create("^1.2.3-beta.2");
			testTrue(predicate.test(new SemanticVersion("1.2.3-beta.2", false)));
			testTrue(predicate.test(new SemanticVersion("1.2.3-rc.7", false)));
			testTrue(predicate.test(new SemanticVersion("1.2.3", false)));
			testTrue(predicate.test(new SemanticVersion("1.2.5", false)));
			testTrue(predicate.test(new SemanticVersion("1.3.0", false)));
			testFalse(predicate.test(new SemanticVersion("1.2.2", false)));
			testFalse(predicate.test(new SemanticVersion("2.0.0", false)));
			testFalse(predicate.test(new SemanticVersion("1.2.4-alpha.4", false)));
			testFalse(predicate.test(new SemanticVersion("1.2.3-alpha.4", false)));
		}
	}
}
