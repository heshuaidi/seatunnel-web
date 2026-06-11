package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.spi.bean.dto.CreateFabMesSpcJdbcTaskRequest;
import org.apache.seatunnel.web.spi.bean.vo.CreateTaskFromTemplateResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncBuiltInTemplateVO;

import java.util.List;

public interface SyncBuiltInTemplateService {

    String FAB_MES_SPC_JDBC_TRANSLATOR = "FAB_MES_SPC_JDBC_TRANSLATOR";

    List<SyncBuiltInTemplateVO> listTemplates();

    SyncBuiltInTemplateVO getTemplate(String templateCode);

    CreateTaskFromTemplateResultVO createTaskFromFabMesSpcJdbcTemplate(
            CreateFabMesSpcJdbcTaskRequest request
    );
}
