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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;
import net.fabricmc.loader.impl.util.version.SemanticVersionImpl;

public class VersionNormalizationTest {
	// Expected normalization results, put into the list in
	// sorted order so we can test the version comparison too!
	// Each entry in the list is a list of versions that are
	// equal to each other.
	private List<MinecraftVersion> expectedResults;

	@BeforeEach
	public void setUp() {
		expectedResults = Arrays.asList(
			// Pre-Classic
			new MinecraftVersion("rd-132211", null, "0.0.0-rd.132211"),
			new MinecraftVersion("rd-20090515", null, "0.0.0-rd.150000"),
			// Early Classic (+timestamps)
			new MinecraftVersion("c0.0.11a", null, "0.11"),
			new MinecraftVersion("c0.0.12a_03", null, "0.12.3"),
			new MinecraftVersion("0.0.14a_08", null, "0.14.8"),
			new MinecraftVersion("0.0.19a_06-0137", null, "0.19.6+0137"),
			new MinecraftVersion("0.0.21a-2008", null, "0.21+2008"),
			// Late Classic (+timestamps)
			new MinecraftVersion("c0.24_st", null, "0.24"),
			new MinecraftVersion("c0.24_st_01", null, "0.24.1"),
			new MinecraftVersion("c0.24_st_02-1734", null, "0.24.2+1734"),
			new MinecraftVersion("c0.25_05_st", null, "0.25.5"),
			new MinecraftVersion("0.28", null, "0.28"),
			new MinecraftVersion("0.29_02", null, "0.29.2"),
			new MinecraftVersion()
				.entry("0.30-c", null, "0.30-c")
				.entry("0.30-c-renew", null, "0.30-c+renew"),
			new MinecraftVersion("0.30-s-1858", null, "0.30-s+1858"),
			new MinecraftVersion("c0.30_01c", null, "0.30.1-c"),
			// Indev (date+time vs date-only)
			new MinecraftVersion("in-20091223-1459", null, "0.31.20091223-1459"),
			new MinecraftVersion("Indev 0.31 20091231-2255", null, "0.31.20091231-2255"),
			new MinecraftVersion("Indev 0.31 20100110", null, "0.31.20100110"),
			new MinecraftVersion("in-20100223", null, "0.31.20100223"),
			// Infdev (date+time vs date-only)
			new MinecraftVersion("inf-20100313", null, "0.31.20100313"),
			new MinecraftVersion("Infdev 0.31 20100325-1640", null, "0.31.20100325-1640"),
			new MinecraftVersion("Infdev 0.31 20100414", null, "0.31.20100414"),
			new MinecraftVersion("inf-20100630-1835", null, "0.31.20100630-1835"),
			// Alpha (client vs server, +timestamps)
			new MinecraftVersion("a1.0.1", null, "1.0.0-alpha.0.1"),
			new MinecraftVersion("a1.0.1_01", null, "1.0.0-alpha.0.1.1"),
			new MinecraftVersion("a1.0.5-2149", null, "1.0.0-alpha.0.5+2149"),
			new MinecraftVersion("Alpha 1.0.8_01", null, "1.0.0-alpha.0.8.1"),
			new MinecraftVersion("Alpha 1.0.13_01-1038", null, "1.0.0-alpha.0.13.1+1038"),
			new MinecraftVersion("Alpha v1.0.16_01", null, "1.0.0-alpha.0.16.1"),
			new MinecraftVersion("a0.1.0", null, "1.0.0-alpha.1.0"),
			new MinecraftVersion("Alpha 0.1.1-1707", null, "1.0.0-alpha.1.1+1707"),
			new MinecraftVersion("a1.2.2a", null, "1.0.0-alpha.2.2.a"),
			new MinecraftVersion("Alpha v0.2.8", null, "1.0.0-alpha.2.8"),
			// Beta (test builds, pre-releases, +timestamps)
			new MinecraftVersion("b1.0", null, "1.0.0-beta.0"),
			new MinecraftVersion("b1.0_01", null, "1.0.0-beta.0.1"),
			new MinecraftVersion("b1.3-1647", null, "1.0.0-beta.3+1647"),
			new MinecraftVersion("b1.3b", null, "1.0.0-beta.3.0.b"),
			new MinecraftVersion("b1.6-pre-trailer", "b1.6", "1.0.0-beta.6.0.0"),
			new MinecraftVersion("b1.6-tb3", "b1.6", "1.0.0-beta.6.0.3"),
			new MinecraftVersion("b1.6", null, "1.0.0-beta.6.0.r"),
			new MinecraftVersion("b1.6.1", null, "1.0.0-beta.6.1"),
			new MinecraftVersion("Beta 1.8 Pre-release", "Beta 1.8", "1.0.0-beta.8.0.1"),
			new MinecraftVersion("Beta 1.8 Pre-release 2 ;)", "Beta 1.8", "1.0.0-beta.8.0.2"),
			new MinecraftVersion("Beta 1.8", null, "1.0.0-beta.8.0.r"),
			new MinecraftVersion("Beta 1.8.1", null, "1.0.0-beta.8.1"),
			new MinecraftVersion("Beta v1.9 Prerelease", "Beta v1.9", "1.0.0-beta.9.0.1"),
			new MinecraftVersion("Beta v1.9-pre3-1350", "Beta v1.9", "1.0.0-beta.9.0.3+1350"),
			// 1.0.0 (special release candidates, +timestamps)
			new MinecraftVersion("1.0.0-rc1", "1.0.0", "1.0.0-rc.1"),
			new MinecraftVersion()
				.entry("1.0.0-rc2-1633", "1.0.0", "1.0.0-rc.2+1633")
				.entry("Minecraft RC2", "Minecraft", "1.0.0-rc.2"),
			new MinecraftVersion("1.0.0", "1.0.0", "1.0.0"),
			new MinecraftVersion("1.0.1", "1.0.1", "1.0.1"),
			// 1.2 (snapshots+timestamps, special pre-releases)
			new MinecraftVersion("12w05a-1354", "1.2", "1.2-alpha.12.5.a+1354"),
			new MinecraftVersion("Snapshot 12w07b", "1.2", "1.2-alpha.12.7.b"),
			new MinecraftVersion("1.2-pre", "1.2", "1.2"),
			new MinecraftVersion("1.2.1", "1.2.1", "1.2.1"),
			new MinecraftVersion("1.2.5-pre", "1.2.5", "1.2.5-rc"),
			new MinecraftVersion("1.2.5", "1.2.5", "1.2.5"),
			// 1.3 (special pre-releases)
			new MinecraftVersion("1.3-pre-1249", "1.3", "1.3+1249"),
			new MinecraftVersion("1.3.1-pre", "1.3.1", "1.3.1-rc"),
			new MinecraftVersion("1.3.2-pre", "1.3.2", "1.3.2-rc"),
			// 1.4
			new MinecraftVersion("1.4-pre", "1.4", "1.4"),
			new MinecraftVersion("1.4.1-pre-1338", "1.4.1", "1.4.1+1338"),
			new MinecraftVersion("1.4.2-pre", "1.4.2", "1.4.2-rc"),
			new MinecraftVersion("1.4.3-pre", "1.4.3", "1.4.3"),
			new MinecraftVersion("1.4.4-pre", "1.4.4", "1.4.4-rc"),
			new MinecraftVersion("1.4.5-pre-172128", "1.4.5", "1.4.5-rc+172128"),
			new MinecraftVersion("1.4.6-pre-1428", "1.4.6", "1.4.6-rc+1428"),
			new MinecraftVersion("1.4.7-pre", "1.4.7", "1.4.7-rc"),
			// 1.5
			new MinecraftVersion("1.5-pre-071309", "1.5", "1.5-rc+071309"),
			new MinecraftVersion("13w12~", null, "1.5.1-alpha.13.12.a"),
			new MinecraftVersion("1.5.1-pre-191519", "1.5.1", "1.5.1-rc+191519"),
			// ... 2.0 april fools - forked from 1.5.1
			new MinecraftVersion()
				.entry("2.0", null, "1.5.2-2.0")
				.entry("af-2013-red", null, "1.5.2-2.0+red")
				.entry("2point0_red", null, "1.5.2-2.0+red")
				.entry("af-2013-purple", null, "1.5.2-2.0+purple")
				.entry("2point0_purple", null, "1.5.2-2.0+purple")
				.entry("af-2013-blue", null, "1.5.2-2.0+blue")
				.entry("2point0_blue", null, "1.5.2-2.0+blue"),
			// 1.5.2 (special pre-release)
			new MinecraftVersion("1.5.2-pre-260738", "1.5.2", "1.5.2-rc+260738"),
			// 1.6 (special pre-releases)
			new MinecraftVersion("1.6-pre-1517", "1.6", "1.6+1517"),
			new MinecraftVersion("1.6.1-pre", "1.6.1", "1.6.1-rc"),
			new MinecraftVersion("1.6.2-pre-1426", "1.6.2", "1.6.2-rc+1426"),
			new MinecraftVersion("1.6.3-pre-171231", "1.6.3", "1.6.3+171231"),
			// 1.7 (special pre-releases and normal pre-releases)
			new MinecraftVersion("1.7-pre-1502", "1.7", "1.7+1502"),
			new MinecraftVersion("1.7.1-pre", "1.7.1", "1.7.1"),
			new MinecraftVersion("1.7.3-pre", "1.7.3", "1.7.3"),
			new MinecraftVersion("1.7.4-pre", "1.7.4", "1.7.4-rc"),
			new MinecraftVersion("1.7.6-pre1", "1.7.6", "1.7.6-rc.1"),
			new MinecraftVersion("1.7.10-pre1", "1.7.10", "1.7.10-rc.1"),
			// 2015 april fools
			new MinecraftVersion()
				.entry("15w14a", null, "1.8.4-alpha.15.14.a+loveandhugs")
				.entry("af-2015", null, "1.8.4-alpha.15.14.a+loveandhugs"),
			// 1.8 (special pre-release)
			new MinecraftVersion("1.8.8-pre", "1.8.8", "1.8.8-rc"),
			// 2016 april fools
			new MinecraftVersion()
				.entry("1.RV-Pre1", null, "1.9.2-rv+trendy")
				.entry("af-2016", null, "1.9.2-rv+trendy"),
			// 2019 april fools
			new MinecraftVersion()
				.entry("3D Shareware v1.34", null, "1.14-alpha.19.13.shareware")
				.entry("af-2019", null, "1.14-alpha.19.13.shareware"),
			// 1.14.3 combat test
			new MinecraftVersion()
				.entry("1.14_combat-212796", null, "1.14.3-rc.4.combat.1")
				.entry("1.14.3 - Combat Test", null, "1.14.3-rc.4.combat.1"),
			// 1.14.4 combat test
			new MinecraftVersion()
				.entry("1.14_combat-0", null, "1.14.5-combat.2")
				.entry("Combat Test 2", null, "1.14.5-combat.2"),
			new MinecraftVersion()
				.entry("1.14_combat-3", null, "1.14.5-combat.3")
				.entry("Combat Test 3", null, "1.14.5-combat.3"),
			// 1.15 combat test
			new MinecraftVersion()
				.entry("1.15_combat-1", null, "1.15-rc.3.combat.4")
				.entry("Combat Test 4", null, "1.15-rc.3.combat.4"),
			// 1.15.2 combat test
			new MinecraftVersion()
				.entry("1.15_combat-6", null, "1.15.2-rc.2.combat.5")
				.entry("Combat Test 5", null, "1.15.2-rc.2.combat.5"),
			// 2020 april fools
			new MinecraftVersion()
				.entry("20w14infinite", null, "1.16-alpha.20.13.inf")
				.entry("20w14~", null, "1.16-alpha.20.13.inf")
				.entry("af-2020", null, "1.16-alpha.20.13.inf"),
			// 1.16 (special release candidate)
			new MinecraftVersion("1.16 Release Candidate 1", "1.16", "1.16-rc.9"),
			// from this point on pre-releases are marked 'beta' instead of 'rc'
			new MinecraftVersion("1.16.2-pre1", "1.16.2", "1.16.2-beta.1"),
			// 1.16.2 combat test
			new MinecraftVersion()
				.entry("1.16_combat-0", null, "1.16.2-beta.3.combat.6")
				.entry("Combat Test 6", null, "1.16.2-beta.3.combat.6"),
			// release candidates since 1.16 are marked 'rc'
			new MinecraftVersion("1.16.2-rc1", "1.16.2", "1.16.2-rc.1"),
			// 1.16.3 combat tests
			new MinecraftVersion()
				.entry("1.16_combat-1", null, "1.16.3-combat.7")
				.entry("Combat Test 7", null, "1.16.3-combat.7"),
			new MinecraftVersion("1.16_combat-2", null, "1.16.3-combat.7.b"),
			new MinecraftVersion("1.16_combat-3", null, "1.16.3-combat.7.c"),
			new MinecraftVersion("1.16_combat-4", null, "1.16.3-combat.8"),
			new MinecraftVersion("1.16_combat-5", null, "1.16.3-combat.8.b"),
			new MinecraftVersion("1.16_combat-6", null, "1.16.3-combat.8.c"),
			// 1.18 (experimental snapshots)
			new MinecraftVersion("1.18 Experimental Snapshot 1", "1.18", "1.18-Experimental.1"),
			new MinecraftVersion()
				.entry("1.18 experimental snapshot 2", "1.18", "1.18-Experimental.2")
				.entry("1.18_experimental-snapshot-2", "1.18", "1.18-Experimental.2"),
			new MinecraftVersion("1.18-exp3", "1.18", "1.18-Experimental.3"),
			// 2022 april fools
			new MinecraftVersion()
				.entry("22w13oneBlockAtATime", null, "1.18.3-alpha.22.13.oneblockatatime")
				.entry("22w13oneblockatatime", null, "1.18.3-alpha.22.13.oneblockatatime")
				.entry("af-2022", null, "1.18.3-alpha.22.13.oneblockatatime"),
			// 1.19 (experimental snapshots)
			new MinecraftVersion()
				.entry("1.19 Deep Dark Experimental Snapshot 1", "1.19", "1.19-Experimental.1")
				.entry("1.19_deep_dark_experimental_snapshot-1", "1.19", "1.19-Experimental.1"),
			// 2023 april fools
			new MinecraftVersion("23w13a_or_b", null, "1.20-alpha.23.13.ab"),
			// 2024 april fools
			new MinecraftVersion("24w14potato", null, "1.20.5-alpha.24.12.potato"),
			// 2025 april fools
			new MinecraftVersion("25w14craftmine", null, "1.21.6-alpha.25.14.craftmine")
		);
	}

	@AfterEach
	public void tearDown() {
		expectedResults = null;
	}

	@Test
	public void testGetRelease() {
		for (MinecraftVersion result : expectedResults) {
			for (NormalizedVersion entry : result.entries) {
				Assertions.assertEquals(entry.release, McVersionLookup.getRelease(entry.version), "getRelease(" + entry.version + ")");
			}
		}
	}

	@Test
	public void testNormalizeVersion() {
		for (MinecraftVersion result : expectedResults) {
			for (NormalizedVersion entry : result.entries) {
				Assertions.assertEquals(entry.normalizedVersion, McVersionLookup.normalizeVersion(entry.version, entry.release), "normalizeVersion(" + entry.version + ", " + entry.release + ")");
			}
		}
	}

	@Test
	public void testVersionComparison() throws Exception {
		for (int i = 0; i < expectedResults.size(); i++) {
			MinecraftVersion result1 = expectedResults.get(i);

			for (int j = 0; j < result1.entries.size(); j++) {
				Version v1 = result1.entries.get(j).semver();

				for (int k = 0; k < expectedResults.size(); k++) {
					MinecraftVersion result2 = expectedResults.get(k);

					for (int l = 0; l < result2.entries.size(); l++) {
						Version v2 = result2.entries.get(l).semver();

						if (i == k) {
							Assertions.assertEquals(true, v1.compareTo(v2) == 0, v1.toString() + " == " + v2.toString());
						} else {
							Assertions.assertEquals(i > k, v1.compareTo(v2) > 0, v1.toString() + " > " + v2.toString());
						}
					}
				}
			}
		}
	}

	static class MinecraftVersion {
		private final List<NormalizedVersion> entries = new ArrayList<>();

		MinecraftVersion() {
		}

		MinecraftVersion(String version, String release, String normalizedVersion) {
			this.entry(version, release, normalizedVersion);
		}

		MinecraftVersion entry(String version, String release, String normalizedVersion) {
			this.entries.add(new NormalizedVersion(version, release, normalizedVersion));
			return this;
		}
	}

	static class NormalizedVersion {
		private final String version;
		private final String release;
		private final String normalizedVersion;

		NormalizedVersion(String version, String release, String normalizedVersion) {
			this.version = version;
			this.release = release;
			this.normalizedVersion = normalizedVersion;
		}

		public Version semver() throws VersionParsingException {
			return new SemanticVersionImpl(this.normalizedVersion, false);
		}
	}
}
