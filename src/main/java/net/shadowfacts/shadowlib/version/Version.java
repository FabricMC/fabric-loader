package net.shadowfacts.shadowlib.version;

/**
 * Wrapper for a Semantic Version.
 * @see <a href="http://semver.org/">http://semver.org</a>
 *
 * @author shadowfacts
 */
public class Version {

	private int major;
	private int minor;
	private int patch;
	private String label;

	/**
	 * Normal constructor
	 * @param v The version String to convert to an object. Should be in the format of X.Y.Z or X.Y.Z-LABEL
	 */
	public Version(String v) {
		String[] arr = v.split("\\-");

		String[] arr2 = arr[0].split("\\.");

		if (arr2.length != 3) {
			throw new InvalidVersionException("Cannot create Version with %d version arguments", arr2.length);
		}

		major = Integer.parseInt(arr2[0]);
		minor = Integer.parseInt(arr2[1]);
		patch = Integer.parseInt(arr2[2]);


		if (arr.length == 2) { // There is 1 label

			if (arr[1] == null) {
				throw new InvalidVersionException("Cannot create Version with null label");
			} else if (arr[1].isEmpty()) {
				throw new InvalidVersionException("Cannot create Version with empty label");
			}

			label = arr[1];

		} else if (arr.length < 2) { //  There are multiple labels

			for (int i = 1; i < arr.length - 1; i++) {

				if (arr[i] == null) {
					throw new InvalidVersionException("Cannot create Version with null label");
				} else if (arr[i].equals("")) {
					throw new InvalidVersionException("Cannot create Version with empty label");
				}

				label += arr[i];

			}

		}
	}

	/**
	 * Creates a new Version object without a label from major, minor, and patch numbers
	 * @param major
	 * @param minor
	 * @param patch
	 */
	public Version(int major, int minor, int patch) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
	}

	/**
	 * Creates a new {@link Version} object from a major version, minor version, patch number, and label
	 * @param major
	 * @param minor
	 * @param patch
	 * @param label
	 */
	public Version(int major, int minor, int patch, String label) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.label = label;
	}

	/**
	 * Creates a new {@link Version} from the current {@link Version} object
	 * @return The duplicated {@link Version}
	 */
	public Version copy() {
		return new Version(major, minor, patch, label);
	}

	/**
	 * Increments the MAJOR version by 1
	 * MINOR and PATCH are reset to 0
	 * @param resetLabel If true, removes the label
	 * @return
	 */
	public Version incrementMajor(boolean resetLabel) {
		major++;
		minor = 0;
		patch = 0;
		if (resetLabel) {
			label = "";
		}
		return this;
	}

	/**
	 * Increments the MAJOR version by 1
	 * MINOR and PATCH are reset to 0
	 * Does not remove the label
	 * @return
	 */
	public Version incrementMajor() {
		return incrementMajor(false);
	}

	/**
	 * Increments the MINOR version by 1
	 * PATCH is reset to 0
	 * @param resetLabel If true, removes the label
	 * @return
	 */
	public Version incrementMinor(boolean resetLabel) {
		minor++;
		patch = 0;
		if (resetLabel) {
			label = "";
		}
		return this;
	}

	/**
	 * Increments the MINOR version by 1
	 * PATCH is reset to 0
	 * Does not remote the label
	 * @return
	 */
	public Version incrementMinor() {
		return incrementMinor(false);
	}

	/**
	 * Increments the PATCH version by 1
	 * @param resetLabel If true, removes the label
	 * @return
	 */
	public Version incrementPatch(boolean resetLabel) {
		patch++;
		if (resetLabel) {
			label = "";
		}
		return this;
	}

	/**
	 * Increments the PATCH version by 1
	 * Does not remove the label
	 * @return
	 */
	public Version incrementPatch() {
		return incrementPatch(false);
	}

	/**
	 * Sets the MAJOR version to whatever is specified
	 * @param major The new MAJOR version
	 * @param resetLabel If true, removes the label
	 * @return
	 */
	public Version setMajor(int major, boolean resetLabel) {
		this.major = major;
		if (resetLabel) {
			label = "";
		}
		return this;
	}

	/**
	 * Sets the MAJOR version to whatever is specified
	 * Does not remove the label
	 * @param major The new MAJOR version
	 * @return
	 */
	public Version setMajor(int major) {
		return setMajor(major, false);
	}

	/**
	 * Sets the MINOR version to whatever is specified
	 * @param minor The new MINOR version
	 * @param resetLabel If true, removes the label
	 * @return
	 */
	public Version setMinor(int minor, boolean resetLabel) {
		this.minor = minor;
		if (resetLabel) {
			label = "";
		}
		return this;
	}

	/**
	 * Sets the MINOR version to whatever is specified
	 * Does not remove the label
	 * @param minor The new MINOR version
	 * @return
	 */
	public Version setMinor(int minor) {
		return setMinor(minor, false);
	}

	/**
	 * Sets the PATCH number to whatever is specified
	 * @param patch The new PATCH number
	 * @param resetLabel If true, removes the label
	 * @return
	 */
	public Version setPatch(int patch, boolean resetLabel) {
		this.patch = patch;
		if (resetLabel) {
			label = "";
		}
		return this;
	}

	/**
	 * Sets the PATCH number to whatever is specified
	 * Does not remote the label
	 * @param patch The new PATCH number
	 * @return
	 */
	public Version setPatch(int patch) {
		return setPatch(patch, false);
	}

	/**
	 * Sets the label
	 * @param label
	 * @return
	 */
	public Version setLabel(String label) {
		this.label = label;
		return this;
	}

	/**
	 * Removes the label
	 * @return
	 */
	public Version removeLabel() {
		return setLabel("");
	}


	/**
	 * @return The MAJOR number
	 */
	public int getMajor() {
		return major;
	}

	/**
	 * @return The MINOR number
	 */
	public int getMinor() {
		return minor;
	}

	/**
	 * @return The PATCH number
	 */
	public int getPatch() {
		return patch;
	}

	/**
	 * @return The label
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * @return If the {@link Version} has a label
	 */
	public boolean hasLabel() {
		return label != null && !label.isEmpty();
	}

	/**
	 * Converts a {@link Version} object to a {@link String}
	 * @return A Semantic Version string, will be in the format of X.Y.Z or X.Y.Z-LABEL
	 */
	@Override
	public String toString() {
		String s = String.format("%d.%d.%d", major, minor, patch);
		if (hasLabel()) {
			s += String.format("-%s", label);
		}
		return s;
	}

	/**
	 * Check if the current {@link Version} is equal to obj
	 * @param obj
	 * @return
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Version) {

			Version other = (Version)obj;

			if (other.major == major && other.minor == minor && other.patch == patch) {
				if (other.hasLabel() && hasLabel()) {
					if (other.label.equals(label)) {
						return true;
					}
				} else {
					return true;
				}
			}

		}

		return false;
	}

	/**
	 * Check if this {@link Version} is greater than the other one
	 * @param other
	 * @return
	 */
	public boolean greaterThan(Version other) {
		if (major > other.major) {
			return true;
		} else if (major == other.major && minor > other.minor) {
			return true;
		} else if (major == other.major && minor == other.minor && patch > other.patch) {
			return true;
		}

		return false;
	}

	/**
	 * Check if this {@link net.shadowfacts.shadowlib.version.Version} is less than the other one
	 * @param other
	 * @return
	 */
	public boolean lessThan(Version other) {
		return other.greaterThan(this);
	}

	/**
	 * Check if the current version is valid for the matcher string using {@link net.shadowfacts.shadowlib.version.VersionMatcher}
	 * @param matcherString
	 * @return
	 */
	public boolean validFor(String matcherString) {
		return VersionMatcher.matches(matcherString, this);
	}
}