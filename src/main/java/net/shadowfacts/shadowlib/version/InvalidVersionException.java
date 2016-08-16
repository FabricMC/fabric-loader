package net.shadowfacts.shadowlib.version;

/**
 * @author shadowfacts
 */
public class InvalidVersionException extends RuntimeException {

	/**
	 * The default constructor
	 * @param msg The detail message
	 */
	public InvalidVersionException(String msg) {
		super(msg);
	}

	/**
	 * Convenience constructor, just passes the result of String.format(msg, args) to the default constructor
	 * @param msg The message
	 * @param args The arguments to be passed into String.format
	 */
	public InvalidVersionException(String msg, Object... args) {
		this(String.format(msg, args));
	}
}