/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.providers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Properties;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.core.Page;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.api.providers.PageProvider;
import org.apache.wiki.util.TextUtil;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.wiki.providers.FileSystemProvider.PROP_EXT;

public class FileSystemProviderMultiWiki extends FileSystemProvider {

	private static final Logger LOG = LoggerFactory.getLogger(FileSystemProviderMultiWiki.class);
	Properties initProperties;

	@Override
	public void initialize(final Engine engine, final Properties properties) throws NoRequiredPropertyException, IOException{
		super.initialize(engine, properties);
		initProperties = properties;
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
	protected void putPageProperties(final Page page ) throws IOException {
		final Properties props = new Properties();
		final String author = page.getAuthor();
		final String changenote = page.getAttribute( Page.CHANGENOTE );
		final String viewcount = page.getAttribute( Page.VIEWCOUNT );

		if( author != null ) {
			props.setProperty( Page.AUTHOR, author );
		}

		if( changenote != null ) {
			props.setProperty( Page.CHANGENOTE, changenote );
		}

		if( viewcount != null ) {
			props.setProperty( Page.VIEWCOUNT, viewcount );
		}

		// Get additional custom properties from page and add to props
		getCustomProperties( page, props );

		String name = SubWikiUtils.getLocalPageName(page.getName());

		final File file = new File( getPageDirectory(null), mangleName(name) + PROP_EXT );
		try( final OutputStream out = Files.newOutputStream( file.toPath() ) ) {
			props.store( out, "JSPWiki page properties for page "+ name);
		}
	}

	@Override
	protected void getPageProperties(final Page page ) throws IOException {
		String name = SubWikiUtils.getLocalPageName(page.getName());
		final File file = new File( getPageDirectory(), mangleName( name) + PROP_EXT );
		if( file.exists() ) {
			try( final InputStream in = Files.newInputStream( file.toPath() ) ) {
				final Properties  props = new Properties();
				props.load( in );
				page.setAuthor( props.getProperty( Page.AUTHOR ) );

				final String changenote = props.getProperty( Page.CHANGENOTE );
				if( changenote != null ) {
					page.setAttribute( Page.CHANGENOTE, changenote );
				}

				final String viewcount = props.getProperty( Page.VIEWCOUNT );
				if( viewcount != null ) {
					page.setAttribute( Page.VIEWCOUNT, viewcount );
				}

				// Set the props values to the page attributes
				setCustomProperties( page, props );
			}
		}
	}

	/**
	 *  {@inheritDoc}
	 * @param page
	 */
	@Override
	public void deletePage( final Page page) throws ProviderException {
		super.deletePage(page);
		String name = SubWikiUtils.getLocalPageName(page.getName());
		final File file = new File( getPageDirectory(page.getName()), mangleName(name)+ FileSystemProvider.PROP_EXT );
		if( file.exists() ) {
			file.delete();
		}
	}

	/**
	 *  {@inheritDoc}
	 */
	@Override
	public void movePage(final Page from, final String to ) throws ProviderException {
		// TODO: test or fix for multi-wiki!!
		final File fromPage = findPage( from.getName() );
		final File toPage = findPage( to );
		fromPage.renameTo( toPage );
	}

	/**
	 * All the other methods are delegated to a standard (non-versioning) MultiWikiFileProvider.
	 */
	private AbstractFileProvider delegateMultiWikiProvider;
	private boolean delegateInitialized = false;

	private static class VersioningFileProviderMultiWikiDelegate extends AbstractMultiWikiFileProvider {

		private final PageProvider parent;

		VersioningFileProviderMultiWikiDelegate(PageProvider parent) {
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
		initDelegateIfNecessary(m_engine, initProperties);
		return delegateMultiWikiProvider.getPageDirectory(pageName);
	}

	@Override
	protected File findPage(String page) {
		initDelegateIfNecessary(m_engine,initProperties);
		return delegateMultiWikiProvider.findPage(page);
	}

	@Override
	public Collection<Page> getAllPages() throws ProviderException {
		initDelegateIfNecessary(m_engine,initProperties);
		return delegateMultiWikiProvider.getAllPages();
	}



}
