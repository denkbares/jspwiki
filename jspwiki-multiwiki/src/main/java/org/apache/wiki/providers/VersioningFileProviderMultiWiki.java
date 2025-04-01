/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.providers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Properties;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.wiki.InternalWikiException;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.core.Page;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.api.exceptions.ProviderException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class tries to implement something like a multi-inheritance, inheriting from {@link AbstractMultiWikiFileProvider}
 * and also from {@link VersioningFileProvider}. This is solved by extending VersioningFileProvider and
 * using a delegate instance inheriting from AbstractMultiWikiFileProvider. One caveat with that is, that if the
 * methods of the real instance and the delegate instance call each other, it gets cumbersome. Therefore,
 * this delegate requires a reference to the parent instance to override and delegate back...
 */
public class VersioningFileProviderMultiWiki extends VersioningFileProvider {

	private static final Logger LOG = LoggerFactory.getLogger(VersioningFileProviderMultiWiki.class);


	@Override
	public void initialize(final Engine engine, final Properties properties) throws NoRequiredPropertyException, IOException {
		super.initialize(engine, properties);
		initDelegateIfNecessary(engine, properties);
	}

	private void initDelegateIfNecessary(Engine engine, Properties properties) {
		if (!delegateInitialized) {
			try {
				delegateMultiWikiProvider = new VersioningFileProviderMultiWikiDelegate(this);
				delegateMultiWikiProvider.initialize(engine, properties);
				delegateInitialized = true;
			}
			catch (NoRequiredPropertyException | IOException e) {
				LOG.error("Problem with initializing delegate: " + e.getMessage());
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	protected Properties getVersioningProperties() throws IOException {
		final File versioningPropsFile = new File(getOldDir(null), VersioningFileProvider.VERSIONING_PROPERTIES_FILE);
		if (!versioningPropsFile.exists()) return new Properties();
		final Properties props;
		try (final InputStream in = new FileInputStream(versioningPropsFile)) {
			props = new Properties();
			props.load(in);
		}
		return props;
	}

	@Override
	protected void writeOldProperties(Properties properties) throws IOException {
		final File oldPropertiesFile = new File(getOldDir(null), VersioningFileProvider.VERSIONING_PROPERTIES_FILE);
		try (final OutputStream out = new FileOutputStream(oldPropertiesFile)) {
			properties.store(out, "JSPWiki versioning properties for");
		}
	}

	@Override
	protected File getOldDir(String page) {
		return new File(getPageDirectory(page), VersioningFileProvider.PAGEDIR);
	}


	/**
	 * Returns the directory where the old versions of the pages
	 * are being kept.
	 */
	@Override
	protected File findOldPageDir(final String page) {
		if (page == null) {
			throw new InternalWikiException("Page may NOT be null in the provider!");
		}
		final File oldpages = getOldDir(page);
		String localPageName = SubWikiUtils.getLocalPageName(page);
		return new File(oldpages, mangleName(localPageName));
	}

	/**
	 * All the other methods are delegated to a standard (non-versioning) MultiWikiFileProvider.
	 */
	private  AbstractFileProvider delegateMultiWikiProvider;
	private boolean delegateInitialized = false;

	private static class VersioningFileProviderMultiWikiDelegate extends AbstractMultiWikiFileProvider {

		private final VersioningFileProviderMultiWiki parent;

		public VersioningFileProviderMultiWikiDelegate(VersioningFileProviderMultiWiki parent) {
			this.parent = parent;
		}


		/**
		Here we need to delegate backwards to the parent instance, as {@link VersioningFileProviderMultiWikiDelegate#getAllPages()}
		 needs to call/use {@link VersioningFileProvider#getPageInfo(String, int)}.
		 */
		@Override
		public Page getPageInfo(String pageName, int version) {
			try {
				return this.parent.getPageInfo(pageName, version);
			}
			catch (ProviderException e) {
				LOG.error("Error getting PageInfo.");
				throw new RuntimeException(e);
			}
		}

		@Override
		public void movePage(Page from, String to) throws ProviderException {
			// will not be delegated and therefore does not need an implementation
			throw new NotImplementedException("Must not be delegated!");
		}
	}

	@Override
	String getPageDirectory() {
		return delegateMultiWikiProvider.getPageDirectory();
	}

	@Override
	String getPageDirectory(@Nullable String pageName) {
		initDelegateIfNecessary(m_engine, m_engine.getWikiProperties());
		return delegateMultiWikiProvider.getPageDirectory(pageName);
	}

	@Override
	protected File findPage(String page) {
		return delegateMultiWikiProvider.findPage(page);
	}

	@Override
	public Collection<Page> getAllPages() throws ProviderException {
		return delegateMultiWikiProvider.getAllPages();
	}


}
