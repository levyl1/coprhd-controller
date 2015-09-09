/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.db.client.model.uimodels.InitialSetup.COMPLETE;
import static com.emc.storageos.db.client.model.uimodels.InitialSetup.CONFIG_ID;
import static com.emc.storageos.db.client.model.uimodels.InitialSetup.CONFIG_KIND;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.SiteMapper;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Site;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

@Path("/site")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class DisasterRecoveryService extends TaggedResource {

    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryService.class);
    private CoordinatorClient coordinator = null;
    private InternalApiSignatureKeyGenerator apiSignatureGenerator;
    private SiteMapper siteMapper;
    
    public DisasterRecoveryService() {
        siteMapper = new SiteMapper();
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteRestRep addStandby(SiteAddParam param) {
        log.info("Begin to add standby site");

        Site standbySite = new Site();
        standbySite.setId(URIUtil.createId(Site.class));
        standbySite.setUuid(param.getUuid());
        standbySite.setName(param.getName());
        standbySite.setVip(param.getVip());
        standbySite.getHostIPv4AddressMap().putAll(new StringMap(param.getHostIPv4AddressMap()));
        standbySite.getHostIPv6AddressMap().putAll(new StringMap(param.getHostIPv6AddressMap()));

        if (log.isDebugEnabled()) {
            log.debug(standbySite.toString());
        }
        
        VirtualDataCenter vdc = queryLocalVDC();
        vdc.getSiteIDs().add(standbySite.getId().toString());

        log.info("Persist standby site to DB");
        _dbClient.createObject(standbySite);
        
        log.info("Update VCD to persist new standby site ID");
        _dbClient.persistObject(vdc);

        return siteMapper.map(standbySite);
    }

    /**
     * Get all sites including standby and primary
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteList getAllStandby() {
        log.info("Begin to list all standby sites of local VDC");
        SiteList standbyList = new SiteList();
        
        VirtualDataCenter vdc = queryLocalVDC();
        Collection<String> standbyIds = vdc.getSiteIDs();

        List<URI> ids = _dbClient.queryByType(Site.class, true);
        Iterator<Site> sites = _dbClient.queryIterativeObjects(Site.class, ids);
        while (sites.hasNext()) {
            Site standby = sites.next();
            if (standbyIds.contains(standby.getId().toString())) {
                standbyList.getSites().add(siteMapper.map(standby));
            }
        }
        
        return standbyList;
    }
    
    /**
     * Get specified site according site ID
     * @param id site ID
     * @return
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public SiteRestRep getStandby(@PathParam("id") String id) {
        log.info("Begin to get standby site by uuid {}", id);
        
        VirtualDataCenter vdc = queryLocalVDC();
        
        List<URI> ids = _dbClient.queryByType(Site.class, true);

        Iterator<Site> sites = _dbClient.queryIterativeObjects(Site.class, ids);
        while (sites.hasNext()) {
            Site standby = sites.next();
            if (vdc.getSiteIDs().contains(standby.getId().toString())) {
                if (standby.getUuid().equals(id)) {
                    return siteMapper.map(standby);
                }
            }
        }
        
        log.info("Can't find site with specified site ID {}", id);
        return null;
    }

    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public SiteRestRep removeStandby(@PathParam("id") String id) {
        log.info("Begin to remove standby site from local vdc");
        
        VirtualDataCenter vdc = queryLocalVDC();
        Collection<String> standbyIds = getStandbyIds(vdc.getSiteIDs());
        
        List<URI> ids = _dbClient.queryByType(Site.class, true);

        Iterator<Site> sites = _dbClient.queryIterativeObjects(Site.class, ids);
        while (sites.hasNext()) {
            Site standby = sites.next();
            if (standbyIds.contains(standby.getId().toString())) {
                if (standby.getUuid().equals(id)) {
                    log.info("Find standby site in local VDC and remove it");
                    vdc.getSiteIDs().remove(standby.getId());
                    _dbClient.persistObject(vdc);
                    _dbClient.markForDeletion(standby);
                    return siteMapper.map(standby);
                }
            }
        }
        
        return null;
    }
    
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/standby/config")
    public SiteRestRep getStandbyConfig() {
        log.info("Begin to get standby config");
        String siteId = this.coordinator.getSiteId();
        VirtualDataCenter vdc = queryLocalVDC();
        SecretKey key = apiSignatureGenerator.getSignatureKey(SignatureKeyType.INTERVDC_API);

        Site localSite = new Site();

        localSite.setUuid(siteId);
        localSite.setVip(vdc.getApiEndpoint());
        localSite.getHostIPv4AddressMap().putAll(vdc.getHostIPv4AddressesMap());
        localSite.getHostIPv6AddressMap().putAll(vdc.getHostIPv6AddressesMap());
        localSite.setSecretKey(new String(Base64.encodeBase64(key.getEncoded()), Charset.forName("UTF-8")));
        
        SiteRestRep siteRestRep = siteMapper.map(localSite);
        
        siteRestRep.setDbSchemaVersion(coordinator.getCurrentDbSchemaVersion());
        siteRestRep.setFreshInstallation(isFreshInstallation());
        
        try {
            siteRestRep.setSoftwareVersion(coordinator.getTargetInfo(RepositoryInfo.class).getCurrentVersion().toString());
        } catch (Exception e) {
            log.error("Fail to get software version {}", e);
        }
        
        log.info("localSite: {}", localSite);
        return siteRestRep;
    }
    
    @POST()
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/standby/config")
    public SiteRestRep addPrimary(SiteAddParam param) {
        log.info("Begin to add primary site");

        Site primarySite = new Site();
        primarySite.setId(URIUtil.createId(Site.class));
        primarySite.setUuid(param.getUuid());
        primarySite.setName(param.getName());
        primarySite.setVip(param.getVip());
        primarySite.getHostIPv4AddressMap().putAll(new StringMap(param.getHostIPv4AddressMap()));
        primarySite.getHostIPv6AddressMap().putAll(new StringMap(param.getHostIPv6AddressMap()));

        VirtualDataCenter vdc = queryLocalVDC();
        vdc.getSiteIDs().add(primarySite.getId().toString());

        log.info("Persist primary site to DB");
        _dbClient.createObject(primarySite);

        log.info("Update VCD to persist new site ID");
        _dbClient.persistObject(vdc);

        return siteMapper.map(primarySite);
    }
    
    @Override
    protected Site queryResource(URI id) {
        ArgValidator.checkUri(id);
        Site standby = _dbClient.queryObject(Site.class, id);
        ArgValidator.checkEntityNotNull(standby, id, isIdEmbeddedInURL(id));
        return standby;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    private Collection<String> getStandbyIds(Set<String> siteIds) {
        Set<String> standbyIds = new HashSet<String>();
        String primarySiteId = this.coordinator.getPrimarySiteId();

        for (String siteId : siteIds){
            if (siteId != null && !siteId.equals(primarySiteId)) {
                standbyIds.add(siteId);
            }
        }
        return Collections.unmodifiableCollection(standbyIds);
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.SITE;
    }
    
    /*
     * Internal method to check whether standby can be attached to current primary site
     */
    protected void precheckForStandbyAttach(SiteAddParam standby) {
        //standby should be refresh install
        if (standby.isFreshInstallation() == false) {
            log.info("Standby is not refresh installation");
            throw APIException.internalServerErrors.addStandbyPrecheckFailed();
        }
        
        //DB schema version should be same
        String currentDbSchemaVersion = coordinator.getCurrentDbSchemaVersion();
        String standbyDbSchemaVersion = standby.getDbSchemaVersion();
        if (!currentDbSchemaVersion.equalsIgnoreCase(standbyDbSchemaVersion)) {
            log.info("Standby db schema version {} is not same as primary {}", standbyDbSchemaVersion, currentDbSchemaVersion);
            throw APIException.internalServerErrors.addStandbyPrecheckFailed();
        }
        
        //software version should be matched
        SoftwareVersion currentSoftwareVersion;
        SoftwareVersion standbySoftwareVersion;
        try {
            currentSoftwareVersion = coordinator.getTargetInfo(RepositoryInfo.class).getCurrentVersion();
            standbySoftwareVersion = new SoftwareVersion(standby.getSoftwareVersion());
        } catch (Exception e) {
            log.error("Fail to get software version {}", e);
            throw APIException.internalServerErrors.addStandbyPrecheckFailed();
        }
        
        if (!currentSoftwareVersion.isNaturallySwitchableTo(standbySoftwareVersion)) {
            log.info("Standby site version {} is not equals to current version {}", standbySoftwareVersion, currentSoftwareVersion);
            throw APIException.internalServerErrors.addStandbyPrecheckFailed();
        }
        
        //this site should be primary site
        if (!coordinator.getSiteId().equals(coordinator.getPrimarySiteId())) {
            log.info("This site is not primary site");
            throw APIException.internalServerErrors.addStandbyPrecheckFailed();
        }
    }
    
    protected boolean isFreshInstallation() {
        Configuration setupConfig = coordinator.queryConfiguration(CONFIG_KIND, CONFIG_ID);
        boolean freshInstall = (setupConfig == null) || Boolean.parseBoolean(setupConfig.getConfig(COMPLETE)) == false;
        
        log.info("Fresh installation {}", freshInstall);
        return freshInstall;
    }
    
    // encapsulate the get local VDC operation for easy UT writing because VDCUtil.getLocalVdc is static method
    protected VirtualDataCenter queryLocalVDC() {
        return VdcUtil.getLocalVdc();
    }
    
    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public InternalApiSignatureKeyGenerator getApiSignatureGenerator() {
        return apiSignatureGenerator;
    }

    public void setApiSignatureGenerator(InternalApiSignatureKeyGenerator apiSignatureGenerator) {
        this.apiSignatureGenerator = apiSignatureGenerator;
    }
    
    public void setSiteMapper(SiteMapper siteMapper) {
        this.siteMapper = siteMapper;
    }
}