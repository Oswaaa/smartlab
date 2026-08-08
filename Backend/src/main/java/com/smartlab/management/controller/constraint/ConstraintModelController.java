package com.smartlab.management.controller.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.engine.constraint.EffectiveConstraintModel;
import com.smartlab.engine.constraint.EffectiveConstraintModelCompiler;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.constraint.EffectiveConstraintModelView;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/constraint/model")
public class ConstraintModelController {

    private final EffectiveConstraintModelCompiler compiler;

    public ConstraintModelController(EffectiveConstraintModelCompiler compiler) {
        this.compiler = compiler;
    }

    @GetMapping(value = "/global/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> exportGlobalModel() {
        return download(compiler.compileGlobal().jsonModel(), "global-constraint-model.json");
    }

    @GetMapping("/task/{taskId}")
    public ApiResponse<EffectiveConstraintModelView> taskModel(@PathVariable Long taskId) {
        try {
            return ApiResponse.ok(view(compiler.compileForTask(taskId)));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping(value = "/task/{taskId}/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> exportTaskModel(@PathVariable Long taskId) {
        JsonNode model = compiler.compileForTask(taskId).jsonModel();
        return download(model, "task-" + taskId + "-constraint-model.json");
    }

    private EffectiveConstraintModelView view(EffectiveConstraintModel model) {
        return new EffectiveConstraintModelView(
                model.taskId(),
                model.taskName(),
                model.taskStatus(),
                OffsetDateTime.ofInstant(model.compiledAt(), java.time.ZoneOffset.UTC),
                model.revision(),
                model.modelHash(),
                model.globalConstraints(),
                model.taskConstraints(),
                model.jsonModel()
        );
    }

    private ResponseEntity<JsonNode> download(JsonNode model, String filename) {
        String disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(model);
    }
}
