
package de.hhu.bsinfo.dxram.lookup;

import java.util.ArrayList;

import de.hhu.bsinfo.dxram.backup.BackupComponent;
import de.hhu.bsinfo.dxram.boot.AbstractBootComponent;
import de.hhu.bsinfo.dxram.engine.AbstractDXRAMService;
import de.hhu.bsinfo.dxram.engine.DXRAMComponentAccessor;
import de.hhu.bsinfo.dxram.engine.DXRAMContext;
import de.hhu.bsinfo.dxram.lookup.messages.GetLookupTreeRequest;
import de.hhu.bsinfo.dxram.lookup.messages.GetLookupTreeResponse;
import de.hhu.bsinfo.dxram.lookup.messages.GetMetadataSummaryRequest;
import de.hhu.bsinfo.dxram.lookup.messages.GetMetadataSummaryResponse;
import de.hhu.bsinfo.dxram.lookup.messages.LookupMessages;
import de.hhu.bsinfo.dxram.lookup.overlay.storage.LookupTree;
import de.hhu.bsinfo.dxram.net.NetworkComponent;
import de.hhu.bsinfo.dxram.net.messages.DXRAMMessageTypes;
import de.hhu.bsinfo.dxram.util.NodeRole;
import de.hhu.bsinfo.ethnet.AbstractMessage;
import de.hhu.bsinfo.ethnet.NetworkException;
import de.hhu.bsinfo.ethnet.NetworkHandler.MessageReceiver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Look up service providing look ups for e.g. use in TCMDs
 * @author Mike Birkhoff
 */
public class LookupService extends AbstractDXRAMService implements MessageReceiver {

	private static final Logger LOGGER = LogManager.getFormatterLogger(LookupService.class.getSimpleName());

	// dependent components
	private AbstractBootComponent m_boot;
	private BackupComponent m_backup;
	private NetworkComponent m_network;
	private LookupComponent m_lookup;

	/**
	 * Constructor
	 */
	public LookupService() {
		super("lookup");
	}

	@Override
	protected void resolveComponentDependencies(final DXRAMComponentAccessor p_componentAccessor) {
		m_boot = p_componentAccessor.getComponent(AbstractBootComponent.class);
		m_backup = p_componentAccessor.getComponent(BackupComponent.class);
		m_network = p_componentAccessor.getComponent(NetworkComponent.class);
		m_lookup = p_componentAccessor.getComponent(LookupComponent.class);
	}

	@Override
	protected boolean startService(final DXRAMContext.EngineSettings p_engineEngineSettings) {
		registerNetworkMessages();
		registerNetworkMessageListener();

		if (m_boot.getNodeRole().equals(NodeRole.PEER)) {
			m_backup.registerPeer();
		}

		return true;
	}

	@Override
	protected boolean shutdownService() {

		m_network = null;
		m_lookup = null;

		return true;
	}

	/**
	 * Sends a Response to a LookupTree Request
	 * @param p_message
	 *            the LookupTreeRequest
	 */
	private void incomingRequestLookupTreeOnServerMessage(final GetLookupTreeRequest p_message) {
		LookupTree tree = m_lookup.superPeerGetLookUpTree(p_message.getTreeNodeID());

		try {
			m_network.sendMessage(new GetLookupTreeResponse(p_message, tree));
		} catch (final NetworkException e) {
			// #if LOGGER >= ERROR
			LOGGER.error("Could not acknowledge initilization of backup range: %s", e);
			// #endif /* LOGGER >= ERROR */
		}
	}

	@Override
	public void onIncomingMessage(final AbstractMessage p_message) {

		if (p_message != null) {
			if (p_message.getType() == DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE) {
				switch (p_message.getSubtype()) {
				case LookupMessages.SUBTYPE_GET_LOOKUP_TREE_REQUEST:
					incomingRequestLookupTreeOnServerMessage((GetLookupTreeRequest) p_message);
					break;
				default:
					break;
				}
			}
		}

	}

	/**
	 * Returns all known superpeers
	 * @return array with all superpeers
	 */
	public ArrayList<Short> getAllSuperpeers() {
		return m_lookup.getAllSuperpeers();
	}

	/**
	 * Returns the responsible superpeer for given peer
	 * @param p_nid
	 *            node id to get responsible super peer from
	 * @return node ID of superpeer
	 */
	public short getResponsibleSuperpeer(final short p_nid) {
		return m_lookup.getResponsibleSuperpeer(p_nid);
	}

	/**
	 * sends a message to a superpeer to get a lookuptree from
	 * @param p_superPeerNid
	 *            superpeer where the lookuptree to get from
	 * @param p_nodeId
	 *            node id which lookuptree to get
	 * @return requested lookup Tree
	 */
	public LookupTree getLookupTreeFromSuperpeer(final short p_superPeerNid, final short p_nodeId) {

		LookupTree retTree;

		GetLookupTreeRequest lookupTreeRequest;
		GetLookupTreeResponse lookupTreeResponse;

		lookupTreeRequest = new GetLookupTreeRequest(p_superPeerNid, p_nodeId);

		try {
			m_network.sendSync(lookupTreeRequest);
		} catch (final NetworkException e) {
			/* TODO err handling */
		}

		lookupTreeResponse = lookupTreeRequest.getResponse(GetLookupTreeResponse.class);
		retTree = lookupTreeResponse.getCIDTree();

		return retTree;
	}

	/**
	 * Sends a request to given superpeer to get a metadata summary
	 * @param p_nodeID
	 *            superpeer to get summary from
	 * @return the metadata summary
	 */
	public String getMetadataSummary(final short p_nodeID) {
		String ret;
		GetMetadataSummaryRequest request;
		GetMetadataSummaryResponse response;

		request = new GetMetadataSummaryRequest(p_nodeID);

		try {
			m_network.sendSync(request);
		} catch (final NetworkException e) {
			return "Error!";
		}

		response = request.getResponse(GetMetadataSummaryResponse.class);
		ret = response.getMetadataSummary();

		return ret;
	}

	/**
	 * Register network messages we use in here.
	 */
	private void registerNetworkMessages() {
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_LOOKUP_TREE_RESPONSE,
				GetLookupTreeResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_LOOKUP_TREE_REQUEST,
				GetLookupTreeRequest.class);

		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_METADATA_SUMMARY_REQUEST,
				GetMetadataSummaryRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_METADATA_SUMMARY_RESPONSE,
				GetMetadataSummaryResponse.class);

	}

	/**
	 * Register network messages we want to listen to in here.
	 */
	private void registerNetworkMessageListener() {

		m_network.register(GetLookupTreeResponse.class, this);
		m_network.register(GetLookupTreeRequest.class, this);
	}

}
