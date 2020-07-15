package net.fabricmc.loader.api.metadata;

import java.util.ArrayList;
import java.util.Collection;

import net.fabricmc.loader.api.VersionRange;

public abstract class AbstractModDependency implements ModDependency {
	protected final String modId;

	protected AbstractModDependency(String modId) {
		this.modId = modId;
	}

	@Override
	public String getModId() {
		return modId;
	}

	protected abstract String[] getVersionMatchers();

	@Override
	public Collection<VersionRange> getVersionRanges() {
		Collection<VersionRange> ranges = new ArrayList<>();
		String[] matchers = getVersionMatchers();
		for (String matcher : matchers) {
			char firstChar = matcher.charAt(0);
			char secondChar = 0;
			if (matcher.length() > 1)
				secondChar = matcher.charAt(1);
			switch (firstChar) {
				case '*':
					if (matcher.length() == 1)
						ranges.add(new VersionRange(VersionRange.Type.ANY, ""));
					else
						ranges.add(new VersionRange(VersionRange.Type.INVALID, ""));
					break;
				case '>':
					if (secondChar == '=')
						ranges.add(new VersionRange(VersionRange.Type.GREATER_THAN_OR_EQUAL, matcher.substring(2)));
					else
						ranges.add(new VersionRange(VersionRange.Type.GREATER_THAN, matcher.substring(1)));
					break;
				case '<':
					if (secondChar == '=')
						ranges.add(new VersionRange(VersionRange.Type.LESSER_THAN_OR_EQUAL, matcher.substring(2)));
					else
						ranges.add(new VersionRange(VersionRange.Type.LESSER_THAN, matcher.substring(1)));
					break;
				case '=':
					ranges.add(new VersionRange(VersionRange.Type.EQUALS, matcher.substring(1)));
					break;
				case '^':
					ranges.add(new VersionRange(VersionRange.Type.SAME_MAJOR, matcher.substring(1)));
					break;
				case '~':
					ranges.add(new VersionRange(VersionRange.Type.SAME_MAJOR_AND_MINOR, matcher.substring(1)));
					break;
				default: // string version
					ranges.add(new VersionRange(VersionRange.Type.EQUALS, matcher));
					break;
			}
		}
		return ranges;
	}
}
