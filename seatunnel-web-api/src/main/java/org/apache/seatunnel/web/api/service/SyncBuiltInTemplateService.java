package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.spi.bean.dto.CreateFabMesSpcJdbcTaskRequest;
import org.apache.seatunnel.web.spi.bean.dto.CreateFabLoaderPublishTaskRequest;
import org.apache.seatunnel.web.spi.bean.dto.CreateGenericJdbcStarRocksTaskRequest;
import org.apache.seatunnel.web.spi.bean.vo.CreateTaskFromTemplateResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncBuiltInTemplateVO;

import java.util.List;

public interface SyncBuiltInTemplateService {

    String FAB_MES_SPC_JDBC_TRANSLATOR = "FAB_MES_SPC_JDBC_TRANSLATOR";

    String FAB_LOADER_PUBLISH_XCHG_TO_STG = "FAB_LOADER_PUBLISH_XCHG_TO_STG";

    String GENERIC_JDBC_SQL_TO_STARROCKS_INCREMENTAL = "GENERIC_JDBC_SQL_TO_STARROCKS_INCREMENTAL";

    List<SyncBuiltInTemplateVO> listTemplates();

    SyncBuiltInTemplateVO getTemplate(String templateCode);

    CreateTaskFromTemplateResultVO createTaskFromFabMesSpcJdbcTemplate(
            CreateFabMesSpcJdbcTaskRequest request
    );

    CreateTaskFromTemplateResultVO createTaskFromFabLoaderPublishTemplate(
            CreateFabLoaderPublishTaskRequest request
    );

    CreateTaskFromTemplateResultVO createTaskFromGenericJdbcStarRocksTemplate(
            CreateGenericJdbcStarRocksTaskRequest request
    );
}
