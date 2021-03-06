package de.hhu.bsinfo.dxram.chunk;

import org.junit.Assert;
import org.junit.runner.RunWith;

import de.hhu.bsinfo.dxmem.data.ChunkByteArray;
import de.hhu.bsinfo.dxram.DXRAM;
import de.hhu.bsinfo.dxram.DXRAMJunitRunner;
import de.hhu.bsinfo.dxram.DXRAMTestConfiguration;
import de.hhu.bsinfo.dxram.TestInstance;
import de.hhu.bsinfo.dxram.util.NodeRole;

@RunWith(DXRAMJunitRunner.class)
@DXRAMTestConfiguration(
        nodes = {
                @DXRAMTestConfiguration.Node(nodeRole = NodeRole.SUPERPEER),
                @DXRAMTestConfiguration.Node(nodeRole = NodeRole.PEER),
        })
public class ChunkServiceGetLocalTest {
    @TestInstance(runOnNodeIdx = 1)
    public void getSimple(final DXRAM p_instance) {
        ChunkLocalService chunkLocalService = p_instance.getService(ChunkLocalService.class);

        ChunkByteArray chunk = new ChunkByteArray(ChunkTestConstants.CHUNK_SIZE_3);
        int ret = chunkLocalService.createLocal().create(chunk);

        Assert.assertEquals(1, ret);
        Assert.assertTrue(chunk.isIDValid());
        Assert.assertTrue(chunk.isStateOk());

        Assert.assertTrue(chunkLocalService.getLocal().get(chunk));
        Assert.assertTrue(chunk.isIDValid());
        Assert.assertTrue(chunk.isStateOk());
    }
}
