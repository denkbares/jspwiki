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
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.core.Page;
import org.apache.wiki.pages.PageManager;
import org.apache.wiki.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.apache.wiki.TestEngine.with;


public class FileSystemProviderTest {

    AbstractFileProvider m_provider;
    AbstractFileProvider m_providerUTF8;
    Properties props = TestEngine.getTestProperties();
    Properties propsUTF8 = TestEngine.getTestProperties();

    Engine m_engine;

    @BeforeEach
    public void setUp() throws Exception {
        props.setProperty( PageManager.PROP_PAGEPROVIDER, "FileSystemProvider" );
        props.setProperty( FileSystemProvider.PROP_PAGEDIR, "./target/jspwiki.test.pages" );

        m_engine = TestEngine.build( with( PageManager.PROP_PAGEPROVIDER, "FileSystemProvider" ),
                with( FileSystemProvider.PROP_PAGEDIR, "./target/jspwiki.test.pages" ) );
        m_provider = new FileSystemProvider();
        m_provider.initialize( m_engine, props );

        props.setProperty( Engine.PROP_ENCODING, StandardCharsets.UTF_8.name() );
        m_providerUTF8 = new FileSystemProvider();
        m_providerUTF8.initialize( m_engine, props );

    }

    @AfterEach
    public void tearDown() {
        TestEngine.deleteAll( new File( props.getProperty( FileSystemProvider.PROP_PAGEDIR ) ) );
    }

    @Test
    public void testStandard() throws Exception {
        final WikiPage page = new WikiPage(m_engine, "Test");

        m_provider.putPageText( page, "test" );

        final File resultfile = getPageFile(props.getProperty(FileSystemProvider.PROP_PAGEDIR), "Test.txt");

        Assertions.assertTrue( resultfile.exists(), "No such file" );

        final String contents = FileUtil.readContents( new FileInputStream(resultfile), StandardCharsets.ISO_8859_1.name() );

        Assertions.assertEquals( contents, "test", "Wrong contents" );
    }

    @Test
    public void testScandinavianLetters() throws Exception {
        final WikiPage page = new WikiPage(m_engine, "\u00c5\u00e4Test");

        m_provider.putPageText( page, "test" );

        final File resultfile = getPageFile(props.getProperty(FileSystemProvider.PROP_PAGEDIR), "%C5%E4Test.txt");

        Assertions.assertTrue( resultfile.exists(), "No such file" );

        final String contents = FileUtil.readContents( new FileInputStream(resultfile), StandardCharsets.ISO_8859_1.name() );

        Assertions.assertEquals( contents, "test", "Wrong contents" );
    }

    @Test
    public void testScandinavianLettersUTF8() throws Exception {
        String pageName = "\u00c5\u00e4Test";
        final WikiPage page = new WikiPage(m_engine, pageName);

        m_providerUTF8.putPageText( page, "test\u00d6" );

        final File resultfile = new File(  m_providerUTF8.findPage(pageName).getParentFile() , "%C3%85%C3%A4Test.txt" );

        Assertions.assertTrue( resultfile.exists(), "No such file" );

        final String contents = FileUtil.readContents( new FileInputStream(resultfile),
                                                 StandardCharsets.UTF_8.name() );

        Assertions.assertEquals( contents, "test\u00d6", "Wrong contents" );
    }

    /**
     * This should never happen, but let's check that we're protected anyway.
     * @throws Exception
     */
    @Test
    public void testSlashesInPageNamesUTF8()
         throws Exception
    {
        final WikiPage page = new WikiPage(m_engine, "Test/Foobar");

        m_providerUTF8.putPageText( page, "test" );

        final File resultfile = getPageFile(props.getProperty(FileSystemProvider.PROP_PAGEDIR), "Test%2FFoobar.txt");

        Assertions.assertTrue( resultfile.exists(), "No such file" );

        final String contents = FileUtil.readContents( new FileInputStream(resultfile),
                                                 StandardCharsets.UTF_8.name() );

        Assertions.assertEquals( contents, "test", "Wrong contents" );
    }

    @Test
    public void testSlashesInPageNames()
         throws Exception
    {
        final WikiPage page = new WikiPage(m_engine, "Test/Foobar");

        m_provider.putPageText( page, "test" );

        final File resultfile = getPageFile(props.getProperty(FileSystemProvider.PROP_PAGEDIR), "Test%2FFoobar.txt");

        Assertions.assertTrue( resultfile.exists(), "No such file" );

        final String contents = FileUtil.readContents( new FileInputStream(resultfile),
                                                 StandardCharsets.ISO_8859_1.name() );

        Assertions.assertEquals( contents, "test", "Wrong contents" );
    }

    @Test
    public void testDotsInBeginning()
       throws Exception
    {
        final WikiPage page = new WikiPage(m_engine, ".Test");

        m_provider.putPageText( page, "test" );

        final File resultfile = getPageFile(props.getProperty(FileSystemProvider.PROP_PAGEDIR), "%2ETest.txt");

        Assertions.assertTrue( resultfile.exists(), "No such file" );

        final String contents = FileUtil.readContents( new FileInputStream(resultfile), StandardCharsets.ISO_8859_1.name() );

        Assertions.assertEquals( contents, "test", "Wrong contents" );
    }

    @Test
    public void testAuthor()
        throws Exception
    {
        try
        {
            final WikiPage page = new WikiPage(m_engine, "\u00c5\u00e4Test");
            page.setAuthor("Min\u00e4");

            m_provider.putPageText( page, "test" );

            final Page page2 = m_provider.getPageInfo( "\u00c5\u00e4Test", 1 );

            Assertions.assertEquals( "Min\u00e4", page2.getAuthor() );
        }
        finally
        {
            File resultfile = getPageFile(props.getProperty(FileSystemProvider.PROP_PAGEDIR), "%C5%E4Test.txt");
            try {
                resultfile.delete();
            } catch( final Exception e) {}

            resultfile = getPageFile(props.getProperty(FileSystemProvider.PROP_PAGEDIR), "%C5%E4Test.properties");
            try {
                resultfile.delete();
            } catch( final Exception e) {}
        }
    }

    @Test
    public void testNonExistantDirectory() throws Exception {
        final String tmpdir =  props.getProperty( FileSystemProvider.PROP_PAGEDIR ) ;
        final String dirname = "non-existant-directory";

        final String newdir = tmpdir + File.separator + dirname;

        final Properties pr = new Properties();

        pr.setProperty( FileSystemProvider.PROP_PAGEDIR,
                           newdir );

        final FileSystemProvider test = new FileSystemProvider();

        test.initialize( m_engine, pr );

        final File f = new File( newdir );

        Assertions.assertTrue( f.exists(), "didn't create it" );
        Assertions.assertTrue( f.isDirectory(), "isn't a dir" );

        f.delete();
    }

    @Test
    public void testDirectoryIsFile()
        throws Exception
    {
        File tmpFile = null;

        try
        {
            tmpFile = FileUtil.newTmpFile("foobar"); // Content does not matter.

            final Properties pr = new Properties();

            pr.setProperty( FileSystemProvider.PROP_PAGEDIR, tmpFile.getAbsolutePath() );

            final FileSystemProvider test = new FileSystemProvider();

            try
            {
                test.initialize( m_engine, pr );

                Assertions.fail( "Wiki did not warn about wrong property." );
            }
            catch( final IOException e )
            {
                // This is okay.
            }
        }
        finally
        {
            if( tmpFile != null )
            {
                tmpFile.delete();
            }
        }
    }

    @Test
    public void testDelete()
        throws Exception
    {
        final String files = props.getProperty( FileSystemProvider.PROP_PAGEDIR );

        final WikiPage p = new WikiPage(m_engine,"Test");
        p.setAuthor("AnonymousCoward");

        m_provider.putPageText( p, "v1" );

        File f = getPageFile(files, "Test" + FileSystemProvider.FILE_EXT);

        Assertions.assertTrue( f.exists(), "file does not exist" );

        f = getPageFile(files, "Test.properties");

        Assertions.assertTrue( f.exists(), "property file does not exist" );

        m_provider.deletePage( p );

        f = getPageFile(files, "Test" + FileSystemProvider.FILE_EXT);

        Assertions.assertFalse( f.exists(), "file exists" );

        f = getPageFile(files, "Test.properties");

        Assertions.assertFalse( f.exists(), "properties exist" );
    }

    @Test
    public void testCustomProperties() throws Exception {
        final String pageDir = props.getProperty( FileSystemProvider.PROP_PAGEDIR );
        final String pageName = "CustomPropertiesTest";
        final String fileName = pageName+FileSystemProvider.FILE_EXT;
        final File file = getPageFile(pageDir, fileName);

        Assertions.assertFalse( file.exists() );
        final WikiPage testPage = new WikiPage(m_engine,pageName);
        testPage.setAuthor("TestAuthor");
        testPage.setAttribute("@test","Save Me");
        testPage.setAttribute("@test2","Save You");
        testPage.setAttribute("test3","Do not save");
        m_provider.putPageText( testPage, "This page has custom properties" );
        Assertions.assertTrue( file.exists(), "No such file" );
        final Page pageRetrieved = m_provider.getPageInfo( pageName, -1 );
        final String value = pageRetrieved.getAttribute("@test");
        final String value2 = pageRetrieved.getAttribute("@test2");
        final String value3 = pageRetrieved.getAttribute("test3");
        Assertions.assertNotNull(value);
        Assertions.assertNotNull(value2);
        Assertions.assertNull(value3);
        Assertions.assertEquals("Save Me",value);
        Assertions.assertEquals("Save You",value2);
        file.delete();
        Assertions.assertFalse( file.exists() );
    }

    protected  @NotNull File getPageFile(String pageDir, String fileName) {
        return new File(pageDir, fileName);
    }
}
