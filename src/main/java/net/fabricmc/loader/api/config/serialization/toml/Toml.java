package net.fabricmc.loader.api.config.serialization.toml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Map;

/**
 * Utility class for reading and writing TOML v0.4.0. This class internally uses {@link TomlReader} and
 * {@link TomlWriter}.
 *
 * <h1>DateTimes support</h1>
 * <p>
 * The datetime support is more extended than in the TOML specification. The reader and the writer support
 * three kind of datetimes:
 * <ol>
 * <li>Full RFC 3339. Example: 2015-03-20T19:26:00+01:00 => represented as {@link ZonedDateTime}</li>
 * <li>Without local offset. Examples: 2015-03-20T19:26:00 => represented as {@link LocalDateTime}</li>
 * <li>Without time (just the date). Example: 2015-03-20 => represented as {@link LocalDate}</li>
 * </ol>
 * </p>
 * <h1>Lenient bare keys</h1>
 * <p>
 * This library allows "lenient" bare keys by default, as opposite to the "strict" bare keys which are
 * required by the TOML specification. Strict bare keys may only contain letters, numbers, underscores, and
 * dashes (A-Za-z0-9_-).
 * Lenient bare keys may contain any character except those before the space character in the unicode table
 * (tabs, newlines and many unprintables characters), spaces, points, square brackets, number signs and equal
 * signs (. [ ] # =).
 * </p>
 * <p>
 * The default setting when reading TOML data is lenient. You may set the behaviour regarding bare keys with
 * the methods
 * {@link #read(String, boolean)} and {@link #read(Reader, int, boolean)}, or by creating a {@link TomlReader}
 * yourself.
 * </p>
 * <p>
 * The {@link TomlWriter} always outputs data t strictly follows the TOML specification. Any key that contains
 * one or more non-strictly valid character is surrounded by quotes.
 * </p>
 *
 * @author TheElectronWill
 *
 */
public final class Toml {

	/**
	 * A DateTimeFormatter that uses the TOML format.
	 */
	public static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
			.append(DateTimeFormatter.ISO_LOCAL_DATE)
			.optionalStart()
			.appendLiteral('T')
			.append(DateTimeFormatter.ISO_LOCAL_TIME)
			.optionalStart()
			.appendOffsetId()
			.optionalEnd()
			.optionalEnd()
			.toFormatter();

	private Toml() {
	}

	/**
	 * Writes the specified data to a String, in the TOML format.
	 *
	 * @param data the data to write
	 * @return a String that contains the data in the TOML format.
	 * @throws IOException if an error occurs
	 */
	public static String writeToString(Map<String, TomlElement> data) throws IOException {
		FastStringWriter writer = new FastStringWriter();
		write(data, writer);
		return writer.toString();
	}

	/**
	 * Writes data to a File, in the TOML format and with the UTF-8 encoding. The default indentation
	 * parameters are used, ie each indent is one tab character.
	 *
	 * @param data the data to write
	 * @param file where to write the data
	 * @throws IOException if an error occurs
	 */
	public static void write(Map<String, TomlElement> data, File file) throws IOException {
		FileOutputStream out = new FileOutputStream(file);
		write(data, out);
	}

	/**
	 * Writes data to an OutputStream, in the TOML format and with the UTF-8 encoding. The default indentation
	 * parameters are used, ie each indent is one tab character.
	 *
	 * @param data the data to write
	 * @param out where to write the data
	 * @throws IOException if a read error occurs
	 * @throws TomlException if a parse error occurs
	 */
	public static void write(Map<String, TomlElement> data, OutputStream out) throws IOException {
		OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
		write(data, writer);
	}

	/**
	 * Writes data to a Writer, in the TOML format and with the default parameters, ie each indent is 1 tab
	 * character.
	 * This is the same as {@code write(data, writer, 1, false)}.
	 *
	 * @param data the data to write
	 * @param writer where to write the data
	 * @throws IOException if a read error occurs
	 * @throws TomlException if a parse error occurs
	 */
	public static void write(Map<String, TomlElement> data, Writer writer) throws IOException {
		TomlWriter tw = new TomlWriter(writer);
		tw.write(data);
		tw.close();
	}

	/**
	 * Writes the specified data to a Writer, in the TOML format and with the specified parameters.
	 *
	 * @param data the data to write
	 * @param writer where to write the data
	 * @param indentSize the indentation size, ie the number of times the indentation character is repeated in
	 * one indent.
	 * @param indentWithSpaces true to indent with spaces, false to indent with tabs
	 * @throws IOException if a read error occurs
	 * @throws TomlException if a parse error occurs
	 */
	public static void write(Map<String, TomlElement> data, Writer writer, int indentSize, boolean indentWithSpaces) throws IOException {
		TomlWriter tw = new TomlWriter(writer, indentSize, indentWithSpaces);
		tw.write(data);
		tw.close();
	}

	/**
	 * Reads a String that contains TOML data. Lenient bare keys are allowed (see {@link Toml}).
	 *
	 * @param toml a String containing TOML data
	 * @return a {@code Map<String, Object>} containing the parsed data
	 * @throws IOException if a read error occurs
	 * @throws TomlException if a parse error occurs
	 */
	public static Map<String, TomlElement> read(String toml) throws TomlException {
		return read(toml, false);
	}

	/**
	 * Reads a String that contains TOML data.
	 *
	 * @param toml a String containing TOML data
	 * @param strictAsciiBareKeys <code>true</code> to enforce strict bare keys (see {@link Toml}).
	 * @return a {@code Map<String, Object>} containing the parsed data
	 * @throws IOException if a read error occurs
	 * @throws TomlException if a parse error occurs
	 */
	public static Map<String, TomlElement> read(String toml, boolean strictAsciiBareKeys) {
		TomlReader tr = new TomlReader(toml, strictAsciiBareKeys);
		return tr.read();
	}

	/**
	 * Reads TOML data from an UTF-8 encoded File. Lenient bare keys are allowed (see {@link Toml}).
	 *
	 * @param file the File to read data from
	 * @return a {@code Map<String, Object>} containing the parsed data
	 * @throws IOException if a read error occurs
	 * @throws TomlException if a parse error occurs
	 */
	public static Map<String, TomlElement> read(File file) throws IOException, TomlException {
		return read(file, false);
	}

	/**
	 * Reads TOML data from an UTF-8 encoded File.
	 *
	 * @param file the File to read data from
	 * @param strictAsciiBareKeys <code>true</code> to enforce strict bare keys (see {@link Toml}).
	 * @return a {@code Map<String, Object>} containing the parsed data
	 * @throws IOException if a read error occurs
	 * @throws TomlException if a parse error occurs
	 */
	public static Map<String, TomlElement> read(File file, boolean strictAsciiBareKeys) throws IOException, TomlException {
		return read(new FileInputStream(file), strictAsciiBareKeys);
	}

	/**
	 * Reads TOML data from an UTF-8 encoded InputStream. Lenient bare keys are allowed (see {@link Toml}).
	 *
	 * @param in the InputStream to read data from
	 * @return a {@code Map<String, Object>} containing the parsed data
	 * @throws IOException if a read error occurs
	 * @throws TomlException if a parse error occurs
	 */
	public static Map<String, TomlElement> read(InputStream in) throws IOException, TomlException {
		return read(in, false);
	}

	/**
	 * Reads TOML data from an UTF-8 encoded InputStream.
	 *
	 * @param in the InputStream to read data from
	 * @param strictAsciiBareKeys <code>true</code> to enforce strict bare keys (see {@link Toml}).
	 * @return a {@code Map<String, Object>} containing the parsed data
	 * @throws IOException if a read error occurs
	 * @throws TomlException if a parse error occurs
	 */
	public static Map<String, TomlElement> read(InputStream in, boolean strictAsciiBareKeys) throws IOException, TomlException {
		return read(new InputStreamReader(in, StandardCharsets.UTF_8), in.available(), strictAsciiBareKeys);
	}

	/**
	 * Reads TOML data from a Reader. The data is read until the end of the stream is reached.
	 *
	 * @param reader the Reader to read data from
	 * @param bufferSize the initial size of the internal buffer that will contain the entire data.
	 * @param strictAsciiBareKeys <code>true</code> to enforce strict bare keys (see {@link Toml}).
	 * @return a {@code Map<String, Object>} containing the parsed data
	 * @throws IOException if a read error occurs
	 * @throws TomlException if a parse error occurs
	 */
	public static Map<String, TomlElement> read(Reader reader, int bufferSize, boolean strictAsciiBareKeys) throws IOException, TomlException {
		StringBuilder sb = new StringBuilder(bufferSize);
		char[] buf = new char[8192];
		int read;
		while ((read = reader.read(buf)) != -1) {
			sb.append(buf, 0, read);
		}
		TomlReader tr = new TomlReader(sb.toString(), strictAsciiBareKeys);
		return tr.read();
	}

}
