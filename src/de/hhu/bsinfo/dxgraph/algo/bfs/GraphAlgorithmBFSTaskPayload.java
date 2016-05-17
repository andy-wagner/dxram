
package de.hhu.bsinfo.dxgraph.algo.bfs;

import de.hhu.bsinfo.dxcompute.ms.AbstractTaskPayload;
import de.hhu.bsinfo.dxgraph.GraphTaskPayloads;
import de.hhu.bsinfo.dxgraph.algo.bfs.front.ConcurrentBitVector;
import de.hhu.bsinfo.dxgraph.algo.bfs.front.FrontierList;
import de.hhu.bsinfo.dxgraph.algo.bfs.messages.*;
import de.hhu.bsinfo.dxgraph.data.BFSResult;
import de.hhu.bsinfo.dxgraph.data.GraphRootList;
import de.hhu.bsinfo.dxgraph.data.Vertex;
import de.hhu.bsinfo.dxgraph.load.GraphLoadBFSRootListTaskPayload;
import de.hhu.bsinfo.dxgraph.load.GraphLoadPartitionIndexTaskPayload;
import de.hhu.bsinfo.dxgraph.load.GraphPartitionIndex;
import de.hhu.bsinfo.dxram.boot.BootService;
import de.hhu.bsinfo.dxram.chunk.ChunkService;
import de.hhu.bsinfo.dxram.data.ChunkID;
import de.hhu.bsinfo.dxram.data.ChunkLockOperation;
import de.hhu.bsinfo.dxram.engine.DXRAMServiceAccessor;
import de.hhu.bsinfo.dxram.logger.LoggerService;
import de.hhu.bsinfo.dxram.lookup.overlay.BarrierID;
import de.hhu.bsinfo.dxram.nameservice.NameserviceService;
import de.hhu.bsinfo.dxram.net.NetworkErrorCodes;
import de.hhu.bsinfo.dxram.net.NetworkService;
import de.hhu.bsinfo.dxram.sync.SynchronizationService;
import de.hhu.bsinfo.menet.AbstractMessage;
import de.hhu.bsinfo.menet.NetworkHandler.MessageReceiver;
import de.hhu.bsinfo.menet.NodeID;
import de.hhu.bsinfo.utils.Pair;
import de.hhu.bsinfo.utils.args.ArgumentList;
import de.hhu.bsinfo.utils.args.ArgumentList.Argument;
import de.hhu.bsinfo.utils.serialization.Exporter;
import de.hhu.bsinfo.utils.serialization.Importer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map.Entry;

public class GraphAlgorithmBFSTaskPayload extends AbstractTaskPayload {

	private static final String MS_BFS_RESULT_NAMESRV_IDENT = "BFR";

	private static final Argument MS_ARG_BFS_ROOT =
			new Argument("bfsRootNameserviceEntryName", null, false,
					"Name of the nameservice entry for the roots to use for BFS.");
	private static final Argument MS_ARG_VERTEX_BATCH_SIZE =
			new Argument("vertexBatchSize", null, false, "Number of vertices to cache as a batch for processing.");
	private static final Argument MS_ARG_VERTEX_MSG_BATCH_SIZE =
			new Argument("vertexMessageBatchSize", null, false,
					"Name of vertices to send as a single batch over the network.");
	private static final Argument MS_ARG_NUM_THREADS =
			new Argument("numThreadsPerNode", null, false, "Number of threads to use for BFS on a single node.");
	private static final Argument MS_ARG_MARK_VERTICES =
			new Argument("markVertices", "true", true,
					"Mark the actual vertices/data visited with the level. On false, we just remember if we have visited it");
	private static final Argument MS_ARG_COMP_VERTEX_MSGS =
			new Argument("compVertexMsgs", "false", true,
					"Use compressed messages when sending non local vertices to their owners");

	private static final String MS_BARRIER_IDENT_0 = "BF0";
	private static final String MS_BARRIER_IDENT_1 = "BF1";
	private static final String MS_BARRIER_IDENT_2 = "BF2";

	private LoggerService m_loggerService;
	private ChunkService m_chunkService;
	private NameserviceService m_nameserviceService;
	private NetworkService m_networkService;
	private BootService m_bootService;
	private SynchronizationService m_synchronizationService;

	private short m_nodeId = NodeID.INVALID_ID;
	private GraphPartitionIndex m_graphPartitionIndex;

	private String m_bfsRootNameserviceEntry = new String(GraphLoadBFSRootListTaskPayload.MS_BFS_ROOTS + "0");
	private int m_vertexBatchSize = 100;
	private int m_vertexMessageBatchSize = 100;
	private int m_numberOfThreadsPerNode = 4;
	private boolean m_markVertices = true;
	private boolean m_compressedVertexMessages;

	private int m_barrierId0 = BarrierID.INVALID_ID;
	private int m_barrierId1 = BarrierID.INVALID_ID;
	private int m_barrierId2 = BarrierID.INVALID_ID;

	public GraphAlgorithmBFSTaskPayload() {
		super(GraphTaskPayloads.TYPE, GraphTaskPayloads.SUBTYPE_GRAPH_ALGO_BFS);
	}

	@Override
	public int execute(final DXRAMServiceAccessor p_dxram) {
		m_loggerService = p_dxram.getService(LoggerService.class);
		m_chunkService = p_dxram.getService(ChunkService.class);
		m_nameserviceService = p_dxram.getService(NameserviceService.class);
		m_networkService = p_dxram.getService(NetworkService.class);
		m_bootService = p_dxram.getService(BootService.class);
		m_synchronizationService = p_dxram.getService(SynchronizationService.class);

		m_networkService.registerMessageType(BFSMessages.TYPE, BFSMessages.SUBTYPE_VERTICES_FOR_NEXT_FRONTIER_REQUEST,
				VerticesForNextFrontierRequest.class);
		m_networkService.registerMessageType(BFSMessages.TYPE,
				BFSMessages.SUBTYPE_VERTICES_FOR_NEXT_FRONTIER_COMPRESSED_REQUEST,
				VerticesForNextFrontierCompressedRequest.class);
		m_networkService.registerMessageType(BFSMessages.TYPE,
				BFSMessages.SUBTYPE_VERTICES_FOR_NEXT_FRONTIER_RESPONSE,
				VerticesForNextFrontierResponse.class);

		// cache node id
		m_nodeId = m_bootService.getNodeID();

		// get partition index of the graph
		long graphPartitionIndexChunkId = m_nameserviceService
				.getChunkID(GraphLoadPartitionIndexTaskPayload.MS_PART_INDEX_IDENT + getComputeGroupId(), 5000);
		if (graphPartitionIndexChunkId == ChunkID.INVALID_ID) {
			m_loggerService.error(getClass(),
					"Cannot find graph partition index for compute group " + getComputeGroupId());
			return -1;
		}

		m_graphPartitionIndex = new GraphPartitionIndex();
		m_graphPartitionIndex.setID(graphPartitionIndexChunkId);
		if (m_chunkService.get(m_graphPartitionIndex) != 1) {
			m_loggerService.error(getClass(), "Getting graph partition index from chunk "
					+ ChunkID.toHexString(graphPartitionIndexChunkId) + " failed.");
			return -2;
		}

		// get entry vertices for bfs
		long chunkIdRootVertices = m_nameserviceService.getChunkID(m_bfsRootNameserviceEntry, 5000);
		if (chunkIdRootVertices == ChunkID.INVALID_ID) {
			m_loggerService.error(getClass(),
					"Getting BFS entry vertex " + m_bfsRootNameserviceEntry + " failed, not valid.");
			return -3;
		}

		GraphRootList rootList = new GraphRootList(chunkIdRootVertices);
		if (m_chunkService.get(rootList) != 1) {
			m_loggerService.error(getClass(),
					"Getting root list " + ChunkID.toHexString(chunkIdRootVertices) + " of vertices for bfs failed.");
			return -4;
		}

		// create barriers for bfs and register
		// or get the newly created barriers
		if (getSlaveId() == 0) {
			m_barrierId0 = m_synchronizationService.barrierAllocate(getSlaveNodeIds().length);
			m_barrierId1 = m_synchronizationService.barrierAllocate(getSlaveNodeIds().length);
			m_barrierId2 = m_synchronizationService.barrierAllocate(getSlaveNodeIds().length);

			m_nameserviceService.register(ChunkID.getChunkID(m_nodeId, m_barrierId0),
					MS_BARRIER_IDENT_0 + getComputeGroupId());
			m_nameserviceService.register(ChunkID.getChunkID(m_nodeId, m_barrierId1),
					MS_BARRIER_IDENT_1 + getComputeGroupId());
			m_nameserviceService.register(ChunkID.getChunkID(m_nodeId, m_barrierId2),
					MS_BARRIER_IDENT_2 + getComputeGroupId());
		} else {
			m_barrierId0 = (int) (m_nameserviceService.getChunkID(MS_BARRIER_IDENT_0 + getComputeGroupId(), -1));
			m_barrierId1 = (int) (m_nameserviceService.getChunkID(MS_BARRIER_IDENT_1 + getComputeGroupId(), -1));
			m_barrierId2 = (int) (m_nameserviceService.getChunkID(MS_BARRIER_IDENT_2 + getComputeGroupId(), -1));
		}

		if (m_markVertices) {
			m_loggerService.info(getClass(), "Marking vertices mode (graph data will be altered)");
		} else {
			m_loggerService.info(getClass(), "Not marking vertices mode (graph data read only)");
		}

		if (m_compressedVertexMessages) {
			m_loggerService.info(getClass(), "Using compressed vertex messages for forwarding");
		} else {
			m_loggerService.info(getClass(), "Using non compressed vertex messages for forwarding");
		}

		int bfsIteration = 0;
		for (long root : rootList.getRoots()) {
			m_loggerService.info(getClass(), "Executing BFS with root " + ChunkID.toHexString(root));

			// run the bfs root on the node it is local to
			if (ChunkID.getCreatorID(root) == m_nodeId) {
				// run as bfs master
				m_loggerService.info(getClass(), "I (" + getSlaveId() + ") am running as master");
				BFSMaster master = new BFSMaster(root);
				master.init(m_graphPartitionIndex.getPartitionIndex(getSlaveId()).getVertexCount(), m_markVertices,
						m_compressedVertexMessages);
				master.execute(root);
				master.shutdown();

				BFSResult result = master.getBFSResult();

				m_loggerService.info(getClass(), "Result of BFS iteration: " + result);

				if (m_chunkService.create(result) != 1) {
					m_loggerService.error(getClass(), "Creating chunk for bfs result failed.");
					return -5;
				}

				if (m_chunkService.put(result) != 1) {
					m_loggerService.error(getClass(), "Putting data of bfs result failed.");
					return -6;
				}

				String resultName = MS_BFS_RESULT_NAMESRV_IDENT + bfsIteration;
				m_nameserviceService.register(result, resultName);
				m_loggerService.info(getClass(), "BFS results stored and registered: " + resultName);
			} else {
				// run as bfs slave, mater is the owner of the root node
				m_loggerService.info(getClass(), "I (" + getSlaveId() + ") am running as slave");
				BFSSlave slave = new BFSSlave(ChunkID.getCreatorID(root));
				slave.init(m_graphPartitionIndex.getPartitionIndex(getSlaveId()).getVertexCount(), m_markVertices,
						m_compressedVertexMessages);
				slave.execute(ChunkID.INVALID_ID);
				slave.shutdown();
			}

			bfsIteration++;
			// limit this to a single iteration on marking vertices
			// because we altered the vertex data, further iterations won't work (vertices already marked as visited)
			if (m_markVertices) {
				break;
			}
		}

		// free barriers
		if (getSlaveId() == 0) {
			m_synchronizationService.barrierFree(m_barrierId0);
			m_synchronizationService.barrierFree(m_barrierId1);
			m_synchronizationService.barrierFree(m_barrierId2);
		} else {
			m_barrierId0 = BarrierID.INVALID_ID;
			m_barrierId1 = BarrierID.INVALID_ID;
			m_barrierId2 = BarrierID.INVALID_ID;
		}

		return 0;
	}

	@Override
	public void terminalCommandRegisterArguments(final ArgumentList p_argumentList) {
		p_argumentList.setArgument(MS_ARG_BFS_ROOT);
		p_argumentList.setArgument(MS_ARG_VERTEX_BATCH_SIZE);
		p_argumentList.setArgument(MS_ARG_VERTEX_MSG_BATCH_SIZE);
		p_argumentList.setArgument(MS_ARG_NUM_THREADS);
		p_argumentList.setArgument(MS_ARG_MARK_VERTICES);
		p_argumentList.setArgument(MS_ARG_COMP_VERTEX_MSGS);
	}

	@Override
	public void terminalCommandCallbackForArguments(final ArgumentList p_argumentList) {
		m_bfsRootNameserviceEntry = p_argumentList.getArgumentValue(MS_ARG_BFS_ROOT, String.class);
		m_vertexBatchSize = p_argumentList.getArgumentValue(MS_ARG_VERTEX_BATCH_SIZE, Integer.class);
		m_vertexMessageBatchSize = p_argumentList.getArgumentValue(MS_ARG_VERTEX_MSG_BATCH_SIZE, Integer.class);
		m_numberOfThreadsPerNode = p_argumentList.getArgumentValue(MS_ARG_NUM_THREADS, Integer.class);
		m_markVertices = p_argumentList.getArgumentValue(MS_ARG_MARK_VERTICES, Boolean.class);
		m_compressedVertexMessages = p_argumentList.getArgumentValue(MS_ARG_COMP_VERTEX_MSGS, Boolean.class);
	}

	@Override
	public int exportObject(final Exporter p_exporter, final int p_size) {
		int size = super.exportObject(p_exporter, p_size);

		p_exporter.writeInt(m_bfsRootNameserviceEntry.length());
		p_exporter.writeBytes(m_bfsRootNameserviceEntry.getBytes(StandardCharsets.US_ASCII));
		p_exporter.writeInt(m_vertexBatchSize);
		p_exporter.writeInt(m_vertexMessageBatchSize);
		p_exporter.writeInt(m_numberOfThreadsPerNode);
		p_exporter.writeByte((byte) (m_markVertices ? 1 : 0));
		p_exporter.writeByte((byte) (m_compressedVertexMessages ? 1 : 0));

		return size + Integer.BYTES + m_bfsRootNameserviceEntry.length() + Integer.BYTES * 3 + 2 * Byte.BYTES;
	}

	@Override
	public int importObject(final Importer p_importer, final int p_size) {
		int size = super.importObject(p_importer, p_size);

		int strLength = p_importer.readInt();
		byte[] tmp = new byte[strLength];
		p_importer.readBytes(tmp);
		m_bfsRootNameserviceEntry = new String(tmp, StandardCharsets.US_ASCII);
		m_vertexBatchSize = p_importer.readInt();
		m_vertexMessageBatchSize = p_importer.readInt();
		m_numberOfThreadsPerNode = p_importer.readInt();
		m_markVertices = p_importer.readByte() > 0;
		m_compressedVertexMessages = p_importer.readByte() > 0;

		return size + Integer.BYTES + m_bfsRootNameserviceEntry.length() + Integer.BYTES * 3 + 2 * Byte.BYTES;
	}

	@Override
	public int sizeofObject() {
		return super.sizeofObject() + Integer.BYTES + m_bfsRootNameserviceEntry.length() + Integer.BYTES * 3
				+ 2 * Byte.BYTES;
	}

	private abstract class AbstractBFSMS implements MessageReceiver {
		private FrontierList m_curFrontier;
		private FrontierList m_nextFrontier;
		private FrontierList m_visitedFrontier;

		private BFSThread[] m_threads;

		public AbstractBFSMS() {

		}

		public void init(final long p_totalVertexCount, final boolean p_verticesMarkVisited,
				final boolean p_compressedVertexMessages) {
			m_curFrontier = new ConcurrentBitVector(p_totalVertexCount);
			m_nextFrontier = new ConcurrentBitVector(p_totalVertexCount);
			if (!p_verticesMarkVisited) {
				m_visitedFrontier = new ConcurrentBitVector(p_totalVertexCount);
			}

			m_networkService.registerReceiver(VerticesForNextFrontierRequest.class, this);
			m_networkService.registerReceiver(VerticesForNextFrontierCompressedRequest.class, this);

			m_loggerService.info(getClass(), "Running BFS with " + m_numberOfThreadsPerNode + " threads on "
					+ p_totalVertexCount + " local vertices");

			m_threads = new BFSThread[m_numberOfThreadsPerNode];
			for (int i = 0; i < m_threads.length; i++) {
				m_threads[i] =
						new BFSThread(i, m_vertexBatchSize, m_vertexMessageBatchSize, m_curFrontier, m_nextFrontier,
								m_visitedFrontier, p_compressedVertexMessages);
				m_threads[i].start();
			}
		}

		public void execute(final long p_entryVertex) {
			if (p_entryVertex != ChunkID.INVALID_ID) {
				m_loggerService.info(getClass(),
						"I am starting BFS with entry vertex " + ChunkID.toHexString(p_entryVertex));
				m_curFrontier.pushBack(ChunkID.getLocalID(p_entryVertex));
			}

			int curBfsLevel = 0;
			long totalVisistedVertices = 0;
			while (true) {
				m_loggerService.debug(getClass(),
						"Processing next BFS level " + curBfsLevel + ", total vertices visited so far "
								+ totalVisistedVertices + "...");

				// kick off threads with current frontier
				for (int t = 0; t < m_threads.length; t++) {
					m_threads[t].runIteration();
				}

				// wait actively until threads are done with their current iteration
				{
					int t = 0;
					while (t < m_threads.length) {
						if (!m_threads[t].hasIterationFinished()) {
							Thread.yield();
							continue;
						}
						t++;
					}
				}

				// all threads finished their iteration, sum up visited vertices
				long visitedVertsIteration = 0;
				for (int t = 0; t < m_threads.length; t++) {
					visitedVertsIteration += m_threads[t].getVisitedCountLastRun();
				}

				m_loggerService.info(getClass(),
						"BFS Level " + curBfsLevel + " finished with " + visitedVertsIteration + " visited vertices");

				totalVisistedVertices += visitedVertsIteration;

				// signal we are done with our iteration
				barrierSignalIterationComplete(visitedVertsIteration);

				// all nodes are finished, frontier swap
				FrontierList tmp = m_curFrontier;
				m_curFrontier = m_nextFrontier;
				m_nextFrontier = tmp;
				m_nextFrontier.reset();

				m_loggerService.debug(getClass(), "Frontier swap, new cur frontier size: " + m_curFrontier.size());

				// also swap the references of all threads!
				for (int t = 0; t < m_threads.length; t++) {
					m_threads[t].triggerFrontierSwap();
				}

				// signal frontier swap and ready for next iteration
				barrierSignalFrontierSwap(m_curFrontier.size());

				if (barrierSignalTerminate()) {
					m_loggerService.info(getClass(), "BFS terminated signal, last iteration level " + curBfsLevel
							+ ", total visited " + totalVisistedVertices);
					break;
				}

				m_loggerService.debug(getClass(), "Continue next BFS level");

				// go for next run
				curBfsLevel++;
			}
		}

		public void shutdown() {
			for (BFSThread thread : m_threads) {
				thread.exitThread();
			}

			m_loggerService.debug(getClass(), "Joining BFS threads...");
			for (BFSThread thread : m_threads) {
				try {
					thread.join();
				} catch (final InterruptedException ignored) {
				}
			}

			m_loggerService.debug(getClass(), "BFS shutdown");
		}

		@Override
		public void onIncomingMessage(final AbstractMessage p_message) {
			if (p_message != null) {
				if (p_message.getType() == BFSMessages.TYPE) {
					switch (p_message.getSubtype()) {
						case BFSMessages.SUBTYPE_VERTICES_FOR_NEXT_FRONTIER_REQUEST:
						case BFSMessages.SUBTYPE_VERTICES_FOR_NEXT_FRONTIER_COMPRESSED_REQUEST:
							onIncomingVerticesForNextFrontierMessage(
									(AbstractVerticesForNextFrontierRequest) p_message);
							break;
						default:
							break;
					}
				}
			}
		}

		private void onIncomingVerticesForNextFrontierMessage(
				final AbstractVerticesForNextFrontierRequest p_message) {
			long vertexId = p_message.getVertex();
			while (vertexId != -1) {
				m_nextFrontier.pushBack(vertexId);
				vertexId = p_message.getVertex();
			}

			VerticesForNextFrontierResponse response = new VerticesForNextFrontierResponse(p_message);
			m_networkService.sendMessage(response);
		}

		protected abstract void barrierSignalIterationComplete(final long p_verticesVisited);

		protected abstract void barrierSignalFrontierSwap(final long p_nextFrontierSize);

		protected abstract boolean barrierSignalTerminate();
	}

	private class BFSMaster extends AbstractBFSMS {
		private BFSResult m_bfsResult;
		private boolean m_signalTermination;

		BFSMaster(final long p_bfsEntryNode) {
			m_bfsResult = new BFSResult();
			m_bfsResult.setRootVertexID(p_bfsEntryNode);
		}

		BFSResult getBFSResult() {
			return m_bfsResult;
		}

		@Override
		protected void barrierSignalIterationComplete(final long p_verticesVisited) {

			Pair<short[], long[]> result = m_synchronizationService.barrierSignOn(m_barrierId0, -1);
			if (result == null) {
				m_loggerService.error(getClass(),
						"Iteration complete, sign on to barrier " + BarrierID.toHexString(m_barrierId0) + " failed.");
			}

			long iterationVertsVisited = p_verticesVisited;
			// results of other slaves of last iteration
			for (long data : result.second()) {
				if (data >= 0) {
					iterationVertsVisited += data;
				}
			}

			m_bfsResult.setTotalVisitedVertices(m_bfsResult.getTotalVisitedVertices() + iterationVertsVisited);
			m_bfsResult.setTotalBFSDepth(m_bfsResult.getTotalBFSDepth() + 1);
		}

		@Override
		protected void barrierSignalFrontierSwap(final long p_nextFrontierSize) {
			Pair<short[], long[]> result = m_synchronizationService.barrierSignOn(m_barrierId1, -1);
			if (result == null) {
				m_loggerService.error(getClass(),
						"Frontier swap, sign on to barrier " + BarrierID.toHexString(m_barrierId1) + " failed.");
			}

			// check if all frontier sizes are 0 -> terminate bfs
			boolean allFrontiersEmpty = true;
			for (long data : result.second()) {
				if (data > 0) {
					allFrontiersEmpty = false;
					break;
				}
			}

			// frontiers of all other slaves and this one have to be empty
			if (allFrontiersEmpty && p_nextFrontierSize == 0) {
				m_signalTermination = true;
			}
		}

		@Override
		protected boolean barrierSignalTerminate() {
			if (m_synchronizationService.barrierSignOn(m_barrierId2, m_signalTermination ? 1 : 0) == null) {
				m_loggerService.error(getClass(),
						"Signal terminate, sign on to barrier " + BarrierID.toHexString(m_barrierId2) + " failed.");
			}

			return m_signalTermination;
		}
	}

	private class BFSSlave extends AbstractBFSMS {

		BFSSlave(final short p_bfsMasterNodeID) {

		}

		@Override
		protected void barrierSignalIterationComplete(final long p_verticesVisited) {
			if (m_synchronizationService.barrierSignOn(m_barrierId0, p_verticesVisited) == null) {
				m_loggerService.error(getClass(),
						"Iteration complete, sign on to barrier " + BarrierID.toHexString(m_barrierId0) + " failed.");
			}
		}

		@Override
		protected void barrierSignalFrontierSwap(final long p_nextFrontierSize) {
			if (m_synchronizationService.barrierSignOn(m_barrierId1, p_nextFrontierSize) == null) {
				m_loggerService.error(getClass(),
						"Frontier swap, sign on to barrier " + BarrierID.toHexString(m_barrierId1) + " failed.");
			}
		}

		@Override
		protected boolean barrierSignalTerminate() {
			Pair<short[], long[]> result = m_synchronizationService.barrierSignOn(m_barrierId2, -1);
			if (result == null) {
				m_loggerService.error(getClass(),
						"Signal terminate, sign on to barrier " + BarrierID.toHexString(m_barrierId2) + " failed.");
			}

			// look for signal terminate flag (0 or 1)
			for (int i = 0; i < result.first().length; i++) {
				if (result.second()[i] == 1) {
					return true;
				}
			}

			return false;
		}
	}

	private class BFSThread extends Thread {

		private int m_id = -1;
		private int m_vertexMessageBatchSize;
		private FrontierList m_curFrontier;
		private FrontierList m_nextFrontier;
		private FrontierList m_visitedFrontier;
		private boolean m_compressedVertexMessages;

		private short m_nodeId;
		private Vertex[] m_vertexBatch;
		private int m_currentIterationLevel;
		private HashMap<Short, AbstractVerticesForNextFrontierRequest> m_remoteMessages =
				new HashMap<>();

		private volatile boolean m_runIteration;
		private volatile int m_visitedCounterRun;
		private volatile boolean m_exitThread;

		BFSThread(final int p_id, final int p_vertexBatchSize, final int p_vertexMessageBatchSize,
				final FrontierList p_curFrontierShared, final FrontierList p_nextFrontierShared,
				final FrontierList p_visitedFrontierShared, final boolean p_compressedVertexMessages) {
			super("BFSThread-" + p_id);

			m_id = p_id;
			m_vertexMessageBatchSize = p_vertexMessageBatchSize;
			m_curFrontier = p_curFrontierShared;
			m_nextFrontier = p_nextFrontierShared;
			m_visitedFrontier = p_visitedFrontierShared;
			m_compressedVertexMessages = p_compressedVertexMessages;

			m_nodeId = m_bootService.getNodeID();
			m_vertexBatch = new Vertex[p_vertexBatchSize];
			for (int i = 0; i < m_vertexBatch.length; i++) {
				m_vertexBatch[i] = new Vertex(ChunkID.INVALID_ID);
				// performance hack: if writing back, we only write back what we changed
				m_vertexBatch[i].setWriteUserDataOnly(true);
			}
		}

		void setCurrentBFSIterationLevel(final int p_iterationLevel) {
			m_currentIterationLevel = p_iterationLevel;
		}

		void runIteration() {
			m_visitedCounterRun = 0;
			m_runIteration = true;
		}

		boolean hasIterationFinished() {
			return !m_runIteration;
		}

		int getVisitedCountLastRun() {
			return m_visitedCounterRun;
		}

		void triggerFrontierSwap() {
			FrontierList tmp = m_curFrontier;
			m_curFrontier = m_nextFrontier;
			m_nextFrontier = tmp;
		}

		void exitThread() {
			m_exitThread = true;
		}

		@Override
		public void run() {
			boolean enterIdle = false;
			while (true) {
				do {
					if (m_exitThread) {
						return;
					}

					if (enterIdle) {
						enterIdle = false;
						m_runIteration = false;
					}

					if (!m_runIteration) {
						Thread.yield();
					}
				} while (!m_runIteration);

				int validVertsInBatch = 0;
				for (Vertex vertexBatch : m_vertexBatch) {
					long tmp = m_curFrontier.popFront();
					if (tmp != -1) {
						vertexBatch.setID(ChunkID.getChunkID(m_nodeId, tmp));
						validVertsInBatch++;
					} else {
						if (validVertsInBatch == 0) {
							enterIdle = true;
							break;
						}
						vertexBatch.setID(ChunkID.INVALID_ID);
					}
				}

				if (validVertsInBatch == 0) {
					// make sure to send out remaining messages which have not reached the
					// batch size, yet (because they will never reach it in this round)
					for (Entry<Short, AbstractVerticesForNextFrontierRequest> entry : m_remoteMessages.entrySet()) {
						AbstractVerticesForNextFrontierRequest msg = entry.getValue();
						if (msg.getBatchSize() > 0) {
							if (m_networkService.sendSync(msg) != NetworkErrorCodes.SUCCESS) {
								m_loggerService.error(getClass(), "Sending vertex message to node "
										+ NodeID.toHexString(msg.getDestination()) + " failed");
								return;
							}

							// don't reuse requests, does not work with previous responses counting as fulfilled
							if (m_compressedVertexMessages) {
								msg = new VerticesForNextFrontierCompressedRequest(msg.getDestination(),
										m_vertexMessageBatchSize);
							} else {
								msg = new VerticesForNextFrontierRequest(msg.getDestination(),
										m_vertexMessageBatchSize);
							}
							m_remoteMessages.put(entry.getKey(), msg);
						}
					}

					// we are done, go to start
					continue;
				}

				int gett = m_chunkService.get(m_vertexBatch, 0, validVertsInBatch);
				if (gett != validVertsInBatch) {
					m_loggerService.error(getClass(),
							"Getting vertices in BFS Thread " + m_id + " failed: " + gett + " != " + validVertsInBatch);
					return;
				}

				int writeBackCount = 0;
				for (int i = 0; i < validVertsInBatch; i++) {
					// check first if visited
					Vertex vertex = m_vertexBatch[i];

					// skip vertices that were already marked invalid before
					if (vertex.getID() == ChunkID.INVALID_ID) {
						continue;
					}

					// two "modes": mark the actual vertex visited with the current bfs level
					// or just remember that we have visited it and don't alter vertex data
					boolean isVisited;
					if (m_visitedFrontier == null) {
						if (vertex.getUserData() == -1) {
							// set depth level
							vertex.setUserData(m_currentIterationLevel);
							isVisited = false;
						} else {
							// already visited, don't have to put back to storage
							vertex.setID(ChunkID.INVALID_ID);
							isVisited = true;
						}
					} else {
						long id = ChunkID.getLocalID(vertex.getID());
						if (!m_visitedFrontier.contains(id)) {
							m_visitedFrontier.pushBack(id);
							isVisited = false;
						} else {
							isVisited = true;
						}
					}

					if (!isVisited) {
						writeBackCount++;
						// set depth level
						m_visitedCounterRun++;
						long[] neighbours = vertex.getNeighbours();

						for (long neighbour : neighbours) {
							// sort by remote and local vertices
							short creatorId = ChunkID.getCreatorID(neighbour);
							if (creatorId != m_nodeId) {
								// go remote, fill message buffers until they are full -> send
								AbstractVerticesForNextFrontierRequest msg = m_remoteMessages.get(creatorId);
								if (msg == null) {
									if (m_compressedVertexMessages) {
										msg = new VerticesForNextFrontierCompressedRequest(creatorId,
												m_vertexMessageBatchSize);
									} else {
										msg = new VerticesForNextFrontierRequest(creatorId,
												m_vertexMessageBatchSize);
									}

									m_remoteMessages.put(creatorId, msg);
								}

								// add vertex to message batch
								if (!msg.addVertex(neighbour)) {
									// neighbor does not fit anymore, full
									if (m_networkService.sendSync(msg) != NetworkErrorCodes.SUCCESS) {
										m_loggerService.error(getClass(), "Sending vertex message to node "
												+ NodeID.toHexString(creatorId) + " failed");
										return;
									}

									// don't reuse requests, does not work with previous responses counting as fulfilled
									if (m_compressedVertexMessages) {
										msg = new VerticesForNextFrontierCompressedRequest(creatorId,
												m_vertexMessageBatchSize);
									} else {
										msg = new VerticesForNextFrontierRequest(creatorId,
												m_vertexMessageBatchSize);
									}
									m_remoteMessages.put(msg.getDestination(), msg);
									msg.addVertex(neighbour);
								}
							} else {
								m_nextFrontier.pushBack(ChunkID.getLocalID(neighbour));
							}
						}
					}
				}

				if (m_visitedFrontier == null) {
					// for marking mode, write back data
					int put =
							m_chunkService
									.put(ChunkLockOperation.NO_LOCK_OPERATION, m_vertexBatch, 0, validVertsInBatch);
					if (put != writeBackCount) {
						m_loggerService.error(getClass(),
								"Putting vertices in BFS Thread " + m_id + " failed: " + put + " != " + writeBackCount);
						return;
					}
				}
			}
		}
	}
}
