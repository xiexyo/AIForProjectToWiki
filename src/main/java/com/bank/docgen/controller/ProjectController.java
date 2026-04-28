package com.bank.docgen.controller;

import com.bank.docgen.model.GenerationResult;
import com.bank.docgen.model.UploadResult;
import com.bank.docgen.service.DocumentationService;
import com.bank.docgen.service.ProjectWorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectWorkspaceService workspaceService;

    private final DocumentationService documentationService;

    public ProjectController(ProjectWorkspaceService workspaceService,
                             DocumentationService documentationService) {
        this.workspaceService = workspaceService;
        this.documentationService = documentationService;
    }

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
                result.projectId(),
                result.sourceDir(),
                result.outputDir());

        return result;
    }

    @PostMapping("/{projectId}/generate")
    public GenerationResult generate(@PathVariable String projectId) throws IOException {
        log.info("收到生成文档请求，projectId={}", projectId);

        long start = System.currentTimeMillis();

        GenerationResult result = documentationService.generate(projectId);

        long cost = System.currentTimeMillis() - start;

        log.info("文档生成完成，projectId={}, generatedFiles={}, costMs={}",
                projectId,
                result.generatedFiles().size(),
                cost);

        return result;
    }

    @GetMapping("/{projectId}/files")
    public List<String> listFiles(@PathVariable String projectId) throws IOException {
        log.info("查询生成文件列表，projectId={}", projectId);
        return workspaceService.listGeneratedFiles(projectId);
    }

    @GetMapping(value = "/{projectId}/file", produces = MediaType.TEXT_PLAIN_VALUE)
    public String readFile(@PathVariable String projectId,
                           @RequestParam String path) throws IOException {
        log.info("读取生成文件，projectId={}, path={}", projectId, path);
        return workspaceService.readGeneratedFile(projectId, path);
    }

    @DeleteMapping("/{projectId}")
    public String deleteProject(@PathVariable String projectId) throws IOException {
        log.info("删除项目工作区，projectId={}", projectId);
        workspaceService.cleanProject(projectId);
        return "OK";
    }
}