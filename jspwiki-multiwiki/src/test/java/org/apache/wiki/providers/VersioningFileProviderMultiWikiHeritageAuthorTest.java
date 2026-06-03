/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.providers;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import org.apache.wiki.TestEngine;
import org.apache.wiki.WikiPage;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.core.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest.WIKI_PREFIX_MAIN;
import static org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest.getMultiWikiNonNestedVersioningProperties;

/**
 * Regression test for the heritage-author migration under a real {@link VersioningFileProviderMultiWiki}.
 * <p>
 * The standalone tests in {@code VersioningFileProviderTest} exercise a <em>base</em>
 * {@link VersioningFileProvider}, where {@code getPageDirectory()} and {@code mangleName(globalName)} happen to
 * resolve the same file the {@link FileSystemProvider} wrote. They therefore cannot catch the multi-wiki bug.
 * <p>
 * Under multi-wiki the heritage {@code .properties} that a {@link FileSystemProviderMultiWiki} would have written
 * lives at {@code getPageDirectory(globalName)/mangleName(getLocalPageName(globalName)) + PROP_EXT}
 * (see {@link FileSystemProviderMultiWiki#getPagePropertiesFile(Page)}), but the (buggy) private
 * {@code VersioningFileProvider.readHeritageAuthor} reads {@code getPageDirectory()} (no-arg, GLOBALNAME-resolved
 * main folder) with {@code mangleName(globalName)} - i.e. the WRONG file name (and, for sub-wiki pages, the wrong
 * directory). The heritage author is therefore never found and version 1 ends up with {@code 1.author=unknown}.
 * <p>
 * This test writes the heritage file at the production write path (computed independently of
 * {@code readHeritageAuthor}), then drives the one-time creation-date batch via
 * {@link VersioningFileProvider#ensureCreationDateProperties(Page, Properties)} and asserts that the persisted
 * {@code OLD/<page>/page.properties} records {@code 1.author=brian}.
 * <p>
 * FAILS before the fix (author == "unknown"); PASSES after.
 */
class VersioningFileProviderMultiWikiHeritageAuthorTest {

	private static final String LOCAL_PAGE = "Test1";
	/** Global page name of a page in the MAIN sub-wiki, e.g. "Main&&Test1". */
	private static final String GLOBAL_PAGE = WIKI_PREFIX_MAIN + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + LOCAL_PAGE;
	private static final String HERITAGE_AUTHOR = "brian";

	/** Page directory the provider points at; the non-nested main wiki lives in the "Main" sub-folder below it. */
	@TempDir
	File pageDir;

	private TestEngine engine;

	@AfterEach
	void tearDown() {
		if (engine != null) {
			engine.shutdown();
		}
	}

	/**
	 * Builds a real non-nested multi-wiki engine (mainFolder = "Main", provider = VersioningFileProviderMultiWiki)
	 * and a freshly initialized {@link VersioningFileProviderMultiWiki} pointing at {@link #pageDir}.
	 */
	private VersioningFileProviderMultiWiki initProvider() throws Exception {
		// Real multi-wiki, non-nested, versioning engine. Carries the jspwiki.mainFolder=Main property that the
		// provider's getPageDirectory(pageName) consults (via m_engine.getWikiProperties()) to resolve sub-folders.
		final Properties engineProps = getMultiWikiNonNestedVersioningProperties();
		engine = TestEngine.build(engineProps);

		// Our own provider instance on an isolated temp page dir, so the creation-date batch can be exercised
		// directly and deterministically (the engine's own provider already ran its background batch on init).
		final Properties providerProps = new Properties();
		providerProps.putAll(engine.getWikiProperties());
		providerProps.setProperty(AbstractFileProvider.PROP_PAGEDIR, pageDir.getAbsolutePath());

		final VersioningFileProviderMultiWiki provider = new VersioningFileProviderMultiWiki();
		provider.initialize(engine, providerProps);
		return provider;
	}

	@Test
	void heritageAuthorIsMigratedForMainWikiPage() throws Exception {
		final VersioningFileProviderMultiWiki provider = initProvider();

		// --- Write the heritage .properties at the PRODUCTION write path, computed INDEPENDENTLY of
		//     readHeritageAuthor. This mirrors FileSystemProviderMultiWiki.getPagePropertiesFile exactly:
		//       getPageDirectory(globalName) / mangleName(getLocalPageName(globalName)) + PROP_EXT
		final File heritageDir = new File(provider.getPageDirectory(GLOBAL_PAGE));
		Assertions.assertTrue(heritageDir.exists() || heritageDir.mkdirs(), "could not create heritage dir");
		final File heritageFile = new File(heritageDir,
				provider.mangleName(SubWikiUtils.getLocalPageName(GLOBAL_PAGE)) + FileSystemProvider.PROP_EXT);
		try (final Writer out = Files.newBufferedWriter(heritageFile.toPath(), StandardCharsets.UTF_8)) {
			out.write(Page.AUTHOR + "=" + HERITAGE_AUTHOR + "\n");
		}
		Assertions.assertTrue(heritageFile.exists(), "heritage properties file was not written");

		// --- A never-versioned page (no OLD/<page>/page.properties yet): the latest <= 0 branch runs and
		//     readHeritageAuthor is invoked to fill in 1.author.
		final WikiPage page = new WikiPage((Engine) engine, GLOBAL_PAGE);
		page.setLastModified(new java.util.Date());

		// --- Drive the one-time creation-date batch. Must report a change (it writes 1.date and 1.author).
		Assertions.assertTrue(provider.ensureCreationDateProperties(page, new Properties()),
				"creation-date batch should have written version-1 properties");

		// --- Read back the persisted OLD/<page>/page.properties via the multi-wiki findOldPageDir override.
		final File persistedFile = new File(provider.findOldPageDir(GLOBAL_PAGE), VersioningFileProvider.PROPERTYFILE);
		Assertions.assertTrue(persistedFile.exists(), "OLD/<page>/page.properties was not persisted at " + persistedFile);
		final Properties persisted = new Properties();
		try (final InputStream in = new FileInputStream(persistedFile)) {
			persisted.load(in);
		}

		// --- The heritage author must have been migrated into version 1. Before the fix this is "unknown".
		Assertions.assertEquals(HERITAGE_AUTHOR, persisted.getProperty("1.author"),
				"version 1 author should be taken from the multi-wiki heritage properties (was 'unknown' before the fix)");
	}
}
