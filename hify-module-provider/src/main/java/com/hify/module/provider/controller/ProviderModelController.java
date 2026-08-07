package com.hify.module.provider.controller;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.module.provider.controller.dto.ProviderModelCreateRequest;
import com.hify.module.provider.controller.dto.ProviderModelResponse;
import com.hify.module.provider.controller.dto.ProviderModelUpdateRequest;
import com.hify.module.provider.service.ProviderModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型配置控制器.
 */
@RestController
@RequestMapping("/api/v1/providers/{providerId}/models")
@RequiredArgsConstructor
public class ProviderModelController {

    private final ProviderModelService providerModelService;

    @GetMapping
    public Result<List<ProviderModelResponse>> list(@PathVariable Long providerId) {
        return Result.ok(providerModelService.listByProviderId(providerId));
    }

    @GetMapping("/{id}")
    public Result<ProviderModelResponse> get(@PathVariable Long providerId, @PathVariable Long id) {
        return Result.ok(providerModelService.getById(id));
    }

    @PostMapping
    public Result<ProviderModelResponse> create(@PathVariable Long providerId,
                                                 @Valid @RequestBody ProviderModelCreateRequest request) {
        request.setProviderId(providerId);
        return Result.ok(providerModelService.create(request));
    }

    @PutMapping("/{id}")
    public Result<ProviderModelResponse> update(@PathVariable Long providerId,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody ProviderModelUpdateRequest request) {
        return Result.ok(providerModelService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long providerId, @PathVariable Long id) {
        providerModelService.delete(id);
        return Result.ok();
    }
}
