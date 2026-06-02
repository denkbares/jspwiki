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

import org.apache.wiki.TestEngine;
import org.apache.wiki.WikiPage;
import org.apache.wiki.api.core.Context;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.core.Page;
import org.apache.wiki.api.providers.PageProvider;
import org.apache.wiki.api.spi.Wiki;
import org.apache.wiki.auth.Users;
import org.apache.wiki.cache.CachingManager;
import org.apache.wiki.pages.PageManager;
import org.apache.wiki.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.Writer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;

// FIXME: Should this thingy go directly to the VersioningFileProvider,
//        or should it rely on the WikiEngine API?

public class VersioningFileProviderTest {

    public static final String NAME1 = "Test1";
    private static final String OLD_AUTHOR = "brian";
    private static final String FAKE_HISTORY =
                "#JSPWiki page properties for page " + NAME1 + "\n"
                + "#Wed Jan 01 12:27:57 GMT 2012" + "\n"
                + "author=" + OLD_AUTHOR + "\n";

    private final Properties PROPS = TestEngine.getTestProperties( "/jspwiki-vers-custom.properties" );
    protected TestEngine engine = createEngine();

    protected TestEngine createEngine() {
        return TestEngine.build( PROPS );
    }

    // this is the testing page directory
    private String files = getWikiPageDirectory();

    protected String getWikiPageDirectory() {
        return engine.getWikiProperties().getProperty(AbstractFileProvider.PROP_PAGEDIR);
    }

    @AfterEach
    public void tearDown() {
        engine.stop();
    }

    /*
     * Checks if a page created or last modified by FileSystemProvider
     * will be seen by VersioningFileProvider as the "first" version.
     */
    @Test
    public void testMigrationInfoAvailable() throws IOException {
        // we cannot switch PageProviders within a single test, so the
        // initial FileSystemProvider wiki page must be faked.
        final String fakeWikiPage = "foobar";
        injectFile(NAME1+AbstractFileProvider.FILE_EXT, fakeWikiPage);

       // also create an associated properties file with some history
        injectFile(NAME1+FileSystemProvider.PROP_EXT, FAKE_HISTORY);

        final String res = engine.getManager( PageManager.class ).getText( NAME1 );
        Assertions.assertEquals( fakeWikiPage, res, "fetch latest should work" );

        final Page page = engine.getManager( PageManager.class ).getPage( NAME1, 1 );
        Assertions.assertEquals( 1, page.getVersion(), "original version expected" );
        Assertions.assertEquals( OLD_AUTHOR, page.getAuthor(), "original author" );
    }

    /*
     * Checks if migration from FileSystemProvider to VersioningFileProvider
     * works when a simple text file (without associated properties) exists,
     * but there is not yet any corresponding history content in OLD/
     */
    @Test
    public void testMigrationSimple() throws IOException {
        // we cannot switch PageProviders within a single test, so the
        // initial FileSystemProvider wiki page must be faked.
        injectFile(NAME1+AbstractFileProvider.FILE_EXT, "foobar");

        String res = engine.getManager( PageManager.class ).getText( NAME1 );
        Assertions.assertEquals( "foobar", res, "fetch latest did not work" );

        res = engine.getManager( PageManager.class ).getText( NAME1, 1 ); // Should be the first version.
        Assertions.assertEquals( "foobar", res, "fetch by direct version did not work" );

        final Page page = engine.getManager( PageManager.class ).getPage( NAME1 );
        Assertions.assertEquals( 1, page.getVersion(), "original version expected" );
        Assertions.assertNull( page.getAuthor(), "original author not expected" );
    }

    /*
     * Checks if migration from FileSystemProvider to VersioningFileProvider
     * works when a simple text file and its associated properties exist, but
     * when there is not yet any corresponding history content in OLD/
     */
    @Test
    public void testMigrationWithSimpleHistory() throws IOException {
        // we cannot switch PageProviders within a single test, so the
        // initial FileSystemProvider wiki page must be faked.
        final String fakeWikiPage = "foobar";
        injectFile(NAME1+AbstractFileProvider.FILE_EXT, fakeWikiPage);

       // now create the associated properties file with some history
        injectFile(NAME1+FileSystemProvider.PROP_EXT, FAKE_HISTORY);

        String res = engine.getManager( PageManager.class ).getText( NAME1 );
        Assertions.assertEquals( fakeWikiPage, res, "fetch latest did not work" );

        res = engine.getManager( PageManager.class ).getText( NAME1, 1 ); // Should be the first version.
        Assertions.assertEquals( fakeWikiPage, res, "fetch by direct version did not work" );

        final Page page = engine.getManager( PageManager.class ).getPage( NAME1, 1 );
        Assertions.assertEquals( 1, page.getVersion(), "original version expected" );
        Assertions.assertEquals( OLD_AUTHOR, page.getAuthor(), "original author" );
    }

    /**
     * Checks if migration from FileSystemProvider to VersioningFileProvider
     * works when a simple text file and its associated properties exist, but
     * when there is not yet any corresponding history content in OLD/.
     * Update the wiki page and confirm the original simple history was
     * assimilated into the newly-created properties.
     */
    @Test
    public void testMigrationChangesHistory() throws Exception {
        // we cannot switch PageProviders within a single test, so the
        // initial FileSystemProvider wiki page must be faked.
        final String fakeWikiPage = "foobar";
        injectFile(NAME1+AbstractFileProvider.FILE_EXT, fakeWikiPage);

       // also create an associated properties file with some history
        injectFile(NAME1+FileSystemProvider.PROP_EXT, FAKE_HISTORY);

        final String result1 = engine.getManager( PageManager.class ).getText( getPageName1() );
        Assertions.assertEquals( fakeWikiPage, result1, "latest should be initial" );

        // now update the wiki page to create a new version
        final String text = "diddo\r\n";
        engine.saveText( getPageName1(), text );

        // confirm the right number of versions have been recorded
        final List< WikiPage > versionHistory = engine.getManager( PageManager.class ).getVersionHistory(getPageName1() );
        Assertions.assertEquals( 2, versionHistory.size(), "number of versions" );

        // fetch the updated page
        final String result2 = engine.getManager( PageManager.class ).getText( getPageName1()  );
        Assertions.assertEquals( text, result2, "latest should be new version" );
        final String result3 = engine.getManager( PageManager.class ).getText( getPageName1() , 2 ); // Should be the 2nd version.
        Assertions.assertEquals( text, result3, "fetch new by version did not work" );

        // now confirm the original page has been archived
        final String result4 = engine.getManager( PageManager.class ).getText( getPageName1() , 1 );
        Assertions.assertEquals( fakeWikiPage, result4, "fetch original by version Assertions.failed" );

        final Page pageNew = engine.getManager( PageManager.class ).getPage( getPageName1() , 2 );
        Assertions.assertEquals( 2, pageNew.getVersion(), "new version" );
        Assertions.assertEquals( "Guest", pageNew.getAuthor(), "new author" );

        final Page pageOld = engine.getManager( PageManager.class ).getPage( getPageName1() , 1 );
        Assertions.assertEquals( 1, pageOld.getVersion(), "old version" );
        Assertions.assertEquals( OLD_AUTHOR, pageOld.getAuthor(), "old author" );
    }

    /*
     * Checks migration from FileSystemProvider to VersioningFileProvider
     * works after multiple updates to a page with existing properties.
     */
    @Test
    public void testMigrationMultiChangesHistory() throws Exception {
        // we cannot switch PageProviders within a single test, so the
        // initial FileSystemProvider wiki page must be faked.
        final String fakeWikiPage = "foobar";
        injectFile(NAME1+AbstractFileProvider.FILE_EXT, fakeWikiPage);

       // also create an associated properties file with some history
        injectFile(NAME1+FileSystemProvider.PROP_EXT, FAKE_HISTORY);

        // next update the wiki page to create a version number 2
        // with a different username
        final String text2 = "diddo\r\n";
        engine.saveTextAsJanne( getPageName1(), text2 );

        // finally, update the wiki page to create a version number 3
        final String text3 = "whateverNext\r\n";
        engine.saveText( getPageName1(), text3 );

        // confirm the right number of versions have been recorded
        final Collection< Page > versionHistory = engine.getManager( PageManager.class ).getVersionHistory(getPageName1());
        Assertions.assertEquals( 3, versionHistory.size(), "number of versions" );

        // fetch the latest version of the page
        final String result = engine.getManager( PageManager.class ).getText( getPageName1() );
        Assertions.assertEquals( text3, result, "latest should be newest version" );
        final String result2 = engine.getManager( PageManager.class ).getText( getPageName1(), 3 );
        Assertions.assertEquals( text3, result2, "fetch new by version did not work" );

        // confirm the original page was archived
        final String result3 = engine.getManager( PageManager.class ).getText( getPageName1(), 1 );
        Assertions.assertEquals( fakeWikiPage, result3, "fetch original by version Assertions.failed" );

        // confirm the first update was archived
        final String result4 = engine.getManager( PageManager.class ).getText( getPageName1(), 2 );
        Assertions.assertEquals( text2, result4, "fetch original by version Assertions.failed" );

        final Page pageNew = engine.getManager( PageManager.class ).getPage( getPageName1() );
        Assertions.assertEquals( 3, pageNew.getVersion(), "newest version" );
        Assertions.assertEquals( "Guest", pageNew.getAuthor(), "newest author" );

        final Page pageMiddle = engine.getManager( PageManager.class ).getPage( getPageName1(), 2 );
        Assertions.assertEquals( 2, pageMiddle.getVersion(), "middle version" );
        Assertions.assertEquals( Users.JANNE, pageMiddle.getAuthor(), "middle author" );

        final Page pageOld = engine.getManager( PageManager.class ).getPage( getPageName1(), 1 );
        Assertions.assertEquals( 1, pageOld.getVersion(), "old version" );
        Assertions.assertEquals( OLD_AUTHOR, pageOld.getAuthor(), "old author" );
    }

    /**
     * A variation of testMigrationMultiChangesHistory when caching is disabled.
     */
    @Test
    public void testMigrationMultiChangesNoCache() throws Exception {
        // discard the default engine, and get another with different properties
        // note: the originating properties file is unchanged.
        String cacheState = PROPS.getProperty( CachingManager.PROP_CACHE_ENABLE );
        Assertions.assertEquals( "true", cacheState, "should cache" );
        cacheState = "false";
        PROPS.setProperty( CachingManager.PROP_CACHE_ENABLE, cacheState );
        engine = new TestEngine(PROPS);

        // the new TestEngine will have assigned a new page directory
        files = engine.getWikiProperties().getProperty( AbstractFileProvider.PROP_PAGEDIR );

        // we cannot switch PageProviders within a single test, so the
        // initial FileSystemProvider wiki page must be faked.
        final String fakeWikiPage = "foobar";
        injectFile(NAME1+AbstractFileProvider.FILE_EXT, fakeWikiPage);

       // also create an associated properties file with some history
        injectFile(NAME1+FileSystemProvider.PROP_EXT, FAKE_HISTORY);

        // next update the wiki page to create a version number 2
        // with a different username
        final String text2 = "diddo\r\n";
        engine.saveTextAsJanne( NAME1, text2 );

        // finally, update the wiki page to create a version number 3
        final String text3 = "whateverNext\r\n";
        engine.saveText( NAME1, text3 );

        // confirm the right number of versions have been recorded
        final Collection< Page > versionHistory = engine.getManager( PageManager.class ).getVersionHistory(NAME1);
        Assertions.assertEquals( 3, versionHistory.size(), "number of versions" );

        // fetch the latest version of the page
        final String result = engine.getManager( PageManager.class ).getText( NAME1 );
        Assertions.assertEquals( text3, result, "latest should be newest version" );
        final String result2 = engine.getManager( PageManager.class ).getText( NAME1, 3 );
        Assertions.assertEquals( text3, result2, "fetch new by version did not work" );

        // confirm the original page was archived
        final String result3 = engine.getManager( PageManager.class ).getText( NAME1, 1 );
        Assertions.assertEquals( fakeWikiPage, result3, "fetch original by version Assertions.failed" );

        // confirm the first update was archived
        final String result4 = engine.getManager( PageManager.class ).getText( NAME1, 2 );
        Assertions.assertEquals( text2, result4, "fetch original by version Assertions.failed" );

        final Page pageNew = engine.getManager( PageManager.class ).getPage( NAME1 );
        Assertions.assertEquals( 3, pageNew.getVersion(), "newest version" );
        Assertions.assertEquals( "Guest", pageNew.getAuthor(), "newest author" );

        final Page pageMiddle = engine.getManager( PageManager.class ).getPage( NAME1, 2 );
        Assertions.assertEquals( 2, pageMiddle.getVersion(), "middle version" );
        Assertions.assertEquals( Users.JANNE, pageMiddle.getAuthor(), "middle author" );

        final Page pageOld = engine.getManager( PageManager.class ).getPage( NAME1, 1 );
        Assertions.assertEquals( 1, pageOld.getVersion(), "old version" );
        Assertions.assertEquals( OLD_AUTHOR, pageOld.getAuthor(), "old author" );
    }

    @Test
    public void testMillionChanges() throws Exception {
        String text = "";
        final int maxver = 100;           // Save 100 versions.
        for( int i = 0; i < maxver; i++ ) {
            text = text + ".";
            engine.saveText( NAME1, text );
        }

        final Page pageinfo = engine.getManager( PageManager.class ).getPage( NAME1 );
        Assertions.assertEquals( maxver, pageinfo.getVersion(), "wrong version" );

        // +2 comes from \r\n.
        Assertions.assertEquals( maxver+2, engine.getManager( PageManager.class ).getText(NAME1).length(), "wrong text" );
    }

    @Test
    public void testCheckin() throws Exception {
        final String text = "diddo\r\n";
        engine.saveText( NAME1, text );
        final String res = engine.getManager( PageManager.class ).getText(NAME1);
        Assertions.assertEquals( text, res );
    }

    @Test
    public void testGetByVersion() throws Exception {
        final String text = "diddo\r\n";
        engine.saveText( NAME1, text );
        final Page page = engine.getManager( PageManager.class ).getPage( NAME1, 1 );

        Assertions.assertEquals( getPageName1(), page.getName(), "name" );
        Assertions.assertEquals( 1, page.getVersion(), "version" );
    }

    protected String getPageName1() {
        return NAME1;
    }

    @Test
    public void testPageInfo() throws Exception {
        final String text = "diddo\r\n";
        engine.saveText( NAME1, text );
        final Page res = engine.getManager( PageManager.class ).getPage(NAME1);
        Assertions.assertEquals( 1, res.getVersion() );
    }

    @Test
    public void testGetOldVersion() throws Exception {
        final String text = "diddo\r\n";
        final String text2 = "barbar\r\n";
        final String text3 = "Barney\r\n";

        engine.saveText( NAME1, text );
        engine.saveText( NAME1, text2 );
        engine.saveText( NAME1, text3 );

        final Page res = engine.getManager( PageManager.class ).getPage(NAME1);
        Assertions.assertEquals( 3, res.getVersion(), "wrong version" );
        Assertions.assertEquals( text, engine.getManager( PageManager.class ).getText( NAME1, 1 ), "ver1" );
        Assertions.assertEquals( text2, engine.getManager( PageManager.class ).getText( NAME1, 2 ), "ver2" );
        Assertions.assertEquals( text3, engine.getManager( PageManager.class ).getText( NAME1, 3 ), "ver3" );
    }

    @Test
    public void testGetOldVersion2() throws Exception {
        final String text = "diddo\r\n";
        final String text2 = "barbar\r\n";
        final String text3 = "Barney\r\n";

        engine.saveText( NAME1, text );
        engine.saveText( NAME1, text2 );
        engine.saveText( NAME1, text3 );

        final Page res = engine.getManager( PageManager.class ).getPage(NAME1);
        Assertions.assertEquals( 3, res.getVersion(), "wrong version" );
        Assertions.assertEquals( 1, engine.getManager( PageManager.class ).getPage( NAME1, 1 ).getVersion(), "ver1" );
        Assertions.assertEquals( 2, engine.getManager( PageManager.class ).getPage( NAME1, 2 ).getVersion(), "ver2" );
        Assertions.assertEquals( 3, engine.getManager( PageManager.class ).getPage( NAME1, 3 ).getVersion(), "ver3" );
}

    /**
     *  2.0.7 and before got this wrong.
     */
    @Test
    public void testGetOldVersionUTF8() throws Exception {
        final String text = "\u00e5\u00e4\u00f6\r\n";
        final String text2 = "barbar\u00f6\u00f6\r\n";
        final String text3 = "Barney\u00e4\u00e4\r\n";

        engine.saveText( NAME1, text );
        engine.saveText( NAME1, text2 );
        engine.saveText( NAME1, text3 );

        final Page res = engine.getManager( PageManager.class ).getPage(NAME1);
        Assertions.assertEquals( 3, res.getVersion(), "wrong version" );
        Assertions.assertEquals( text, engine.getManager( PageManager.class ).getText( NAME1, 1 ), "ver1" );
        Assertions.assertEquals( text2, engine.getManager( PageManager.class ).getText( NAME1, 2 ), "ver2" );
        Assertions.assertEquals( text3, engine.getManager( PageManager.class ).getText( NAME1, 3 ), "ver3" );
    }

    @Test
    public void testNonexistentPage() {
        Assertions.assertNull( engine.getManager( PageManager.class ).getPage("fjewifjeiw") );
    }

    @Test
    public void testVersionHistory() throws Exception {
        final String text = "diddo\r\n";
        final String text2 = "barbar\r\n";
        final String text3 = "Barney\r\n";

        engine.saveText( NAME1, text );
        engine.saveText( NAME1, text2 );
        engine.saveText( NAME1, text3 );

        final Collection< Page > history = engine.getManager( PageManager.class ).getVersionHistory(NAME1);
        Assertions.assertEquals( 3, history.size(), "size" );
    }

    @Test
    public void testDelete() throws Exception {
        engine.saveText( NAME1, "v1" );
        engine.saveText( NAME1, "v2" );
        engine.saveText( NAME1, "v3" );

        final PageManager mgr = engine.getManager( PageManager.class );
        final PageProvider provider = mgr.getProvider();

        WikiPage p = new WikiPage((Engine) engine, NAME1);
        provider.deletePage(p);

        final File f = new File( files, NAME1+AbstractFileProvider.FILE_EXT );
        Assertions.assertFalse( f.exists(), "file exists" );
    }

    @Test
    public void testDeleteVersion() throws Exception {
        engine.saveText( getPageName1(), "v1\r\n" );
        engine.saveText( getPageName1(), "v2\r\n" );
        engine.saveText( getPageName1(), "v3\r\n" );

        final PageManager mgr = engine.getManager( PageManager.class );
        final PageProvider provider = mgr.getProvider();

        List< Page > l = provider.getVersionHistory( NAME1 );
        Assertions.assertEquals( 3, l.size(), "wrong # of versions" );

        WikiPage p = new WikiPage((Engine) engine, NAME1);
        provider.deleteVersion(p, 2);

        l = provider.getVersionHistory( getPageName1() );
        Assertions.assertEquals( 2, l.size(), "wrong # of versions" );
        Assertions.assertEquals( "v1\r\n", provider.getPageText( getPageName1(), 1 ), "v1" );
        Assertions.assertEquals( "v3\r\n", provider.getPageText( getPageName1(), 3 ), "v3" );

        try {
            provider.getPageText( NAME1, 2 );
            Assertions.fail( "v2" );
        } catch ( final NoSuchVersionException e ) {
            // This is expected
        }
    }


    @Test
    public void testChangeNote() throws Exception {
        final Page p = Wiki.contents().page( engine, NAME1 );
        p.setAttribute( Page.CHANGENOTE, "Test change" );
        final Context context = Wiki.context().create( engine, p );
        engine.getManager( PageManager.class ).saveText( context, "test" );
        final Page p2 = engine.getManager( PageManager.class ).getPage( NAME1 );
        Assertions.assertEquals( "Test change", p2.getAttribute( Page.CHANGENOTE ) );
    }

    @Test
    public void testChangeNoteOldVersion() throws Exception {
        final Page p = Wiki.contents().page( engine, NAME1 );
        final Context context = Wiki.context().create(engine,p);
        context.getPage().setAttribute(Page.CHANGENOTE, "Test change" );
        engine.getManager( PageManager.class ).saveText( context, "test" );
        context.getPage().setAttribute(Page.CHANGENOTE, "Change 2" );
        engine.getManager( PageManager.class ).saveText( context, "test2" );
        final Page p2 = engine.getManager( PageManager.class ).getPage( NAME1, 1 );
        Assertions.assertEquals( "Test change", p2.getAttribute(Page.CHANGENOTE) );
        final Page p3 = engine.getManager( PageManager.class ).getPage( NAME1, 2 );
        Assertions.assertEquals( "Change 2", p3.getAttribute(Page.CHANGENOTE) );
    }

    @Test
    public void testChangeNoteOldVersion2() throws Exception {
        final Page p = Wiki.contents().page( engine, NAME1 );
        final Context context = Wiki.context().create(engine,p);
        context.getPage().setAttribute( Page.CHANGENOTE, "Test change" );
        engine.getManager( PageManager.class ).saveText( context, "test" );
        for( int i = 0; i < 5; i++ ) {
            final Page p2 = engine.getManager( PageManager.class ).getPage( getPageName1() ).clone();
            p2.removeAttribute(Page.CHANGENOTE);
            context.setPage( p2 );
            engine.getManager( PageManager.class ).saveText( context, "test"+i );
        }
        final Page p3 = engine.getManager( PageManager.class ).getPage( getPageName1(), -1 );
        Assertions.assertNull( p3.getAttribute( Page.CHANGENOTE ) );
    }

    // ----------------------------------------------------------------------------------------------------
    // Tests for the creation-date batch: persisting the creation date in page.properties and the optional
    // restore-creation-dates.properties recovery mechanism (see VersioningFileProvider).
    // ----------------------------------------------------------------------------------------------------

    @TempDir
    File creationDateTempDir;

    /**
     * Builds a standalone provider on an isolated page directory so the creation-date batch logic can be
     * exercised directly and deterministically, without going through the background thread started by
     * initialize().
     */
    private VersioningFileProvider standaloneProvider() {
        final VersioningFileProvider provider = new VersioningFileProvider();
        provider.m_pageDirectory = creationDateTempDir.getAbsolutePath();
        provider.m_encoding = AbstractFileProvider.DEFAULT_ENCODING;
        return provider;
    }

    /** Reads back the page.properties that the batch wrote for the given (never-versioned) page. */
    private Properties readPageProperties( final VersioningFileProvider provider, final String page ) throws IOException {
        final File propsFile = new File( provider.findOldPageDir( page ), VersioningFileProvider.PROPERTYFILE );
        final Properties props = new Properties();
        try( final InputStream in = new FileInputStream( propsFile ) ) {
            props.load( in );
        }
        return props;
    }

    private static Instant toInstant( final String isoDateTime ) {
        return Instant.from( DateTimeFormatter.ISO_DATE_TIME.parse( isoDateTime ) );
    }

    private static Date asDate( final String localDateTime ) {
        return Date.from( LocalDateTime.parse( localDateTime ).atZone( ZoneId.systemDefault() ).toInstant() );
    }

    @Test
    public void testParseRestoreDateFormats() {
        // epoch milliseconds
        Assertions.assertEquals( Instant.ofEpochMilli( 1523518260000L ),
                DateSupport.parseRestoreDate( "1523518260000" ).toInstant(), "epoch millis" );

        // ISO date-time with offset
        Assertions.assertEquals( Instant.parse( "2018-04-12T07:31:00Z" ),
                DateSupport.parseRestoreDate( "2018-04-12T09:31:00+02:00" ).toInstant(), "ISO offset" );

        // ISO local date-time (system zone), "T" separated
        Assertions.assertEquals( asDate( "2018-04-12T09:31:00" ).toInstant(),
                DateSupport.parseRestoreDate( "2018-04-12T09:31:00" ).toInstant(), "ISO local with T" );

        // ISO local date-time (system zone), space separated
        Assertions.assertEquals( asDate( "2018-04-12T09:31:00" ).toInstant(),
                DateSupport.parseRestoreDate( "2018-04-12 09:31:00" ).toInstant(), "ISO local with space" );

        // date only (start of day, system zone)
        Assertions.assertEquals( LocalDate.parse( "2018-04-12" ).atStartOfDay( ZoneId.systemDefault() ).toInstant(),
                DateSupport.parseRestoreDate( "2018-04-12" ).toInstant(), "date only" );
    }

    @Test
    public void testParseRestoreDateInvalidReturnsNull() {
        Assertions.assertNull( DateSupport.parseRestoreDate( "not-a-date" ) );
        Assertions.assertNull( DateSupport.parseRestoreDate( "" ) );
        Assertions.assertNull( DateSupport.parseRestoreDate( null ) );
    }

    @Test
    public void testRestoreDatePreferredWhenFileSystemDateNewer() throws Exception {
        final VersioningFileProvider provider = standaloneProvider();
        final String page = "RestoreNewer";

        // file system timestamp is later than the restore date -> it was reset by a copy/zip
        final WikiPage wikiPage = new WikiPage( (Engine) engine, page );
        wikiPage.setLastModified( asDate( "2020-01-01T00:00:00" ) );

        final Properties restore = new Properties();
        restore.setProperty( provider.mangleName( page ) + "#latest", "2018-04-12T09:31:00" );

        Assertions.assertTrue( provider.ensureCreationDateProperties( wikiPage, restore ), "properties should be written" );

        final Instant persisted = toInstant( readPageProperties( provider, page ).getProperty( "1.date" ) );
        Assertions.assertEquals( asDate( "2018-04-12T09:31:00" ).toInstant(), persisted,
                "older restored date should win over the newer file system timestamp" );
    }

    @Test
    public void testRestoreDateFallbackToPlainPageNameKey() throws Exception {
        final VersioningFileProvider provider = standaloneProvider();
        final String page = "RestorePlainKey";

        final WikiPage wikiPage = new WikiPage( (Engine) engine, page );
        wikiPage.setLastModified( asDate( "2020-01-01T00:00:00" ) );

        // key stored under the plain page name (not the mangled file name)
        final Properties restore = new Properties();
        restore.setProperty( page + "#latest", "2017-05-06T07:08:09" );

        provider.ensureCreationDateProperties( wikiPage, restore );

        final Instant persisted = toInstant( readPageProperties( provider, page ).getProperty( "1.date" ) );
        Assertions.assertEquals( asDate( "2017-05-06T07:08:09" ).toInstant(), persisted,
                "restore date should also be found under the plain page name key" );
    }

    @Test
    public void testFileSystemDateKeptWhenOlderThanRestoreDate() throws Exception {
        final VersioningFileProvider provider = standaloneProvider();
        final String page = "RestoreOlderFs";

        // file system timestamp is older than the restore date -> keep the (earlier) file system date
        final Date fsDate = asDate( "2018-01-01T00:00:00" );
        final WikiPage wikiPage = new WikiPage( (Engine) engine, page );
        wikiPage.setLastModified( fsDate );

        final Properties restore = new Properties();
        restore.setProperty( provider.mangleName( page ) + "#latest", "2020-06-15T10:00:00" );

        provider.ensureCreationDateProperties( wikiPage, restore );

        final Instant persisted = toInstant( readPageProperties( provider, page ).getProperty( "1.date" ) );
        Assertions.assertEquals( fsDate.toInstant(), persisted,
                "file system date must be kept when it is older than the restore date" );
    }

    @Test
    public void testFileSystemDateUsedWhenNoRestoreEntry() throws Exception {
        final VersioningFileProvider provider = standaloneProvider();
        final String page = "NoRestoreEntry";

        final Date fsDate = asDate( "2019-09-09T12:00:00" );
        final WikiPage wikiPage = new WikiPage( (Engine) engine, page );
        wikiPage.setLastModified( fsDate );

        Assertions.assertTrue( provider.ensureCreationDateProperties( wikiPage, new Properties() ),
                "creation date must be persisted even without a restore file" );

        final Properties persisted = readPageProperties( provider, page );
        Assertions.assertEquals( fsDate.toInstant(), toInstant( persisted.getProperty( "1.date" ) ),
                "file system date should be used when no restore entry exists" );
        Assertions.assertEquals( "unknown", persisted.getProperty( "1.author" ),
                "author should default to 'unknown' without heritage properties" );
    }

    @Test
    public void testHeritageAuthorPersistedForSingleVersionPage() throws Exception {
        final VersioningFileProvider provider = standaloneProvider();
        final String page = "HeritageAuthor";

        // simulate a heritage properties file written by the FileSystemProvider
        final File heritage = new File( creationDateTempDir, provider.mangleName( page ) + FileSystemProvider.PROP_EXT );
        try( final Writer out = new FileWriter( heritage ) ) {
            out.write( "author=brian\n" );
        }

        final WikiPage wikiPage = new WikiPage( (Engine) engine, page );
        wikiPage.setLastModified( asDate( "2016-03-03T03:03:03" ) );

        provider.ensureCreationDateProperties( wikiPage, new Properties() );

        Assertions.assertEquals( "brian", readPageProperties( provider, page ).getProperty( "1.author" ),
                "version 1 author should be taken from the heritage properties" );
    }

    /**
     * Creates a file of the given name in the wiki page directory, containing the data provided.
     */
    private void injectFile( final String fileName, final String fileContent) throws IOException {
        final File ft = new File( files, fileName );
        final Writer out = new FileWriter( ft );
        FileUtil.copyContents( new StringReader(fileContent), out );
        out.close();
    }

}
