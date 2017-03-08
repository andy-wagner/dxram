package de.hhu.bsinfo.dxram.util;

import java.util.Arrays;

import de.hhu.bsinfo.dxram.data.ChunkID;
import de.hhu.bsinfo.utils.serialization.Exportable;
import de.hhu.bsinfo.utils.serialization.Exporter;
import de.hhu.bsinfo.utils.serialization.Importable;
import de.hhu.bsinfo.utils.serialization.Importer;

/**
 * Custom array list implementation offering direct access to a primitive long array
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 17.01.2017
 */
public class ArrayListLong implements Importable, Exportable {

    private int m_capacityChunk = 10;
    private long[] m_array;
    private int m_size = 0;
    private int m_endOfList = 0;

    /**
     * Default constructor
     */
    public ArrayListLong() {
        m_array = new long[m_capacityChunk];
        Arrays.fill(m_array, ChunkID.INVALID_ID);
    }

    /**
     * Create the array list with a specific capacity chunk size
     *
     * @param p_capacityChunk
     *     capacity chunk size
     */
    public ArrayListLong(final int p_capacityChunk) {
        m_capacityChunk = p_capacityChunk;
        m_array = new long[p_capacityChunk];
        Arrays.fill(m_array, ChunkID.INVALID_ID);
    }

    /**
     * Create the array list with a single element inserted on construction
     *
     * @param p_element
     *     Element to insert on construction
     */
    public ArrayListLong(final long p_element) {
        m_array = new long[] {p_element};
        m_size = 1;
    }

    /**
     * Constructor for wrapper method
     *
     * @param p_array
     *     Array to wrap
     */
    private ArrayListLong(final long[] p_array) {
        m_array = p_array;
        m_size = p_array.length;
    }

    /**
     * Wrap an existing primitive long aray
     *
     * @param p_array
     *     Array to wrap
     * @return ArrayListLong object with wrapped array
     */
    public static ArrayListLong wrap(final long[] p_array) {
        return new ArrayListLong(p_array);
    }

    /**
     * Get the size (number of inserted elements NOT capacity) of the array
     *
     * @return Size of the array
     */
    public int getSize() {
        return m_size;
    }

    /**
     * Check if the array ist empty
     *
     * @return True on empty, false otherwise
     */
    public boolean isEmpty() {
        return m_size == 0;
    }

    /**
     * Get the underlying primitive long array
     *
     * @return Primitive long array
     */
    public long[] getArray() {
        return m_array;
    }

    /**
     * Add an element to the array. The array is automatically resized if necessary
     *
     * @param p_val
     *     Value to add
     */
    public void add(final long p_val) {
        if (m_array.length - m_endOfList == 0) {
            m_array = Arrays.copyOf(m_array, m_array.length + m_capacityChunk);
            Arrays.fill(m_array, m_size, m_array.length, ChunkID.INVALID_ID);
        }

        m_array[m_endOfList++] = p_val;
        m_size++;
    }

    /**
     * Add an element to the array at given index. The array is automatically resized if necessary
     *
     * @param p_index
     *     index at which the specified element is to be inserted
     * @param p_val
     *     Value to add
     */
    public void add(final int p_index, final long p_val) {
        if (m_array.length <= p_index) {
            int oldSize = m_array.length;
            m_array = Arrays.copyOf(m_array, p_index + 1);
            Arrays.fill(m_array, oldSize, m_array.length, ChunkID.INVALID_ID);
        }

        m_array[p_index] = p_val;
        m_size++;

        if (p_index > m_endOfList) {
            m_endOfList = p_index + 1;
        }
    }

    public void addSorted(final long p_val) {
        if (m_array.length - m_endOfList == 0) {
            m_array = Arrays.copyOf(m_array, m_array.length + m_capacityChunk);
            Arrays.fill(m_array, m_endOfList, m_array.length, ChunkID.INVALID_ID);
        }

        if (m_endOfList == 0 || m_array[m_endOfList - 1] < p_val) {
            m_array[m_endOfList++] = p_val;
        } else {
            for (int i = 0; i < m_array.length; i++) {
                if (p_val < m_array[i]) {
                    System.arraycopy(m_array, i, m_array, i + 1, m_endOfList - i);
                    m_endOfList++;

                    m_array[i] = p_val;
                    break;
                }
            }
        }
        m_size++;
    }

    public long[] getRanges() {
        ArrayListLong ranges = new ArrayListLong();

        long currentCID;
        int currentIndex;
        int index = 0;
        while (index < m_endOfList) {
            currentCID = m_array[index];
            ranges.add(currentCID);
            currentIndex = 1;

            while (index + currentIndex < m_array.length && m_array[index + currentIndex] == currentCID + currentIndex) {
                currentIndex++;
            }
            ranges.add(currentCID + currentIndex - 1);
            index += currentIndex;
        }

        return ranges.getArray();
    }

    /**
     * Add all values of another ArrayListLong object
     *
     * @param p_list
     *     Array with elements to add to the current one
     */
    public void addAll(final ArrayListLong p_list) {
        m_array = Arrays.copyOf(m_array, m_array.length + p_list.m_size);
        System.arraycopy(p_list.m_array, 0, m_array, m_size, p_list.m_size);
        m_size += p_list.m_size;
    }

    /**
     * Replaces the element at given index
     *
     * @param p_index
     *     index at which the specified element is to be overwritten
     * @param p_val
     *     Value to set
     */
    public void set(final int p_index, final long p_val) {
        m_array[p_index] = p_val;
    }

    /**
     * Get an element from the array
     *
     * @param p_index
     *     Index to access
     * @return Element at the specified index
     */
    public long get(final int p_index) {
        return m_array[p_index];
    }

    @Override
    public void exportObject(final Exporter p_exporter) {
        p_exporter.writeInt(m_endOfList);
        p_exporter.writeLongs(m_array, 0, m_endOfList);
    }

    @Override
    public void importObject(final Importer p_importer) {
        m_array = p_importer.readLongArray();
        m_endOfList = m_array.length;
        m_size = 0;
        for (long l : m_array) {
            if (l != ChunkID.INVALID_ID) {
                m_size++;
            }
        }
    }

    @Override
    public int sizeofObject() {
        return Integer.BYTES + Long.BYTES * m_endOfList;
    }
}
