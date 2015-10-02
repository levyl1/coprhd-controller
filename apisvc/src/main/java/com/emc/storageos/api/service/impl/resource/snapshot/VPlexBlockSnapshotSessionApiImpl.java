/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.VPlexUtil;

/**
 * 
 */
public class VPlexBlockSnapshotSessionApiImpl extends DefaultBlockSnapshotSessionApiImpl {

    /**
     * Private default constructor should not be called outside class.
     */
    @SuppressWarnings("unused")
    private VPlexBlockSnapshotSessionApiImpl() {
        super();
    }

    /**
     * Constructor.
     * 
     * @param dbClient A reference to a data base client.
     * @param coordinator A reference to the coordinator client.
     * @param permissionsHelper A reference to a permission helper.
     * @param securityContext A reference to the security context.
     * @param blockSnapshotSessionMgr A reference to the snapshot session manager.
     */
    public VPlexBlockSnapshotSessionApiImpl(DbClient dbClient, CoordinatorClient coordinator, PermissionsHelper permissionsHelper,
            SecurityContext securityContext, BlockSnapshotSessionManager blockSnapshotSessionMgr) {
        super(dbClient, coordinator, permissionsHelper, securityContext, blockSnapshotSessionMgr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj, List<BlockObject> sourceObjList, Project project,
            String name, int newTargetsCount, String newTargetsName, String newTargetCopyMode, boolean skipInternalCheck,
            BlockFullCopyManager fcManager) {
        // We can only create a snapshot session for a VPLEX volume, where the
        // source side backend volume supports the creation of a snapshot session.
        for (BlockObject sourceObj : sourceObjList) {
            URI sourceURI = sourceObj.getId();
            if (URIUtil.isType(sourceURI, Volume.class)) {
                // Get the platform specific implementation for the source side
                // backend storage system and call the validation routine. Currently,
                // we only support snapshot sessions for VMAX3. Otherwise, it's not
                // supported.
                Volume vplexVolume = (Volume) sourceObj;
                BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
                StorageSystem srcSideBackendSystem = _dbClient.queryObject(StorageSystem.class,
                        srcSideBackendVolume.getStorageController());
                BlockSnapshotSessionApi snapshotSessionImpl = _blockSnapshotSessionMgr
                        .getPlatformSpecificImplForSystem(srcSideBackendSystem);
                snapshotSessionImpl.validateSnapshotSessionCreateRequest(srcSideBackendVolume, Arrays.asList(srcSideBackendVolume),
                        project, name, newTargetsCount, newTargetsName, newTargetCopyMode, true, fcManager);
            } else {
                // We don't currently support snaps of BlockSnapshot instances
                // so should not be called.
                throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateLinkNewTargetsRequest(BlockObject snapSessionSourceObj, Project project, int newTargetsCount,
            String newTargetsName, String newTargetCopyMode) {
        throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRelinkSnapshotSessionTargets(BlockSnapshotSession tgtSnapSession, Project project,
            List<URI> snapshotURIs, UriInfo uriInfo) {
        throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateUnlinkSnapshotSessionTargets(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, Project project,
            Set<URI> snapshotURIs, UriInfo uriInfo) {
        throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRestoreSnapshotSession(BlockObject snapSessionSourceObj, Project project) {
        throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateDeleteSnapshotSession(BlockSnapshotSession snapSession, Project project) {
        throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockSnapshotSession> getSnapshotSessionsForSource(BlockObject sourceObj) {
        throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
    }
}