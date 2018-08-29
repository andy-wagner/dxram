package de.hhu.bsinfo.dxram.job;

import com.google.gson.annotations.Expose;

import de.hhu.bsinfo.dxram.engine.DXRAMComponentConfig;
import de.hhu.bsinfo.dxram.engine.DXRAMContext;

/**
 * Config for the JobWorkStealingComponent
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 24.05.2017
 */
@DXRAMComponentConfig.Settings(component = JobWorkStealingComponent.class, supportsSuperpeer = false,
        supportsPeer = true)
public class JobWorkStealingComponentConfig extends DXRAMComponentConfig {
    private static final int NUM_WORKERS_MAX = 64;

    @Expose
    private int m_numWorkers = 1;

    /**
     * Number of worker threads to dispatch jobs to
     */
    public int getNumWorkers() {
        return m_numWorkers;
    }

    @Override
    protected boolean verify(final DXRAMContext.Config p_config) {
        if (m_numWorkers < 1) {
            LOGGER.error("Invalid value (%d) for m_numWorkers", m_numWorkers);

            return false;
        }

        if (m_numWorkers > NUM_WORKERS_MAX) {
            LOGGER.error("Max limit m_numWorkers: %d", NUM_WORKERS_MAX);

            return false;
        }

        return true;
    }
}
