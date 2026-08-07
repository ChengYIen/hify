package com.hify.module.provider.controller;

import com.hify.common.web.Result;
import com.hify.module.provider.controller.dto.ProviderModelResponse;
import com.hify.module.provider.service.ProviderModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型配置查询控制器（跨 provider，用于 Agent 创建/编辑时的模型下拉选择）.
 */
@RestController
@RequestMapping("/api/v1/model-configs")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ProviderModelService providerModelService;

    @GetMapping
    public Result<List<ProviderModelResponse>> listAllEnabled() {
        return Result.ok(providerModelService.listAllEnabled());
    }
}
