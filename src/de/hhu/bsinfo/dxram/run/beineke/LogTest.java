
package de.hhu.bsinfo.dxram.run.beineke;

import java.util.ArrayList;
import java.util.Collections;

import de.hhu.bsinfo.dxram.DXRAM;
import de.hhu.bsinfo.dxram.chunk.ChunkService;
import de.hhu.bsinfo.dxram.data.Chunk;
import de.hhu.bsinfo.utils.Tools;

/**
 * Test case for the logging interface
 * @author Kevin Beineke
 *         28.07.2014
 */
public final class LogTest implements Runnable {

	// Attributes
	private static DXRAM m_dxram;
	private static ChunkService m_chunkService;

	private static int m_numberOfThreads;
	private static int m_minChunkSize;
	private static int m_maxChunkSize;
	private static long m_numberOfChunks;
	private static long m_chunksPerThread;
	private static int m_numberOfUpdates;
	private static int m_updatesPerThread;
	private static int m_numberOfDeletes;
	private static int m_deletesPerThread;
	private short m_nodeID;
	private int m_id;

	// Constructors
	/**
	 * Creates an instance of LogTest
	 * @param p_nodeID
	 *            the NodeID
	 * @param p_id
	 *            the thread identifier
	 */
	private LogTest(final short p_nodeID, final int p_id) {
		m_nodeID = p_nodeID;
		m_id = p_id;
	}

	/**
	 * Program entry point
	 * @param p_arguments
	 *            The program arguments
	 */
	public static void main(final String[] p_arguments) {
		long timeStart;
		short[] nodes;
		Thread[] threads = null;

		if (p_arguments.length == 6) {
			m_numberOfThreads = Integer.parseInt(p_arguments[0]);
			m_numberOfChunks = Long.parseLong(p_arguments[1]);
			m_chunksPerThread = m_numberOfChunks / m_numberOfThreads;
			m_minChunkSize = Integer.parseInt(p_arguments[2]);
			m_maxChunkSize = Integer.parseInt(p_arguments[3]);
			m_numberOfUpdates = Integer.parseInt(p_arguments[4]);
			m_updatesPerThread = m_numberOfUpdates / m_numberOfThreads;
			m_numberOfDeletes = Integer.parseInt(p_arguments[5]);
			m_deletesPerThread = m_numberOfDeletes / m_numberOfThreads;

			if (m_chunksPerThread > Integer.MAX_VALUE) {
				System.out.println("Too many chunks per thread! Exiting.");
				System.exit(-1);
			}
		} else if (p_arguments.length > 6) {
			System.out.println("Too many program arguments! Exiting.");
			System.exit(-1);
		} else {
			System.out.println("Missing program arguments (#threads, #chunks, minimal chunk size, maximal chunk size, #updates, #deletes)! Exiting.");
			System.exit(-1);
		}

		threads = new Thread[m_numberOfThreads];

		// Initialize DXRAM
		m_dxram = new DXRAM();
		m_dxram.initialize("config/dxram.conf");
		m_chunkService = m_dxram.getService(ChunkService.class);

		timeStart = System.currentTimeMillis();
		nodes = new short[m_numberOfThreads];
		for (int i = 0; i < m_numberOfThreads; i++) {
			nodes[i] = (short) (Math.random() * (65536 - 1 + 1) + 1);

			threads[i] = new Thread(new LogTest(nodes[i], i));
			threads[i].start();
		}
		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (final InterruptedException e) {
				System.out.println("Error while joining threads");
			}
		}
		System.out.println("All chunks logged in " + (System.currentTimeMillis() - timeStart) + "ms");

		/*-timeStart = System.currentTimeMillis();
		for (short i = 0; i < m_numberOfNodes; i++) {
			try {
				log.printMetadataOfAllEntries(nodes[i]);
				// log.readAllEntries(nodes[i]);
			} catch (final DXRAMException e) {
				System.out.println("Error: Could not read entries of node " + nodes[i]);
			}
		}
		System.out.println("All chunks read in " + (System.currentTimeMillis() - timeStart) + "ms");*/
	}

	@Override
	public void run() {
		long[] removes;
		Chunk[] chunks;
		Chunk[] updates;
		Chunk[] fillChunks;
		ArrayList<Long> chunkIDList;
		ArrayList<Chunk> chunkList;

		System.out.println("I am " + m_id + ", writing " + m_chunksPerThread + " chunks between " + m_minChunkSize + " Bytes and " + m_maxChunkSize + " Bytes");

		/*
		 * Preparation
		 */
		// Create array of Chunks
		chunks = new Chunk[(int) m_chunksPerThread];
		for (int i = 0; i < m_chunksPerThread; i++) {
			chunks[i] = new Chunk(Tools.getRandomValue(m_minChunkSize, m_maxChunkSize));
			chunks[i].getData().put(("This is a test! (" + m_nodeID + ")").getBytes());
		}
		m_chunkService.create(chunks);

		// Create list for updates (chunks)
		chunkList = new ArrayList<Chunk>();
		for (int i = 0; i < m_chunksPerThread; i++) {
			chunkList.add(chunks[i]);
		}
		Collections.shuffle(chunkList);
		updates = chunkList.subList(0, m_updatesPerThread).toArray(new Chunk[m_updatesPerThread]);

		// Create list for deletes (chunkIDs)
		chunkIDList = new ArrayList<Long>();
		for (int i = 0; i < m_chunksPerThread; i++) {
			chunkIDList.add(chunks[i].getID());
		}
		Collections.shuffle(chunkIDList);
		removes = chunkIDList.subList(0, m_deletesPerThread).stream().mapToLong(l -> l).toArray();

		// Create fill chunks (to clear secondary log buffer)
		fillChunks = new Chunk[10];
		for (int i = 0; i < 10; i++) {
			fillChunks[i] = new Chunk(1048576);
		}

		/*
		 * Execution
		 */
		// Put
		System.out.print("Starting replication...");
		m_chunkService.put(chunks);
		System.out.println("done\n");

		// Updates
		System.out.print("Starting updates...");
		m_chunkService.put(updates);
		System.out.println("done\n");

		// Delete
		System.out.print("Starting deletion...");
		m_chunkService.remove(removes);
		System.out.println("done\n");

		// Put dummies
		System.out.print("Starting fill replication...");
		m_chunkService.put(fillChunks);
		System.out.println("done");
	}
}