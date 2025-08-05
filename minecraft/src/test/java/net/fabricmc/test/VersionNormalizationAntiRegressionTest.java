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

	public static void main(String[] args) {
		try {
			generateInitialJson();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void generateInitialJson() throws IOException {
		Set<MinecraftVersion> minecraftVersions = new TreeSet<>();
		minecraftVersions.addAll(getMojangData());
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
