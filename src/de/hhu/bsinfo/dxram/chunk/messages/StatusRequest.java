
package de.hhu.bsinfo.dxram.chunk.messages;

import de.hhu.bsinfo.menet.AbstractRequest;

/**
 * Request to get status information from a remote chunk service.
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 07.04.16
 */
public class StatusRequest extends AbstractRequest {
	/**
	 * Creates an instance of StatusRequest.
	 * This constructor is used when receiving this message.
	 */
	public StatusRequest() {
		super();
	}

	/**
	 * Creates an instance of StatusRequest.
	 * This constructor is used when sending this message.
	 * @param p_destination
	 *            the destination node id.
	 */
	public StatusRequest(final short p_destination) {
		super(p_destination, ChunkMessages.TYPE, ChunkMessages.SUBTYPE_STATUS_REQUEST);
	}
}