/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_file_snapshots")
public class FileSnapshotBulkRep extends BulkRestRep {
    private List<FileSnapshotRestRep> fileSnapshots;

    /**
     * The list of file snapshots, returned as response to bulk
     * queries.
     * 
     * @valid none
     */
    @XmlElement(name = "file_snapshot")
    public List<FileSnapshotRestRep> getFileSnapshots() {
        if (fileSnapshots == null) {
            fileSnapshots = new ArrayList<FileSnapshotRestRep>();
        }
        return fileSnapshots;
    }

    public void setFileSnapshots(List<FileSnapshotRestRep> fileSnapshots) {
        this.fileSnapshots = fileSnapshots;
    }

    public FileSnapshotBulkRep() {
    }

    public FileSnapshotBulkRep(List<FileSnapshotRestRep> fileSnapshots) {
        this.fileSnapshots = fileSnapshots;
    }
}
