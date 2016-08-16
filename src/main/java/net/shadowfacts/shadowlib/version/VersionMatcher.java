package net.shadowfacts.shadowlib.version;



/**
 * Used to match version matching strings (e.g. "1.2.x") with versions.
 * <b>Example:</b>
 * <code>
 *     VersionMatcher.matches("1.2.x", new Version("1.2.3"));
 * </code>
 * returns true whereas
 * <code>
 *     VersionMatcher.matches("1.2.x", new Version("1.3.4"));
 * </code>
 * returns false
 * @author shadowfacts
 */
public class VersionMatcher {

	private static final String[] WILDCARD_STRINGS = new String[] {
			"*", "x", "X"
	};

	private String matcherString;

	/**
	 * Constructor used to create a new reusable {@link VersionMatcher}
	 * @param matcherString
	 */
	public VersionMatcher(String matcherString) {
		this.matcherString = matcherString;
	}

	/**
	 * Checks if the {@link Version} is valid for the matcher {@link String} passed to the constructor
	 * @param v
	 * @return
	 */
	public boolean matches(Version v) {
		return matches(matcherString, v);
	}

	/**
	 * Check if the {@link Version} is valid for the matcher {@link String}
	 * @param matcherString
	 * @param v
	 * @return
	 */
	public static boolean matches(String matcherString, Version v) {
		for (String s : WILDCARD_STRINGS) {
			if (matcherString.equals(s)) {
				return true;
			}
		}

		if (matcherString.startsWith(">=")) {
			return  matchesGreaterThanOrEqualTo(matcherString.substring(2), v);
		} else if (matcherString.startsWith("<=")) {
			return matchesLessThanOrEqualTo(matcherString.substring(2), v);
		} else if (matcherString.startsWith("^") || matcherString.startsWith(">")) {
			return matchesGreaterThan(matcherString.substring(1), v);
		} else if (matcherString.startsWith("<")) {
			return matchesLessThan(matcherString.substring(1), v);
		}

		boolean majorValid = false;
		boolean minorValid = false;
		boolean patchValid = false;
		boolean labelValid = false;

		String major;
		String minor;
		String patch;
		String label = "";

		String[] arr = matcherString.split("\\-");
		String[] arr2 = arr[0].split("\\.");

		if (arr2.length != 3) {
			throw new InvalidVersionException("Cannot create Version with %d version arguments", arr2.length);
		}

		major = arr2[0];
		minor = arr2[1];
		patch = arr2[2];

		if (arr.length == 2) { // There is 1 label

			if (arr[1] == null) {
				throw new InvalidVersionException("Cannot create Version with null label");
			} else if (arr[1].equals("")) {
				throw new InvalidVersionException("Cannot create Version with empty label");
			}

			label = arr[1];
		} else if (arr.length > 2) { //  There are multiple labels

			for (int i = 1; i < arr.length - 1; i++) {

				if (arr[i] == null) {
					throw new InvalidVersionException("Cannot create Version with null label");
				} else if (arr[i].equals("")) {
					throw new InvalidVersionException("Cannot create Version with empty label");
				}

				label += arr[i];

			}

		}

		for (String s : WILDCARD_STRINGS) {
			if (!majorValid) majorValid = major.equals(s);
			if (!minorValid) minorValid = minor.equals(s);
			if (!patchValid) patchValid = patch.equals(s);
		}

		if (!majorValid) majorValid = (Integer.parseInt(major) == v.getMajor());
		if (!minorValid) minorValid = (Integer.parseInt(minor) == v.getMinor());
		if (!patchValid) patchValid = (Integer.parseInt(patch) == v.getPatch());

		labelValid = !v.hasLabel() && label.isEmpty();
		if (!labelValid) labelValid = label.equals(v.getLabel());

		return majorValid && minorValid && patchValid && labelValid;
	}

	private static boolean matchesGreaterThan(String s, Version v) {
		return v.greaterThan(new Version(s));
	}

	private static boolean matchesLessThan(String s, Version v) {
		return v.lessThan(new Version(s));
	}

	private static boolean matchesGreaterThanOrEqualTo(String s, Version v) {
		Version other = new Version(s);
		return v.equals(other) || v.greaterThan(other);
	}

	private static boolean matchesLessThanOrEqualTo(String s, Version v) {
		Version other = new Version(s);
		return v.equals(other) || v.lessThan(other);
	}

}