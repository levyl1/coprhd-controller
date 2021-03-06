/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import java.io.Writer;
import javax.ws.rs.core.MediaType;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.model.AuditLogTimeSeries;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.api.service.impl.resource.utils.JSONAuditLogMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.XMLAuditLogMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.AuditLogQueryResult;

/**
 * 
 * An implementation of auditlog retriever from a dbClient
 * 
 */
public class DbAuditLogRetriever extends AbstractDbRetriever implements AuditLogRetriever {

    private static final Logger log = LoggerFactory.getLogger(DbAuditLogRetriever.class);

    @Override
    public void getBulkAuditLogs(DateTime time, TimeSeriesMetadata.TimeBucket bucket,
            MediaType type, String lang, Writer writer) throws MarshallingExcetion {

        if (dbClient == null) {
            throw APIException.internalServerErrors.auditLogNoDb();
        }

        AuditLogMarshaller marshaller = null;

        if (type.equals(MediaType.APPLICATION_XML_TYPE)) {
            marshaller = new XMLAuditLogMarshaller();
            log.debug("Parser type: {}", type.toString());
        } else if (type.equals(MediaType.APPLICATION_JSON_TYPE)) {
            marshaller = new JSONAuditLogMarshaller();
            log.debug("Parser type: {}", type.toString());
        } else {
            log.warn("unsupported type: {}, use XML", type.toString());
            marshaller = new XMLAuditLogMarshaller();
        }
        marshaller.setLang(lang);

        AuditLogQueryResult result = new AuditLogQueryResult(marshaller,
                writer);

        marshaller.header(writer);

        log.info("Query time bucket {}", time.toString());

        dbClient.queryTimeSeries(AuditLogTimeSeries.class, time, bucket, result,
                getThreadPool());

        marshaller.tailer(writer);
    }
}
