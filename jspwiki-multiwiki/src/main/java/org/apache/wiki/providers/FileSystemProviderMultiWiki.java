/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.providers;

import java.io.File;

import org.apache.wiki.api.core.Page;
import org.apache.wiki.api.exceptions.ProviderException;

import static org.apache.wiki.providers.FileSystemProvider.PROP_EXT;

public class FileSystemProviderMultiWiki extends AbstractMultiWikiFileProvider {


	/**
	 *  {@inheritDoc}
	 * @param page
	 */
	@Override
	public void deletePage( final Page page) throws ProviderException {
		super.deletePage(page);
		// TODO: fix for multi wikis!!!
		final File file = new File( getPageDirectory(page.getName()), mangleName(page.getName())+ FileSystemProvider.PROP_EXT );
		if( file.exists() ) {
			file.delete();
		}
	}

	/**
	 *  {@inheritDoc}
	 */
	@Override
	public void movePage(final Page from, final String to ) throws ProviderException {
		// TODO: fix for multi-wiki!!
		final File fromPage = findPage( from.getName() );
		final File toPage = findPage( to );
		fromPage.renameTo( toPage );
	}

}
