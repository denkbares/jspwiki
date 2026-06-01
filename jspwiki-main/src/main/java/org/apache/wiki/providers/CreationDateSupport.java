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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Shared helpers for persisting creation/version dates in the page and attachment property files, and for the
 * optional {@code restore-creation-dates.properties} recovery mechanism. Used by both
 * {@link VersioningFileProvider} (pages) and {@link BasicAttachmentProvider} (attachments) so the stored date
 * format, the restore-date parsing and the logging stay consistent across both.
 */
final class CreationDateSupport {

	private static final Logger LOG = LoggerFactory.getLogger(CreationDateSupport.class);

	/**
	 * Cumulative wall-clock time of the creation-date batch phases (pages, attachments) that have run in this JVM.
	 * The phases live in separate providers and run once each on startup; the last phase to finish logs the grand
	 * total. The batches are flag-gated, so this is written at most once per phase per installation.
	 */
	private static final AtomicLong CUMULATIVE_BATCH_MILLIS = new AtomicLong(0L);

	private CreationDateSupport() {
	}

	/**
	 * Records a finished creation-date batch phase and returns the cumulative time across all phases run so far in
	 * this JVM (so the phase that finishes last reports the total duration).
	 */
	static long recordBatchDuration(final long millis) {
		return CUMULATIVE_BATCH_MILLIS.addAndGet(millis);
	}

	/**
	 * Formats a version/creation date the way it is stored in the {@code *.properties} files (ISO date-time).
	 */
	static String formatVersionDate(final ZonedDateTime date) {
		return date.format(DateTimeFormatter.ISO_DATE_TIME);
	}

	/**
	 * Parses a value from {@code restore-creation-dates.properties}. Accepts epoch milliseconds (all digits),
	 * ISO date-time with offset/zone, ISO local date-time (assumed to be in the system time zone, "T" or space
	 * separated) and ISO date only. Returns {@code null} (and logs) if the value is {@code null} or cannot be
	 * parsed.
	 */
	static ZonedDateTime parseRestoreDate(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		try {
			if (!trimmed.isEmpty() && trimmed.chars().allMatch(Character::isDigit)) {
				return ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(trimmed)), ZoneId.systemDefault());
			}
			final String iso = trimmed.replace(' ', 'T');
			try {
				return ZonedDateTime.parse(iso);
			}
			catch (final DateTimeException ignored) {
				// not an offset/zoned date-time, try the next format
			}
			try {
				return LocalDateTime.parse(iso).atZone(ZoneId.systemDefault());
			}
			catch (final DateTimeException ignored) {
				// not a local date-time, try date only
			}
			return LocalDate.parse(iso).atStartOfDay(ZoneId.systemDefault());
		}
		catch (final Exception e) {
			LOG.warn("Cannot parse restore creation date '" + value + "', ignoring it");
			return null;
		}
	}

	/**
	 * Loads the optional restore file. If the plain {@code .properties} file is not present, falls back to a
	 * zipped variant ({@code <name>.properties.zip} or {@code <name>.zip}) - the restore file can get large, so
	 * shipping it zipped is convenient. Returns an empty Properties object if nothing is present or readable
	 * (callers then simply fall back to the file-system timestamps).
	 */
	static Properties loadRestoreDates(final File restoreFile) {
		final Properties restore = new Properties();
		if (restoreFile.exists()) {
			try (final InputStream in = new BufferedInputStream(Files.newInputStream(restoreFile.toPath()))) {
				restore.load(in);
				LOG.info("Loaded " + restore.size() + " creation date(s) to restore from " + restoreFile.getAbsolutePath());
			}
			catch (final IOException e) {
				LOG.error("Unable to read " + restoreFile.getAbsolutePath() + ", ignoring restore dates", e);
			}
			return restore;
		}

		final File parent = restoreFile.getParentFile();
		final String plainName = restoreFile.getName();
		for (final File zipFile : new File[]{ new File(parent, plainName + ".zip"), new File(parent, stripExtension(plainName) + ".zip") }) {
			if (zipFile.exists()) {
				loadRestoreDatesFromZip(zipFile, plainName, restore);
				break;
			}
		}
		return restore;
	}

	/**
	 * Loads the restore properties from a ZIP file: prefers the entry named like the plain restore file,
	 * otherwise uses the first {@code *.properties} entry found.
	 */
	private static void loadRestoreDatesFromZip(final File zipFile, final String preferredEntryName, final Properties restore) {
		try (final ZipFile zip = new ZipFile(zipFile)) {
			ZipEntry entry = zip.getEntry(preferredEntryName);
			if (entry == null) {
				final Enumeration<? extends ZipEntry> entries = zip.entries();
				while (entries.hasMoreElements()) {
					final ZipEntry candidate = entries.nextElement();
					if (!candidate.isDirectory() && candidate.getName().toLowerCase(Locale.ROOT).endsWith(".properties")) {
						entry = candidate;
						break;
					}
				}
			}
			if (entry == null) {
				LOG.error("No .properties entry found in " + zipFile.getAbsolutePath() + ", ignoring restore dates");
				return;
			}
			try (final InputStream in = new BufferedInputStream(zip.getInputStream(entry))) {
				restore.load(in);
				LOG.info("Loaded " + restore.size() + " creation date(s) to restore from " + zipFile.getAbsolutePath() + " (entry '" + entry.getName() + "')");
			}
		}
		catch (final IOException e) {
			LOG.error("Unable to read " + zipFile.getAbsolutePath() + ", ignoring restore dates", e);
		}
	}

	private static String stripExtension(final String name) {
		final int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	/**
	 * Decides which date to persist for a version: the restored date if one is configured under any of the given
	 * keys (tried in order) <em>and</em> the file-system timestamp is newer than it - which means the timestamp
	 * was reset by a copy/zip, so the (older) backup date is the correct one. Otherwise the file-system date is
	 * kept. Logs whenever a date is restored. This is the single place that holds the restore <em>policy</em>;
	 * the providers only build their (layout-specific) keys and call this.
	 *
	 * @param restoreDates the loaded restore file (may be empty)
	 * @param fsDate       the date derived from the file-system timestamp
	 * @param label        human-readable identifier for logging, e.g. {@code "page 'Foo' version 2"}
	 * @param keys         restore keys to try in order (e.g. the mangled key first, then a plain fallback)
	 * @return the date to persist
	 */
	static ZonedDateTime preferRestoreDate(final Properties restoreDates, final ZonedDateTime fsDate, final String label, final String... keys) {
		if (!restoreDates.isEmpty()) {
			for (final String key : keys) {
				final ZonedDateTime restoreDate = parseRestoreDate(restoreDates.getProperty(key));
				if (restoreDate != null) {
					if (fsDate.isAfter(restoreDate)) {
						LOG.info("Restoring date of " + label + " from " + fsDate + " to " + restoreDate);
						return restoreDate;
					}
					return fsDate; // restore date present but not newer - keep the (already older) file-system date
				}
			}
		}
		return fsDate;
	}

	/**
	 * Formats a duration in milliseconds as a human-readable string (e.g. {@code "8m 3s"} or {@code "1h 2m 5s"}).
	 */
	static String formatDuration(final long millis) {
		final long totalSeconds = millis / 1000;
		final long hours = totalSeconds / 3600;
		final long minutes = (totalSeconds % 3600) / 60;
		final long seconds = totalSeconds % 60;
		if (hours > 0) {
			return hours + "h " + minutes + "m " + seconds + "s";
		}
		if (minutes > 0) {
			return minutes + "m " + seconds + "s";
		}
		return seconds + "s";
	}
}
