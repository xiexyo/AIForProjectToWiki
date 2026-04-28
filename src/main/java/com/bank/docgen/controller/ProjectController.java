// src/main/java/com/bank/docgen/controller/ProjectController.java
package com.bank.docgen.controller;

import com.bank.docgen.log.LogBroadcaster;
import com.bank.docgen.model.GenerationResult;
import com.bank.docgen.model.UploadResult;
import com.bank.docgen.service.DocumentationService;
import com.bank.docgen.service.ProjectWorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectWorkspaceService workspaceService;
    private final DocumentationService documentationService;
    private final LogBroadcaster logBroadcaster;
    private final ObjectMapper objectMapper;

    /**
     * 记录每个 projectId 的构建状态。
     */
    private final Map<String, String> buildStatus = new ConcurrentHashMap<>();

    /**
     * 异步执行构建任务的线程池。
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public ProjectController(ProjectWorkspaceService workspaceService,
                             DocumentationService documentationService,
                             LogBroadcaster logBroadcaster,
                             ObjectMapper objectMapper) {
        this.workspaceService = workspaceService;
        this.documentationService = documentationService;
        this.logBroadcaster = logBroadcaster;
        this.objectMapper = objectMapper;
    }

    /**
     * 上传 ZIP 文件。
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResult upload(@RequestPart("file") MultipartFile file) throws IOException {
        log.info("收到上传请求，fileName={}, size={}",
                file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getSize());

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("当前版本仅支持上传 ZIP 文件");
        }

        UploadResult result = workspaceService.createFromZip(file);

        log.info("上传完成，projectId={}, sourceDir={}, outputDir={}",
                result.projectId(), result.sourceDir(), result.outputDir());

        return result;
    }

    /**
     * 异步触发构建。
     */
    @PostMapping("/{projectId}/generate")
    public Map<String, String> generate(@PathVariable String projectId) {
        log.info("收到生成文档请求，projectId={}", projectId);

        if ("RUNNING".equals(buildStatus.get(projectId))) {
            return Map.of("status", "ALREADY_RUNNING", "projectId", projectId);
        }

        buildStatus.put(projectId, "RUNNING");

        executor.submit(() -> {
            try {
                GenerationResult result = documentationService.generate(projectId);

                buildStatus.put(projectId, "DONE");

                String json = objectMapper.writeValueAsString(result);
                logBroadcaster.broadcastEvent("build-complete", json);

                log.info("文档生成完成，projectId={}, files={}", projectId, result.generatedFiles().size());

            } catch (Exception e) {
                buildStatus.put(projectId, "FAILED");

                log.error("文档生成失败，projectId={}", projectId, e);
                logBroadcaster.broadcastEvent("build-error", e.getMessage());
            }
        });

        return Map.of("status", "STARTED", "projectId", projectId);
    }

    /**
     * 查询构建状态。
     */
    @GetMapping("/{projectId}/status")
    public Map<String, String> status(@PathVariable String projectId) {
        String s = buildStatus.getOrDefault(projectId, "UNKNOWN");
        return Map.of("projectId", projectId, "status", s);
    }

    /**
     * SSE 日志流端点。
     */
    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter logStream() {
        log.info("前端建立 SSE 日志连接");
        return logBroadcaster.createEmitter();
    }

    /**
     * 查询已生成的文件列表。
     */
    @GetMapping("/{projectId}/files")
    public List<String> listFiles(@PathVariable String projectId) throws IOException {
        return workspaceService.listGeneratedFiles(projectId);
    }

    /**
     * 读取已生成的文件内容。
     */
    @GetMapping(value = "/{projectId}/file", produces = MediaType.TEXT_PLAIN_VALUE)
    public String readFile(@PathVariable String projectId,
                           @RequestParam String path) throws IOException {
        return workspaceService.readGeneratedFile(projectId, path);
    }

    /**
     * 删除项目。
     */
    @DeleteMapping("/{projectId}")
    public String deleteProject(@PathVariable String projectId) throws IOException {
        workspaceService.cleanProject(projectId);
        buildStatus.remove(projectId);
        return "OK";
    }
}