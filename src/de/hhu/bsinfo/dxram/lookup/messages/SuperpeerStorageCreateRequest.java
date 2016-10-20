
package de.hhu.bsinfo.dxram.lookup.messages;

import java.nio.ByteBuffer;

import de.hhu.bsinfo.dxram.net.messages.DXRAMMessageTypes;
import de.hhu.bsinfo.menet.AbstractRequest;

/**
 * Message to allocate memory in the superpeer storage.
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 17.05.15
 */
public class SuperpeerStorageCreateRequest extends AbstractRequest {
	private int m_storageId;
	private int m_size;
	private boolean m_replicate;

	/**
	 * Creates an instance of SuperpeerStorageCreateRequest
	 */
	public SuperpeerStorageCreateRequest() {
		super();
	}

	/**
	 * Creates an instance of SuperpeerStorageCreateRequest
	 * @param p_destination
	 *            the destination
	 * @param p_storageId
	 *            Identifier for the chunk.
	 * @param p_size
	 *            Size in bytes of the data to store
	 * @param p_replicate
	 *            True if this message is a replication to other superpeer message, false if normal message
	 */
	public SuperpeerStorageCreateRequest(final short p_destination, final int p_storageId, final int p_size,
			final boolean p_replicate) {
		super(p_destination, DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_SUPERPEER_STORAGE_CREATE_REQUEST);

		m_storageId = p_storageId;
		m_size = p_size;
		m_replicate = p_replicate;
	}

	/**
	 * Get the storage id to use for this memory block
	 * @return Storage id.
	 */
	public int getStorageId() {
		return m_storageId;
	}

	/**
	 * Get the size for the allocation
	 * @return Size in bytes
	 */
	public int getSize() {
		return m_size;
	}

	/**
	 * Check if this message is a replicate message.
	 * @return True if replicate message, false otherwise.
	 */
	public boolean isReplicate() {
		return m_replicate;
	}

	@Override
	protected final void writePayload(final ByteBuffer p_buffer) {
		p_buffer.putInt(m_storageId);
		p_buffer.putInt(m_size);
		p_buffer.put((byte) (m_replicate ? 1 : 0));
	}

	@Override
	protected final void readPayload(final ByteBuffer p_buffer) {
		m_storageId = p_buffer.getInt();
		m_size = p_buffer.getInt();
		m_replicate = p_buffer.get() != 0;
	}

	@Override
	protected final int getPayloadLength() {
		return Integer.BYTES + Integer.BYTES + Byte.BYTES;
	}
}
