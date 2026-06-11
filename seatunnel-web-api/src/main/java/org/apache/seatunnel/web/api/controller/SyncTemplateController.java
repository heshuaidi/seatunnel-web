package org.apache.seatunnel.web.api.controller;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncBuiltInTemplateService;
import org.apache.seatunnel.web.spi.bean.dto.CreateFabMesSpcJdbcTaskRequest;
import org.apache.seatunnel.web.spi.bean.entity.Result;
import org.apache.seatunnel.web.spi.bean.vo.CreateTaskFromTemplateResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncBuiltInTemplateVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sync/templates")
public class SyncTemplateController {

    @Resource
    private SyncBuiltInTemplateService syncBuiltInTemplateService;

    @GetMapping
    public Result<List<SyncBuiltInTemplateVO>> listTemplates() {
        return Result.buildSuc(syncBuiltInTemplateService.listTemplates());
    }

    @GetMapping("/{templateCode}")
    public Result<SyncBuiltInTemplateVO> getTemplate(@PathVariable("templateCode") String templateCode) {
        return Result.buildSuc(syncBuiltInTemplateService.getTemplate(templateCode));
    }

    @PostMapping("/fab-mes-spc-jdbc/create-task")
    public Result<CreateTaskFromTemplateResultVO> createFabMesSpcJdbcTask(
            @RequestBody CreateFabMesSpcJdbcTaskRequest request
    ) {
        return Result.buildSuc(syncBuiltInTemplateService.createTaskFromFabMesSpcJdbcTemplate(request));
    }
}
