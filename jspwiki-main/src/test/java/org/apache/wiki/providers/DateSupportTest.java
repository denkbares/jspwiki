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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DateSupportTest {

	@TempDir
	File tempDir;

	private static final String CONTENT = "Main#latest=2018-04-12T09:31:00+02:00\n";
	private static final String RESTORE_FILE = "restore-creation-dates.properties";

	@Test
	public void testLoadsPlainPropertiesFile() throws IOException {
		final File plain = new File(tempDir, RESTORE_FILE);
		Files.write(plain.toPath(), CONTENT.getBytes(StandardCharsets.ISO_8859_1));

		final Properties props = DateSupport.loadRestoreDates(plain);
		Assertions.assertEquals("2018-04-12T09:31:00+02:00", props.getProperty("Main#latest"));
	}

	@Test
	public void testLoadsFromPropertiesDotZip() throws IOException {
		// only "restore-creation-dates.properties.zip" present, no plain file
		writeZip(new File(tempDir, RESTORE_FILE + ".zip"), RESTORE_FILE);

		final Properties props = DateSupport.loadRestoreDates(new File(tempDir, RESTORE_FILE));
		Assertions.assertEquals("2018-04-12T09:31:00+02:00", props.getProperty("Main#latest"));
	}

	@Test
	public void testLoadsFromStrippedZipWithDifferentEntryName() throws IOException {
		// "restore-creation-dates.zip" with a differently named inner entry -> first *.properties entry is used
		writeZip(new File(tempDir, "restore-creation-dates.zip"), "whatever-the-admin-called-it.properties");

		final Properties props = DateSupport.loadRestoreDates(new File(tempDir, RESTORE_FILE));
		Assertions.assertEquals("2018-04-12T09:31:00+02:00", props.getProperty("Main#latest"));
	}

	@Test
	public void testPlainFileWinsOverZip() throws IOException {
		final File plain = new File(tempDir, RESTORE_FILE);
		Files.write(plain.toPath(), "Main#latest=2000-01-01T00:00:00+00:00\n".getBytes(StandardCharsets.ISO_8859_1));
		writeZip(new File(tempDir, RESTORE_FILE + ".zip"), RESTORE_FILE);

		final Properties props = DateSupport.loadRestoreDates(plain);
		Assertions.assertEquals("2000-01-01T00:00:00+00:00", props.getProperty("Main#latest"), "plain file must win over zip");
	}

	@Test
	public void testMissingReturnsEmpty() {
		Assertions.assertTrue(DateSupport.loadRestoreDates(new File(tempDir, RESTORE_FILE)).isEmpty());
	}

	@Test
	public void testFormatParseVersionDateRoundTrip() {
		final ZonedDateTime date = ZonedDateTime.of(2018, 4, 12, 9, 31, 0, 0, ZoneOffset.ofHours(2));
		final String formatted = DateSupport.formatVersionDate(date);
		Assertions.assertEquals(date.toInstant(), DateSupport.parseVersionDate(formatted).toInstant(),
				"parseVersionDate must be the inverse of formatVersionDate");
	}

	@Test
	public void testParseVersionDateThrowsOnGarbage() {
		Assertions.assertThrows(DateTimeException.class, () -> DateSupport.parseVersionDate("not-a-date"));
	}

	@Test
	public void testExtractDateFromPropertiesFileComment() throws IOException {
		// Mimics what java.util.Properties.store() writes: a header comment on line 1 and the timestamp on line 2.
		final File props = new File(tempDir, "page.properties");
		Files.write(props.toPath(),
				"#JSPWiki page properties\n#Thu Apr 12 09:31:00 GMT 2018\n1.author=foo\n".getBytes(StandardCharsets.ISO_8859_1));

		final ZonedDateTime date = DateSupport.extractDateFromPropertiesFileComment(props);
		Assertions.assertNotNull(date, "should parse the 2nd-line comment timestamp");
		Assertions.assertEquals(Instant.parse("2018-04-12T09:31:00Z"), date.toInstant());
	}

	@Test
	public void testExtractDateFromMissingFileReturnsNull() {
		Assertions.assertNull(DateSupport.extractDateFromPropertiesFileComment(new File(tempDir, "absent.properties")));
	}

	private void writeZip(final File zipFile, final String entryName) throws IOException {
		try (final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
			zos.putNextEntry(new ZipEntry(entryName));
			zos.write(CONTENT.getBytes(StandardCharsets.ISO_8859_1));
			zos.closeEntry();
		}
	}
}
