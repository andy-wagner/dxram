
package de.uniduesseldorf.dxram.core.net;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import de.uniduesseldorf.dxram.core.api.Core;
import de.uniduesseldorf.dxram.core.api.config.Configuration.ConfigurationConstants;

/**
 * Access to a singleton of an ExecutorService
 * for usage inside of the network package
 * NOTE:
 * The currently running Task has to stay in the task map.
 * Otherwise the Queue will never be used.
 * @see java.util.concurrent.ExecutorService
 * @author Marc Ewert 14.08.14
 */
public final class TaskExecutor {

	// Constants
	private static final Logger LOGGER = Logger.getLogger(TaskExecutor.class);

	private static TaskExecutor m_defaultExecutor;

	private final ExecutorService m_executor;
	private final HashMap<Short, TaskQueue> m_taskMap;
	private final String m_name;

	static {
		m_defaultExecutor = new TaskExecutor("Default", Core.getConfiguration().getIntValue(ConfigurationConstants.NETWORK_TASK_HANDLER_THREAD_COUNT));
	}

	// Constructors
	/**
	 * Creates a new TaskExecutor
	 * @param p_name
	 *            Identifier for debug prints
	 * @param p_threads
	 *            Number of Threads to create
	 */
	public TaskExecutor(final String p_name, final int p_threads) {
		m_taskMap = new HashMap<>();
		m_name = p_name;

		LOGGER.info(m_name + ": Initialising " + p_threads + " threads");
		m_executor = Executors.newFixedThreadPool(p_threads, new ExecutorThreadFactory());
	}

	// Methods
	/**
	 * Add a task to the queue to be executed
	 * @param p_runnable
	 *            Task to be executed
	 */
	public void execute(final Runnable p_runnable) {
		try {
			m_executor.execute(p_runnable);
		} catch (final RejectedExecutionException e) {
			LOGGER.error("ERROR::" + m_name + ":" + e.getMessage());
		}
	}

	/**
	 * Queue up a task with potential follow up tasks
	 * @param p_id
	 *            Task identifier to identify follow ups
	 * @param p_runnable
	 *            Task to be executed
	 */
	public synchronized void execute(final short p_id, final Runnable p_runnable) {
		TaskQueue taskQueue;

		synchronized (m_taskMap) {
			if (!m_taskMap.containsKey(p_id)) {
				taskQueue = new TaskQueue();
				m_taskMap.put(p_id, taskQueue);
			} else {
				taskQueue = m_taskMap.get(p_id);
			}

			// store task
			taskQueue.add(p_runnable);
		}
	}

	/**
	 * Purges the task queue for a specific identifier.
	 * All remaining tasks will be still executed
	 * @param p_id
	 *            Task identifier which will be purged
	 */
	public void purgeQueue(final short p_id) {
		synchronized (m_taskMap) {
			if (m_taskMap.containsKey(p_id)) {
				m_taskMap.remove(p_id);
			}
		}
	}

	/**
	 * Initiate a graceful shutdown of the thread pool
	 */
	public void shutdown() {
		LOGGER.info("Shutdown TaskExecutor " + m_name);
		m_executor.shutdown();
	}

	/**
	 * Returns a pointer to the default TaskExecutor
	 * @return Default TaskExecutor
	 */
	public static TaskExecutor getDefaultExecutor() {
		return m_defaultExecutor;
	}

	// Classes
	/**
	 * Wrapper class for Runnables that calls the
	 * finishedTask method after it has finished
	 * @author Marc Ewert 14.08.14
	 */
	private class TaskQueue implements Runnable {

		private final ArrayDeque<Runnable> m_queue;

		/**
		 * Creates a new TaskQueue
		 */
		public TaskQueue() {
			m_queue = new ArrayDeque<>();
		}

		/**
		 * Enqueue a new task to be executed
		 * @param p_runnable
		 *            Task to be executed
		 */
		public void add(final Runnable p_runnable) {
			synchronized (m_queue) {

				// enqueue itself when this is the first task
				if (m_queue.size() == 0) {
					execute(this);
				}

				m_queue.offer(p_runnable);
			}
		}

		@Override
		public void run() {
			Runnable runnable;

			synchronized (m_queue) {
				runnable = m_queue.peek();
			}

			try {
				runnable.run();
			} catch (final Exception e) {
				LOGGER.error("ERROR::" + m_name + ":exception during " + runnable, e);
			} finally {
				synchronized (m_queue) {
					// remove executed task
					m_queue.remove();

					// enqueue itself again when there are tasks left
					if (m_queue.size() > 0) {
						execute(this);
					}
				}
			}
		}
	}

	/**
	 * Creates new Threads for the TaskExecutor
	 * @author Marc Ewert 01.10.14
	 */
	private class ExecutorThreadFactory implements ThreadFactory {

		private final AtomicInteger m_threadNumber;

		/**
		 * Creates a new ExecutorThreadFactory
		 */
		public ExecutorThreadFactory() {
			m_threadNumber = new AtomicInteger(1);
		}

		@Override
		public Thread newThread(final Runnable p_runnable) {
			String name;
			Thread thread;

			name = m_name + "-thread-" + m_threadNumber.getAndIncrement();
			thread = new Thread(p_runnable, name);
			thread.setDaemon(true);
			thread.setPriority(Thread.NORM_PRIORITY);

			return thread;
		}
	}
}
