package net.fabricmc.loader.api.config.serialization.toml;

import java.io.Writer;

/**
 * A Writer writing in a StringBuilder. This is NOT Thread safe.
 * 
 * @author TheElectronWill
 */
public class FastStringWriter extends Writer {
	
	/**
	 * The underlying StringBuilder. Everything is appended to it.
	 */
	private final StringBuilder sb;
	
	/**
	 * Creates a new FastStringWriter with a default StringBuilder
	 */
	public FastStringWriter() {
		sb = new StringBuilder();
	}
	
	/**
	 * Creates a new FastStringWriter with a given StringBuilder. It will append everything to this StringBuilder.
	 *
	 * @param sb the StringBuilder
	 */
	public FastStringWriter(StringBuilder sb) {
		this.sb = sb;
	}
	
	/**
	 * Returns the underlying StringBuilder.
	 *
	 * @return the underlying StringBuilder
	 */
	public StringBuilder getBuilder() {
		return sb;
	}
	
	/**
	 * Returns the content of the underlying StringBuilder, as a String. Equivalent to {@link #getBuilder()#toString()}.
	 *
	 * @return the content of the underlying StringBuilder
	 */
	@Override
	public String toString() {
		return sb.toString();
	}
	
	@Override
	public FastStringWriter append(char c) {
		sb.append(c);
		return this;
	}
	
	@Override
	public FastStringWriter append(CharSequence csq, int start, int end) {
		sb.append(csq, start, end);
		return this;
	}
	
	@Override
	public FastStringWriter append(CharSequence csq) {
		sb.append(csq);
		return this;
	}
	
	@Override
	public void write(String str, int off, int len) {
		sb.append(str, off, off + len);
	}
	
	@Override
	public void write(String str) {
		sb.append(str);
	}
	
	@Override
	public void write(char[] cbuf, int off, int len) {
		sb.append(cbuf, off, len);
	}
	
	@Override
	public void write(int c) {
		sb.append(c);
	}
	
	/**
	 * This method does nothing.
	 */
	@Override
	public void flush() {}
	
	/**
	 * This method does nothing.
	 */
	@Override
	public void close() {}
	
}
