package org.apache.wiki;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.apache.wiki.providers.AbstractMultiWikiFileProvider;
import org.apache.wiki.providers.SubWikiUtils;
import org.jetbrains.annotations.NotNull;

public class SubWikiInit {

	public static @NotNull List<String> getAllSubWikiFoldersWithoutMain(@NotNull Properties wikiProperties) {
		String mainWikiFolder = SubWikiUtils.getMainWikiFolder(wikiProperties);
		List<String> allSubWikiFoldersInclMain = getAllSubWikiFoldersInclMain(AbstractMultiWikiFileProvider.get_m_pageDirectory(wikiProperties));
		allSubWikiFoldersInclMain.remove(mainWikiFolder);
		return allSubWikiFoldersInclMain;
	}

	public static @NotNull List<String> getAllSubWikiFoldersInclMain(@NotNull String m_pageDirectory) {
		List<String> result = new ArrayList<>();
		File baseDir = new File(m_pageDirectory);
		File[] folders = baseDir.listFiles();
		assert folders != null;
		Collection<File> filteredFolders = Arrays.stream(folders).filter(file ->
				file.isDirectory()
						&& !file.getAbsolutePath().equals(".git")
						&& !file.getAbsolutePath().equals(m_pageDirectory)
						&& !file.getName().equals("OLD")
						&& !file.getParentFile().getName().equals("OLD")
						&& !file.getName().endsWith("-att")
						&& !file.getParentFile().getName().endsWith("-att")
						&& !file.getName().equals("_metadata")
		).toList();
		filteredFolders.forEach(folder -> result.add(folder.getName()));
		return result;
	}
}
