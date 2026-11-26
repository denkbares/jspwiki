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
import org.apache.wiki.api.core.Attachment;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.api.providers.WikiProvider;
import org.apache.wiki.api.spi.Wiki;
import org.apache.wiki.util.FileUtil;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


public class BasicAttachmentProviderTest {

    public static final String NAME1 = "FirstTestPage";
    public static final String NAME2 = "AfterFirstTestPage";
    TestEngine m_engine;
    BasicAttachmentProvider m_provider;

    /** This is the sound of my head hitting the keyboard. */
    private static final String FILE_CONTENTS = "gy th tgyhgthygyth tgyfgftrfgvtgfgtr";

    @BeforeEach
    public void setUp() throws Exception {
        m_engine = TestEngine.build();
        m_provider = new BasicAttachmentProvider();
        m_provider.initialize( m_engine, m_engine.getWikiProperties() );

        m_engine.saveText( NAME1, "Foobar" );
        m_engine.saveText( NAME2, "Foobar2" );
    }
    
    @AfterEach
    void tearDown() {
        m_engine.stop();
    }

    private File makeAttachmentFile() throws Exception {
        final File tmpFile = File.createTempFile("test-",".txt");
        return copyContents( tmpFile );
    }

    private File makeExtraFile( final File directory, final String name ) throws Exception {
        final File tmpFile = new File( directory, name );
        return copyContents( tmpFile );
    }

    private File copyContents( final File to ) throws Exception {
        to.deleteOnExit();
        try( final FileWriter out = new FileWriter( to ) ) {
            FileUtil.copyContents( new StringReader( FILE_CONTENTS ), out );
        }

        return to;
    }

    @Test
    public void testExtension() {
        final String s = "test.png";
        Assertions.assertEquals( "png", BasicAttachmentProvider.getFileExtension(s) );
    }

    @Test
    public void testExtension2() {
        final String s = ".foo";
        Assertions.assertEquals( "foo", BasicAttachmentProvider.getFileExtension(s) );
    }

    @Test
    public void testExtension3() {
        final String s = "test.png.3";
        Assertions.assertEquals( "3", BasicAttachmentProvider.getFileExtension(s) );
    }

    @Test
    public void testExtension4() {
        final String s = "testpng";
        Assertions.assertEquals( "bin", BasicAttachmentProvider.getFileExtension(s) );
    }

    @Test
    public void testExtension5() {
        final String s = "test.";
        Assertions.assertEquals( "bin", BasicAttachmentProvider.getFileExtension(s) );
    }

    @Test
    public void testExtension6() {
        final String s = "test.a";
        Assertions.assertEquals( "a", BasicAttachmentProvider.getFileExtension(s) );
    }

    /**
     *  Can we save attachments with names in UTF-8 range?
     */
    @Test
    public void testPutAttachmentUTF8() throws Exception {
        final File in = makeAttachmentFile();
        final Attachment att = Wiki.contents().attachment( m_engine, NAME1, "\u3072\u3048\u308b\u00e5\u00e4\u00f6test.f\u00fc\u00fc" );

        m_provider.putAttachmentData( att, Files.newInputStream( in.toPath() ) );
        final List< Attachment > res = m_provider.listAllChanged( new Date(0L) );
        final Attachment a0 = res.get(0);

        Assertions.assertEquals( att.getName(), a0.getName(), "name" );
    }

    @Test
    public void testGetAttachmentDataRaisesProviderExceptionIfInexistentFileOnDisk() {
        final Attachment att = Wiki.contents().attachment( m_engine, NAME1, "test1.txt" );
        Assertions.assertThrows( ProviderException.class, () -> m_provider.getAttachmentData( att ) );
    }

    ConditionFactory awaitility() {
        return Awaitility.given().ignoreException( ProviderException.class );
    }

    /**
     * Actual save on disk is OS-dependent, meaning the files of two consecutive attachment saves may end up having the
     * same last modified date. As we'll check the order of {@link BasicAttachmentProvider#listAllChanged(Date)}, we must
     * wait until the file saved is actually on disk.
     *
     * If after all, the next saves of two consecutive attachments end up with the same last modified date,
     * {@link BasicAttachmentProvider#listAllChanged(Date)} will sort attachments using wiki page names and versions,
     * so we're preparing attachment names too in order to not mangle with the assertions.
     *
     * @param att attachment to check
     * @return {@code Callable< Boolean.TRUE >} or {@code ProviderException}.
     */
    Callable< Boolean > attachmentIsSaved( final Attachment att ) {
        return () -> {
            m_provider.getAttachmentData( att ).close(); // throws exception if file does not exist on disk
            return true;
        };
    }

    @Test
    public void testListAll() throws Exception {
        final File in = makeAttachmentFile();
        final Attachment att = Wiki.contents().attachment( m_engine, NAME1, "test1.txt" );
        m_provider.putAttachmentData( att, Files.newInputStream( in.toPath() ) );

        awaitility().await( "testListAll" ).until( attachmentIsSaved( att ) );

        final Attachment att2 = Wiki.contents().attachment( m_engine, NAME2, "test2.txt" );
        m_provider.putAttachmentData( att2, Files.newInputStream( in.toPath() ) );

        final List< Attachment > res = m_provider.listAllChanged( new Date(0L) );

        Assertions.assertEquals( 2, res.size(), "list size" );

        final Attachment a2 = res.get(0);  // Most recently changed
        final Attachment a1 = res.get(1);  // Least recently changed

        Assertions.assertEquals( att.getName(), a1.getName(), "a1 name :: " + res );
        Assertions.assertEquals( att2.getName(), a2.getName(), "a2 name :: " + res );
    }


    /**
     *  Deleting the last attachment of a page also removes the directory of the page, deleting one of several keeps it.
     */
    @Test
    public void testDeleteLastAttachmentRemovesPageDir() throws Exception {
        final File in = makeAttachmentFile();
        final Attachment att = Wiki.contents().attachment( m_engine, NAME1, "test1.txt" );
        m_provider.putAttachmentData( att, Files.newInputStream( in.toPath() ) );
        final Attachment att2 = Wiki.contents().attachment( m_engine, NAME1, "test2.txt" );
        m_provider.putAttachmentData( att2, Files.newInputStream( in.toPath() ) );
        final File pageDir = m_provider.findPageDir( NAME1 );

        m_provider.deleteAttachment( att );
        Assertions.assertTrue( pageDir.exists(), "the other attachment is still there" );

        m_provider.deleteAttachment( att2 );
        Assertions.assertFalse( pageDir.exists(), "no attachment left, so the directory of the page is gone" );
    }

    /**
     *  Check that the system does not Assertions.fail if there are extra files in the directory.
     */
    @Test
    public void testListAllExtrafile() throws Exception {
        final File in = makeAttachmentFile();
        final File sDir = new File(m_engine.getWikiProperties().getProperty( BasicAttachmentProvider.PROP_STORAGEDIR ) );
        makeExtraFile( sDir, "foobar.blob" );

        final Attachment att = Wiki.contents().attachment( m_engine, NAME1, "test1.txt" );
        m_provider.putAttachmentData( att, Files.newInputStream( in.toPath() ) );

        awaitility().await( "testListAllExtrafile" ).until( attachmentIsSaved( att ) );

        final Attachment att2 = Wiki.contents().attachment( m_engine, NAME2, "test2.txt" );
        m_provider.putAttachmentData( att2, Files.newInputStream( in.toPath() ) );

        final List< Attachment > res = m_provider.listAllChanged( new Date(0L) );

        Assertions.assertEquals( 2, res.size(), "list size" );

        final Attachment a2 = res.get(0);  // Most recently changed
        final Attachment a1 = res.get(1);  // Least recently changed

        Assertions.assertEquals( att.getName(), a1.getName(), "a1 name :: " + res );
        Assertions.assertEquals( att2.getName(), a2.getName(), "a2 name :: " + res );
    }

    /**
     *  Check that the system does not Assertions.fail if there are extra files in the attachment directory.
     */
    @Test
    public void testListAllExtrafileInAttachmentDir() throws Exception {
        final File in = makeAttachmentFile();
        final File sDir = new File( m_engine.getWikiProperties().getProperty( BasicAttachmentProvider.PROP_STORAGEDIR ) );
        final File attDir = new File( sDir, NAME1+"-att" );

        final Attachment att = Wiki.contents().attachment( m_engine, NAME1, "test1.txt" );
        m_provider.putAttachmentData( att, Files.newInputStream( in.toPath() ) );
        makeExtraFile( attDir, "ping.pong" );

        awaitility().await( "testListAllExtrafileInAttachmentDir" ).until( attachmentIsSaved( att ) );

        final Attachment att2 = Wiki.contents().attachment( m_engine, NAME2, "test2.txt" );

        m_provider.putAttachmentData( att2, Files.newInputStream( in.toPath() ) );

        final List< Attachment > res = m_provider.listAllChanged( new Date(0L) );

        Assertions.assertEquals( 2, res.size(), "list size" );

        final Attachment a2 = res.get(0);  // Most recently changed
        final Attachment a1 = res.get(1);  // Least recently changed

        Assertions.assertEquals( att.getName(), a1.getName(), "a1 name :: " + res );
        Assertions.assertEquals( att2.getName(), a2.getName(), "a2 name :: " + res );
    }

    /**
     *  Check that the system does not Assertions.fail if there are extra dirs in the attachment directory.
     */
    @Test
    public void testListAllExtradirInAttachmentDir() throws Exception {
        final File in = makeAttachmentFile();
        final File sDir = new File(m_engine.getWikiProperties().getProperty( BasicAttachmentProvider.PROP_STORAGEDIR ));
        final File attDir = new File( sDir, NAME1+"-att" );
        final Attachment att = Wiki.contents().attachment( m_engine, NAME1, "test1.txt" );
        m_provider.putAttachmentData( att, Files.newInputStream( in.toPath() ) );

        // This is our extraneous directory.
        final File extrafile = new File( attDir, "ping.pong" );
        extrafile.mkdir();

        awaitility().await( "testListAllExtradirInAttachmentDir" ).until( attachmentIsSaved( att ) );

        final Attachment att2 = Wiki.contents().attachment( m_engine, NAME2, "test2.txt" );

        m_provider.putAttachmentData( att2, Files.newInputStream( in.toPath() ) );

        final List< Attachment > res = m_provider.listAllChanged( new Date(0L) );

        Assertions.assertEquals( 2, res.size(), "list size" );

        final Attachment a2 = res.get(0);  // Most recently changed
        final Attachment a1 = res.get(1);  // Least recently changed

        Assertions.assertEquals( att.getName(), a1.getName(), "a1 name :: " + res );
        Assertions.assertEquals( att2.getName(), a2.getName(), "a2 name :: " + res );
    }

    /**
     *  An attachment's stream is copied without holding the attachment lock: a slow upload or URL import used to block every
     *  attachment read and write of the wiki. The new version only becomes visible once its stream is complete.
     */
    @Test
    public void testStalledStreamDoesNotBlockOtherAttachments() throws Exception {
        final File in = makeAttachmentFile();
        final Attachment other = Wiki.contents().attachment( m_engine, NAME2, "other.txt" );
        m_provider.putAttachmentData( other, Files.newInputStream( in.toPath() ) );
        final File storageDir = new File( m_engine.getWikiProperties().getProperty( BasicAttachmentProvider.PROP_STORAGEDIR ) );
        final Set< String > storageBefore = new HashSet<>( Arrays.asList( Objects.requireNonNull( storageDir.list() ) ) );

        final StalledStream stalled = new StalledStream( FILE_CONTENTS.getBytes( StandardCharsets.UTF_8 ), false );
        final Attachment slow = Wiki.contents().attachment( m_engine, NAME1, "slow.txt" );
        slow.setAuthor( "Uploader" );
        final AtomicReference< Throwable > uploadFailure = new AtomicReference<>();
        final Thread upload = startDaemon( "stalled-upload", () -> m_provider.putAttachmentData( slow, stalled ), uploadFailure );
        try {
            Assertions.assertTrue( stalled.stalled.await( 10, TimeUnit.SECONDS ), "upload did not start" );
            // the storage directory may be the working tree of a git repository, which commits any file it finds there
            Assertions.assertEquals( storageBefore, new HashSet<>( Arrays.asList( Objects.requireNonNull( storageDir.list() ) ) ),
                                     "an upload in progress must not create files in the storage directory" );

            final AtomicReference< Throwable > othersFailure = new AtomicReference<>();
            final Attachment[] seen = new Attachment[ 2 ];
            final Thread others = startDaemon( "other-attachments", () -> {
                seen[ 0 ] = m_provider.getAttachmentInfo( Wiki.contents().page( m_engine, NAME2 ), "other.txt", WikiProvider.LATEST_VERSION );
                seen[ 1 ] = m_provider.getAttachmentInfo( Wiki.contents().page( m_engine, NAME1 ), "slow.txt", WikiProvider.LATEST_VERSION );
                m_provider.putAttachmentData( Wiki.contents().attachment( m_engine, NAME2, "third.txt" ), Files.newInputStream( in.toPath() ) );
            }, othersFailure );
            others.join( 5_000 );
            Assertions.assertFalse( others.isAlive(), "other attachments waited for the stalled upload" );
            Assertions.assertNull( othersFailure.get() );
            Assertions.assertNotNull( seen[ 0 ], "the other attachment is readable" );
            Assertions.assertNull( seen[ 1 ], "the stalled attachment must not be visible before its stream is complete" );
        } finally {
            stalled.release.countDown();
            upload.join( 10_000 );
        }

        Assertions.assertFalse( upload.isAlive(), "upload did not finish" );
        Assertions.assertNull( uploadFailure.get() );
        final Attachment stored = m_provider.getAttachmentInfo( Wiki.contents().page( m_engine, NAME1 ), "slow.txt", WikiProvider.LATEST_VERSION );
        Assertions.assertNotNull( stored );
        Assertions.assertEquals( 1, stored.getVersion() );
        Assertions.assertEquals( "Uploader", stored.getAuthor() );
        try( final InputStream data = m_provider.getAttachmentData( stored ) ) {
            Assertions.assertEquals( FILE_CONTENTS, new String( data.readAllBytes(), StandardCharsets.UTF_8 ) );
        }

        final Set< String > storageAfter = new HashSet<>( Arrays.asList( Objects.requireNonNull( storageDir.list() ) ) );
        storageAfter.removeAll( storageBefore );
        Assertions.assertEquals( Set.of( NAME1 + BasicAttachmentProvider.DIR_EXTENSION ), storageAfter, "no temporary file is left behind" );
    }

    /**
     *  A stream that fails while it is copied neither creates a version nor leaves a part of it behind, the next upload gets the
     *  version number the failed one would have had.
     */
    @Test
    public void testFailedStreamLeavesNoVersion() throws Exception {
        final File in = makeAttachmentFile();
        final Attachment att = Wiki.contents().attachment( m_engine, NAME1, "broken.txt" );
        final File storageDir = new File( m_engine.getWikiProperties().getProperty( BasicAttachmentProvider.PROP_STORAGEDIR ) );
        final Set< String > storageBefore = new HashSet<>( Arrays.asList( Objects.requireNonNull( storageDir.list() ) ) );

        Assertions.assertThrows( IOException.class,
                                 () -> m_provider.putAttachmentData( att, new StalledStream( FILE_CONTENTS.getBytes( StandardCharsets.UTF_8 ), true ) ) );
        Assertions.assertNull( m_provider.getAttachmentInfo( Wiki.contents().page( m_engine, NAME1 ), "broken.txt", WikiProvider.LATEST_VERSION ),
                               "a broken upload must not become a version" );
        Assertions.assertEquals( storageBefore, new HashSet<>( Arrays.asList( Objects.requireNonNull( storageDir.list() ) ) ),
                                 "no temporary file is left behind" );

        m_provider.putAttachmentData( att, Files.newInputStream( in.toPath() ) );
        final Attachment stored = m_provider.getAttachmentInfo( Wiki.contents().page( m_engine, NAME1 ), "broken.txt", WikiProvider.LATEST_VERSION );
        Assertions.assertNotNull( stored );
        Assertions.assertEquals( 1, stored.getVersion() );
    }

    private static Thread startDaemon( final String name, final ThrowingRunnable task, final AtomicReference< Throwable > failure ) {
        final Thread thread = new Thread( () -> {
            try {
                task.run();
            } catch( final Throwable e ) {
                failure.set( e );
            }
        }, name );
        thread.setDaemon( true );
        thread.start();
        return thread;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     *  Delivers the first half of its content, then waits until released (or fails, if so configured), like a stalled upload.
     */
    private static final class StalledStream extends InputStream {
        final CountDownLatch stalled = new CountDownLatch( 1 );
        final CountDownLatch release = new CountDownLatch( 1 );
        private final byte[] content;
        private final boolean fail;
        private int pos;

        StalledStream( final byte[] content, final boolean fail ) {
            this.content = content;
            this.fail = fail;
        }

        @Override
        public int read() throws IOException {
            if( pos == content.length / 2 && stalled.getCount() > 0 ) {
                stalled.countDown();
                if( fail ) {
                    throw new IOException( "connection reset" );
                }
                try {
                    release.await();
                } catch( final InterruptedException e ) {
                    Thread.currentThread().interrupt();
                    throw new IOException( e );
                }
            }
            return pos < content.length ? content[ pos++ ] : -1;
        }

        @Override
        public int read( final byte[] b, final int off, final int len ) throws IOException {
            // one byte at a time, so the copy reaches the stall point on its own and does not swallow the failure
            if( len == 0 ) {
                return 0;
            }
            final int c = read();
            if( c < 0 ) {
                return -1;
            }
            b[ off ] = ( byte )c;
            return 1;
        }
    }

    @Test
    public void testListAllNoExtension() throws Exception {
        final File in = makeAttachmentFile();
        final Attachment att = Wiki.contents().attachment( m_engine, NAME1, "test1." );
        m_provider.putAttachmentData( att, Files.newInputStream( in.toPath() ) );

        awaitility().await( "testListAllNoExtension" ).until( attachmentIsSaved( att ) );

        final Attachment att2 = Wiki.contents().attachment( m_engine, NAME2, "test2." );
        m_provider.putAttachmentData( att2, Files.newInputStream( in.toPath() ) );

        final List< Attachment > res = m_provider.listAllChanged( new Date( 0L ) );

        Assertions.assertEquals( 2, res.size(), "list size" );

        final Attachment a2 = res.get(0);  // Most recently changed
        final Attachment a1 = res.get(1);  // Least recently changed

        Assertions.assertEquals( att.getName(), a1.getName(), "a1 name :: " + res );
        Assertions.assertEquals( att2.getName(), a2.getName(), "a2 name :: " + res );
    }

}
