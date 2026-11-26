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
package org.apache.wiki.search;

import org.apache.wiki.api.core.Page;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Pages are queued for indexing while the queue is being worked off. Every page save queues its page, so a save used to wait until a
 * whole backlog was indexed, e.g. after a mass import.
 */
class LuceneUpdateQueueTest {

    @Test
    void pagesCanBeQueuedWhileTheQueueIsIndexed() throws Exception {
        final CountDownLatch indexing = new CountDownLatch( 1 );
        final CountDownLatch release = new CountDownLatch( 1 );
        final List< String > indexed = new ArrayList<>();
        final LuceneSearchProvider provider = new LuceneSearchProvider() {
            @Override
            protected synchronized void updateLuceneIndex( final Page page, final String text ) {
                indexing.countDown();
                try {
                    release.await();
                } catch( final InterruptedException e ) {
                    Thread.currentThread().interrupt();
                }
                indexed.add( text );
            }
        };
        provider.m_updates.add( new Object[] { Mockito.mock( Page.class ), "first" } );
        final Thread updater = new Thread( provider::processQueuedUpdates, "lucene-updater" );
        updater.setDaemon( true );
        updater.start();
        Assertions.assertTrue( indexing.await( 5, TimeUnit.SECONDS ) );
        try {
            final Thread save = new Thread( () -> provider.m_updates.add( new Object[] { Mockito.mock( Page.class ), "second" } ), "page-save" );
            save.setDaemon( true );
            save.start();
            save.join( 5_000 );
            Assertions.assertFalse( save.isAlive(), "queueing a page waited for the indexing of the queue" );
        } finally {
            release.countDown();
            updater.join( 5_000 );
        }
        provider.processQueuedUpdates();
        Assertions.assertEquals( List.of( "first", "second" ), indexed );
    }

}
