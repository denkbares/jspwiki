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

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.apache.wiki.api.core.Engine;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;


/**
 *  WatchDog is a general system watchdog.  You can attach any Watchable or a Thread object to it, and it will notify you
 *  if a timeout has been exceeded.
 *  <p>
 *  The notification of the timeouts is done from a separate WatchDog thread, of which there is one per watched thread.
 *  This Thread is named 'WatchDog for XXX', where XXX is your Thread name.
 *  <p>
 *  The suggested method of obtaining a WatchDog is via the static factory method, since it will return you the correct
 *  watchdog for the current thread.  However, we do not prevent you from creating your own watchdogs either.
 *  <p>
 *  If you create a WatchDog for a Thread, the WatchDog will figure out when the Thread is dead, and will stop itself
 *  accordingly. However, this object is not automatically released, so you might want to check it out after a while.
 *
 *  @since  2.4.92
 */
public final class WatchDog {

    private final Watchable m_watchable;
    private final Stack< State > m_stateStack = new Stack<>();
    private boolean m_enabled = true;
    private final Engine m_engine;

    /** How many threads are described at most along a chain of threads waiting for each other. */
    private static final int MAX_DESCRIBED_THREADS = 5;

    private static final Logger LOG = LoggerFactory.getLogger( WatchDog.class );

    private static final Map< Integer, WeakReference< WatchDog > > c_kennel = new ConcurrentHashMap<>();
    private static WikiBackgroundThread c_watcherThread;

    /**
     *  Returns the current watchdog for the current thread. This is the preferred method of getting you a Watchdog, since it
     *  keeps an internal list of Watchdogs for you so that there won't be more than one watchdog per thread.
     *
     *  @param engine The Engine to which the Watchdog should be bonded to.
     *  @return A usable WatchDog object.
     */
    public static WatchDog getCurrentWatchDog( final Engine engine ) {
        final Thread t = Thread.currentThread();

        WeakReference< WatchDog > w = c_kennel.get( t.hashCode() );
        WatchDog wd = null;
        if( w != null ) {
            wd = w.get();
        }

        if( w == null || wd == null ) {
            wd = new WatchDog( engine, t );
            w = new WeakReference<>( wd );
            c_kennel.put( t.hashCode(), w );
        }

        return wd;
    }

    /**
     *  Creates a new WatchDog for a Watchable.
     *
     *  @param engine The Engine.
     *  @param watch A Watchable object.
     */
    public WatchDog( final Engine engine, final Watchable watch ) {
        m_engine    = engine;
        m_watchable = watch;

        synchronized( WatchDog.class ) {
            if( c_watcherThread == null ) {
                c_watcherThread = new WatchDogThread( engine );
                c_watcherThread.start();
            }
        }
    }

    /**
     *  Creates a new WatchDog for a Thread.  The Thread is wrapped in a Watchable wrapper for this purpose.
     *
     *  @param engine The Engine
     *  @param thread A Thread for watching.
     */
    public WatchDog( final Engine engine, final Thread thread ) {
        this( engine, new ThreadWrapper( thread ) );
    }

    /**
     *  Hopefully finalizes this properly.  This is rather untested for now...
     */
    /**
     *  Checks all watched threads once and reports the late ones.
     *
     *  @return the number of threads that exceeded their timeout
     */
    static int checkAll() {
        int late = 0;
        for( final Map.Entry< Integer, WeakReference< WatchDog > > entry : c_kennel.entrySet() ) {
            final WeakReference< WatchDog > wr = entry.getValue();
            final WatchDog w = wr.get();
            if( w != null ) {
                if( !w.isWatchableAlive() ) {
                    c_kennel.remove( entry.getKey() );
                } else if( w.isStateStackNotEmpty() && w.check() ) {
                    late++;
                }
                // A thread that is alive but in no state stays registered. It used to be removed, and the pass ended
                // there: the threads behind it were checked a pass later (or never, as long as a thread in front of them
                // was idle again), and a thread removed just before it entered a state was not watched at all. Its
                // watchdog goes away by itself, as it is only weakly referenced here.
            }
        }
        return late;
    }

    private static void scrub() {
        //  During finalization, the object may already be cleared (depending on the finalization order). Therefore, it's
        //  possible that this method is called from another thread after the WatchDog itself has been cleared.
        if( c_kennel.isEmpty() ) {
            return;
        }

        for( final Map.Entry< Integer, WeakReference< WatchDog > > e : c_kennel.entrySet() ) {
            final WeakReference< WatchDog > w = e.getValue();

            //  Remove expired as well
            if( w.get() == null ) {
                c_kennel.remove( e.getKey() );
                scrub();
                break;
            }
        }
    }

    /**
     *  Can be used to enable the WatchDog.  Will cause a new Thread to be created, if none was existing previously.
     */
    public void enable() {
        synchronized( WatchDog.class ) {
            if( !m_enabled ) {
                m_enabled = true;
                c_watcherThread = new WatchDogThread( m_engine );
                c_watcherThread.start();
            }
        }
    }

    /**
     *  Is used to disable a WatchDog.  The watchdog thread is shut down and resources released.
     */
    public void disable() {
        synchronized( WatchDog.class ) {
            if( m_enabled ) {
                m_enabled = false;
                c_watcherThread.shutdown();
                c_watcherThread = null;
            }
        }
    }

    /**
     *  Enters a watched state with no expectation of the expected completion time. In practice this method is
     *  used when you have no idea, but would like to figure out, e.g. via debugging, where exactly your Watchable is.
     *
     *  @param state A free-form string description of your state.
     */
    public void enterState( final String state ) {
        enterState( state, Integer.MAX_VALUE );
    }

    /**
     *  Enters a watched state which has an expected completion time.  This is the main method for using the
     *  WatchDog.  For example:
     *
     *  <code>
     *     WatchDog w = m_engine.getCurrentWatchDog();
     *     w.enterState("Processing Foobar", 60);
     *     foobar();
     *     w.exitState();
     *  </code>
     *
     *  If the call to foobar() takes more than 60 seconds, you will receive an ERROR in the log stream.
     *
     *  @param state A free-form string description of the state
     *  @param expectedCompletionTime The timeout in seconds.
     */
    public void enterState( final String state, final int expectedCompletionTime ) {
        LOG.debug(  "{}: Entering state {}, expected completion in {} s", m_watchable.getName(), state, expectedCompletionTime );
        synchronized( m_stateStack ) {
            final State st = new State( state, expectedCompletionTime );
            m_stateStack.push( st );
        }
    }

    /**
     *  Exits a state entered with enterState().  This method does not check that the state is correct, it'll just
     *  pop out whatever is on the top of the state stack.
     */
    public void exitState() {
        exitState( null );
    }

    /**
     *  Exits a particular state entered with enterState().  The state is checked against the current state, and if
     *  they do not match, an error is flagged.
     *
     *  @param state The state you wish to exit.
     */
    public void exitState( final String state ) {
        if( !m_stateStack.empty() ) {
            synchronized( m_stateStack ) {
                final State st = m_stateStack.peek();
                if( state == null || st.getState().equals( state ) ) {
                    m_stateStack.pop();

                    LOG.debug( "{}: Exiting state {}", m_watchable.getName(), st.getState() );
                } else {
                    // FIXME: should actually go and fix things for that
                    LOG.error( "exitState() called before enterState()" );
                }
            }
        } else {
            LOG.warn( "Stack for " + m_watchable.getName() + " is empty!" );
        }
    }

    /**
     * helper to see if the associated stateStack is not empty.
     *
     * @return {@code true} if not empty, {@code false} otherwise.
     */
    public boolean isStateStackNotEmpty() {
        return !m_stateStack.isEmpty();
    }

    /**
     * helper to see if the associated watchable is alive.
     *
     * @return {@code true} if it's alive, {@code false} otherwise.
     */
    public boolean isWatchableAlive() {
        return m_watchable != null && m_watchable.isAlive();
    }

    /**
     *  Checks whether the watched thread exceeded the timeout of its current state, and reports it if so.
     *
     *  @return whether the thread is late
     */
    private boolean check() {
        LOG.debug( "Checking watchdog '{}'", m_watchable.getName() );

        synchronized( m_stateStack ) {
            if( !m_stateStack.empty() ) {
                final State st = m_stateStack.peek();
                final long now = System.currentTimeMillis();

                if( now > st.getExpiryTime() ) {
                    LOG.info( "Watchable '" + m_watchable.getName() + "' exceeded timeout in state '" + st.getState() +
                              "' by " + (now - st.getExpiryTime()) / 1000 + " seconds" +
                             ( LOG.isDebugEnabled() ? "" : "Enable DEBUG-level logging to see stack traces." ) );
                    reportHangingThread( st );
                    dumpStackTraceForWatchable();

                    m_watchable.timeoutExceeded( st.getState() );
                    return true;
                }
            } else {
                LOG.warn( "Stack for " + m_watchable.getName() + " is empty!" );
            }
        }
        return false;
    }

    /**
     *  Describes what a thread that exceeded its timeout is doing: its stack trace and, if it waits for a lock, the thread
     *  holding that lock with its stack trace, and so on along the chain of waiting threads.
     *
     *  @param thread the late thread
     *  @return the description as multiple lines of text
     */
    static String describeHangingThread( final Thread thread ) {
        final StringBuilder description = new StringBuilder();
        try {
            final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
            final Set< Long > described = new HashSet<>();
            long id = thread.getId();
            // a thread waiting for a lock leads to the holder of the lock, which may wait itself: follow that chain
            while( id != -1 && described.size() < MAX_DESCRIBED_THREADS && described.add( id ) ) {
                final ThreadInfo info = threads.getThreadInfo( new long[] { id }, true, true )[ 0 ];
                if( info == null ) {
                    break; // the thread has ended meanwhile
                }
                describe( info, description );
                id = info.getLockOwnerId();
            }
            final long[] deadlocked = threads.findDeadlockedThreads();
            if( deadlocked != null && Arrays.stream( deadlocked ).anyMatch( described::contains ) ) {
                description.append( "These threads are in a deadlock: they wait for each other and will never continue." );
            }
        } catch( final RuntimeException e ) {
            // not every JVM supports this, and a report must never break the watchdog
            description.append( "No lock information available (" ).append( e ).append( "), stack trace of thread '" )
                       .append( thread.getName() ).append( "':\n" );
            for( final StackTraceElement element : thread.getStackTrace() ) {
                description.append( "\tat " ).append( element ).append( '\n' );
            }
        }
        return description.toString();
    }

    private static void describe( final ThreadInfo info, final StringBuilder description ) {
        description.append( "Thread '" ).append( info.getThreadName() ).append( "' is " ).append( info.getThreadState() );
        final LockInfo lock = info.getLockInfo();
        if( lock != null ) {
            description.append( ", waiting for " ).append( lock );
            if( info.getLockOwnerName() != null ) {
                description.append( " held by thread '" ).append( info.getLockOwnerName() ).append( "'" );
            }
        }
        description.append( ":\n" );
        final StackTraceElement[] stack = info.getStackTrace();
        for( int depth = 0; depth < stack.length; depth++ ) {
            description.append( "\tat " ).append( stack[ depth ] ).append( '\n' );
            for( final MonitorInfo monitor : info.getLockedMonitors() ) {
                if( monitor.getLockedStackDepth() == depth ) {
                    description.append( "\t- locked " ).append( monitor ).append( '\n' );
                }
            }
        }
    }

    /**
     *  Writes to the log, once per state, what a late thread is doing. Without this the log only says that a request is
     *  late, which does not help to find out why after the wiki was restarted.
     */
    private void reportHangingThread( final State state ) {
        if( state.m_reported || !( m_watchable instanceof ThreadWrapper ) ) {
            return;
        }
        state.m_reported = true;
        final Thread thread = ( ( ThreadWrapper )m_watchable ).m_thread;
        LOG.warn( "Watchable '" + m_watchable.getName() + "' exceeded timeout in state '" + state.getState() +
                  "', this is what it is doing (logged once):\n" + describeHangingThread( thread ) );
    }

    /**
     *  Dumps the stack traces as DEBUG level events.
     */
    private void dumpStackTraceForWatchable() {
        if( !LOG.isDebugEnabled() ) {
            return;
        }

        final Map< Thread, StackTraceElement[] > stackTraces = Thread.getAllStackTraces();
        final Set< Thread > threads = stackTraces.keySet();
        final Iterator< Thread > threadIterator = threads.iterator();
        final StringBuilder stacktrace = new StringBuilder();

        while ( threadIterator.hasNext() ) {
            final Thread t = threadIterator.next();
            if( t.getName().equals( m_watchable.getName() ) ) {
                if( t.getName().equals( m_watchable.getName() ) ) {
                    stacktrace.append( "dumping stacktrace for too long running thread : " ).append( t );
                } else {
                    stacktrace.append( "dumping stacktrace for other running thread : " ).append( t );
                }
                final StackTraceElement[] ste = stackTraces.get( t );
                for( final StackTraceElement stackTraceElement : ste ) {
                    stacktrace.append( "\n" ).append( stackTraceElement );
                }
            }
        }

        LOG.debug( stacktrace.toString() );
    }

    /**
     *  Strictly for debugging/informative purposes.
     *
     *  @return Random ramblings.
     */
    @Override
    public String toString() {
        synchronized( m_stateStack ) {
            String state = "Idle";

            if( !m_stateStack.empty() ) {
                final State st = m_stateStack.peek();
                state = st.getState();
            }
            return "WatchDog state=" + state;
        }
    }

    /**
     *  This is the chief watchdog thread.
     */
    private static class WatchDogThread extends WikiBackgroundThread {
        /** How often the watchdog thread should wake up (in seconds) */
        private static final int CHECK_INTERVAL = 30;

        public WatchDogThread( final Engine engine ) {
            super( engine, CHECK_INTERVAL );
            setName( "WatchDog for '" + engine.getApplicationName() + "'" );
        }

        @Override
        public void startupTask() {
        }

        @Override
        public void shutdownTask() {
            WatchDog.scrub();
        }

        /**
         *  Checks if the watchable is alive, and if it is, checks if the stack is finished.
         *
         *  If the watchable has been deleted in the meantime, will simply shut down itself.
         */
        @Override
        public void backgroundTask() {
            if( c_kennel.isEmpty() ) {
                return;
            }

            WatchDog.checkAll();

            WatchDog.scrub();
        }

    }

    /**
     *  A class which just stores the state in our State stack.
     */
    private static class State {

        protected final String m_state;
        protected final long   m_enterTime;
        protected final long   m_expiryTime;
        /** Whether the log already tells what the thread is doing in this state. Guarded by the state stack. */
        protected boolean      m_reported;

        protected State( final String state, final int expiry ) {
            m_state      = state;
            m_enterTime  = System.currentTimeMillis();
            m_expiryTime = m_enterTime + ( expiry * 1_000L );
        }

        protected String getState() {
            return m_state;
        }

        protected long getExpiryTime() {
            return m_expiryTime;
        }

    }

    /**
     *  This class wraps a Thread so that it can become Watchable.
     */
    private static class ThreadWrapper implements Watchable {
        private final Thread m_thread;

        public ThreadWrapper( final Thread thread ) {
            m_thread = thread;
        }

        @Override
        public void timeoutExceeded( final String state ) {
            // TODO: Figure out something sane to do here.
        }

        @Override
        public String getName() {
            return m_thread.getName();
        }

        @Override
        public boolean isAlive() {
            return m_thread.isAlive();
        }
    }

}
