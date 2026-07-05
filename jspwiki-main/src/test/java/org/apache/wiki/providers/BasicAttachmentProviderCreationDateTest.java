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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Properties;

/**
 * Tests for the attachment creation-date batch ({@link BasicAttachmentProvider#ensureAttachmentCreationDates}):
 * persisting a date per version, the per-version restore from {@code restore-creation-dates.properties}, and the
 * precedence of an already-persisted date. Engine-free: it drives the provider's batch method directly.
 */
public class BasicAttachmentProviderCreationDateTest {

	@TempDir
	File tempDir;

	@Test
	public void testRestoreAppliesPerVersionAndFsKeptWithoutRestore() throws Exception {
		final File attDir = attachmentDir("SomePage-att", "diagram.png-dir");
		writeVersion(attDir, "1.png", "2020-01-01T00:00:00"); // file system newer than restore -> restore wins
		writeVersion(attDir, "2.png", "2021-02-02T00:00:00"); // no restore entry -> keep file system date

		final Properties restore = new Properties();
		restore.setProperty("SomePage-att/diagram.png-dir#1", "2016-05-05T05:05:05");

		final boolean changed = new BasicAttachmentProvider().ensureAttachmentCreationDates("SomePage-att", attDir, restore);
		Assertions.assertTrue(changed, "dates should be written");

		final Properties props = readProperties(attDir);
		assertSameSecond(millis("2016-05-05T05:05:05"), props.getProperty("1.date"), "version 1 restored from backup");
		assertSameSecond(millis("2021-02-02T00:00:00"), props.getProperty("2.date"), "version 2 keeps file system date");
	}

	@Test
	public void testFileSystemDateKeptWhenOlderThanRestore() throws Exception {
		final File attDir = attachmentDir("SomePage-att", "diagram.png-dir");
		writeVersion(attDir, "1.png", "2015-01-01T00:00:00"); // older than restore -> keep file system date

		final Properties restore = new Properties();
		restore.setProperty("SomePage-att/diagram.png-dir#1", "2020-06-15T10:00:00");

		new BasicAttachmentProvider().ensureAttachmentCreationDates("SomePage-att", attDir, restore);

		assertSameSecond(millis("2015-01-01T00:00:00"), readProperties(attDir).getProperty("1.date"),
				"file system date must be kept when it is older than the restore date");
	}

	@Test
	public void testExistingDateWinsOverRestore() throws Exception {
		final File attDir = attachmentDir("SomePage-att", "diagram.png-dir");
		writeVersion(attDir, "1.png", "2020-01-01T00:00:00");

		// pre-existing persisted date (e.g. written at upload time); must not be overwritten
		final Properties existing = new Properties();
		existing.setProperty("1.date", "2012-12-12T12:12:12");
		try (final OutputStream out = Files.newOutputStream(new File(attDir, BasicAttachmentProvider.PROPERTY_FILE).toPath())) {
			existing.store(out, "test");
		}

		final Properties restore = new Properties();
		restore.setProperty("SomePage-att/diagram.png-dir#1", "2016-05-05T05:05:05");

		final boolean changed = new BasicAttachmentProvider().ensureAttachmentCreationDates("SomePage-att", attDir, restore);
		Assertions.assertFalse(changed, "an already persisted date must not be changed");
		Assertions.assertEquals("2012-12-12T12:12:12", readProperties(attDir).getProperty("1.date"));
	}

	// ----------------------------------------------------------------------------------------------------

	private File attachmentDir(final String pageAttName, final String attachmentDirName) {
		final File dir = new File(new File(tempDir, pageAttName), attachmentDirName);
		dir.mkdirs();
		return dir;
	}

	private void writeVersion(final File attachmentDir, final String fileName, final String localDateTime) throws IOException {
		final File file = new File(attachmentDir, fileName);
		Files.write(file.toPath(), ("content of " + fileName).getBytes(StandardCharsets.UTF_8));
		file.setLastModified(millis(localDateTime));
	}

	private Properties readProperties(final File attachmentDir) throws IOException {
		final Properties props = new Properties();
		try (final InputStream in = new FileInputStream(new File(attachmentDir, BasicAttachmentProvider.PROPERTY_FILE))) {
			props.load(in);
		}
		return props;
	}

	private static long millis(final String localDateTime) {
		return LocalDateTime.parse(localDateTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	private static void assertSameSecond(final long expectedMillis, final String storedValue, final String message) {
		final Instant expected = Instant.ofEpochMilli(expectedMillis).truncatedTo(ChronoUnit.SECONDS);
		final Instant actual = Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(storedValue)).truncatedTo(ChronoUnit.SECONDS);
		Assertions.assertEquals(expected, actual, message + " (stored=" + storedValue + ")");
	}
}
