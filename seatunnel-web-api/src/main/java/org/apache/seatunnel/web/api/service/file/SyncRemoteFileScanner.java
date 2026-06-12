package org.apache.seatunnel.web.api.service.file;

import org.apache.seatunnel.web.api.service.model.DiscoveredFile;
import org.apache.seatunnel.web.api.service.model.RemoteFileScanRequest;

import java.util.List;

public interface SyncRemoteFileScanner {

    List<DiscoveredFile> scan(RemoteFileScanRequest request);
}
