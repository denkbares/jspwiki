/*
 * Copyright (C) 2024 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.utils;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.core.Page;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.api.exceptions.WikiException;
import org.apache.wiki.pages.PageManager;
import org.apache.wiki.tags.LinkTag;
import org.slf4j.LoggerFactory;

/**
 * @author Antonia Heyder (denkbares GmbH)
 * @created 11.01.2024
 */
public class WikiPageUtils {
	private static final String invalidCharsRegex = "[\\\\/|;:<>?*+]";
	private static final String invalidChars = "\\/|;:<>?*+";
	private static String m_jar_version;

	public static void checkDuplicatePagesCaseSensitive(Engine engine, String pageName) throws ProviderException {

		for (Page existingPage : engine.getManager(PageManager.class).getAllPages()) {
			if (existingPage.getName().equalsIgnoreCase(pageName)) {
				throw new ProviderException("Page already exists (case insensitive): " + pageName);
			}
		}
	}

	public static void checkIllegalCharacters(String renameTo) throws WikiException {
		if (renameTo.matches(".*" + invalidCharsRegex + ".*")) {
			throw new WikiException("Page name contains prohibited characters (" + invalidChars + ").");
		}
	}


	public static String getJarVersion() {
		if (m_jar_version != null) return m_jar_version;
		Class<? extends LinkTag> clazz = LinkTag.class;
		try {
			// Hole den URL zur .class-Datei der Klasse
			URL classUrl = clazz.getResource(clazz.getSimpleName() + ".class");
			if (classUrl != null) {
				URLConnection connection = classUrl.openConnection();
				long lastModified = connection.getLastModified();
				if (lastModified > 0) {
					m_jar_version = String.valueOf(lastModified);
				}
			}
		} catch (IOException e) {
			LoggerFactory.getLogger(clazz).warn("Couldn't get jar version, using current date", e);
		}
		if (m_jar_version == null) {
			m_jar_version = String.valueOf(System.currentTimeMillis());
		}
		return m_jar_version;
	}
}
