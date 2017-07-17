/*
 * Copyright (C) 2017 Heinrich-Heine-Universitaet Duesseldorf, Institute of Computer Science, Department Operating Systems
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package de.hhu.bsinfo.net.core;

import java.util.concurrent.locks.LockSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hhu.bsinfo.net.NetworkResponseCancelledException;
import de.hhu.bsinfo.net.NetworkResponseDelayedException;

/**
 * Represents a Request
 *
 * @author Florian Klein, florian.klein@hhu.de, 09.03.2012
 */
public abstract class AbstractRequest extends AbstractMessage {
    private static final Logger LOGGER = LogManager.getFormatterLogger(AbstractRequest.class.getSimpleName());

    // Attributes
    private volatile boolean m_fulfilled;
    private volatile boolean m_aborted;

    private volatile Thread m_waitingThread;

    private boolean m_ignoreTimeout;

    private volatile AbstractResponse m_response;

    // Constructors

    /**
     * Creates an instance of Request
     */
    protected AbstractRequest() {
        super();

        m_response = null;
    }

    /**
     * Creates an instance of Request
     *
     * @param p_destination
     *         the destination
     * @param p_type
     *         the message type
     * @param p_subtype
     *         the message subtype
     */
    protected AbstractRequest(final short p_destination, final byte p_type, final byte p_subtype) {
        this(p_destination, p_type, p_subtype, DEFAULT_EXCLUSIVITY_VALUE);
    }

    /**
     * Creates an instance of Request
     *
     * @param p_destination
     *         the destination
     * @param p_type
     *         the message type
     * @param p_subtype
     *         the message subtype
     * @param p_exclusivity
     *         whether this request type allows parallel execution
     */
    protected AbstractRequest(final short p_destination, final byte p_type, final byte p_subtype, final boolean p_exclusivity) {
        super(p_destination, p_type, p_subtype, p_exclusivity);

        m_response = null;
    }

    // Getters

    /**
     * Checks if the network timeout for the request should be ignored
     *
     * @return true if the timeout should be ignored, false otherwise
     */
    public final boolean isIgnoreTimeout() {
        return m_ignoreTimeout;
    }

    /**
     * Set the ignore timeout option
     *
     * @param p_ignoreTimeout
     *         if true the request ignores the network timeout
     */
    public final void setIgnoreTimeout(final boolean p_ignoreTimeout) {
        m_ignoreTimeout = p_ignoreTimeout;
    }

    /**
     * Get the requestID
     *
     * @return the requestID
     */
    public final int getRequestID() {
        return getMessageID();
    }

    /**
     * Get the Response
     *
     * @return the Response
     */
    public final AbstractResponse getResponse() {
        return m_response;
    }

    /**
     * Checks if the Request is fulfilled
     *
     * @return true if the Request is fulfilled, false otherwise
     */
    public final boolean isFulfilled() {
        return m_fulfilled;
    }

    /**
     * Checks if the Request is aborted
     *
     * @return true if the Request is aborted, false otherwise
     */
    public final boolean isAborted() {
        return m_aborted;
    }

    /**
     * Aborts waiting on response. Is called on failure detection
     */
    public final void abort() {
        m_aborted = true;
    }

    // Setters

    /**
     * Get the Response
     *
     * @param <T>
     *         the Response type
     * @param p_class
     *         the Class of the Response
     * @return the Response
     */
    public final <T extends AbstractResponse> T getResponse(final Class<T> p_class) {
        T ret = null;

        assert p_class != null;

        if (m_response != null && p_class.isAssignableFrom(m_response.getClass())) {
            ret = p_class.cast(m_response);
            m_response.setCorrespondingRequest(this);
        }

        return ret;
    }

    /**
     * Wait until the Request is fulfilled or aborted
     *
     * @param p_timeoutMs
     *         Max amount of time to wait for response.
     */
    public final void waitForResponse(final int p_timeoutMs) throws NetworkException {
        m_waitingThread = Thread.currentThread();

        long cur = System.nanoTime();
        long deadline = cur + p_timeoutMs * 1000 * 1000;
        while (!m_fulfilled) {

            if (m_aborted) {
                // #if LOGGER >= TRACE
                LOGGER.trace("Response for request %s , aborted, latency %f ms", toString(), (System.nanoTime() - cur) / 1000.0 / 1000.0);
                // #endif /* LOGGER >= TRACE */

                throw new NetworkResponseCancelledException(getDestination());
            }

            if (!m_ignoreTimeout) {
                if (System.nanoTime() > deadline) {
                    // #if LOGGER >= TRACE
                    LOGGER.trace("Response for request %s , delayed, latency %f ms", toString(), (System.nanoTime() - cur) / 1000.0 / 1000.0);
                    // #endif /* LOGGER >= TRACE */

                    throw new NetworkResponseDelayedException(getDestination());
                }
                LockSupport.parkNanos(p_timeoutMs * 1000 * 1000);
            } else {
                LockSupport.park();
            }
        }

        // TODO statistics for req/resp latency?

        // #if LOGGER >= TRACE
        LOGGER.trace("Request %s fulfilled, response %s, latency %f ms", toString(), m_response, (System.nanoTime() - cur) / 1000.0 / 1000.0);
        // #endif /* LOGGER >= TRACE */
    }

    /**
     * Fulfill the Request
     *
     * @param p_response
     *         the Response
     */
    final void fulfill(final AbstractResponse p_response) {
        assert p_response != null;

        m_response = p_response;
        m_fulfilled = true;

        LockSupport.unpark(m_waitingThread);
    }

}
