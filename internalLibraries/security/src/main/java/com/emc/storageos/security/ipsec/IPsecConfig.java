/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.ipsec;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.keystore.impl.CoordinatorConfigStoringHelper;
import org.apache.commons.codec.binary.StringUtils;
import org.jsoup.helper.StringUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class IPsecConfig {

    private static final String IPSEC_CONFIG_LOCK = "IPsecConfigLock";
    private static final String IPSEC_CONFIG_KIND = "ipsec";
    private static final String IPSEC_CONFIG_ID = "ipsec";
    private static final String IPSEC_PSK_KEY = "ipsec_key";

    // Properties injected by spring
    private CoordinatorClient coordinator;
    private String defaultPskFile;

    private CoordinatorConfigStoringHelper coordinatorHelper;

    public String getPreSharedKey() throws Exception {
        String preSharedKey = getCoordinatorHelper().readConfig(IPSEC_CONFIG_KIND, IPSEC_CONFIG_ID, IPSEC_PSK_KEY);
        if (StringUtil.isBlank(preSharedKey)) {
            preSharedKey = loadDefaultIpsecKeyFromFile();
        }
        return preSharedKey;
    }

    public void setPreSharedKey(String preSharedKey) throws Exception {
        getCoordinatorHelper().createOrUpdateConfig(preSharedKey, IPSEC_CONFIG_LOCK, IPSEC_CONFIG_KIND, IPSEC_CONFIG_ID, IPSEC_PSK_KEY);
    }

    private String loadDefaultIpsecKeyFromFile() throws Exception {
        BufferedReader in = new BufferedReader(new FileReader(new File(defaultPskFile)));
        try {
            String key = in.readLine();
            return key;
        } finally {
            in.close();
        }
    }

    private CoordinatorConfigStoringHelper getCoordinatorHelper() {
        if (coordinatorHelper == null) {
            coordinatorHelper = new CoordinatorConfigStoringHelper(coordinator);
        }
        return coordinatorHelper;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public void setDefaultPskFile(String defaultPskFile) {
        this.defaultPskFile = defaultPskFile;
    }
}
