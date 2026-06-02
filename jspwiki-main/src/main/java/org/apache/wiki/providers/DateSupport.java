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

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
 * The single home for date formatting/parsing used by the file-system providers: the stored version/creation
 * date format (ISO date-time), the optional {@code restore-creation-dates.properties} value format and lenient
 * parsing, the legacy {@code .properties}-comment timestamp recovery, plus the restore policy and one-time
 * batch-duration bookkeeping. Used by {@link VersioningFileProvider} (pages), {@link BasicAttachmentProvider}
 * (attachments) and the {@link RestoreCreationDatesFromZip} tool so all of them keep one consistent set of date
 * formats and parsing rules.
 */
final class DateSupport {

	private static final Logger LOG = LoggerFactory.getLogger(DateSupport.class);

	/**
	 * Cumulative wall-clock time of the creation-date batch phases (pages, attachments) that have run in this JVM.
	 * The phases live in separate providers and run once each on startup; the last phase to finish logs the grand
	 * total. The batches are flag-gated, so this is written at most once per phase per installation.
	 */
	private static final AtomicLong CUMULATIVE_BATCH_MILLIS = new AtomicLong(0L);

	/**
	 * Format of the values in {@code restore-creation-dates.properties} as written by the
	 * {@link RestoreCreationDatesFromZip} tool. {@link #parseRestoreDate} reads this (among other accepted formats).
	 */
	private static final DateTimeFormatter RESTORE_VALUE_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	/**
	 * Format of the auto-generated comment timestamp that {@link java.util.Properties#store} writes on the second
	 * line of a {@code .properties} file (i.e. {@link java.util.Date#toString()}); English locale first, German as
	 * a fallback for files written under a German default locale.
	 */
	private static final DateTimeFormatter PROPERTIES_COMMENT_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter PROPERTIES_COMMENT_DATE_FORMAT_DE = PROPERTIES_COMMENT_DATE_FORMAT.withLocale(Locale.GERMAN);

	private DateSupport() {
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
	 * Parses a stored version/creation date previously written by {@link #formatVersionDate} (ISO date-time) -
	 * the inverse of that method, so the canonical stored format lives in exactly one place. Throws
	 * {@link java.time.DateTimeException} on an unparseable value (callers rely on this to fall back to other
	 * date sources), so it deliberately does <em>not</em> return {@code null}.
	 */
	static ZonedDateTime parseVersionDate(final String value) {
		return ZonedDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(value));
	}

	/**
	 * Formats an epoch-millisecond timestamp as a {@code restore-creation-dates.properties} value (ISO date-time
	 * with offset). This is the producer counterpart of {@link #parseRestoreDate}; used by
	 * {@link RestoreCreationDatesFromZip} so the file's producer and consumer share one format definition.
	 */
	static String formatRestoreValue(final long epochMillis) {
		return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(RESTORE_VALUE_FORMAT);
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
	 * kept. Logs each restored date at debug level (the batch phases log the aggregate count at info). This is the
	 * single place that holds the restore <em>policy</em>; the providers only build their (layout-specific) keys
	 * and call this.
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
						// Per-item detail at debug level only; the per-phase batch logs the restored-version count
						// as a summary, so INFO stays uniform (and un-spammy) across the page and attachment phases.
						LOG.debug("Restoring date of " + label + " from " + fsDate + " to " + restoreDate);
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

	/**
	 * Read-only legacy fallback: recovers a date from the auto-generated comment timestamp on the second line of
	 * a {@code .properties} file (written by {@link java.util.Properties#store}), used when a page/attachment has
	 * no parseable stored date property. Returns {@code null} if the file is absent, unreadable or unparseable.
	 */
	static @Nullable ZonedDateTime extractDateFromPropertiesFileComment(final File propertiesFile) {
		if (!propertiesFile.exists()) return null;
		try (BufferedReader reader = new BufferedReader(new FileReader(propertiesFile, StandardCharsets.UTF_8))) {
			reader.readLine();  // skip the first line
			String dateLine = reader.readLine();  // read the second line
			if (dateLine != null) {
				String cleaned = dateLine.replace("#", "").trim();
				try {
					return ZonedDateTime.parse(cleaned, PROPERTIES_COMMENT_DATE_FORMAT);
				}
				catch (Exception e) {
					return ZonedDateTime.parse(cleaned, PROPERTIES_COMMENT_DATE_FORMAT_DE);
				}
			}
		}
		catch (Exception e) {
			LOG.error("Cannot read last modified from properties file");
		}
		return null;
	}
}
