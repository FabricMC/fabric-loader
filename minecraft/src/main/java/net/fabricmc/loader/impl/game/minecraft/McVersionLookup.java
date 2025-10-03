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

package net.fabricmc.loader.impl.game.minecraft;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.lib.gson.JsonReader;
import net.fabricmc.loader.impl.lib.gson.JsonToken;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.SimpleClassPath;
import net.fabricmc.loader.impl.util.SimpleClassPath.CpEntry;
import net.fabricmc.loader.impl.util.version.SemanticVersionImpl;
import net.fabricmc.loader.impl.util.version.VersionPredicateParser;

public final class McVersionLookup {
	private static final Pattern RELEASE_PATTERN = Pattern.compile("(1\\.(\\d+)(?:\\.(\\d+))?)(?:-(\\d+))?"); // 1.6, 1.16.5, 1,16+231620
	private static final Pattern TEST_BUILD_PATTERN = Pattern.compile(".+(?:-tb| Test Build )(\\d+)?(?:-(\\d+))?"); // ... Test Build 1, ...-tb2, ...-tb3-1234
	private static final Pattern PRE_RELEASE_PATTERN = Pattern.compile(".+(?:-pre| Pre-?[Rr]elease ?)(?:(\\d+)(?: ;\\))?)?(?:-(\\d+))?"); // ... Prerelease, ... Pre-release 1, ... Pre-Release 2, ...-pre3, ...-pre4-1234
	private static final Pattern RELEASE_CANDIDATE_PATTERN = Pattern.compile(".+(?:-rc| RC| [Rr]elease Candidate )(\\d+)(?:-(\\d+))?"); // ... RC1, ... Release Candidate 2, ...-rc3, ...-rc4-1234
	private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("(?:Snapshot )?(\\d+)w0?(0|[1-9]\\d*)([a-z])(?:-(\\d+))?"); // Snapshot 16w02a, 20w13b, 22w18c-1234
	private static final Pattern EXPERIMENTAL_PATTERN = Pattern.compile(".+(?:-exp|(?:_deep_dark)?_experimental[_-]snapshot-|(?: Deep Dark)? [Ee]xperimental [Ss]napshot )(\\d+)"); // 1.18 Experimental Snapshot 1, 1.18_experimental-snapshot-2, 1.18-exp3, 1.19 Deep Dark Experimental Snapshot 1
	private static final Pattern BETA_PATTERN = Pattern.compile("(?:b|Beta v?)1\\.((\\d+)(?:\\.(\\d+))?(_0\\d)?)([a-z])?(?:-(\\d+))?(?:-(launcher))?"); // Beta 1.2, b1.2_02-launcher, b1.3b, b1.3-1731, Beta v1.5_02, b1.8.1
	private static final Pattern ALPHA_PATTERN = Pattern.compile("(?:(?:server-)?a|Alpha v?)[01]\\.(\\d+\\.\\d+(?:_0\\d)?)([a-z])?(?:-(\\d+))?(?:-(launcher))?"); // Alpha v1.0.1, Alpha 1.0.1_01, a1.0.4-launcher, a1.1.0-131933, a1.2.2a, a1.2.3_05, Alpha 0.1.0, server-a0.2.8
	private static final Pattern INDEV_PATTERN = Pattern.compile("(?:inf?-|Inf?dev )(?:0\\.31 )?(\\d+)(?:-(\\d+))?"); // Indev 0.31 200100110, in-20100124-2310, Infdev 0.31 20100227-1433, inf-20100611
	private static final Pattern CLASSIC_SERVER_PATTERN = Pattern.compile("(?:(?:server-)?c)1\\.(\\d\\d?(?:\\.\\d)?)(?:-(\\d+))?"); // c1.0, server-c1.3, server-c1.5-1301, c1.8.1, c1.10.1
	private static final Pattern LATE_CLASSIC_PATTERN = Pattern.compile("(?:c?0\\.)(\\d\\d?)(?:_0(\\d))?(?:_st)?(?:_0(\\d))?([a-z])?(?:-([cs]))?(?:-(\\d+))?(?:-(renew))?"); // c0.24_st, 0.24_st_03, 0.25_st-1658, c0.25_05_st, 0.29, c0.30-s, 0.30-c-renew, c0.30_01c
	private static final Pattern EARLY_CLASSIC_PATTERN = Pattern.compile("(?:c?0\\.0\\.)(\\d\\d?)a(?:_0(\\d))?(?:-(\\d+))?(?:-(launcher))?"); // c0.0.11a, c0.0.13a_03-launcher, c0.0.17a-2014, 0.0.18a_02
	private static final Pattern PRE_CLASSIC_PATTERN = Pattern.compile("(?:rd|pc)-(\\d+)(?:-(launcher))?"); // rd-132211, pc-132011-launcher
	private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("(.+)(?:-(\\d+))");
	private static final String STRING_DESC = "Ljava/lang/String;";
	private static final Pattern VERSION_PATTERN = Pattern.compile(
			PRE_CLASSIC_PATTERN.pattern()
			+ "|" + EARLY_CLASSIC_PATTERN.pattern()
			+ "|" + LATE_CLASSIC_PATTERN.pattern()
			+ "|" + CLASSIC_SERVER_PATTERN.pattern()
			+ "|" + INDEV_PATTERN.pattern()
			+ "|" + ALPHA_PATTERN.pattern()
			+ "|" + BETA_PATTERN.pattern()
				+ "(" + TEST_BUILD_PATTERN.pattern().substring(2) + ")?"
				+ "(" + PRE_RELEASE_PATTERN.pattern().substring(2) + ")?"
				+ "(" + RELEASE_CANDIDATE_PATTERN.pattern().substring(2) + ")?"
			+ "|" + RELEASE_PATTERN.pattern()
				+ "(" + TEST_BUILD_PATTERN.pattern().substring(2) + ")?"
				+ "(" + PRE_RELEASE_PATTERN.pattern().substring(2) + ")?"
				+ "(" + RELEASE_CANDIDATE_PATTERN.pattern().substring(2) + ")?"
				+ "(" + EXPERIMENTAL_PATTERN.pattern().substring(2) + ")?"
			+ "|" + SNAPSHOT_PATTERN.pattern()
			+ "|" + "[Cc]ombat(?: Test )?\\d[a-z]?" // combat snapshots
			+ "|" + "Minecraft RC\\d" // special case for 1.0.0 release candidates
			+ "|" + "2.0|1\\.RV-Pre1|3D Shareware v1\\.34|20w14~|22w13oneBlockAtATime|23w13a_or_b|24w14potato|25w14craftmine" // odd exceptions
				+ "(" + TIMESTAMP_PATTERN.pattern() + ")?"
	);

	public static McVersion getVersion(List<Path> gameJars, String entrypointClass, String versionName) {
		McVersion.Builder builder = new McVersion.Builder();

		if (versionName != null) {
			builder.setNameAndRelease(versionName);
		}

		try (SimpleClassPath cp = new SimpleClassPath(gameJars)) {
			// Determine class version
			if (entrypointClass != null) {
				try (InputStream is = cp.getInputStream(LoaderUtil.getClassFileName(entrypointClass))) {
					DataInputStream dis = new DataInputStream(is);

					if (dis.readInt() == 0xCAFEBABE) {
						dis.readUnsignedShort();
						builder.setClassVersion(dis.readUnsignedShort());
					}
				}
			}

			// Check various known files for version information if unknown
			if (versionName == null) {
				fillVersionFromJar(cp, builder);
			}
		} catch (IOException e) {
			throw ExceptionUtil.wrap(e);
		}

		return builder.build();
	}

	public static McVersion getVersionExceptClassVersion(Path gameJar) {
		McVersion.Builder builder = new McVersion.Builder();

		try (SimpleClassPath cp = new SimpleClassPath(Collections.singletonList(gameJar))) {
			fillVersionFromJar(cp, builder);
		} catch (IOException e) {
			throw ExceptionUtil.wrap(e);
		}

		return builder.build();
	}

	public static void fillVersionFromJar(SimpleClassPath cp, McVersion.Builder builder) {
		try {
			InputStream is;

			// version.json - contains version and target release for 18w47b+
			if ((is = cp.getInputStream("version.json")) != null && fromVersionJson(is, builder)) {
				return;
			}

			// constant field RealmsSharedConstants.VERSION_STRING
			if ((is = cp.getInputStream("net/minecraft/realms/RealmsSharedConstants.class")) != null && fromAnalyzer(is, new FieldStringConstantVisitor("VERSION_STRING"), builder)) {
				return;
			}

			// constant return value of RealmsBridge.getVersionString (presumably inlined+dead code eliminated VERSION_STRING)
			if ((is = cp.getInputStream("net/minecraft/realms/RealmsBridge.class")) != null && fromAnalyzer(is, new MethodConstantRetVisitor("getVersionString"), builder)) {
				return;
			}

			// version-like String constant used in MinecraftServer.run or another MinecraftServer method
			if ((is = cp.getInputStream("net/minecraft/server/MinecraftServer.class")) != null && fromAnalyzer(is, new MethodConstantVisitor("run"), builder)) {
				return;
			}

			CpEntry entry = cp.getEntry("net/minecraft/client/Minecraft.class");

			if (entry != null) {
				// version-like constant return value of a Minecraft method (obfuscated/unknown name)
				if (fromAnalyzer(entry.getInputStream(), new MethodConstantRetVisitor(null), builder)) {
					return;
				}

				// version-like constant passed into Display.setTitle in a Minecraft method (obfuscated/unknown name)
				if (fromAnalyzer(entry.getInputStream(), new MethodStringConstantContainsVisitor("org/lwjgl/opengl/Display", "setTitle"), builder)) {
					return;
				}
			}

			// classic: version-like String constant used in Minecraft.init, Minecraft referenced by field in MinecraftApplet
			String type;

			if (((is = cp.getInputStream("net/minecraft/client/MinecraftApplet.class")) != null || (is = cp.getInputStream("com/mojang/minecraft/MinecraftApplet.class")) != null)
					&& (type = analyze(is, new FieldTypeCaptureVisitor())) != null
					&& (is = cp.getInputStream(type.concat(".class"))) != null
					&& fromAnalyzer(is, new MethodConstantVisitor("init"), builder)) {
				return;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		builder.setFromFileName(cp.getPaths().get(0).getFileName().toString());
	}

	private static boolean fromVersionJson(InputStream is, McVersion.Builder builder) {
		try (JsonReader reader = new JsonReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			String id = null;
			String name = null;
			String release = null;

			reader.beginObject();

			while (reader.hasNext()) {
				switch (reader.nextName()) {
				case "id":
					if (reader.peek() != JsonToken.STRING) {
						throw new IOException("\"id\" in version json must be a string");
					}

					id = reader.nextString();
					break;
				case "name":
					if (reader.peek() != JsonToken.STRING) {
						throw new IOException("\"name\" in version json must be a string");
					}

					name = reader.nextString();
					break;
				case "release_target":
					if (reader.peek() != JsonToken.STRING) {
						throw new IOException("\"release_target\" in version json must be a string");
					}

					release = reader.nextString();
					break;
				default:
					// There is typically other stuff in the file, just ignore anything we don't know
					reader.skipValue();
				}
			}

			reader.endObject();

			String version;

			if (name == null
					|| id != null && id.length() < name.length()) {
				version = id;
			} else {
				version = name;
			}

			if (version == null) return false;

			builder.setId(id);
			builder.setName(name);

			if (release == null) {
				builder.setNameAndRelease(version);
			} else {
				builder.setVersion(version);
				builder.setRelease(release);
			}

			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return false;
	}

	private static <T extends ClassVisitor & Analyzer> boolean fromAnalyzer(InputStream is, T analyzer, McVersion.Builder builder) {
		String result = analyze(is, analyzer);

		if (result != null) {
			builder.setNameAndRelease(result);
			return true;
		} else {
			return false;
		}
	}

	private static <T extends ClassVisitor & Analyzer> String analyze(InputStream is, T analyzer) {
		try {
			ClassReader cr = new ClassReader(is);
			cr.accept(analyzer, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

			return analyzer.getResult();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				// ignored
			}
		}

		return null;
	}

	@VisibleForTesting
	public static String getRelease(String version) {
		// 1.6, 1.16.5, 1,16+231620
		Matcher matcher = RELEASE_PATTERN.matcher(version);

		if (matcher.matches()) {
			// return name without timestamp
			return matcher.group(1);
		}

		// version ids as found in versions manifest
		// ... as in 1.19_deep_dark_experimental_snapshot-1
		int pos = version.indexOf("_deep_dark_experimental_snapshot-");
		if (pos >= 0) return version.substring(0, pos);

		// ... as in 1.18_experimental-snapshot-1
		pos = version.indexOf("_experimental-snapshot-");
		if (pos >= 0) return version.substring(0, pos);

		// ... as in 1.18-exp1
		pos = version.indexOf("-exp");
		if (pos >= 0) return version.substring(0, pos);

		// ... as in b1.6-tb3
		pos = version.indexOf("-tb");
		if (pos >= 0) return version.substring(0, pos);

		// ... as in 1.21.6-pre1
		pos = version.indexOf("-pre");
		if (pos >= 0) return version.substring(0, pos);

		// ... as in 1.21.6-rc1
		pos = version.indexOf("-rc");
		if (pos >= 0) return version.substring(0, pos);

		// version names as extracted from the jar
		// ... as in 1.19 Deep Dark Experimental Snapshot 1
		pos = version.indexOf(" Deep Dark Experimental Snapshot");
		if (pos >= 0) return version.substring(0, pos);

		// ... as in 1.18 Experimental Snapshot 1
		pos = version.indexOf(" Experimental Snapshot");
		if (pos >= 0) return version.substring(0, pos);

		// ... as in 1.18 experimental snapshot 2
		pos = version.indexOf(" experimental snapshot ");
		if (pos >= 0) return version.substring(0, pos);

		// ... as in Beta 1.6 Test Build 3
		pos = version.indexOf(" Test Build");
		if (pos >= 0) return version.substring(0, pos);

		// ... as in 1.21.6 Pre-Release 1
		pos = version.indexOf(" Pre-Release");
		if (pos >= 0) return version.substring(0, pos);

		// ... as in Beta 1.8 Pre-release 1
		pos = version.indexOf(" Pre-release");
		if (pos >= 0) return version.substring(0, pos);

		// ... as in Beta 1.9 Prerelease 2
		pos = version.indexOf(" Prerelease");
		if (pos >= 0) return version.substring(0, pos);

		// ... as in Minecraft RC1
		pos = version.indexOf(" RC");
		if (pos >= 0) return version.substring(0, pos);

		// ... as in 1.21.6 Release Candidate 1
		pos = version.indexOf(" Release Candidate");
		if (pos >= 0) return version.substring(0, pos);

		matcher = SNAPSHOT_PATTERN.matcher(version); // Snapshot 16w02a, 20w13b, 22w18c-1234

		if (matcher.matches()) {
			int year = Integer.parseInt(matcher.group(1));
			int week = Integer.parseInt(matcher.group(2));

			if (year == 25 && week >= 31 || year > 25) {
				return "1.21.9";
			} else if (year == 25 && week >= 15 && week <= 21) {
				return "1.21.6";
			} else if (year == 25 && week >= 2 && week <= 10) {
				return "1.21.5";
			} else if (year == 24 && week >= 44) {
				return "1.21.4";
			} else if (year == 24 && week >= 33 && week <= 40) {
				return "1.21.2";
			} else if (year == 24 && week >= 18 && week <= 21) {
				return "1.21";
			} else if (year == 23 && week >= 51 || year == 24 && week <= 14) {
				return "1.20.5";
			} else if (year == 23 && week >= 40 && week <= 46) {
				return "1.20.3";
			} else if (year == 23 && week >= 31 && week <= 35) {
				return "1.20.2";
			} else if (year == 23 && week >= 12 && week <= 18) {
				return "1.20";
			} else if (year == 23 && week <= 7) {
				return "1.19.4";
			} else if (year == 22 && week >= 42) {
				return "1.19.3";
			} else if (year == 22 && week == 24) {
				return "1.19.1";
			} else if (year == 22 && week >= 11 && week <= 19) {
				return "1.19";
			} else if (year == 22 && week >= 3 && week <= 7) {
				return "1.18.2";
			} else if (year == 21 && week >= 37 && week <= 44) {
				return "1.18";
			} else if (year == 20 && week >= 45 || year == 21 && week <= 20) {
				return "1.17";
			} else if (year == 20 && week >= 27 && week <= 30) {
				return "1.16.2";
			} else if (year == 20 && week >= 6 && week <= 22) {
				return "1.16";
			} else if (year == 19 && week >= 34) {
				return "1.15";
			} else if (year == 18 && week >= 43 || year == 19 && week <= 14) {
				return "1.14";
			} else if (year == 18 && week >= 30 && week <= 33) {
				return "1.13.1";
			} else if (year == 17 && week >= 43 || year == 18 && week <= 22) {
				return "1.13";
			} else if (year == 17 && week == 31) {
				return "1.12.1";
			} else if (year == 17 && week >= 6 && week <= 18) {
				return "1.12";
			} else if (year == 16 && week == 50) {
				return "1.11.1";
			} else if (year == 16 && week >= 32 && week <= 44) {
				return "1.11";
			} else if (year == 16 && week >= 20 && week <= 21) {
				return "1.10";
			} else if (year == 16 && week >= 14 && week <= 15) {
				return "1.9.3";
			} else if (year == 15 && week >= 31 || year == 16 && week <= 7) {
				return "1.9";
			} else if (year == 14 && week >= 2 && week <= 34) {
				return "1.8";
			} else if (year == 13 && week >= 47 && week <= 49) {
				return "1.7.3";
			} else if (year == 13 && week >= 36 && week <= 43) {
				return "1.7";
			} else if (year == 13 && week >= 16 && week <= 26) {
				return "1.6";
			} else if (year == 13 && week >= 11 && week <= 12) {
				return "1.5.1";
			} else if (year == 13 && week >= 1 && week <= 10) {
				return "1.5";
			} else if (year == 12 && week >= 49 && week <= 50) {
				return "1.4.6";
			} else if (year == 12 && week >= 32 && week <= 42) {
				return "1.4";
			} else if (year == 12 && week >= 15 && week <= 30) {
				return "1.3";
			} else if (year == 12 && week >= 3 && week <= 8) {
				return "1.2";
			} else if (year == 11 && week >= 47 || year == 12 && week <= 1) {
				return "1.1";
			}
		}

		return null;
	}

	private static boolean isProbableVersion(String str) {
		return VERSION_PATTERN.matcher(str).matches();
	}

	/**
	 * Returns the probable version contained in the given string, or null if the string doesn't contain a version.
	 */
	private static String findProbableVersion(String str) {
		Matcher matcher = VERSION_PATTERN.matcher(str);

		if (matcher.find()) {
			return matcher.group();
		} else {
			return null;
		}
	}

	/**
	 * Convert an arbitrary MC version into semver-like release-preRelease form.
	 *
	 * <p>MC Snapshot -> alpha, MC Pre-Release -> rc.
	 */
	@VisibleForTesting
	public static String normalizeVersion(String name, String release) {
		if (release == null || name.equals(release)) {
			String ret = normalizeSpecialVersion(name);
			return ret != null ? ret : normalizeVersion(name);
		}

		String normalizedRelease = normalizeVersion(release);
		// timestamps distinguish between re-uploads of the same version
		String timestamp = null;

		StringBuilder ret = new StringBuilder();
		ret.append(normalizedRelease);
		ret.append('-');

		Matcher matcher;

		if ((matcher = RELEASE_PATTERN.matcher(name)).matches()) { // 1.6, 1.16.5, 1.16+131620
			timestamp = matcher.group(4);

			// remove - separator
			ret.setLength(ret.length() - 1);
		} else if ((matcher = EXPERIMENTAL_PATTERN.matcher(name)).matches()) { // 1.18 Experimental Snapshot 1, 1.18 experimental snapshot 2, 1.18-exp3
			ret.append("Experimental.");
			ret.append(matcher.group(1)); // exp build nr
		} else if (name.startsWith(release)) {
			if ((matcher = RELEASE_CANDIDATE_PATTERN.matcher(name)).matches()) { // ... RC1, ... Release Candidate 2, ...-rc3, ...-rc4-1234
				String rcBuild = matcher.group(1);
				timestamp = matcher.group(2);

				// 1.0.0 release candidates are simply known as eg. 'Minecraft RC1' in the jar
				if (release.equals("Minecraft")) {
					ret.replace(0, "Minecraft".length(), "1.0.0");
				// This is a hack to fake 1.16's new release candidates to follow on from the 8 pre releases.
				} else if (release.equals("1.16")) {
					int build = Integer.parseInt(rcBuild);
					rcBuild = Integer.toString(8 + build);
				}

				ret.append("rc.");
				ret.append(rcBuild);
			} else if ((matcher = PRE_RELEASE_PATTERN.matcher(name)).matches()) { // ... Prerelease, ... Pre-release 1, ... Pre-Release 2, ...-pre3, ...-pre4-1234
				// Pre-releases in Beta need special treatment
				Matcher releaseMatcher = BETA_PATTERN.matcher(release); // Beta 1.2, b1.3-1731, Beta v1.5_02, b1.8.1

				if (releaseMatcher.matches()) {
					// Beta versions with pre-releases end with .r after normalization
					// for pre-releases, the pre-release nr is put in that place instead
					if (normalizedRelease.charAt(normalizedRelease.length() - 1) != 'r') {
						throw new IllegalStateException("improperly normalized release " + release + " to " + normalizedRelease + " for pre-release " + name);
					}

					String prBuild = matcher.group(1);
					timestamp = matcher.group(2);

					// pre-release 1 is sometimes just called 'Prerelease'
					if (prBuild == null) {
						prBuild = "1";
					}

					// remove the - separator and replace the final r
					// of the normalized release version
					ret.setLength(ret.length() - 2);
					ret.append(prBuild);
				} else {
					boolean legacyVersion;

					try {
						legacyVersion = VersionPredicateParser.parse("<=1.16").test(new SemanticVersionImpl(release, false));
					} catch (VersionParsingException e) {
						throw new RuntimeException("Failed to parse version: " + release);
					}

					String prBuild = matcher.group(1);
					timestamp = matcher.group(2);

					if (prBuild == null) {
						// between 1.2 and 1.7, regular release ids were used for
						// pre-releases, yet omniarchive marks these versions with
						// a 'pre' identifier
						// we won't do that here because it would be inconsistent
						// with the snapshot release targets

						releaseMatcher = RELEASE_PATTERN.matcher(release); // 1.6, 1.16.5, 1.16+131620

						if (!releaseMatcher.matches()) {
							throw new IllegalStateException("version " + name + " is a pre-release targeting neither a Beta version, nor a release version?!");
						}

						int minor = Integer.parseInt(releaseMatcher.group(2));
						int patch = (releaseMatcher.group(3) == null)
											? 0 // use 0 if no patch version is given (1.7 -> 1.7.0)
											: Integer.parseInt(releaseMatcher.group(3));

						boolean showAsRelease = (minor == 2 && patch == 0) // 1.2
											|| (minor == 3 && patch == 0) // 1.3
											|| (minor == 4 && (patch == 0 || patch == 1 || patch == 3)) // 1.4, 1.4.1, 1.4.3
											|| (minor == 6 && (patch == 0 || patch == 3)) // 1.6, 1.6.3
											|| (minor == 7 && (patch == 0 || patch == 1 || patch == 3)); // 1.7, 1.7.1, 1.7.3

						if (showAsRelease) {
							// remove the - separator
							ret.setLength(ret.length() - 1);
						} else {
							// then there are also actual pre-releases that use regular
							// release ids that were later re-used for the actual release
							// e.g. 1.6.3-pre and 1.7.4-pre

							// use 'rc' to be consistent with other pre-releases
							// for versions older than 1.16
							ret.append("rc");
						}
					} else {
						// Mark pre-releases as 'beta' versions, except for version 1.16 and before, where they are 'rc'
						ret.append(legacyVersion ? "rc." : "beta.");
						ret.append(prBuild);
					}
				}
			} else if ((matcher = TEST_BUILD_PATTERN.matcher(name)).matches()) { // ... Test Build 1, ...-tb2, ...-tb3-1234
				// Test builds in Beta need special treatment
				Matcher releaseMatcher = BETA_PATTERN.matcher(release); // Beta 1.2, b1.3-1731, Beta v1.5_02, b1.8.1

				if (releaseMatcher.matches()) {
					// Beta versions with test builds end with .r after normalization
					// for test builds, the build nr is put in that place instead
					if (normalizedRelease.charAt(normalizedRelease.length() - 1) != 'r') {
						throw new IllegalStateException("improperly normalized release " + release + " to " + normalizedRelease + " for test build " + name);
					}

					String tbBuild = matcher.group(1);
					timestamp = matcher.group(2);

					// remove the - separator and replace the final r
					// of the normalized release version
					ret.setLength(ret.length() - 2);
					ret.append(tbBuild);
				} else {
					String tbBuild = matcher.group(1);
					timestamp = matcher.group(2);

					ret.append("test.");
					ret.append(tbBuild);
				}
			} else {
				String normalized = normalizeSpecialVersion(name);
				if (normalized != null) return normalized;
			}
		} else if ((matcher = SNAPSHOT_PATTERN.matcher(name)).matches()) { // Snapshot 16w02a, 20w13b, 22w18c-1234
			timestamp = matcher.group(4);

			ret.append("alpha.");
			ret.append(matcher.group(1)); // year
			ret.append('.');
			ret.append(matcher.group(2)); // week
			ret.append('.');
			ret.append(matcher.group(3)); // patch
		} else {
			// Try short-circuiting special versions which are complete on their own
			String normalized = normalizeSpecialVersion(name);
			if (normalized != null) return normalized;

			ret.append(normalizeVersion(name));
		}

		// add timestamp as extra build information
		if (timestamp != null) {
			ret.append('+');
			ret.append(timestamp);
		}

		return ret.toString();
	}

	private static String normalizeVersion(String version) {
		// timestamps distinguish between re-uploads of the same version
		String timestamp = null;
		// omniarchive marks some versions with a -launcher suffix
		// and there is one classic version marked -renew
		String suffix = null;

		StringBuilder prep = new StringBuilder();

		// old version normalization scheme
		// do this before the main part of normalization as we can get crazy strings like "Indev 0.31 12345678-9"
		Matcher matcher;

		if ((matcher = BETA_PATTERN.matcher(version)).matches()) { // Beta 1.2, b1.3-1731, Beta v1.5_02, b1.8.1
			String trail = matcher.group(5);
			timestamp = matcher.group(6);
			suffix = matcher.group(7);

			prep.append("1.0.0-beta.");
			prep.append(matcher.group(1));

			// there are pre-releases in Beta too, and they
			// are annoying to normalize
			// the solution we use is to use the pre-release
			// numbers as patch numbers, then for the 'release'
			// version, use some text - the letter 'r' - instead
			// to ensure it is sorted after the pre-releases
			// for this to work, a minor number must be present
			// but it is only necessary for b1.6, b1.8 and b1.9
			// the minor version is also set to 0 to ensure
			// following minor versions are sorted after
			if (matcher.group(3) == null && matcher.group(4) == null) {
				int maj = Integer.parseInt(matcher.group(2));

				if (maj == 6 || maj == 8 || maj == 9) {
					prep.append(".0.r"); // 'r' for 'release'
				}
			}

			// in the launcher manifest, some Beta versions have
			// trailing alphabetic chars
			if (trail != null) {
				// if no minor version is given, set it to 0 to
				// ensure this version is sorted before subsequent
				// minor updates
				if (matcher.group(3) == null && matcher.group(4) == null) {
					prep.append(".0");
				}

				prep.append('.').append(trail);
			}
		} else if ((matcher = ALPHA_PATTERN.matcher(version)).matches()) { // Alpha v1.0.1, Alpha 1.0.1_01, a1.1.0-131933, a1.2.3_05, Alpha 0.1.0, a0.2.8
			String trail = matcher.group(2);
			timestamp = matcher.group(3);
			suffix = matcher.group(4);

			prep.append("1.0.0-alpha.");
			prep.append(matcher.group(1));

			// in the launcher manifest, some Alpha versions have
			// trailing alphabetic chars
			if (trail != null) {
				prep.append('.').append(trail);
			}
		} else if ((matcher = INDEV_PATTERN.matcher(version)).matches()) { // Indev 0.31 200100110, in-20100124-2310, Infdev 0.31 20100227-1433, inf-20100611
			String date = matcher.group(1);
			// multiple releases could occur on the same day!
			String time = matcher.group(2);

			prep.append("0.31.");
			prep.append(date);
			if (time != null) prep.append('-').append(time);
		} else if ((matcher = EARLY_CLASSIC_PATTERN.matcher(version)).matches() // c0.0.11a, c0.0.17a-2014, 0.0.18a_02
				|| (matcher = LATE_CLASSIC_PATTERN.matcher(version)).matches()) { // c0.24_st, 0.24_st_03, 0.25_st-1658, c0.25_05_st, 0.29, c0.30-s, 0.30-c-renew
			boolean late = LATE_CLASSIC_PATTERN.matcher(version).matches();

			String minor = matcher.group(1);
			String patch = matcher.group(2);
			String trail = late ? matcher.group(4) : null;
			String type = late ? matcher.group(5) : null;
			timestamp = matcher.group(late ? 6 : 3);
			suffix = matcher.group(late ? 7 : 4);

			// in late classic, sometimes the patch number appears before
			// the survival test identifier (_st), and sometimes after it
			if (late && patch == null) {
				patch = matcher.group(3);
			}

			prep.append("0.");
			prep.append(minor);
			if (patch != null) prep.append('.').append(patch);
			// in the launcher manifest, some Classic versions have trailing alphabetic chars
			if (trail != null) prep.append('-').append(trail);
			// in the Omniarchive manifest, some classic versions releases for creative and survival
			if (type != null) prep.append('-').append(type);
		} else if ((matcher = CLASSIC_SERVER_PATTERN.matcher(version)).matches()) {
			String release = matcher.group(1);
			timestamp = matcher.group(2);

			prep.append("0.");
			prep.append(release);
		} else if ((matcher = PRE_CLASSIC_PATTERN.matcher(version)).matches()) { // rd-132211
			String build = matcher.group(1);
			suffix = matcher.group(2);

			// account for a weird exception to the pre-classic versioning scheme
			if ("20090515".equals(build)) {
				build = "150000";
			}

			prep.append("0.0.0-rd.");
			prep.append(build);
		} else {
			prep.append(version);
		}

		StringBuilder ret = new StringBuilder(prep.length() + 5);
		boolean lastIsDigit = false;
		boolean lastIsLeadingZero = false;
		boolean lastIsSeparator = false;

		for (int i = 0, max = prep.length(); i < max; i++) {
			char c = prep.charAt(i);

			if (c >= '0' && c <= '9') {
				if (i > 0 && !lastIsDigit && !lastIsSeparator) { // no separator between non-number and number, add one
					ret.append('.');
				} else if (lastIsDigit && lastIsLeadingZero) { // leading zero in output -> strip
					ret.setLength(ret.length() - 1);
				}

				lastIsLeadingZero = c == '0' && (!lastIsDigit || lastIsLeadingZero); // leading or continued leading zero(es)
				lastIsSeparator = false;
				lastIsDigit = true;
			} else if (c == '.' || c == '-') { // keep . and - separators
				if (lastIsSeparator) continue;

				lastIsSeparator = true;
				lastIsDigit = false;
			} else if ((c < 'A' || c > 'Z') && (c < 'a' || c > 'z')) { // replace remaining non-alphanumeric with .
				if (lastIsSeparator) continue;

				c = '.';
				lastIsSeparator = true;
				lastIsDigit = false;
			} else { // keep other characters (alpha)
				if (lastIsDigit) ret.append('.'); // no separator between number and non-number, add one

				lastIsSeparator = false;
				lastIsDigit = false;
			}

			ret.append(c);
		}

		// strip leading and trailing .

		int start = 0;
		while (start < ret.length() && ret.charAt(start) == '.') start++;

		int end = ret.length();
		while (end > start && ret.charAt(end - 1) == '.') end--;

		ret.setLength(end);

		// add timestamp and suffix as extra build information
		if (timestamp != null || suffix != null) {
			ret.append('+');

			if (timestamp != null) {
				ret.append(timestamp);
				if (suffix != null) ret.append('.');
			}

			if (suffix != null) {
				ret.append(suffix);
			}
		}

		return ret.substring(start);
	}

	private static String normalizeSpecialVersion(String version) {
		// first attempt to normalize the version as-is
		String normalized = normalizeSpecialVersionBase(version);

		// only if that yields no result, check if it's a re-upload from Omniarchive
		if (normalized == null) {
			// timestamps distinguish between re-uploads of the same version
			String timestamp = null;

			Matcher matcher = TIMESTAMP_PATTERN.matcher(version);

			if (matcher.matches()) {
				version = matcher.group(1);
				timestamp = matcher.group(2);
			}

			normalized = normalizeSpecialVersionBase(version);

			// add timestamp as extra build information
			if (normalized != null && timestamp != null) {
				normalized += "+" + timestamp;
			}
		}

		return normalized;
	}

	private static String normalizeSpecialVersionBase(String version) {
		switch (version) {
		case "b1.2_02-dev":
			// a dev version of b1.2
			return "1.0.0-beta.2.dev";
		case "b1.3-demo":
			// a demo version of b1.3 given to PC Gamer magazine
			return "1.0.0-beta.3.demo";
		case "b1.6-trailer":
		case "b1.6-pre-trailer":
			// pre-release version used for the Beta 1.6 trailer
			return "1.0.0-beta.6.0.0"; // sort it before the test builds

		case "13w02a-whitetexturefix":
			// a fork from 13w02a to attempt to fix a white texture glitch
			return "1.5-alpha.13.2.a.whitetexturefix";
		case "13w04a-whitelinefix":
			// a fork from 13w04a to attempt to fix a white line glitch
			return "1.5-alpha.13.4.a.whitelinefix";
		case "1.5-whitelinefix":
		case "1.5-pre-whitelinefix":
			// a pre-release for 1.5 to attempt to fix a white line glitch
			return "1.5-rc.whitelinefix";
		case "13w12~":
			// A pair of debug snapshots immediately before 1.5.1-pre
			return "1.5.1-alpha.13.12.a";

		case "2.0":
			// 2.0 update version as known in the jar, forked from 1.5.1
			return "1.5.2-2.0";

		case "2point0_red":
		case "af-2013-red":
			// 2.0 update version red, forked from 1.5.1
			return "1.5.2-2.0+red";

		case "2point0_purple":
		case "af-2013-purple":
			// 2.0 update version purple, forked from 1.5.1
			return "1.5.2-2.0+purple";

		case "2point0_blue":
		case "af-2013-blue":
			// 2.0 update version blue, forked from 1.5.1
			return "1.5.2-2.0+blue";

		case "15w14a":
		case "af-2015":
			// The Love and Hugs Update, forked from 1.8.3
			return "1.8.4-alpha.15.14.a+loveandhugs";

		case "1.RV-Pre1":
		case "af-2016":
			// The Trendy Update, probably forked from 1.9.2 (although the protocol/data versions immediately follow 1.9.1-pre3)
			return "1.9.2-rv+trendy";

		case "3D Shareware v1.34":
		case "af-2019":
			// Minecraft 3D, forked from 19w13b
			return "1.14-alpha.19.13.shareware";

		case "20w14infinite":
		case "20w14~":
		case "af-2020":
			// The Ultimate Content update, forked from 20w13b
			return "1.16-alpha.20.13.inf"; // Not to be confused with the actual 20w14a

		case "22w13oneblockatatime":
		case "22w13oneBlockAtATime":
		case "af-2022":
			// Minecraft 22w13oneblockatatime - forked from 1.18.2
			return "1.18.3-alpha.22.13.oneblockatatime";

		case "23w13a_or_b":
		case "af-2023":
			// Minecraft 23w13a_or_b, forked from 23w13a
			return "1.20-alpha.23.13.ab";
		case "23w13a_or_b_original":
			// the pre-reupload version of 23w13a_or_b
			return "1.20-alpha.23.13.ab+original";

		case "24w14potato":
		case "af-2024":
			// Minecraft 24w14potato, forked from 24w12a
			return "1.20.5-alpha.24.12.potato";
		case "24w14potato_original":
			// the pre-reupload version of 24w14potato
			return "1.20.5-alpha.24.12.potato+original";

		case "25w14craftmine":
		case "af-2025":
			// Minecraft 25w14craftmine, forked from 1.21.5
			return "1.21.6-alpha.25.14.craftmine";

		case "1.14_combat-212796":
		case "1.14.3 - Combat Test":
		case "combat1":
			// The first Combat Test, forked from 1.14.3 Pre-Release 4
			return "1.14.3-rc.4.combat.1";

		case "1.14_combat-0":
		case "Combat Test 2":
		case "combat2":
			// The second Combat Test, forked from 1.14.4
			return "1.14.5-combat.2";

		case "1.14_combat-3":
		case "Combat Test 3":
		case "combat3":
			// The third Combat Test, forked from 1.14.4
			return "1.14.5-combat.3";

		case "1.15_combat-1":
		case "Combat Test 4":
		case "combat4":
			// The fourth Combat Test, forked from 1.15 Pre-release 3
			return "1.15-rc.3.combat.4";

		case "1.15_combat-6":
		case "Combat Test 5":
		case "combat5":
			// The fifth Combat Test, forked from 1.15.2 Pre-release 2
			return "1.15.2-rc.2.combat.5";

		case "1.16_combat-0":
		case "Combat Test 6":
		case "combat6":
			// The sixth Combat Test, forked from 1.16.2 Pre-release 3
			return "1.16.2-beta.3.combat.6";

		case "1.16_combat-1":
		case "Combat Test 7":
		case "combat7":
			// Private testing Combat Test 7, forked from 1.16.2
			return "1.16.3-combat.7";

		case "1.16_combat-2":
		case "Combat Test 7b":
		case "combat7b":
			// Private testing Combat Test 7b, forked from 1.16.2
			return "1.16.3-combat.7.b";

		case "combat7c":
		case "Combat Test 7c":
		case "1.16_combat-3":
			// The seventh Combat Test 7c, forked from 1.16.2
			return "1.16.3-combat.7.c";

		case "combat8":
		case "Combat Test 8":
		case "1.16_combat-4":
			// Private testing Combat Test 8(a?), forked from 1.16.2
			return "1.16.3-combat.8";

		case "combat8b":
		case "Combat Test 8b":
		case "1.16_combat-5":
			// The eighth Combat Test 8b, forked from 1.16.2
			return "1.16.3-combat.8.b";

		case "combat8c":
		case "Combat Test 8c":
		case "1.16_combat-6":
			// The ninth Combat Test 8c, forked from 1.16.2
			return "1.16.3-combat.8.c";

		default:
			return null; //Don't recognise the version
		}
	}

	private interface Analyzer {
		String getResult();
	}

	private static final class FieldStringConstantVisitor extends ClassVisitor implements Analyzer {
		private final String fieldName;
		private String className;
		private String result;

		FieldStringConstantVisitor(String fieldName) {
			super(FabricLoaderImpl.ASM_VERSION);

			this.fieldName = fieldName;
		}

		@Override
		public String getResult() {
			return result;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			this.className = name;
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			if (result == null && name.equals(fieldName) && descriptor.equals(STRING_DESC) && value instanceof String) {
				result = (String) value;
			}

			return null;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			if (result != null || !name.equals("<clinit>")) return null;

			// capture LDC ".." followed by PUTSTATIC this.fieldName
			return new InsnFwdMethodVisitor() {
				@Override
				public void visitLdcInsn(Object value) {
					String str;

					if (value instanceof String && isProbableVersion(str = (String) value)) {
						lastLdc = str;
					} else {
						lastLdc = null;
					}
				}

				@Override
				public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
					if (result == null
							&& lastLdc != null
							&& opcode == Opcodes.PUTSTATIC
							&& owner.equals(className)
							&& name.equals(fieldName)
							&& descriptor.equals(STRING_DESC)) {
						result = lastLdc;
					}

					lastLdc = null;
				}

				@Override
				protected void visitAnyInsn() {
					lastLdc = null;
				}

				String lastLdc;
			};
		}
	}

	private static final class MethodStringConstantContainsVisitor extends ClassVisitor implements Analyzer {
		private final String methodOwner;
		private final String methodName;
		private String result;

		MethodStringConstantContainsVisitor(String methodOwner, String methodName) {
			super(FabricLoaderImpl.ASM_VERSION);

			this.methodOwner = methodOwner;
			this.methodName = methodName;
		}

		@Override
		public String getResult() {
			return result;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			if (result != null) return null;

			// capture LDC ".." followed by INVOKE methodOwner.methodName
			return new InsnFwdMethodVisitor() {
				@Override
				public void visitLdcInsn(Object value) {
					if (value instanceof String) {
						lastLdc = findProbableVersion((String) value);
					} else {
						lastLdc = null;
					}
				}

				@Override
				public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
					if (result == null
							&& lastLdc != null
							&& owner.equals(methodOwner)
							&& name.equals(methodName)
							&& descriptor.startsWith("(" + STRING_DESC + ")")) {
						result = lastLdc;
					}

					lastLdc = null;
				}

				@Override
				protected void visitAnyInsn() {
					lastLdc = null;
				}

				String lastLdc;
			};
		}
	}

	private static final class MethodConstantRetVisitor extends ClassVisitor implements Analyzer {
		private final String methodName;
		private String result;

		MethodConstantRetVisitor(String methodName) {
			super(FabricLoaderImpl.ASM_VERSION);

			this.methodName = methodName;
		}

		@Override
		public String getResult() {
			return result;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			if (result != null
					|| methodName != null && !name.equals(methodName)
					|| !descriptor.endsWith(STRING_DESC)
					|| descriptor.charAt(descriptor.length() - STRING_DESC.length() - 1) != ')') {
				return null;
			}

			// capture LDC ".." followed by ARETURN
			return new InsnFwdMethodVisitor() {
				@Override
				public void visitLdcInsn(Object value) {
					String str;

					if (value instanceof String && isProbableVersion(str = (String) value)) {
						lastLdc = str;
					} else {
						lastLdc = null;
					}
				}

				@Override
				public void visitInsn(int opcode) {
					if (result == null
							&& lastLdc != null
							&& opcode == Opcodes.ARETURN) {
						result = lastLdc;
					}

					lastLdc = null;
				}

				@Override
				protected void visitAnyInsn() {
					lastLdc = null;
				}

				String lastLdc;
			};
		}
	}

	private static final class MethodConstantVisitor extends ClassVisitor implements Analyzer {
		private static final String STARTING_MESSAGE = "Starting minecraft server version ";
		private static final String CLASSIC_PREFIX = "Minecraft ";

		private final String methodNameHint;
		private String result;
		private boolean foundInMethodHint;

		MethodConstantVisitor(String methodNameHint) {
			super(FabricLoaderImpl.ASM_VERSION);

			this.methodNameHint = methodNameHint;
		}

		@Override
		public String getResult() {
			return result;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			final boolean isRequestedMethod = name.equals(methodNameHint);

			if (result != null && !isRequestedMethod) {
				return null;
			}

			return new MethodVisitor(FabricLoaderImpl.ASM_VERSION) {
				@Override
				public void visitLdcInsn(Object value) {
					if ((result == null || !foundInMethodHint && isRequestedMethod) && value instanceof String) {
						String str = (String) value;

						// a0.1.0 - 1.2.5 have a startup message including the version, extract it from there
						// Examples:
						//  release 1.0.0 - Starting minecraft server version 1.0.0
						// 	beta 1.7.3 - Starting minecraft server version Beta 1.7.3
						// 	alpha 0.2.8 - Starting minecraft server version 0.2.8
						if (str.startsWith(STARTING_MESSAGE)) {
							str = str.substring(STARTING_MESSAGE.length());

							// Alpha servers don't have any prefix, but they all have 0 as the major
							if (!str.startsWith("Beta") && str.startsWith("0.")) {
								str = "Alpha " + str;
							}
						} else if (str.startsWith(CLASSIC_PREFIX)) {
							str = str.substring(CLASSIC_PREFIX.length());

							if (str.startsWith(CLASSIC_PREFIX)) { // some beta versions repeat the Minecraft prefix
								str = str.substring(CLASSIC_PREFIX.length());
							}
						}

						// 1.0.0 - 1.13.2 have an obfuscated method that just returns the version, so we can use that

						if (isProbableVersion(str)) {
							result = str;
							foundInMethodHint = isRequestedMethod;
						}
					}
				}
			};
		}
	}

	private abstract static class InsnFwdMethodVisitor extends MethodVisitor {
		InsnFwdMethodVisitor() {
			super(FabricLoaderImpl.ASM_VERSION);
		}

		protected abstract void visitAnyInsn();

		@Override
		public void visitLdcInsn(Object value) {
			visitAnyInsn();
		}

		@Override
		public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
			visitAnyInsn();
		}

		@Override
		public void visitInsn(int opcode) {
			visitAnyInsn();
		}

		@Override
		public void visitIntInsn(int opcode, int operand) {
			visitAnyInsn();
		}

		@Override
		public void visitVarInsn(int opcode, int var) {
			visitAnyInsn();
		}

		@Override
		public void visitTypeInsn(int opcode, java.lang.String type) {
			visitAnyInsn();
		}

		@Override
		public void visitMethodInsn(int opcode, java.lang.String owner, java.lang.String name, java.lang.String descriptor, boolean isInterface) {
			visitAnyInsn();
		}

		@Override
		public void visitInvokeDynamicInsn(java.lang.String name, java.lang.String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
			visitAnyInsn();
		}

		@Override
		public void visitJumpInsn(int opcode, Label label) {
			visitAnyInsn();
		}

		@Override
		public void visitIincInsn(int var, int increment) {
			visitAnyInsn();
		}

		@Override
		public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
			visitAnyInsn();
		}

		@Override
		public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
			visitAnyInsn();
		}

		@Override
		public void visitMultiANewArrayInsn(java.lang.String descriptor, int numDimensions) {
			visitAnyInsn();
		}
	}

	private static final class FieldTypeCaptureVisitor extends ClassVisitor implements Analyzer {
		private String type;

		FieldTypeCaptureVisitor() {
			super(FabricLoaderImpl.ASM_VERSION);
		}

		@Override
		public String getResult() {
			return type;
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			if (type == null && descriptor.startsWith("L") && !descriptor.startsWith("Ljava/")) {
				type = descriptor.substring(1, descriptor.length() - 1);
			}

			return null;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return null;
		}
	}
}
