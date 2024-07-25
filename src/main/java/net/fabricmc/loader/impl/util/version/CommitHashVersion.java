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

package net.fabricmc.loader.impl.util.version;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.lib.gson.JsonReader;

public class CommitHashVersion implements Version {
	private static final Pattern GIT_COMMIT_HASH_PATTERN = Pattern.compile("^[0-9a-fA-F]{40}$");
	private static final Pattern SOURCES_PATTERN = Pattern.compile("^(?:https?://)?github\\.com/([\\w-]+)/([\\w-]+)/?$");

	private final String commitHash;
	private final Instant commitDate;

	public CommitHashVersion(String commitHash, String sources) throws VersionParsingException {
		if (!GIT_COMMIT_HASH_PATTERN.matcher(commitHash).matches()) {
			throw new VersionParsingException("Invalid Git commit hash");
		}

		this.commitHash = commitHash;

		Matcher matcher = SOURCES_PATTERN.matcher(sources);

		if (!matcher.matches()) {
			throw new VersionParsingException("Unsupported or invalid sources link");
		}

		String apiUrlString = String.format("https://api.github.com/repos/%s/%s/git/commits/%s", matcher.group(1), matcher.group(2), this.commitHash);
		URL apiUrl;

		try {
			apiUrl = new URL(apiUrlString);
		} catch (MalformedURLException e) {
			throw new VersionParsingException("Sources URL is malformed");
		}

		String dateString;

		try (JsonReader reader = new JsonReader(new InputStreamReader(apiUrl.openStream()))) {
			reader.beginObject();

			// skip sha, node id, url, HTML url, author
			for (int i = 0; i < 5; i++) {
				reader.nextName();
				reader.skipValue();
			}

			reader.nextName();
			reader.beginObject();

			// skip name, email
			for (int i = 0; i < 2; i++) {
				reader.nextName();
				reader.skipValue();
			}

			reader.nextName();
			dateString = reader.nextString();
		} catch (IOException e) {
			throw new VersionParsingException("Could not connect to GitHub's API");
		}

		Instant date;

		try {
			date = Instant.parse(dateString);
		} catch (DateTimeParseException e) {
			throw new VersionParsingException("Date could not be parsed");
		}

		this.commitDate = date;
	}

	@Override
	public String getFriendlyString() {
		return String.format("%s (%s)", this.commitHash.substring(0, 7), this.commitDate.toString());
	}

	@Override
	public int compareTo(@NotNull Version other) {
		if (!(other instanceof CommitHashVersion)) {
			return this.getFriendlyString().compareTo(other.getFriendlyString());
		}

		return this.commitDate.compareTo(((CommitHashVersion) other).commitDate);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof CommitHashVersion) {
			return this.commitHash.equals(((CommitHashVersion) obj).commitHash);
		}

		return false;
	}

	@Override
	public String toString() {
		return this.getFriendlyString();
	}
}
