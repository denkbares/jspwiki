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
package org.apache.wiki;

import org.apache.wiki.api.core.Engine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * What the watchdog writes to the log about a request that exceeded its timeout. In November 2026 a production wiki
 * hung for hours and the log only said which threads were late, not what they were waiting for.
 */
class WatchDogTest {

    @Test
    void hangingThreadIsDescribedTogetherWithTheThreadHoldingItsLock() throws Exception {
        final Object lock = new Object();
        final CountDownLatch locked = new CountDownLatch( 1 );
        final CountDownLatch release = new CountDownLatch( 1 );
        final Thread holder = daemon( "thread-holding-the-lock", () -> holdUntilReleased( lock, locked, release ) );
        final Thread waiter = daemon( "request-waiting-for-the-lock", () -> enter( lock ) );
        holder.start();
        Assertions.assertTrue( locked.await( 10, TimeUnit.SECONDS ) );
        waiter.start();
        try {
            awaitState( waiter, Thread.State.BLOCKED );

            final String description = WatchDog.describeHangingThread( waiter );

            Assertions.assertTrue( description.contains( "'request-waiting-for-the-lock' is BLOCKED" ), description );
            Assertions.assertTrue( description.contains( "held by thread 'thread-holding-the-lock'" ), description );
            // the stack traces of both: where the request waits, and what the holder of the lock is doing meanwhile
            Assertions.assertTrue( description.contains( "org.apache.wiki.WatchDogTest.enter(" ), description );
            Assertions.assertTrue( description.contains( "org.apache.wiki.WatchDogTest.holdUntilReleased(" ), description );
            Assertions.assertFalse( description.contains( "deadlock" ), description );
        } finally {
            release.countDown();
            holder.join( 10_000 );
            waiter.join( 10_000 );
        }
    }

    @Test
    void deadlockIsNamed() throws Exception {
        final ReentrantLock first = new ReentrantLock();
        final ReentrantLock second = new ReentrantLock();
        final CountDownLatch bothLocked = new CountDownLatch( 2 );
        final Thread one = daemon( "request-one", () -> lockBoth( first, second, bothLocked ) );
        final Thread other = daemon( "request-other", () -> lockBoth( second, first, bothLocked ) );
        one.start();
        other.start();
        try {
            awaitState( one, Thread.State.WAITING );
            awaitState( other, Thread.State.WAITING );

            final String description = WatchDog.describeHangingThread( one );

            Assertions.assertTrue( description.contains( "'request-one' is WAITING" ), description );
            Assertions.assertTrue( description.contains( "held by thread 'request-other'" ), description );
            Assertions.assertTrue( description.contains( "'request-other' is WAITING" ), description );
            Assertions.assertTrue( description.contains( "held by thread 'request-one'" ), description );
            Assertions.assertTrue( description.contains( "These threads are in a deadlock" ), description );
        } finally {
            one.interrupt();
            other.interrupt();
            one.join( 10_000 );
            other.join( 10_000 );
        }
    }

    @Test
    void threadThatIsNotWaitingForALockIsDescribedByItsStackTrace() {
        final String description = WatchDog.describeHangingThread( Thread.currentThread() );

        Assertions.assertTrue( description.contains( "'" + Thread.currentThread().getName() + "' is RUNNABLE" ), description );
        Assertions.assertTrue( description.contains( "org.apache.wiki.WatchDogTest." ), description );
        Assertions.assertFalse( description.contains( "held by thread" ), description );
    }

    /**
     * The watcher used to end its pass at the first idle thread, so a late request behind it was reported minutes later or
     * never: in the production log the first report of a request came up to 191 seconds after its timeout. It also
     * dropped idle threads, including one that was about to enter its state, as the late request of this test is when the
     * watcher thread makes its first pass while the threads are still starting.
     */
    @Test
    void lateThreadIsFoundAlthoughOtherWatchedThreadsAreIdle() throws Exception {
        final Engine engine = Mockito.mock( Engine.class );
        final int idleThreads = 20;
        final CountDownLatch watched = new CountDownLatch( idleThreads + 1 );
        final CountDownLatch finish = new CountDownLatch( 1 );
        final List< Thread > threads = new ArrayList<>();
        for( int i = 0; i < idleThreads; i++ ) {
            threads.add( daemon( "idle-request-" + i, () -> {
                final WatchDog watchDog = WatchDog.getCurrentWatchDog( engine );
                watched.countDown();
                await( finish );
                Reference.reachabilityFence( watchDog ); // the watcher only holds weak references
            } ) );
        }
        threads.add( daemon( "late-request", () -> {
            final WatchDog watchDog = WatchDog.getCurrentWatchDog( engine );
            watchDog.enterState( "late", 0 );
            watched.countDown();
            await( finish );
            watchDog.exitState();
        } ) );
        threads.forEach( Thread::start );
        try {
            Assertions.assertTrue( watched.await( 10, TimeUnit.SECONDS ) );
            Thread.sleep( 20 ); // the state expired the moment it was entered, be clearly behind it

            Assertions.assertTrue( WatchDog.checkAll() >= 1, "the late request was not found in one pass over the watched threads" );
        } finally {
            finish.countDown();
            for( final Thread thread : threads ) {
                thread.join( 10_000 );
            }
            WatchDog.getCurrentWatchDog( engine ).disable(); // stops the watcher thread this test started
        }
    }

    private static void await( final CountDownLatch latch ) {
        try {
            latch.await();
        } catch( final InterruptedException e ) {
            Thread.currentThread().interrupt();
        }
    }

    private static void holdUntilReleased( final Object lock, final CountDownLatch locked, final CountDownLatch release ) {
        synchronized( lock ) {
            locked.countDown();
            try {
                release.await();
            } catch( final InterruptedException e ) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void enter( final Object lock ) {
        synchronized( lock ) {
            lock.hashCode();
        }
    }

    private static void lockBoth( final ReentrantLock mine, final ReentrantLock theirs, final CountDownLatch bothLocked ) {
        try {
            mine.lockInterruptibly();
            try {
                bothLocked.countDown();
                bothLocked.await();
                theirs.lockInterruptibly();
                theirs.unlock();
            } finally {
                mine.unlock();
            }
        } catch( final InterruptedException e ) {
            Thread.currentThread().interrupt();
        }
    }

    private static Thread daemon( final String name, final Runnable runnable ) {
        final Thread thread = new Thread( runnable, name );
        thread.setDaemon( true );
        return thread;
    }

    private static void awaitState( final Thread thread, final Thread.State state ) throws InterruptedException {
        final long end = System.currentTimeMillis() + 10_000;
        while( thread.getState() != state && System.currentTimeMillis() < end ) {
            Thread.sleep( 10 );
        }
        Assertions.assertEquals( state, thread.getState(), "state of " + thread.getName() );
    }

}
