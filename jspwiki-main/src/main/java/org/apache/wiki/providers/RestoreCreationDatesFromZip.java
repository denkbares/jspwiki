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
 * It is meant to recover version dates (the creation date and all later versions) that were lost when the wiki
 * content was copied/zipped (which resets the file system timestamps), but which are still preserved as entry
 * timestamps inside an older backup ZIP. The resulting properties file can be placed into the {@code OLD}
 * directory so the page and attachment batches can restore those dates.
 * <p>
 * <b>Locating the wiki pages in the ZIP:</b> the tool descends through wrapper folders and uses the
 * <em>shallowest</em> directory that actually contains {@code .txt} files as the wiki page directory (on ties,
 * the directory with the most {@code .txt} files). So both {@code Main.txt} at the ZIP root and the common
 * {@code mybackup/Main.txt} (one wrapping folder) - or even deeper nesting - work.
 * <p>
 * One entry is written per version, with the version encoded in the key (exactly as the providers look it up):
 * <ul>
 * <li>current page file {@code <stem>.txt} &rarr; key {@code <stem>#latest}</li>
 * <li>old page version {@code OLD/<stem>/<n>.txt} &rarr; key {@code <stem>#<n>}</li>
 * <li>attachment version {@code <page>-att/<dir>/<n>.<ext>} &rarr; key {@code <page>-att/<dir>#<n>}</li>
 * </ul>
 * The value is the ISO date-time of the ZIP entry.
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

		final Map<String, Long> allTimes = readAllEntryTimes(zipFile);
		if (allTimes.isEmpty()) {
			System.out.println("No entries with usable timestamps found in " + zipFile.getName() + " - nothing to do.");
			return;
		}
		final Map<String, Long> txtTimes = filterByExtension(allTimes, AbstractFileProvider.FILE_EXT);

		final String pageDir = choosePageDirectory(new ArrayList<>(txtTimes.keySet()));
		final SortedMap<String, String> pageEntries = buildPageEntries(allTimes, pageDir);
		final SortedMap<String, String> attachmentEntries = buildAttachmentEntries(allTimes, pageDir);

		final SortedMap<String, String> entries = new TreeMap<>(pageEntries);
		entries.putAll(attachmentEntries);
		writeProperties(outputFile, entries, zipFile, pageDir);

		System.out.println("Detected wiki page directory in ZIP: '" + displayDir(pageDir) + "'");
		System.out.println("Wrote " + pageEntries.size() + " page + " + attachmentEntries.size()
				+ " attachment creation date(s) to " + outputFile.getAbsolutePath());
	}

	/**
	 * Reads all (non-directory) entries from the ZIP and returns a map of normalized entry path to its
	 * last-modified time in epoch milliseconds. Entries without a usable timestamp are skipped.
	 */
	static Map<String, Long> readAllEntryTimes(final File zipFile) throws IOException {
		final Map<String, Long> result = new LinkedHashMap<>();
		try (final ZipFile zip = new ZipFile(zipFile)) {
			final Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				final ZipEntry entry = entries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}
				final long millis = entryMillis(entry);
				if (millis >= 0) {
					result.put(normalize(entry.getName()), millis);
				}
			}
		}
		return result;
	}

	/**
	 * Reads the {@code .txt} entries (the wiki pages) from the ZIP.
	 */
	static Map<String, Long> readTxtEntryTimes(final File zipFile) throws IOException {
		return filterByExtension(readAllEntryTimes(zipFile), AbstractFileProvider.FILE_EXT);
	}

	private static Map<String, Long> filterByExtension(final Map<String, Long> entries, final String extension) {
		final Map<String, Long> filtered = new LinkedHashMap<>();
		for (final Map.Entry<String, Long> e : entries.entrySet()) {
			if (e.getKey().toLowerCase(Locale.ROOT).endsWith(extension)) {
				filtered.put(e.getKey(), e.getValue());
			}
		}
		return filtered;
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
	static SortedMap<String, String> buildPageEntries(final Map<String, Long> allTimes, final String pageDir) {
		final SortedMap<String, String> entries = new TreeMap<>();
		for (final Map.Entry<String, Long> e : allTimes.entrySet()) {
			final String path = e.getKey();
			if (!path.startsWith(pageDir)) {
				continue;
			}
			final String[] segments = path.substring(pageDir.length()).split("/");
			if (segments.length == 1 && segments[0].toLowerCase(Locale.ROOT).endsWith(AbstractFileProvider.FILE_EXT)) {
				// current page file = latest version (its version number is not encoded in the file name)
				final String stem = segments[0].substring(0, segments[0].length() - AbstractFileProvider.FILE_EXT.length());
				entries.put(stem + "#latest", formatDate(e.getValue()));
			}
			else if (segments.length == 3 && segments[0].equals(VersioningFileProvider.PAGEDIR)
					&& segments[2].toLowerCase(Locale.ROOT).endsWith(AbstractFileProvider.FILE_EXT)) {
				// OLD/<stem>/<n>.txt = older version
				final int version = versionNumber(segments[2]);
				if (version > 0) {
					entries.put(segments[1] + "#" + version, formatDate(e.getValue()));
				}
			}
		}
		return entries;
	}

	/**
	 * Builds the {@code key -> ISO date-time} entries for all attachment versions. An attachment version file
	 * sits at {@code <pageDir>/<page>-att/<attachment-dir>/<n>.<ext>}; the key is
	 * {@code <page>-att/<attachment-dir>#<n>}, which is exactly what {@link BasicAttachmentProvider} reconstructs
	 * when looking the date up. The {@code attachment.properties} file is ignored.
	 */
	static SortedMap<String, String> buildAttachmentEntries(final Map<String, Long> allTimes, final String pageDir) {
		final SortedMap<String, String> entries = new TreeMap<>();
		for (final Map.Entry<String, Long> e : allTimes.entrySet()) {
			final String path = e.getKey();
			if (!path.startsWith(pageDir)) {
				continue;
			}
			final String[] segments = path.substring(pageDir.length()).split("/");
			// <page>-att / <attachment-dir> / <versionFile>
			if (segments.length != 3 || !segments[0].endsWith(BasicAttachmentProvider.DIR_EXTENSION)) {
				continue;
			}
			final int version = versionNumber(segments[2]);
			if (version > 0) {
				entries.put(segments[0] + "/" + segments[1] + "#" + version, formatDate(e.getValue()));
			}
		}
		return entries;
	}

	/**
	 * Returns the version number encoded in a version file name (the part before the first dot), or -1 if it is
	 * not a numbered version file (e.g. {@code attachment.properties} or {@code page.properties}).
	 */
	private static int versionNumber(final String fileName) {
		final int dot = fileName.indexOf('.');
		final String number = (dot > 0) ? fileName.substring(0, dot) : fileName;
		try {
			return Integer.parseInt(number);
		}
		catch (final NumberFormatException e) {
			return -1;
		}
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
			w.write("# page key = <mangled-page>#<version|latest>; "
					+ "attachment key = <page" + BasicAttachmentProvider.DIR_EXTENSION + ">/<attachment-dir>#<version>; "
					+ "value = date from the ZIP entry");
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

	private static String displayDir(final String pageDir) {
		return pageDir.isEmpty() ? "<root>" : pageDir;
	}
}
