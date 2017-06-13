package de.hhu.bsinfo.net.ib;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hhu.bsinfo.net.NetworkDestinationUnreachableException;
import de.hhu.bsinfo.net.core.AbstractConnection;
import de.hhu.bsinfo.net.core.AbstractConnectionManager;
import de.hhu.bsinfo.net.core.DataReceiver;
import de.hhu.bsinfo.net.core.MessageCreator;
import de.hhu.bsinfo.net.core.MessageDirectory;
import de.hhu.bsinfo.net.core.NetworkException;
import de.hhu.bsinfo.net.core.RequestMap;
import de.hhu.bsinfo.utils.NodeID;

/**
 * Created by nothaas on 6/13/17.
 */
public class IBConnectionManager extends AbstractConnectionManager implements JNIIBnet.Callbacks {
    private static final Logger LOGGER = LogManager.getFormatterLogger(IBConnectionManager.class.getSimpleName());

    private final short m_ownNodeId;
    private final int m_bufferSize;
    private final int m_flowControlWindowSize;

    private final MessageDirectory m_messageDirectory;
    private final RequestMap m_requestMap;
    private final MessageCreator m_messageCreator;
    private final DataReceiver m_dataReceiver;

    private final IBBufferPool m_bufferPool;

    private final boolean m_nodeConnected[];

    protected IBConnectionManager(final short p_ownNodeId, final int p_maxConnections, final int p_bufferSize, final int p_flowControlWindowSize,
            final MessageDirectory p_messageDirectory, final RequestMap p_requestMap, final MessageCreator p_messageCreator,
            final DataReceiver p_dataReciever) {
        super(p_maxConnections);

        m_ownNodeId = p_ownNodeId;
        m_bufferSize = p_bufferSize;
        m_flowControlWindowSize = p_flowControlWindowSize;

        m_messageDirectory = p_messageDirectory;
        m_requestMap = p_requestMap;
        m_messageCreator = p_messageCreator;
        m_dataReceiver = p_dataReciever;

        // TODO configurable pool size
        m_bufferPool = new IBBufferPool(p_bufferSize, 100);

        m_nodeConnected = new boolean[NodeID.MAX_ID];

        // TODO expose further infiniband only config values
        JNIIBnet.init(p_ownNodeId, 10, 10, p_bufferSize, 10, 10, 1000, 1, 1, 1000, this);
    }

    @Override
    public void close() {
        JNIIBnet.shutdown();

        super.close();
    }

    @Override
    protected AbstractConnection createConnection(final short p_destination, final AbstractConnection p_existingConnection) throws NetworkException {
        IBConnection connection;

        if (!m_nodeConnected[p_destination]) {
            throw new NetworkDestinationUnreachableException(p_destination);
        }

        m_connectionCreationLock.lock();
        if (m_openConnections == m_maxConnections) {
            dismissRandomConnection();
        }

        connection = (IBConnection) m_connections[p_destination & 0xFFFF];

        if (connection == null) {
            connection = new IBConnection(m_ownNodeId, p_destination, m_bufferSize, m_flowControlWindowSize, m_messageCreator, m_messageDirectory, m_requestMap,
                    m_dataReceiver, m_bufferPool);
            m_connections[p_destination & 0xFFFF] = connection;
            m_openConnections++;
        }

        m_connectionCreationLock.unlock();

        connection.setConnected(true, false);
        connection.setConnected(true, true);

        return connection;
    }

    @Override
    protected void closeConnection(final AbstractConnection p_connection, final boolean p_removeConnection) {
        // #if LOGGER >= DEBUG
        LOGGER.debug("Closing connection 0x%X", p_connection.getDestinationNodeId());
        // #endif /* LOGGER >= DEBUG */

        p_connection.setConnected(false, false);
        p_connection.setConnected(false, true);

        m_connectionCreationLock.lock();
        AbstractConnection tmp = m_connections[p_connection.getDestinationNodeId() & 0xFFFF];
        if (p_connection.equals(tmp)) {
            m_connections[p_connection.getDestinationNodeId() & 0xFFFF] = null;
            m_openConnections--;
        }
        m_connectionCreationLock.unlock();

        // Trigger failure handling for remote node over faulty connection
        m_listener.connectionLost(p_connection.getDestinationNodeId());
    }

    @Override
    public void nodeDiscovered(final short p_nodeId) {
        // #if LOGGER >= DEBUG
        LOGGER.debug("Node discovered 0x%X", p_nodeId);
        // #endif /* LOGGER >= DEBUG */

        m_nodeConnected[p_nodeId] = true;
    }

    @Override
    public void nodeInvalidated(final short p_nodeId) {
        // #if LOGGER >= DEBUG
        LOGGER.debug("Node invalidated 0x%X", p_nodeId);
        // #endif /* LOGGER >= DEBUG */

        m_nodeConnected[p_nodeId] = false;
    }

    @Override
    public void nodeConnected(final short p_nodeId) {
        // #if LOGGER >= DEBUG
        LOGGER.debug("Node connected 0x%X", p_nodeId);
        // #endif /* LOGGER >= DEBUG */
    }

    @Override
    public void nodeDisconnected(final short p_nodeId) {
        // #if LOGGER >= DEBUG
        LOGGER.debug("Node disconnected 0x%X", p_nodeId);
        // #endif /* LOGGER >= DEBUG */
    }

    @Override
    public ByteBuffer getReceiveBuffer(final int p_size) {
        return m_bufferPool.getBuffer();
    }

    @Override
    public void receivedBuffer(final short p_sourceNodeId, ByteBuffer p_buffer, final int p_length) {
        // #if LOGGER >= TRACE
        LOGGER.trace("Received buffer (%d) from 0x%X", p_length, p_sourceNodeId);
        // #endif /* LOGGER >= TRACE */

        IBConnection connection;
        try {
            connection = (IBConnection) getConnection(p_sourceNodeId);
        } catch (final NetworkException e) {
            // #if LOGGER >= ERROR
            LOGGER.error("Getting connection for recv buffer of node 0x%X failed", p_sourceNodeId, e);
            // #endif /* LOGGER >= ERROR */
            return;
        }

        p_buffer.limit(p_length);
        p_buffer.rewind();

        connection.getPipeIn().processReceivedBuffer(p_buffer, p_length);
    }

    @Override
    public void receivedFlowControlData(final short p_sourceNodeId, final int p_bytes) {
        // #if LOGGER >= TRACE
        LOGGER.trace("Received flow control data (%d) from 0x%X", p_bytes, p_sourceNodeId);
        // #endif /* LOGGER >= TRACE */

        IBConnection connection;
        try {
            connection = (IBConnection) getConnection(p_sourceNodeId);
        } catch (final NetworkException e) {
            // #if LOGGER >= ERROR
            LOGGER.error("Getting connection for recv flow control data of node 0x%X failed", p_sourceNodeId, e);
            // #endif /* LOGGER >= ERROR */
            return;
        }

        connection.getPipeIn().handleFlowControlData(p_bytes);
    }
}
