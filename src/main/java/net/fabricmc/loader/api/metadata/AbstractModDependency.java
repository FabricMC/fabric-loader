package net.fabricmc.loader.api.metadata;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.fabricmc.loader.api.VersionRange;

public abstract class AbstractModDependency implements ModDependency {
	protected final String modId;
	private Collection<VersionRange> ranges;

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
		if (ranges == null) {
			ranges = Stream.of(getVersionMatchers()).map(matcher -> {
				char firstChar = matcher.charAt(0);
				char secondChar = 0;
				if (matcher.length() > 1)
					secondChar = matcher.charAt(1);
				switch (firstChar) {
					case '*':
						if (matcher.length() == 1)
							return new VersionRange(VersionRange.Type.ANY, "");
						else
							return new VersionRange(VersionRange.Type.INVALID, "");
					case '>':
						if (secondChar == '=')
							return new VersionRange(VersionRange.Type.GREATER_THAN_OR_EQUAL, matcher.substring(2));
						else
							return new VersionRange(VersionRange.Type.GREATER_THAN, matcher.substring(1));
					case '<':
						if (secondChar == '=')
							return new VersionRange(VersionRange.Type.LESSER_THAN_OR_EQUAL, matcher.substring(2));
						else
							return new VersionRange(VersionRange.Type.LESSER_THAN, matcher.substring(1));
					case '=':
						return new VersionRange(VersionRange.Type.EQUALS, matcher.substring(1));
					case '^':
						return new VersionRange(VersionRange.Type.SAME_MAJOR, matcher.substring(1));
					case '~':
						return new VersionRange(VersionRange.Type.SAME_MAJOR_AND_MINOR, matcher.substring(1));
					default: // string version
						return new VersionRange(VersionRange.Type.EQUALS, matcher);
				}
			}).collect(Collectors.toSet());
			// simplify: if one ANY range exists, only use that
			if (ranges.stream().anyMatch(range -> range.getType() == VersionRange.Type.ANY)) {
				ranges.clear();
				ranges.add(new VersionRange(VersionRange.Type.ANY, ""));
			}
			ranges = Collections.unmodifiableCollection(ranges);
		}
		return ranges;
	}
}
