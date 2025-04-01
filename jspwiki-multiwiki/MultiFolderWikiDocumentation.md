MultiFolder Extension
========================
The JSPWiki fork has been extended to manage sub-wiki-folders in a way that allows to run multiple 
wiki content-folders in one JSPWiki (i.e. also KnowWE).
There is one main wiki folder which is the same folder as before the extension, hence the extension is of course 100% backwards 
compatible. 
The idea is to consider wikis like modules with dependencies, i.e. one wiki might know (link to) another.


HowTo Configure/Activate the MultiFolder Extension
--------------
Use the jspwiki-properties file to configure the components that activate the MultiWiki functionality.

```
jspwiki.renderingManager.markupParser = org.apache.wiki.parser.JSPWikiMarkupParserMultiWiki
jspwiki.pageRenamer = DefaultPageRenamerMultiWiki
jspwiki.attachmentProvider = BasicAttachmentProviderMultiWiki
jspwiki.pageProvider = VersioningFileProviderMultiWiki # with page versioning
# jspwiki.pageProvider = FileSystemProviderMultiWiki # if no versioning is intended
```

Note: All four extensions are required for having a consistently working wiki engine!

Pitfalls & Non-backwards-compatibilities
---------------
There are two minor dangers regarding backwards compatibility:
* If a wiki content has page names that contain the sequence `&&` then it will be the apocalypse. -> not allowed (@see SubWikiUtils.java)
* Hopefully never been done because it would be weird: If you have/had inside your wiki folder arbitrary other folders folder besides the attachment folders, 
then those will now be interpreted as sub-wikis while they had been ignored before.

Limitations
---------------
The sub-wiki mechanism right now is not (yet) working recursively. Hence, You may only have one level of nested wikis.
Maybe this possibility will be added in the future.

Sub-Wikis
========================
Sub-wikis are sub-folders within the wiki content folder of the main wiki. The folder name defines the namespace of that 
sub-wiki. They should not refer to outside to the main-wiki or to sibling wiki as a sub-wiki is thought of as an 
independent module, not knowing other wikis outside. Consequently, normal wiki links in a sub-wiki always refer to the
local sub-wiki namespace.

__Note:__ Attachment folders (ending on `-att` and History folders `OLD`) are of course excluded from sub-wiki interpretation.
Further, also `.git` folders are excluded.

Attachments
---------------
Attachments are stored in each sub-wiki-folder separately.

History via VersioningFileProvider
---------------
The VersioningFileProvider has been extended to manage the history of each (sub-)wiki in its distinct `OLD`-Folder.
Hence, after editing multiple Wikis together, they can be detached from another again, started separately and the history
prevails.

References from Main-Wiki to Sub-Wiki
---------------------------------------
When we have a page in a sub-wiki `Sub1` such as `/mainWikiContent/Sub1/MyBike.txt` and You want to link to it from
main wiki, then we need to use the sub-wiki namespace as prefix in the link:

```
[Sub1&&MyBike]
```

__Note:__ If the page BikeStructure does not yet exist in the sub-wiki `Sub1` then it will be created there as usual in JSPWiki.
Furthermore: If the sub-wiki `Sub1` does not yet exist, it will be created, that is a corresponding wiki content folder with
name `Sub1` will be created within the main wiki content folder.


KnowWE-Compilation
-------------------
KnowWE is completely multi-folder-agnostic. The JSPWiki-`PageProvider` will for sub-wiki pages add
the sub-wiki namespace as prefix for the page name. Hence, the KnowWE-Article-Manager will treat everything as one wiki.
When using package-compilers, this can bring the benefit of flexible modular knowledge compilation.


Usage Modes:
========================
Nested Mode: (default)
---------------
If nothing else is configured, then the 'main' wiki content lives in the base-dir folder (as always). If no
additional sub-wikis exist, this is exactly as JSPWiki was working for ages. If however additional sub-wikis
exists, each lives inside the base-dir folder in a distinct sub-wiki-folder - leading to an untidy _nested_ folder structure.
In that folder structure, main-wiki files, main-wiki-attachment folders, the main-wiki-OLD-folder are wildly mixed up with the sub-wikis.
If You want to work with sub-wikis in a more tidy way, use the Flat-Mode as described below.

Flat Mode:
---------------
To confiure the flat-mode, You have to specify a distinct folder name for the main-wiki-content. Then, the main-wiki-content
(txt-files, attachments, OLD-folder,....) is stored there. You can do so by specifiying the folder by 'jspwiki.mainFolder' in
the jspwiki-custom.properties file, as for example:

```
jspwiki.mainFolder = MainContent
```
In that example, the main-wiki-content, for example the LeftMenu.txt, will be stored as: `$basedir/MainContent/LeftMenu.txt`

__Note:__ In the flat mode, You can create links from any sub-wiki into the main wiki. That is not possible in the nested mode. 
