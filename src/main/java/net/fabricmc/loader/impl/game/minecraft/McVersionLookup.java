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
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import net.fabricmc.loader.impl.util.FileSystemUtil;
import net.fabricmc.loader.impl.util.version.SemanticVersionImpl;
import net.fabricmc.loader.impl.util.version.VersionPredicateParser;

public final class McVersionLookup {
	private static final Pattern VERSION_PATTERN = Pattern.compile(
			"0\\.\\d+(\\.\\d+)?a?(_\\d+)?|" // match classic versions first: 0.1.2a_34
			+ "\\d+\\.\\d+(\\.\\d+)?(-pre\\d+| Pre-[Rr]elease \\d+)?|" // modern non-snapshot: 1.2, 1.2.3, optional -preN or " Pre-Release N" suffix
			+ "\\d+\\.\\d+(\\.\\d+)?(-rc\\d+| [Rr]elease Candidate \\d+)?|" // 1.16+ Release Candidate
			+ "\\d+w\\d+[a-z]|" // modern snapshot: 12w34a
			+ "[a-c]\\d\\.\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?|" // alpha/beta a1.2.3_45
			+ "(Alpha|Beta) v?\\d+\\.\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?|" // long alpha/beta names: Alpha v1.2.3_45
			+ "Inf?dev (0\\.31 )?\\d+(-\\d+)?|" // long indev/infdev names: Infdev 12345678-9
			+ "(rd|inf)-\\d+|" // early rd-123, inf-123
			+ "1\\.RV-Pre1|3D Shareware v1\\.34" // odd exceptions
			);
	private static final Pattern RELEASE_PATTERN = Pattern.compile("\\d+\\.\\d+(\\.\\d+)?");
	private static final Pattern PRE_RELEASE_PATTERN = Pattern.compile(".+(?:-pre| Pre-[Rr]elease )(\\d+)");
	private static final Pattern RELEASE_CANDIDATE_PATTERN = Pattern.compile(".+(?:-rc| [Rr]elease Candidate )(\\d+)");
	private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("(?:Snapshot )?(\\d+)w0?(0|[1-9]\\d*)([a-z])");
	private static final Pattern BETA_PATTERN = Pattern.compile("(?:b|Beta v?)1\\.(\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?)");
	private static final Pattern ALPHA_PATTERN = Pattern.compile("(?:a|Alpha v?)1\\.(\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?)");
	private static final Pattern INDEV_PATTERN = Pattern.compile("(?:inf-|Inf?dev )(?:0\\.31 )?(\\d+(-\\d+)?)");
	private static final String STRING_DESC = "Ljava/lang/String;";

	public static McVersion getVersion(Path gameJar, List<String> entrypointClasses, String versionName) {
		McVersion.Builder builder = new McVersion.Builder();

		if (versionName != null) {
			builder.setNameAndRelease(versionName);
		}

		try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(gameJar, false)) {
			FileSystem fs = jarFs.get();

			// Determine class version
			for (String entrypointClass : entrypointClasses) {
				String fileString = entrypointClass.replace('.', '/') + ".class";
				Path file = fs.getPath(fileString);

				if (Files.isRegularFile(file)) {
					try (DataInputStream is = new DataInputStream(Files.newInputStream(file))) {
						if (is.readInt() != 0xCAFEBABE) {
							continue;
						}

						is.readUnsignedShort();
						builder.setClassVersion(is.readUnsignedShort());

						break;
					}
				}
			}

			// Check various known files for version information if unknown
			if (versionName == null) {
				fillVersionFromJar(gameJar, fs, builder);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return builder.build();
	}

	public static McVersion getVersionExceptClassVersion(Path gameJar) {
		McVersion.Builder builder = new McVersion.Builder();

		try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(gameJar, false)) {
			fillVersionFromJar(gameJar, jarFs.get(), builder);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return builder.build();
	}

	public static void fillVersionFromJar(Path gameJar, FileSystem fs, McVersion.Builder builder) {
		try {
			Path file;

			// version.json - contains version and target release for 18w47b+
			if (Files.isRegularFile(file = fs.getPath("version.json")) && fromVersionJson(Files.newInputStream(file), builder)) {
				return;
			}

			// constant field RealmsSharedConstants.VERSION_STRING
			if (Files.isRegularFile(file = fs.getPath("net/minecraft/realms/RealmsSharedConstants.class")) && fromAnalyzer(Files.newInputStream(file), new FieldStringConstantVisitor("VERSION_STRING"), builder)) {
				return;
			}

			// constant return value of RealmsBridge.getVersionString (presumably inlined+dead code eliminated VERSION_STRING)
			if (Files.isRegularFile(file = fs.getPath("net/minecraft/realms/RealmsBridge.class")) && fromAnalyzer(Files.newInputStream(file), new MethodConstantRetVisitor("getVersionString"), builder)) {
				return;
			}

			// version-like String constant used in MinecraftServer.run or another MinecraftServer method
			if (Files.isRegularFile(file = fs.getPath("net/minecraft/server/MinecraftServer.class")) && fromAnalyzer(Files.newInputStream(file), new MethodConstantVisitor("run"), builder)) {
				return;
			}

			if (Files.isRegularFile(file = fs.getPath("net/minecraft/client/Minecraft.class"))) {
				// version-like constant return value of a Minecraft method (obfuscated/unknown name)
				if (fromAnalyzer(Files.newInputStream(file), new MethodConstantRetVisitor(null), builder)) {
					return;
				}

				// version-like constant passed into Display.setTitle in a Minecraft method (obfuscated/unknown name)
				if (fromAnalyzer(Files.newInputStream(file), new MethodStringConstantContainsVisitor("org/lwjgl/opengl/Display", "setTitle"), builder)) {
					return;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		builder.setFromFileName(gameJar.getFileName().toString());
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

			if (name == null) {
				name = id;
			} else if (id != null) {
				if (id.length() < name.length()) name = id;
			}

			if (name != null && release != null) {
				builder.setName(name);
				builder.setName(release);

				return true;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return false;
	}

	private static <T extends ClassVisitor & Analyzer> boolean fromAnalyzer(InputStream is, T analyzer, McVersion.Builder builder) {
		try {
			ClassReader cr = new ClassReader(is);
			cr.accept(analyzer, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
			String result = analyzer.getResult();

			if (result != null) {
				builder.setNameAndRelease(result);
				return true;
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				// ignored
			}
		}

		return false;
	}

	protected static String getRelease(String version) {
		if (RELEASE_PATTERN.matcher(version).matches()) return version;

		assert isProbableVersion(version);

		int pos = version.indexOf("-pre");
		if (pos >= 0) return version.substring(0, pos);

		pos = version.indexOf(" Pre-Release ");
		if (pos >= 0) return version.substring(0, pos);

		pos = version.indexOf(" Pre-release ");
		if (pos >= 0) return version.substring(0, pos);

		pos = version.indexOf(" Release Candidate ");
		if (pos >= 0) return version.substring(0, pos);

		Matcher matcher = SNAPSHOT_PATTERN.matcher(version);

		if (matcher.matches()) {
			int year = Integer.parseInt(matcher.group(1));
			int week = Integer.parseInt(matcher.group(2));

			if (year == 20 && week >= 6) {
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
				return "1.7.4";
			} else if (year == 13 && week >= 36 && week <= 43) {
				return "1.7.2";
			} else if (year == 13 && week >= 16 && week <= 26) {
				return "1.6";
			} else if (year == 13 && week >= 11 && week <= 12) {
				return "1.5.1";
			} else if (year == 13 && week >= 1 && week <= 10) {
				return "1.5";
			} else if (year == 12 && week >= 49 && week <= 50) {
				return "1.4.6";
			} else if (year == 12 && week >= 32 && week <= 42) {
				return "1.4.2";
			} else if (year == 12 && week >= 15 && week <= 30) {
				return "1.3.1";
			} else if (year == 12 && week >= 3 && week <= 8) {
				return "1.2.1";
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
	protected static String normalizeVersion(String name, String release) {
		if (release == null || name.equals(release)) {
			return normalizeVersion(name);
		}

		Matcher matcher;

		if (name.startsWith(release)) {
			matcher = RELEASE_CANDIDATE_PATTERN.matcher(name);

			if (matcher.matches()) {
				String rcBuild = matcher.group(1);

				// This is a hack to fake 1.16's new release candidates to follow on from the 8 pre releases.
				if (release.equals("1.16")) {
					int build = Integer.parseInt(rcBuild);
					rcBuild = Integer.toString(8 + build);
				}

				name = String.format("rc.%s", rcBuild);
			} else {
				matcher = PRE_RELEASE_PATTERN.matcher(name);

				if (matcher.matches()) {
					boolean legacyVersion;

					try {
						legacyVersion = VersionPredicateParser.parse("<=1.16").test(new SemanticVersionImpl(release, false));
					} catch (VersionParsingException e) {
						throw new RuntimeException("Failed to parse version: " + release);
					}

					// Mark pre-releases as 'beta' versions, except for version 1.16 and before, where they are 'rc'
					if (legacyVersion) {
						name = String.format("rc.%s", matcher.group(1));
					} else {
						name = String.format("beta.%s", matcher.group(1));
					}
				}
			}
		} else if ((matcher = SNAPSHOT_PATTERN.matcher(name)).matches()) {
			name = String.format("alpha.%s.%s.%s", matcher.group(1), matcher.group(2), matcher.group(3));
		} else {
			name = normalizeVersion(name);
		}

		return String.format("%s-%s", release, name);
	}

	private static String normalizeVersion(String version) {
		// old version normalization scheme
		// do this before the main part of normalization as we can get crazy strings like "Indev 0.31 12345678-9"
		Matcher matcher;

		if ((matcher = BETA_PATTERN.matcher(version)).matches()) { // beta 1.2.3: 1.0.0-beta.2.3
			version = "1.0.0-beta." + matcher.group(1);
		} else if ((matcher = ALPHA_PATTERN.matcher(version)).matches()) { // alpha 1.2.3: 1.0.0-alpha.2.3
			version = "1.0.0-alpha." + matcher.group(1);
		} else if ((matcher = INDEV_PATTERN.matcher(version)).matches()) { // indev/infdev 12345678: 0.31.12345678
			version = "0.31." + matcher.group(1);
		} else if (version.startsWith("c0.")) { // classic: unchanged, except remove prefix
			version = version.substring(1);
		} else if (version.startsWith("rd-")) { // pre-classic
			version = version.substring("rd-".length());
			if ("20090515".equals(version)) version = "150000"; // account for a weird exception to the pre-classic versioning scheme
			version = "0.0.0-rd." + version;
		}

		StringBuilder ret = new StringBuilder(version.length() + 5);
		boolean lastIsDigit = false;
		boolean lastIsLeadingZero = false;
		boolean lastIsSeparator = false;

		for (int i = 0, max = version.length(); i < max; i++) {
			char c = version.charAt(i);

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

		return ret.substring(start, end);
	}

	private interface Analyzer {
		String getResult();
	}

	private static final class FieldStringConstantVisitor extends ClassVisitor implements Analyzer {
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

		private final String fieldName;
		private String className;
		private String result;
	}

	private static final class MethodStringConstantContainsVisitor extends ClassVisitor implements Analyzer {
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

		private final String methodOwner;
		private final String methodName;
		private String result;
	}

	private static final class MethodConstantRetVisitor extends ClassVisitor implements Analyzer {
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

		private final String methodName;
		private String result;
	}

	private static final class MethodConstantVisitor extends ClassVisitor implements Analyzer {
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
					String str;

					if ((result == null || !foundInMethodHint && isRequestedMethod)
							&& value instanceof String
							&& isProbableVersion(str = (String) value)) {
						result = str;
						foundInMethodHint = isRequestedMethod;
					}
				}
			};
		}

		private final String methodNameHint;
		private String result;
		private boolean foundInMethodHint;
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
}
