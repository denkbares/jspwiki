/*
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
 */
package org.apache.wiki.providers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Stand-alone command line tool that generates a {@link VersioningFileProvider#RESTORE_CREATION_DATES_FILE
 * restore-creation-dates.properties} from a backup ZIP of the wiki content.
 * <p>
 * It is meant to recover creation dates that were lost when the wiki content was copied/zipped (which resets
 * the file system timestamps), but which are still preserved as entry timestamps inside an older backup ZIP.
 * The resulting properties file can be placed into the {@code OLD} directory so the
 * {@link VersioningFileProvider} batch can restore those dates (see {@code ensureCreationDateProperties}).
 * <p>
 * <b>Locating the wiki pages in the ZIP:</b> the tool descends through wrapper folders and uses the
 * <em>shallowest</em> directory that actually contains {@code .txt} files as the wiki page directory (on ties,
 * the directory with the most {@code .txt} files). So both {@code Main.txt} at the ZIP root and the common
 * {@code mybackup/Main.txt} (one wrapping folder) - or even deeper nesting - work. Only the {@code .txt} files
 * <em>directly</em> in that directory are used; version files below {@code OLD/} are ignored.
 * <p>
 * The property key is the file name without the {@code .txt} extension (i.e. the mangled page name, exactly as
 * stored on disk), which is what {@link VersioningFileProvider} looks up. The value is the ISO date-time of the
 * ZIP entry.
 * <p>
 * Usage: {@code java -cp <jspwiki.jar> org.apache.wiki.providers.RestoreCreationDatesFromZip <backup.zip> [output.properties]}
 */
public final class RestoreCreationDatesFromZip {

	private static final DateTimeFormatter VALUE_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private RestoreCreationDatesFromZip() {
	}

	public static void main(final String[] args) throws IOException {
		if (args.length < 1) {
			System.err.println("Usage: java " + RestoreCreationDatesFromZip.class.getName() + " <backup.zip> [output.properties]");
			System.exit(2);
			return;
		}

		final File zipFile = new File(args[0]);
		if (!zipFile.isFile()) {
			System.err.println("ZIP file not found: " + zipFile.getAbsolutePath());
			System.exit(2);
			return;
		}

		final File outputFile = args.length >= 2
				? new File(args[1])
				: new File(zipFile.getAbsoluteFile().getParentFile(), VersioningFileProvider.RESTORE_CREATION_DATES_FILE);

		final Map<String, Long> txtTimes = readTxtEntryTimes(zipFile);
		if (txtTimes.isEmpty()) {
			System.out.println("No .txt entries found in " + zipFile.getName() + " - nothing to do.");
			return;
		}

		final String pageDir = choosePageDirectory(new ArrayList<>(txtTimes.keySet()));
		final SortedMap<String, String> entries = buildRestoreEntries(txtTimes, pageDir);
		writeProperties(outputFile, entries, zipFile, pageDir);

		System.out.println("Detected wiki page directory in ZIP: '" + displayDir(pageDir) + "'");
		System.out.println("Wrote " + entries.size() + " creation date(s) to " + outputFile.getAbsolutePath());
	}

	/**
	 * Reads all {@code .txt} entries (recursively) from the ZIP and returns a map of normalized entry path to
	 * its last-modified time in epoch milliseconds. Entries without a usable timestamp are skipped.
	 */
	static Map<String, Long> readTxtEntryTimes(final File zipFile) throws IOException {
		final Map<String, Long> result = new LinkedHashMap<>();
		try (final ZipFile zip = new ZipFile(zipFile)) {
			final Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				final ZipEntry entry = entries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}
				final String name = normalize(entry.getName());
				if (!name.toLowerCase(Locale.ROOT).endsWith(AbstractFileProvider.FILE_EXT)) {
					continue;
				}
				final long millis = entryMillis(entry);
				if (millis >= 0) {
					result.put(name, millis);
				}
			}
		}
		return result;
	}

	private static long entryMillis(final ZipEntry entry) {
		final FileTime lastModified = entry.getLastModifiedTime();
		if (lastModified != null) {
			return lastModified.toMillis();
		}
		return entry.getTime(); // -1 if not stored
	}

	/**
	 * Determines the wiki page directory: the shallowest directory that directly contains {@code .txt} files;
	 * on ties, the one with the most {@code .txt} files (then lexicographically smallest). The returned prefix
	 * either ends with {@code '/'} or is the empty string for the ZIP root.
	 */
	static String choosePageDirectory(final List<String> txtPaths) {
		final Map<String, Integer> countsPerDir = new HashMap<>();
		for (final String path : txtPaths) {
			countsPerDir.merge(directoryOf(path), 1, Integer::sum);
		}
		return countsPerDir.entrySet().stream()
				.min(Comparator
						.comparingInt((final Map.Entry<String, Integer> e) -> depth(e.getKey())) // shallowest wins
						.thenComparing(e -> -e.getValue())                                        // then most .txt files
						.thenComparing(Map.Entry::getKey))                                        // then lexicographic
				.map(Map.Entry::getKey)
				.orElse("");
	}

	/**
	 * Builds the sorted {@code key -> ISO date-time} entries for all {@code .txt} files located directly in the
	 * given page directory (version files in sub-directories such as {@code OLD/} are excluded).
	 */
	static SortedMap<String, String> buildRestoreEntries(final Map<String, Long> txtTimes, final String pageDir) {
		final SortedMap<String, String> entries = new TreeMap<>();
		for (final Map.Entry<String, Long> e : txtTimes.entrySet()) {
			final String path = e.getKey();
			if (!isDirectChild(path, pageDir)) {
				continue;
			}
			final String fileName = path.substring(pageDir.length());
			final String key = fileName.substring(0, fileName.length() - AbstractFileProvider.FILE_EXT.length());
			entries.put(key, formatDate(e.getValue()));
		}
		return entries;
	}

	static String formatDate(final long epochMillis) {
		return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(VALUE_FORMAT);
	}

	private static void writeProperties(final File outputFile, final SortedMap<String, String> entries, final File zipFile, final String pageDir) throws IOException {
		try (final BufferedWriter w = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.ISO_8859_1)) {
			w.write("# " + VersioningFileProvider.RESTORE_CREATION_DATES_FILE);
			w.newLine();
			w.write("# generated from " + zipFile.getName() + " (wiki page directory: '" + displayDir(pageDir) + "')");
			w.newLine();
			w.write("# key = mangled wiki page file name (without " + AbstractFileProvider.FILE_EXT + "), value = creation date from the ZIP entry");
			w.newLine();
			for (final Map.Entry<String, String> e : entries.entrySet()) {
				w.write(e.getKey() + "=" + e.getValue());
				w.newLine();
			}
		}
	}

	private static String normalize(String name) {
		name = name.replace('\\', '/');
		while (name.startsWith("./")) {
			name = name.substring(2);
		}
		return name;
	}

	private static String directoryOf(final String path) {
		final int slash = path.lastIndexOf('/');
		return slash < 0 ? "" : path.substring(0, slash + 1);
	}

	private static int depth(final String dir) {
		int depth = 0;
		for (int i = 0; i < dir.length(); i++) {
			if (dir.charAt(i) == '/') {
				depth++;
			}
		}
		return depth;
	}

	private static boolean isDirectChild(final String path, final String pageDir) {
		if (!path.startsWith(pageDir)) {
			return false;
		}
		final String rest = path.substring(pageDir.length());
		return !rest.isEmpty() && rest.indexOf('/') < 0;
	}

	private static String displayDir(final String pageDir) {
		return pageDir.isEmpty() ? "<root>" : pageDir;
	}
}
