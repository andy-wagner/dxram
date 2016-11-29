package de.hhu.bsinfo.dxram.chunk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hhu.bsinfo.dxram.DXRAMComponentOrder;
import de.hhu.bsinfo.dxram.boot.AbstractBootComponent;
import de.hhu.bsinfo.dxram.data.Chunk;
import de.hhu.bsinfo.dxram.engine.AbstractDXRAMComponent;
import de.hhu.bsinfo.dxram.engine.DXRAMComponentAccessor;
import de.hhu.bsinfo.dxram.engine.DXRAMContext;
import de.hhu.bsinfo.dxram.log.messages.InitRequest;
import de.hhu.bsinfo.dxram.log.messages.LogMessage;
import de.hhu.bsinfo.dxram.mem.MemoryManagerComponent;
import de.hhu.bsinfo.dxram.net.NetworkComponent;
import de.hhu.bsinfo.dxram.recovery.RecoveryDataStructure;
import de.hhu.bsinfo.ethnet.NetworkException;

/**
 * Component for chunk handling.
 *
 * @author Kevin Beineke, kevin.beineke@hhu.de, 30.03.2016
 */
public class ChunkBackupComponent extends AbstractDXRAMComponent {

    private static final Logger LOGGER = LogManager.getFormatterLogger(ChunkBackupComponent.class.getSimpleName());

    // dependent components
    private AbstractBootComponent m_boot;
    private MemoryManagerComponent m_memoryManager;
    private NetworkComponent m_network;

    /**
     * Constructor
     */
    public ChunkBackupComponent() {
        super(DXRAMComponentOrder.Init.CHUNK, DXRAMComponentOrder.Shutdown.CHUNK);
    }

    /**
     * Replicates all local Chunks of given range to a specific backup peer
     *
     * @param p_backupPeer
     *     the new backup peer
     * @param p_firstChunkID
     *     the first ChunkID
     * @param p_lastChunkID
     *     the last ChunkID
     */
    public void replicateBackupRange(final short p_backupPeer, final long p_firstChunkID, final long p_lastChunkID) {
        int counter = 0;
        Chunk currentChunk;
        Chunk[] chunks;

        // Initialize backup range on backup peer
        InitRequest request = new InitRequest(p_backupPeer, p_firstChunkID, m_boot.getNodeID());
        try {
            m_network.sendMessage(request);
        } catch (final NetworkException e) {
            // #if LOGGER == ERROR
            LOGGER.error("Replicating backup range 0x%X to 0x%X failed. Could not initialize backup range", p_firstChunkID, p_backupPeer);
            // #endif /* LOGGER == ERROR */
            return;
        }

        // Gather all chunks of backup range
        chunks = new Chunk[(int) (p_lastChunkID - p_firstChunkID + 1)];
        for (long chunkID = p_firstChunkID; chunkID <= p_lastChunkID; chunkID++) {
            currentChunk = new Chunk(chunkID);

            m_memoryManager.lockAccess();
            m_memoryManager.get(currentChunk);
            m_memoryManager.unlockAccess();

            chunks[counter++] = currentChunk;
        }

        // Send all chunks to backup peer
        try {
            m_network.sendMessage(new LogMessage(p_backupPeer, chunks));
        } catch (final NetworkException ignore) {

        }
    }

    /**
     * Replicates all local Chunks to a specific backup peer
     *
     * @param p_backupPeer
     *     the new backup peer
     * @param p_chunkIDs
     *     the ChunkIDs of the Chunks to replicate
     * @param p_rangeID
     *     the RangeID
     */
    public void replicateBackupRange(final short p_backupPeer, final long[] p_chunkIDs, final byte p_rangeID) {
        int counter = 0;
        Chunk currentChunk;
        Chunk[] chunks;

        // Initialize backup range on backup peer
        InitRequest request = new InitRequest(p_backupPeer, p_rangeID, m_boot.getNodeID());

        try {
            m_network.sendMessage(request);
        } catch (final NetworkException e) {
            // #if LOGGER == ERROR
            LOGGER.error("Replicating backup range 0x%X to 0x%X failed. Could not initialize backup range", p_rangeID, p_backupPeer);
            // #endif /* LOGGER == ERROR */
            return;
        }

        // Gather all chunks of backup range
        chunks = new Chunk[p_chunkIDs.length];
        for (long chunkID : p_chunkIDs) {
            currentChunk = new Chunk(chunkID);

            m_memoryManager.lockAccess();
            m_memoryManager.get(currentChunk);
            m_memoryManager.unlockAccess();

            chunks[counter++] = currentChunk;
        }

        // Send all chunks to backup peer
        try {
            m_network.sendMessage(new LogMessage(p_backupPeer, chunks));
        } catch (final NetworkException ignore) {

        }
    }

    /**
     * Put a recovered chunks into local memory.
     *
     * @param p_chunks
     *     Chunks to put.
     */
    public void putRecoveredChunks(final Chunk[] p_chunks) {

        m_memoryManager.lockManage();
        for (Chunk chunk : p_chunks) {

            m_memoryManager.create(chunk.getID(), chunk.getDataSize());
            m_memoryManager.put(chunk);

            // #if LOGGER == TRACE
            LOGGER.trace("Stored recovered chunk 0x%X locally", chunk.getID());
            // #endif /* LOGGER == TRACE */
        }
        m_memoryManager.unlockManage();
    }

    public void startBlockRecovery() {
        m_memoryManager.lockManage();
    }

    public void stopBlockRecovery() {
        m_memoryManager.unlockManage();
    }

    /**
     * Put a recovered chunks into local memory.
     *
     * @lock manage lock of memory manager must be acquired
     */
    public boolean putRecoveredChunk(final RecoveryDataStructure p_dataStructure) {
        long chunkID = p_dataStructure.getID();

        if (m_memoryManager.create(chunkID, p_dataStructure.getLength()) != chunkID) {
            return false;
        }

        if (m_memoryManager.put(p_dataStructure) != MemoryManagerComponent.MemoryErrorCodes.SUCCESS) {
            return false;
        }

        // #if LOGGER == TRACE
        LOGGER.trace("Stored recovered chunk 0x%X locally", chunkID);
        // #endif /* LOGGER == TRACE */

        return true;
    }

    @Override
    protected void resolveComponentDependencies(final DXRAMComponentAccessor p_componentAccessor) {
        m_boot = p_componentAccessor.getComponent(AbstractBootComponent.class);
        m_memoryManager = p_componentAccessor.getComponent(MemoryManagerComponent.class);
        m_network = p_componentAccessor.getComponent(NetworkComponent.class);
    }

    @Override
    protected boolean initComponent(final DXRAMContext.EngineSettings p_engineEngineSettings) {
        return true;
    }

    @Override
    protected boolean shutdownComponent() {
        return true;
    }

}