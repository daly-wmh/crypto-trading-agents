package com.tradingagents.api.controller;

import com.tradingagents.api.entity.ModelProvider;
import com.tradingagents.api.service.ModelProviderService;
import com.tradingagents.model.ModelClientResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/model-providers")
public class ModelProviderController {

    private final ModelProviderService service;
    private final ModelClientResolver modelClientResolver;

    public ModelProviderController(ModelProviderService service,
                                    ModelClientResolver modelClientResolver) {
        this.service = service;
        this.modelClientResolver = modelClientResolver;
    }

    @GetMapping
    public List<ModelProvider> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ModelProvider getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public ModelProvider create(@RequestBody ModelProvider provider) {
        return service.create(provider);
    }

    @PutMapping("/{id}")
    public ModelProvider update(@PathVariable Long id, @RequestBody ModelProvider provider) {
        ModelProvider result = service.update(id, provider);
        modelClientResolver.clearCache(String.valueOf(id));
        return result;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            modelClientResolver.clearCache(String.valueOf(id));
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
