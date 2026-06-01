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
import java.util.Properties;

/**
 * Shared helpers for persisting creation/version dates in the page and attachment property files, and for the
 * optional {@code restore-creation-dates.properties} recovery mechanism. Used by both
 * {@link VersioningFileProvider} (pages) and {@link BasicAttachmentProvider} (attachments) so the stored date
 * format, the restore-date parsing and the logging stay consistent across both.
 */
final class CreationDateSupport {

	private static final Logger LOG = LoggerFactory.getLogger(CreationDateSupport.class);

	private CreationDateSupport() {
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
	 * Loads the optional restore file. Returns an empty Properties object if the file is missing or unreadable
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
		}
		return restore;
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
