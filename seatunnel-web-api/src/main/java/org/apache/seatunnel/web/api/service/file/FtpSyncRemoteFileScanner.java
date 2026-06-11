package org.apache.seatunnel.web.api.service.file;

import org.apache.seatunnel.web.api.service.model.DiscoveredFile;
import org.apache.seatunnel.web.api.service.model.RemoteFileScanRequest;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FtpSyncRemoteFileScanner implements SyncRemoteFileScanner {

    @Override
    public List<DiscoveredFile> scan(RemoteFileScanRequest request) {
        throw new ServiceException(
                "FTP file discovery is not implemented because no reusable FTP datasource client was found"
        );
    }
}
