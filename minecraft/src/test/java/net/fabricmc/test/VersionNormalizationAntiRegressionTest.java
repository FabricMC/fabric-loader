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

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;

public class VersionNormalizationAntiRegressionTest {
	private static final String MINECRAFT_VERSIONS_RESOURCE = "minecraft_versions.json";
	private static final String MINECRAFT_VERSIONS_JSON = "minecraft/src/test/resources/" + MINECRAFT_VERSIONS_RESOURCE;
	private List<MinecraftVersion> expectedResults;

	@BeforeEach
	public void setup() {
		JsonDeserializer<Instant> instantDeserializer = (json, type, ctx) ->
				Instant.parse(json.getAsString());

		Gson gson = new GsonBuilder()
				.registerTypeAdapter(Instant.class, instantDeserializer)
				.create();

		Type listType = new TypeToken<List<MinecraftVersion>>() {
		}.getType();

		try (Reader reader = new InputStreamReader(VersionNormalizationAntiRegressionTest.class.getClassLoader()
				.getResourceAsStream(MINECRAFT_VERSIONS_RESOURCE))) {
			expectedResults = gson.fromJson(reader, listType);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read in existing versions from json", e);
		}
	}

	@Test
	public void confirmExistingVersions() {
		boolean hasFailure = false;
		List<String[]> failures = new ArrayList<>();

		for (MinecraftVersion expectedResult : expectedResults) {
			String nNormal = McVersionLookup.normalizeVersion(expectedResult.id, McVersionLookup.getRelease(expectedResult.id));

			if (!Objects.equals(nNormal, expectedResult.normalized)) {
				hasFailure = true;
				failures.add(new String[] {expectedResult.id, expectedResult.normalized, nNormal});
			}
		}

		assertFalse(hasFailure, () -> {
			StringBuilder sb = new StringBuilder("The following versions differ from what they were before:");

			for (String[] failure : failures) {
				sb.append('\n');
				sb.append('\t');
				sb.append("id: ").append(failure[0]).append('\t');
				sb.append("expected: ").append(failure[1]).append('\t');
				sb.append("actual: ").append(failure[2]).append('\t');
			}

			return sb.toString();
		});
	}

	/**
	 * Confirms that no two versions are considered equal.
	 */
	@Test
	public void confirmAllUnique() throws VersionParsingException {
		boolean hasFailure = false;
		List<String[]> failures = new ArrayList<>();
		List<String> failedIds = new ArrayList<>();
		Set<Set<String>> duplicated = getRereleasedVersions();

		for (MinecraftVersion expectedResult : expectedResults) {
			Version v1 = Version.parse(expectedResult.normalized);

			inner: for (MinecraftVersion expectedResult2 : expectedResults) {
				if (expectedResult2.equals(expectedResult)) {
					continue;
				}

				Version v2 = Version.parse(expectedResult2.normalized);

				if (v1.compareTo(v2) == 0) {
					// OmniArchive gives re-released versions different ids,
					// but they should normalize to the same version
					for (Set<String> dupes : duplicated) {
						if (dupes.contains(expectedResult.id) && dupes.contains(expectedResult2.id)) {
							continue inner;
						}
					}

					hasFailure = true;

					if (!failedIds.contains(expectedResult.id) && !failedIds.contains(expectedResult2.id)) {
						failures.add(new String[] {expectedResult.id, expectedResult.normalized, expectedResult2.id, expectedResult2.normalized});
						failedIds.add(expectedResult.id);
						failedIds.add(expectedResult2.id);
					}
				}
			}
		}

		assertFalse(hasFailure, () -> {
			StringBuilder sb = new StringBuilder("The following versions compare as equivalent:");

			for (String[] failure : failures) {
				sb.append('\n');
				sb.append('\t');
				sb.append("id1: ").append(failure[0]).append('\t');
				sb.append("normalized1: ").append(failure[1]).append('\t');
				sb.append("id2: ").append(failure[2]).append('\t');
				sb.append("normalized2: ").append(failure[3]).append('\t');
			}

			return sb.toString();
		});
	}

	/**
	 * Manually handle rereleased versions.
	 */
	private Set<Set<String>> getRereleasedVersions() {
		Set<Set<String>> vs = new HashSet<>();

		vs.add(new HashSet<>(Arrays.asList("1.16", "1.16-231620", "1.16-221349")));
		vs.add(new HashSet<>(Arrays.asList("1.7.7", "1.7.7-091529", "1.7.7-101331")));
		vs.add(new HashSet<>(Arrays.asList("1.6.2", "1.6.2-080933", "1.6.2-091847")));
		vs.add(new HashSet<>(Arrays.asList("1.0", "1.0.0")));
		vs.add(new HashSet<>(Arrays.asList("c0.0.11a", "c0.0.11a-launcher")));
		vs.add(new HashSet<>(Arrays.asList("c0.0.12a_03-192349", "c0.0.12a_03-200018")));
		vs.add(new HashSet<>(Arrays.asList("c0.0.13a_03", "c0.0.13a_03-launcher")));
		vs.add(new HashSet<>(Arrays.asList("c0.0.14a_04-1735", "c0.0.14a_04-1743")));
		vs.add(new HashSet<>(Arrays.asList("c0.0.14a_05-1748", "c0.0.14a_05-1752")));
		vs.add(new HashSet<>(Arrays.asList("c0.0.13a", "c0.0.13a-launcher")));
		vs.add(new HashSet<>(Arrays.asList("c0.0.15a-05311904", "c0.0.15a-06031816", "c0.0.15a-06031828",
				"c0.0.15a-06031900", "c0.0.15a-06031950", "c0.0.15a-06041651", "c0.0.15a-06041658", "c0.0.15a-06041703")));
		vs.add(new HashSet<>(Arrays.asList("c0.0.16a_02-071841", "c0.0.16a_02-081026", "c0.0.16a_02-081036",
				"c0.0.16a_02-081047", "c0.0.16a_02-081722", "c0.0.16a_02-081736", "c0.0.16a_02-081855")));
		vs.add(new HashSet<>(Arrays.asList("c0.0.17a-1945", "c0.0.17a-2014")));
		vs.add(new HashSet<>(Arrays.asList("c0.0.19a_06-0132", "c0.0.19a_06-0137")));
		vs.add(new HashSet<>(Arrays.asList("c0.0.21a-1951", "c0.0.21a-2008")));
		vs.add(new HashSet<>(Arrays.asList("c0.0.22a-2154", "c0.0.22a-2158")));
		vs.add(new HashSet<>(Arrays.asList("c0.24_st_02-1734", "c0.24_st_02-1742")));
		vs.add(new HashSet<>(Arrays.asList("c0.25_st-1613", "c0.25_st-1615", "c0.25_st-1626", "c0.25_st-1658")));
		vs.add(new HashSet<>(Arrays.asList("c0.30-c-1821", "c0.30-c-1900", "c0.30-c-1900-renew")));
		vs.add(new HashSet<>(Arrays.asList("c0.30-s-1849", "c0.30-s-1858")));
		vs.add(new HashSet<>(Arrays.asList("a1.0.4", "a1.0.4-launcher")));
		vs.add(new HashSet<>(Arrays.asList("a1.0.5-2133", "a1.0.5-2149")));
		vs.add(new HashSet<>(Arrays.asList("a1.0.13_01-1038", "a1.0.13_01-1444")));
		vs.add(new HashSet<>(Arrays.asList("a1.0.14", "a1.0.14-1603", "a1.0.14-1659", "a1.0.14-1659-launcher")));
		vs.add(new HashSet<>(Arrays.asList("a1.1.0", "a1.1.0-101840", "a1.1.0-101847", "a1.1.0-101847-launcher", "a1.1.0-131933")));
		vs.add(new HashSet<>(Arrays.asList("a1.2.0", "a1.2.0-2051", "a1.2.0-2057")));
		vs.add(new HashSet<>(Arrays.asList("a1.2.0_02", "a1.2.0_02-launcher")));
		vs.add(new HashSet<>(Arrays.asList("a1.2.2-1613", "a1.2.2-1624", "a1.2.2-1938")));
		vs.add(new HashSet<>(Arrays.asList("a1.2.3_01", "a1.2.3_01-0956", "a1.2.3_01-0958")));
		vs.add(new HashSet<>(Arrays.asList("b1.0.2", "b1.0.2-0836")));
		vs.add(new HashSet<>(Arrays.asList("b1.1-1245", "b1.1-1255")));
		vs.add(new HashSet<>(Arrays.asList("b1.2_02", "b1.2_02-launcher")));
		vs.add(new HashSet<>(Arrays.asList("b1.3-1647", "b1.3-1713", "b1.3-1733", "b1.3-1750")));
		vs.add(new HashSet<>(Arrays.asList("b1.4", "b1.4-1507", "b1.4-1634")));
		vs.add(new HashSet<>(Arrays.asList("23w13a_or_b_original", "23w13a_or_b")));
		vs.add(new HashSet<>(Arrays.asList("24w14potato_original", "24w14potato")));

		return vs;
	}

	public static void main(String[] args) {
		try {
			generateInitialJson();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void generateInitialJson() throws IOException {
		Set<MinecraftVersion> minecraftVersions = new TreeSet<MinecraftVersion>() {
			final Set<String> ids = new HashSet<>();

			@Override
			public boolean add(MinecraftVersion minecraftVersion) {
				if (ids.contains(minecraftVersion.id)) {
					return false;
				}

				ids.add(minecraftVersion.id);
				return super.add(minecraftVersion);
			}
		};
		minecraftVersions.addAll(getMojangData());
		minecraftVersions.addAll(getFabricMirror());
		minecraftVersions.addAll(getOmniArchiveData());

		JsonSerializer<Instant> instantSerializer = (src, typeOfSrc, context) ->
				new JsonPrimitive(src.toString());

		Gson gson = new GsonBuilder()
				.setPrettyPrinting()
				.registerTypeAdapter(Instant.class, instantSerializer)
				.create();

		Type listType = new TypeToken<List<MinecraftVersion>>() {
		}.getType();
		String json = gson.toJson(minecraftVersions, listType);

		System.out.println(json);

		Files.write(new File(MINECRAFT_VERSIONS_JSON).toPath(),
				json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
	}

	private static List<MinecraftVersion> getMojangData() throws MalformedURLException {
		String manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
		URL url = new URL(manifestUrl);

		JsonDeserializer<OffsetDateTime> deserializer = (json, typeOfT, context) ->
				OffsetDateTime.parse(json.getAsString());

		Gson gson = new GsonBuilder()
				.registerTypeAdapter(OffsetDateTime.class, deserializer)
				.create();

		List<MinecraftVersion> minecraftVersions = new ArrayList<>();

		try (Reader in = new InputStreamReader(url.openStream())) {
			PistonMetaV2 pistonMetaV2 = gson.fromJson(in, PistonMetaV2.class);
			pistonMetaV2.versions.forEach(v -> {
				minecraftVersions.add(new MinecraftVersion(v.id, v.releaseTime.toInstant()));
			});
		} catch (IOException e) {
			throw new RuntimeException("Failed to read PistonV2 meta", e);
		}

		return minecraftVersions;
	}

	private static List<MinecraftVersion> getFabricMirror() throws MalformedURLException {
		String manifestUrl = "https://maven.fabricmc.net/net/minecraft/experimental_versions.json";
		URL url = new URL(manifestUrl);

		JsonDeserializer<OffsetDateTime> deserializer = (json, typeOfT, context) ->
				OffsetDateTime.parse(json.getAsString());

		Gson gson = new GsonBuilder()
				.registerTypeAdapter(OffsetDateTime.class, deserializer)
				.create();

		List<MinecraftVersion> minecraftVersions = new ArrayList<>();

		try (Reader in = new InputStreamReader(url.openStream())) {
			FabricMeta fabricMeta = gson.fromJson(in, FabricMeta.class);
			fabricMeta.versions.forEach(v -> {
				minecraftVersions.add(new MinecraftVersion(v.id, v.releaseTime.toInstant()));
			});
		} catch (IOException e) {
			throw new RuntimeException("Failed to read PistonV2 meta", e);
		}

		return minecraftVersions;
	}

	/**
	 * <a href="https://docs.google.com/spreadsheets/d/1OCxMNQLeZJi4BlKKwHx2OlzktKiLEwFXnmCrSdAFwYQ/htmlview#gid=872531987">Google Sheet</a>.
	 * <a href="https://docs.google.com/spreadsheets/d/1OCxMNQLeZJi4BlKKwHx2OlzktKiLEwFXnmCrSdAFwYQ/export?gid=872531987&format=csv">Direct CSV download for "Java Clients (Full)" sheet</a>.
	 * <a href="https://docs.google.com/spreadsheets/d/1OCxMNQLeZJi4BlKKwHx2OlzktKiLEwFXnmCrSdAFwYQ/gviz/tq?gid=872531987&tqx=out:csv&headers=0&tq=SELECT%20B%2C%20E">Direct CSV download for "Java Clients (Full)" sheet, just columns B and E, ignoring headers</a>.
	 */
	private static List<MinecraftVersion> getOmniArchiveData() throws MalformedURLException {
		URL url = new URL("https://docs.google.com/spreadsheets/d/1OCxMNQLeZJi4BlKKwHx2OlzktKiLEwFXnmCrSdAFwYQ/gviz/tq?gid=872531987&tqx=out:csv&headers=0&tq=SELECT%20B%2C%20E");

		List<MinecraftVersion> versions = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
			String line;

			while ((line = reader.readLine()) != null) {
				List<String> fields = parseCsvLine(line);

				if (fields.size() != 2) {
					throw new IllegalStateException("Incorrect data: " + line);
				}

				String id = fields.get(0);
				String date = fields.get(1);

				// Strip quotes
				id = id.replaceAll("\"", "");
				date = date.replaceAll("\"", "");

				// Clean data for parsing
				date = date.replaceAll("([1-9])x", "$10").replaceAll("0x", "01");

				if (id.isEmpty() || id.equals("ID") || date.equals("Released")) {
					// OmniArchive for some reason duplicates the headers into the data
					continue;
				}

				versions.add(new MinecraftVersion(id, LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC)));
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to read OmniArchive data", e);
		}

		return versions;
	}

	private static List<String> parseCsvLine(String line) {
		// Let's just do the simple parsing for this, it isn't run often anyways
		return Arrays.asList(line.split(","));
	}

	/**
	 * <a href="https://piston-meta.mojang.com/mc/game/version_manifest_v2.json">Metadata</a>.
	 */
	private static class PistonMetaV2 {
		List<Version> versions;

		static class Version {
			String id;
			OffsetDateTime releaseTime;
		}
	}

	/**
	 * Versions not in Mojang's data that fabric mirrors
	 * <a href="https://maven.fabricmc.net/net/minecraft/experimental_versions.json">Metadata</a>.
	 */
	private static class FabricMeta {
		List<Version> versions;

		static class Version {
			String id;
			OffsetDateTime releaseTime;
		}
	}

	private static class MinecraftVersion implements Comparable<MinecraftVersion> {
		String id;
		String normalized;
		Instant releaseTime;

		MinecraftVersion(String id, Instant releaseTime) {
			this(id, McVersionLookup.normalizeVersion(id, McVersionLookup.getRelease(id)), releaseTime);
		}

		MinecraftVersion(String id, String normalized, Instant releaseTime) {
			Objects.requireNonNull(id);
			Objects.requireNonNull(releaseTime);
			this.id = id;
			this.normalized = normalized;
			this.releaseTime = releaseTime;
		}

		@Override
		public int compareTo(MinecraftVersion o) {
			int c = id.compareTo(o.id);

			if (c == 0) {
				return 0;
			}

			c = releaseTime.compareTo(o.releaseTime);

			if (c != 0) {
				return c;
			}

			if (normalized != null && o.normalized != null) {
				try {
					c = SemanticVersion.parse(normalized).compareTo((Version) SemanticVersion.parse(o.normalized));
				} catch (VersionParsingException ignored) {
					// NO-OP
				}

				if (c != 0) {
					return c;
				}
			}

			return id.compareTo(o.id);
		}

		@Override
		public final boolean equals(Object selectable) {
			if (!(selectable instanceof MinecraftVersion)) return false;

			MinecraftVersion that = (MinecraftVersion) selectable;
			return id.equals(that.id);
		}

		@Override
		public int hashCode() {
			return id.hashCode();
		}
	}
}
