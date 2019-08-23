/*
 * Copyright (C) 2018 Heinrich-Heine-Universitaet Duesseldorf, Institute of Computer Science,
 * Department Operating Systems
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package de.hhu.bsinfo.dxram.chunk;

import de.hhu.bsinfo.dxram.backup.BackupComponent;
import de.hhu.bsinfo.dxram.boot.BootComponent;
import de.hhu.bsinfo.dxram.chunk.operation.*;
import de.hhu.bsinfo.dxutils.dependency.Dependency;
import de.hhu.bsinfo.dxram.engine.Module;
import de.hhu.bsinfo.dxram.engine.Service;
import de.hhu.bsinfo.dxram.engine.ComponentProvider;
import de.hhu.bsinfo.dxram.engine.DXRAMConfig;
import de.hhu.bsinfo.dxram.lookup.LookupComponent;
import de.hhu.bsinfo.dxram.nameservice.NameserviceComponent;
import de.hhu.bsinfo.dxram.net.NetworkComponent;

/**
 * Main service for using the key-value store with chunks
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 03.02.2016
 */
@Module.Attributes(supportsSuperpeer = false, supportsPeer = true)
public class ChunkService extends Service<ChunkServiceConfig> {

    @Dependency
    private BootComponent m_boot;

    @Dependency
    private BackupComponent m_backup;

    @Dependency
    private ChunkComponent m_chunk;

    @Dependency
    private NetworkComponent m_network;

    @Dependency
    private LookupComponent m_lookup;

    @Dependency
    private NameserviceComponent m_nameservice;

    // chunk operations of service
    private Status m_status;
    private CIDStatus m_cidStatus;
    private Create m_create;
    private CreateLocal m_createLocal;
    private Get m_get;
    private Put m_put;
    private Remove m_remove;
    private Resize m_resize;
    private Lock m_lock;

    /**
     * Get the status operation
     *
     * @return Operation
     */
    public Status status() {
        return m_status;
    }

    /**
     * Get the cidStatus operation
     *
     * @return Operation
     */
    public CIDStatus cidStatus() {
        return m_cidStatus;
    }

    /**
     * Get the create operation
     *
     * @return Operation
     */
    public Create create() {
        return m_create;
    }

    /**
     * Get the CreateLocal operation.
     *
     * @return Operation
     */
    public CreateLocal createLocal() {
        return m_createLocal;
    }

    /**
     * Get the get operation
     *
     * @return Operation
     */
    public Get get() {
        return m_get;
    }

    /**
     * Get the put operation
     *
     * @return Operation
     */
    public Put put() {
        return m_put;
    }

    /**
     * Get the remove operation
     *
     * @return Operation
     */
    public Remove remove() {
        return m_remove;
    }

    /**
     * Get the resize operation
     *
     * @return Operation
     */
    public Resize resize() {
        return m_resize;
    }

    /**
     * Get the lock operation
     *
     * @return Operation
     */
    public Lock lock() {
        return m_lock;
    }

    @Override
    protected boolean startService(final DXRAMConfig p_config) {
        ChunkServiceConfig chunkConfig = p_config.getServiceConfig(ChunkService.class);

        m_status = new Status(getClass(), m_boot, m_backup, m_chunk, m_network, m_lookup, m_nameservice);
        m_cidStatus = new CIDStatus(getClass(), m_boot, m_backup, m_chunk, m_network, m_lookup, m_nameservice);
        m_create = new Create(getClass(), m_boot, m_backup, m_chunk, m_network, m_lookup, m_nameservice);
        m_createLocal = new CreateLocal(getClass(), m_boot, m_backup, m_chunk, m_network, m_lookup, m_nameservice);
        m_get = new Get(getClass(), m_boot, m_backup, m_chunk, m_network, m_lookup, m_nameservice);
        m_put = new Put(getClass(), m_boot, m_backup, m_chunk, m_network, m_lookup, m_nameservice);
        m_remove = new Remove(getClass(), m_boot, m_backup, m_chunk, m_network, m_lookup, m_nameservice,
                chunkConfig.getRemoverQueueSize());
        m_resize = new Resize(getClass(), m_boot, m_backup, m_chunk, m_network, m_lookup, m_nameservice);
        m_lock = new Lock(getClass(), m_boot, m_backup, m_chunk, m_network, m_lookup, m_nameservice);

        return true;
    }

    @Override
    protected boolean shutdownService() {
        return true;
    }
}
