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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class RestoreCreationDatesFromZipTest {

	@TempDir
	File tempDir;

	@Test
	public void testChoosePageDirectoryWithOneWrappingFolder() {
		// the common case: one folder in the ZIP, the wiki pages inside it, plus OLD/ versions deeper down
		final String dir = RestoreCreationDatesFromZip.choosePageDirectory(Arrays.asList(
				"mybackup/Main.txt",
				"mybackup/SecondPage.txt",
				"mybackup/OLD/Main/1.txt",
				"mybackup/OLD/Main/2.txt"));
		Assertions.assertEquals("mybackup/", dir);
	}

	@Test
	public void testChoosePageDirectoryAtRoot() {
		final String dir = RestoreCreationDatesFromZip.choosePageDirectory(Arrays.asList(
				"Main.txt", "SecondPage.txt", "OLD/Main/1.txt"));
		Assertions.assertEquals("", dir);
	}

	@Test
	public void testChoosePageDirectoryWithDeepNesting() {
		final String dir = RestoreCreationDatesFromZip.choosePageDirectory(Arrays.asList(
				"export/2024/wikicontent/Main.txt",
				"export/2024/wikicontent/SecondPage.txt",
				"export/2024/wikicontent/OLD/Main/1.txt"));
		Assertions.assertEquals("export/2024/wikicontent/", dir);
	}

	@Test
	public void testBuildPageEntriesIncludesAllVersions() {
		final Map<String, Long> times = new LinkedHashMap<>();
		times.put("backup/Main.txt", millis("2018-04-12T09:31:00"));        // latest version of Main
		times.put("backup/SecondPage.txt", millis("2019-01-02T03:04:05"));  // latest version of SecondPage
		times.put("backup/OLD/Main/1.txt", millis("2010-01-01T00:00:00"));  // older version of Main

		final String dir = RestoreCreationDatesFromZip.choosePageDirectory(new ArrayList<>(times.keySet()));
		final SortedMap<String, String> entries = RestoreCreationDatesFromZip.buildPageEntries(times, dir);

		Assertions.assertEquals(Set.of("Main#latest", "SecondPage#latest", "Main#1"), entries.keySet(),
				"current page files plus every OLD version");
		assertSameSecond(millis("2018-04-12T09:31:00"), entries.get("Main#latest"));
		assertSameSecond(millis("2019-01-02T03:04:05"), entries.get("SecondPage#latest"));
		assertSameSecond(millis("2010-01-01T00:00:00"), entries.get("Main#1"));
	}

	@Test
	public void testReadFromRealZipNestedAndIgnoresNonTxt() throws IOException {
		final Map<String, Long> zipEntries = new LinkedHashMap<>();
		zipEntries.put("mybackup/Main.txt", millis("2018-04-12T09:31:00"));
		zipEntries.put("mybackup/My%20Page.txt", millis("2017-07-07T07:07:07"));
		zipEntries.put("mybackup/Main.properties", millis("2021-12-12T12:12:12")); // must be ignored
		zipEntries.put("mybackup/OLD/Main/1.txt", millis("2010-01-01T00:00:00")); // older version of Main
		final File zip = createZip(zipEntries);

		final Map<String, Long> txtTimes = RestoreCreationDatesFromZip.readTxtEntryTimes(zip);
		Assertions.assertEquals(Set.of("mybackup/Main.txt", "mybackup/My%20Page.txt", "mybackup/OLD/Main/1.txt"),
				txtTimes.keySet(), "only .txt entries are read");

		final String dir = RestoreCreationDatesFromZip.choosePageDirectory(new ArrayList<>(txtTimes.keySet()));
		Assertions.assertEquals("mybackup/", dir);

		final SortedMap<String, String> entries = RestoreCreationDatesFromZip.buildPageEntries(txtTimes, dir);
		Assertions.assertEquals(Set.of("Main#latest", "My%20Page#latest", "Main#1"), entries.keySet());
		assertSameSecond(millis("2018-04-12T09:31:00"), entries.get("Main#latest"));
		assertSameSecond(millis("2017-07-07T07:07:07"), entries.get("My%20Page#latest"));
		assertSameSecond(millis("2010-01-01T00:00:00"), entries.get("Main#1"));
	}

	@Test
	public void testMangledKeyMatchesProviderLookup() {
		// The ZIP stores wiki pages under their mangled (URL-encoded) file names, e.g. "CC1182808+Memo+1.txt",
		// so the generated restore key is "CC1182808+Memo+1". VersioningFileProvider looks the restore date up
		// via mangleName(pageName) first, which must reproduce exactly that encoded form for the lookup to hit.
		final VersioningFileProvider provider = new VersioningFileProvider();
		provider.m_encoding = AbstractFileProvider.DEFAULT_ENCODING;
		Assertions.assertEquals("CC1182808+Memo+1", provider.mangleName("CC1182808 Memo 1"),
				"mangleName must reproduce the URL-encoded file-name stem used as the restore key");
	}

	@Test
	public void testAttachmentCreationDatesFromZip() throws IOException {
		final Map<String, Long> zipEntries = new LinkedHashMap<>();
		zipEntries.put("mybackup/Main.txt", millis("2018-04-12T09:31:00"));
		zipEntries.put("mybackup/Main-att/diagram.png-dir/1.png", millis("2016-01-02T03:04:05")); // creation
		zipEntries.put("mybackup/Main-att/diagram.png-dir/2.png", millis("2020-01-02T03:04:05")); // later version
		zipEntries.put("mybackup/Main-att/diagram.png-dir/attachment.properties", millis("2021-01-01T00:00:00")); // ignored
		final File zip = createZip(zipEntries);

		final Map<String, Long> all = RestoreCreationDatesFromZip.readAllEntryTimes(zip);
		final String pageDir = RestoreCreationDatesFromZip.choosePageDirectory(
				new ArrayList<>(RestoreCreationDatesFromZip.readTxtEntryTimes(zip).keySet()));
		Assertions.assertEquals("mybackup/", pageDir);

		final SortedMap<String, String> attachments = RestoreCreationDatesFromZip.buildAttachmentEntries(all, pageDir);
		Assertions.assertEquals(Set.of("Main-att/diagram.png-dir#1", "Main-att/diagram.png-dir#2"), attachments.keySet(),
				"every attachment version, but not the property file");
		assertSameSecond(millis("2016-01-02T03:04:05"), attachments.get("Main-att/diagram.png-dir#1"));
		assertSameSecond(millis("2020-01-02T03:04:05"), attachments.get("Main-att/diagram.png-dir#2"));
	}

	// ----------------------------------------------------------------------------------------------------

	private File createZip(final Map<String, Long> entries) throws IOException {
		final File zip = new File(tempDir, "backup.zip");
		try (final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
			for (final Map.Entry<String, Long> e : entries.entrySet()) {
				final ZipEntry entry = new ZipEntry(e.getKey());
				entry.setLastModifiedTime(FileTime.fromMillis(e.getValue()));
				zos.putNextEntry(entry);
				zos.write(("content of " + e.getKey()).getBytes(StandardCharsets.UTF_8));
				zos.closeEntry();
			}
		}
		return zip;
	}

	private static long millis(final String localDateTime) {
		return LocalDateTime.parse(localDateTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	private static void assertSameSecond(final long expectedMillis, final String actualValue) {
		final Instant expected = Instant.ofEpochMilli(expectedMillis).truncatedTo(ChronoUnit.SECONDS);
		final Instant actual = OffsetDateTime.parse(actualValue).toInstant().truncatedTo(ChronoUnit.SECONDS);
		Assertions.assertEquals(expected, actual, "value " + actualValue);
	}
}
