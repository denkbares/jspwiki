/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.providers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.wiki.api.core.Attachment;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.api.spi.Wiki;
import org.apache.wiki.pages.PageTimeComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicAttachmentProviderMultiWiki extends BasicAttachmentProvider {

	private static final Logger LOG = LoggerFactory.getLogger(BasicAttachmentProviderMultiWiki.class);

	// sub-wiki-folders; for jspwiki the folder is a prefix/namespace in the page name, e.g. /Subwiki/Main.txt is page Subwiki::Main
	private Collection<String> subfolders;

	@Override
	public void initialize(final Engine engine, final Properties properties) throws NoRequiredPropertyException, IOException {
		super.initialize(engine, properties);
		this.subfolders = SubWikiUtils.getAllSubWikiFoldersWithoutMain(m_storageDir, properties);
	}


	@Override
	protected File findPageDir(String wikipage) throws ProviderException {
		String localPageName = SubWikiUtils.getLocalPageName(wikipage);
		String subWikiFolder = SubWikiUtils.getSubFolderNameOfPage(wikipage, m_engine.getWikiProperties());
		String folderPathSuffix = "";
		if (subWikiFolder != null && !subWikiFolder.isEmpty()) {
			folderPathSuffix = File.separator + subWikiFolder;
		}

		wikipage = mangleName(localPageName);

		final File f = new File(m_storageDir + folderPathSuffix, wikipage + BasicAttachmentProvider.DIR_EXTENSION);
		if (f.exists() && !f.isDirectory()) {
			throw new ProviderException("Storage dir '" + f.getAbsolutePath() + "' is not a directory!");
		}

		return f;
	}


	@Override
	public List<Attachment> listAllChanged(final Date timestamp) throws ProviderException {
		attachmentLock.readLock().lock();
		try {
			final ArrayList<Attachment> list = new ArrayList<>(collectAttachments(timestamp, SubWikiUtils.getMainWikiFolder(m_engine.getWikiProperties())));
			for (String subfolder : subfolders) {
				list.addAll(collectAttachments(timestamp, subfolder));
			}
			return list;
		}
		catch (Exception e) {
			LOG.error("Problem collecting attachments: "+e.getMessage());
			throw new RuntimeException(e);
		}
		finally {
			attachmentLock.readLock().unlock();
		}
	}

	private String getPageDirectoryMain() {
		return SubWikiUtils.getPageDirectory(null, m_storageDir, this.m_engine.getWikiProperties());
	}

	private Collection<Attachment> collectAttachments(Date timestamp, String subfolder) throws ProviderException {
		attachmentLock.readLock().lock();
		try {
			String folder;
			if (subfolder == null || subfolder.isBlank()) {
				// this is the case that the main wiki is directly in the storage dir
				folder = getPageDirectoryMain();
			}
			else {
				folder = m_storageDir + File.separator + subfolder;
			}
			List<Attachment> list = new ArrayList<>();
			final File attDir = new File(folder);
			if (!attDir.exists()) {
				// might be initialization of a new empty wiki folder, hence just create the folder
				attDir.mkdirs();
			}

			final String[] pagesWithAttachments = attDir.list(new AttachmentFilter());

			if (pagesWithAttachments != null) {
				for (final String pagesWithAttachment : pagesWithAttachments) {
					String pageId = SubWikiUtils.concatSubWikiAndLocalPageName(subfolder, unmangleName(pagesWithAttachment), m_engine.getWikiProperties());
					pageId = pageId.substring(0, pageId.length() - BasicAttachmentProvider.DIR_EXTENSION.length());

					final Collection<Attachment> c = listAttachments(Wiki.contents().page(m_engine, pageId));
					for (final Attachment att : c) {
						if (att.getLastModified().after(timestamp)) {
							list.add(att);
						}
					}
				}
			}

			list.sort(new PageTimeComparator());
			return list;
		}
		finally {
			attachmentLock.readLock().unlock();
		}
	}
}