/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.providers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Some handy methods for file-system-based providers
 *
 * @author Albrecht Striffler (denkbares GmbH)
 * @created 26.09.2025
 */
public class FileSystemProviderUtils {

	private static final Logger LOG = LoggerFactory.getLogger(FileSystemProviderUtils.class);

	private static final DateTimeFormatter PROPERTIES_COMMENT_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter PROPERTIES_COMMENT_DATE_FORMAT_DE = PROPERTIES_COMMENT_DATE_FORMAT.withLocale(Locale.GERMAN);

	public static @Nullable ZonedDateTime extractDateFromPropertiesFileComment(File propertiesFile) {
		if (!propertiesFile.exists()) return null;
		try (BufferedReader reader = new BufferedReader(new FileReader(propertiesFile, StandardCharsets.UTF_8))) {
			reader.readLine();  // erste Zeile überspringen
			String dateLine = reader.readLine();  // zweite Zeile lesen
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
