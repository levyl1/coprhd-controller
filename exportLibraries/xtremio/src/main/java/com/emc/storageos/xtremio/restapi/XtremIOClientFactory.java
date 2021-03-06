/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi;

import java.net.URI;

import com.emc.storageos.services.restutil.RestClientFactory;
import com.emc.storageos.services.restutil.RestClientItf;
import com.sun.jersey.api.client.Client;

public class XtremIOClientFactory extends RestClientFactory {

    private static final String DOT_OPERATOR = "\\.";
    private static final Integer XIO_MIN_4X_VERSION = 4;

    private String model;

    public void setModel(String model) {
        this.model = model;
    }

    @Override
    protected RestClientItf createNewRestClient(URI endpoint, String username,
            String password, Client client) {
        XtremIOClient xioClient = null;
        // Based on the storagesystem/provider version, create v2 or v1 client.
        if (model != null && Integer.valueOf(model.split(DOT_OPERATOR)[0]) >= XIO_MIN_4X_VERSION) {
            xioClient = new XtremIOV2Client(endpoint, username, password, client);
        } else {
            xioClient = new XtremIOV1Client(endpoint, username, password, client);
        }

        return xioClient;
    }

    public RestClientItf getXtremIOV1Client(URI endpoint, String username,
            String password, boolean authFilter) {
        Client jerseyClient = super.getBaseClient(endpoint, username, password, authFilter);
        return new XtremIOV1Client(endpoint, username, password, jerseyClient);
    }
}
