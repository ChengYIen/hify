package com.hify.module.provider.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.module.provider.controller.dto.ConnectionTestResult;
import com.hify.module.provider.controller.dto.ProviderCreateRequest;
import com.hify.module.provider.controller.dto.ProviderResponse;
import com.hify.module.provider.controller.dto.ProviderUpdateRequest;
import com.hify.module.provider.service.ProviderConnectivityService;
import com.hify.module.provider.service.ProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型提供商控制器.
 */
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;
    private final ProviderConnectivityService connectivityService;

    @GetMapping
    public PageResult<ProviderResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String providerCode,
            @RequestParam(required = false) String status) {
        IPage<ProviderResponse> result = providerService.list(
                page != null ? page : 1,
                pageSize != null ? pageSize : 20,
                providerCode,
                status);
        return PageHelper.toPageResult(result);
    }

    @GetMapping("/{id}")
    public Result<ProviderResponse> get(@PathVariable Long id) {
        return Result.ok(providerService.getById(id));
    }

    @PostMapping
    public Result<ProviderResponse> create(@Valid @RequestBody ProviderCreateRequest request) {
        return Result.ok(providerService.create(request));
    }

    @PutMapping("/{id}")
    public Result<ProviderResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody ProviderUpdateRequest request) {
        return Result.ok(providerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        providerService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test-connection")
    public Result<ConnectionTestResult> testConnection(@PathVariable Long id) {
        return Result.ok(connectivityService.testConnection(id));
    }
}
