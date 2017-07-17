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

import java.nio.BufferOverflowException;
import java.util.concurrent.atomic.AtomicInteger;

import de.hhu.bsinfo.utils.NodeID;

/**
 * Represents a network message
 *
 * @author Florian Klein, florian.klein@hhu.de, 09.03.2012
 * @author Marc Ewert, marc.ewert@hhu.de, 18.09.2014
 */
public abstract class AbstractMessage {

    static final boolean DEFAULT_EXCLUSIVITY_VALUE = false;
    /*- Header size:
     *  messageID + type + subtype + messageType and exclusivity + payloadSize
     *  3b        + 1b   + 1b      + 1b                          + 4b           = 10 bytes
     */
    static final byte HEADER_SIZE = 10;
    static final byte PAYLOAD_SIZE_LENGTH = 4;
    // Constants
    private static final int INVALID_MESSAGE_ID = -1;
    private static final byte DEFAULT_MESSAGE_TYPE = 0;
    private static final byte DEFAULT_TYPE = 0;
    private static final byte DEFAULT_SUBTYPE = 0;

    private static AtomicInteger ms_nextMessageID = new AtomicInteger();

    // Attributes
    // (!) MessageID occupies only 3 byte in message header
    int m_messageID = INVALID_MESSAGE_ID;
    private short m_source;
    private short m_destination;
    // Message type: message and requests -> 0, responses -> 1; used to avoid instanceof in message processing
    private byte m_messageType;
    private byte m_type;
    private byte m_subtype;
    // (!) Exclusivity is written as a byte (0 -> false, 1 -> true)
    private boolean m_exclusivity;

    private int m_oldMessageID = INVALID_MESSAGE_ID;

    // Constructors

    /**
     * Creates an instance of Message
     *
     * @param p_destination
     *         the destination
     * @param p_type
     *         the message type
     * @param p_subtype
     *         the message subtype
     */
    protected AbstractMessage(final short p_destination, final byte p_type, final byte p_subtype) {
        this(getNextMessageID(), p_destination, p_type, p_subtype, DEFAULT_EXCLUSIVITY_VALUE);
    }

    /**
     * Creates an instance of Message
     *
     * @param p_destination
     *         the destination
     * @param p_type
     *         the message type
     * @param p_subtype
     *         the message subtype
     * @param p_exclusivity
     *         whether this message type allows parallel execution
     */
    protected AbstractMessage(final short p_destination, final byte p_type, final byte p_subtype, final boolean p_exclusivity) {
        this(getNextMessageID(), p_destination, p_type, p_subtype, p_exclusivity);
    }

    /**
     * Creates an instance of Message
     */
    protected AbstractMessage() {
        m_messageID = INVALID_MESSAGE_ID;
        m_source = NodeID.INVALID_ID;
        m_destination = NodeID.INVALID_ID;
        m_messageType = 0;
        m_type = DEFAULT_TYPE;
        m_subtype = DEFAULT_SUBTYPE;

        m_exclusivity = DEFAULT_EXCLUSIVITY_VALUE;
    }

    /**
     * Creates an instance of Message
     *
     * @param p_messageID
     *         the messageID
     * @param p_destination
     *         the destination
     * @param p_type
     *         the message type
     * @param p_subtype
     *         the message subtype
     */
    protected AbstractMessage(final int p_messageID, final short p_destination, final byte p_type, final byte p_subtype) {
        this(p_messageID, p_destination, p_type, p_subtype, DEFAULT_EXCLUSIVITY_VALUE);

        // Set message type to 1 for responses only
        m_messageType = (byte) 1;
    }

    /**
     * Creates an instance of Message
     *
     * @param p_messageID
     *         the messageID
     * @param p_destination
     *         the destination
     * @param p_type
     *         the message type
     * @param p_subtype
     *         the message subtype
     * @param p_exclusivity
     *         whether this is an exclusive message or not
     */
    private AbstractMessage(final int p_messageID, final short p_destination, final byte p_type, final byte p_subtype, final boolean p_exclusivity) {
        assert p_destination != NodeID.INVALID_ID;

        m_messageID = p_messageID;
        m_source = -1;
        m_destination = p_destination;
        m_messageType = 0;
        m_type = p_type;
        m_subtype = p_subtype;

        m_exclusivity = p_exclusivity;
    }

    void initialize(final MessageHeader p_header) {
        m_messageID = p_header.getMessageID();
        m_type = p_header.getType();
        m_subtype = p_header.getSubtype();
        m_messageType = p_header.getMessageType();
        m_exclusivity = p_header.isExclusive();
    }

    // Getters

    /**
     * Get the source
     *
     * @return the source
     */
    public final short getSource() {
        return m_source;
    }

    /**
     * Sets source of the message
     *
     * @param p_source
     *         the source node ID
     */
    final void setSource(final short p_source) {
        m_source = p_source;
    }

    /**
     * Get the destination
     *
     * @return the destination
     */
    public final short getDestination() {
        return m_destination;
    }

    /**
     * Sets destination of the message
     *
     * @param p_destination
     *         the destination node ID
     */
    final void setDestination(final short p_destination) {
        m_destination = p_destination;
    }

    /**
     * Return if this message is a response
     *
     * @return true if message is a response, false otherwise (message or request)
     */
    public final boolean isResponse() {
        return m_messageType == 1;
    }

    /**
     * Get the message type
     *
     * @return the message type
     */
    public final byte getType() {
        return m_type;
    }

    /**
     * Get the message subtype
     *
     * @return the message subtype
     */
    public final byte getSubtype() {
        return m_subtype;
    }

    /**
     * Returns whether this message type allows parallel execution
     *
     * @return the exclusivity
     */
    public final boolean isExclusive() {
        return m_exclusivity;
    }

    // Setters

    @Override
    public final String toString() {
        if (m_source != -1) {
            return getClass().getSimpleName() + '[' + m_messageID + ", " + NodeID.toHexString(m_source) + ", " + NodeID.toHexString(m_destination) + ']';
        } else {
            return getClass().getSimpleName() + '[' + m_messageID + ", " + NodeID.toHexString(m_destination) + ']';
        }
    }

    // Methods

    /**
     * Get the total number of bytes the payload requires to create a buffer.
     *
     * @return Number of bytes of the payload
     */
    protected int getPayloadLength() {
        return 0;
    }

    int getTotalSize() {
        return HEADER_SIZE + getPayloadLength();
    }

    /**
     * Serialize the message into given byte buffer
     *
     * @param p_buffer
     *         the ByteBuffer to store serialized message
     * @param p_messageSize
     *         the message to serialize
     * @throws NetworkException
     *         if message could not be serialized
     */
    protected final void serialize(final byte[] p_buffer, final int p_offset, final int p_messageSize, final boolean p_hasOverflow) throws NetworkException {
        fillBuffer(p_buffer, p_offset, p_messageSize - HEADER_SIZE, p_hasOverflow);
    }

    /**
     * Reads the message payload from the byte buffer
     *
     * @param p_importer
     *         the importer
     */
    protected void readPayload(final AbstractMessageImporter p_importer) throws ArrayIndexOutOfBoundsException {
    }

    /**
     * Reads the message payload from the byte buffer; used for logging to copy directly into primary write buffer if possible
     *
     * @param p_importer
     *         the importer
     * @param p_payloadSize
     *         the message's payload size
     */
    protected void readPayload(final AbstractMessageImporter p_importer, final int p_payloadSize) throws ArrayIndexOutOfBoundsException {
        readPayload(p_importer);
    }

    /**
     * Writes the message payload into the buffer
     *
     * @param p_exporter
     *         the buffer
     */
    protected void writePayload(final AbstractMessageExporter p_exporter) {
    }

    /**
     * Get the messageID
     *
     * @return the messageID
     */
    final int getMessageID() {
        return m_messageID;
    }

    /**
     * Get next free messageID
     *
     * @return next free messageID
     */
    private static int getNextMessageID() {
        return ms_nextMessageID.incrementAndGet();
    }

    /**
     * Fills a given ByteBuffer with the message
     *
     * @param p_buffer
     *         a given ByteBuffer
     * @param p_payloadSize
     *         the payload size
     * @throws NetworkException
     *         if message buffer is too small
     */
    private void fillBuffer(final byte[] p_buffer, final int p_offset, final int p_payloadSize, final boolean p_hasOverflow) throws NetworkException {

        AbstractMessageExporter exporter = ExporterPool.getInstance().getExporter(p_hasOverflow);
        exporter.setBuffer(p_buffer);
        exporter.setPosition(p_offset);

        try {
            // Message reused (probably pooled)
            if (m_messageID == m_oldMessageID) {
                m_messageID = getNextMessageID();
            }

            // Put 3 byte message ID
            exporter.writeByte((byte) (m_messageID >>> 16));
            exporter.writeByte((byte) (m_messageID >>> 8));
            exporter.writeByte((byte) m_messageID);

            System.out.println("Exporting " + m_messageID + ", " + this);

            exporter.writeByte(m_type);
            exporter.writeByte(m_subtype);
            exporter.writeByte((byte) ((m_messageType << 4) + (m_exclusivity ? 1 : 0)));
            exporter.writeInt(p_payloadSize);

            writePayload(exporter);
        } catch (final BufferOverflowException e) {
            throw new NetworkException("Could not create message " + this + ", because message buffer is too small, payload size " + p_payloadSize, e);
        }

        int numberOfWrittenBytes = exporter.getNumberOfWrittenBytes();
        int messageSize = p_payloadSize + HEADER_SIZE;
        if (numberOfWrittenBytes < messageSize) {
            throw new NetworkException(
                    "Did not create message " + this + ", because message contents are smaller than expected payload size: " + numberOfWrittenBytes + " < " +
                            messageSize);
        }

        m_oldMessageID = m_messageID;
    }

}
