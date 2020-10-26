package com.eydlin.lmbd;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.core.DockerClientBuilder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@Slf4j
public class LmbdController {

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, @RequestParam(name = "tag", required = false) String tag) {
        return processFile(file, tag);
    }

    private String processFile(@NonNull MultipartFile file, String tag) {
        String tmpDir = System.getProperty("java.io.tmpdir");
        String uuid = UUID.randomUUID().toString();
        Path tmp = FileSystems.getDefault()
                              .getPath(tmpDir)
                              .normalize();
        Path target = tmp.resolve("lmbd." + uuid).normalize();
        try {
            Files.createDirectories(target);
            log.debug("created directory: {}", target.toString());
            try (ZipInputStream zipStream = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;
                while((entry = zipStream.getNextEntry()) != null) {
                    log.debug("process entry: {}", entry.getName());
                    Path path = target.resolve(entry.getName()).normalize();
                    if (!path.startsWith(target)) {
                        // avoid Zip Slip vulnerability
                        throw new IOException("Invalid ZIP");
                    }
                    if (entry.isDirectory()) {
                        log.debug("create directory: {}", path.toString());
                        Files.createDirectories(path);
                    } else {
                        try (OutputStream os = Files.newOutputStream(path)) {
                            log.debug("create file: {}", path.toString());
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = zipStream.read(buffer)) > 0) {
                                os.write(buffer, 0, len);
                            }
                        }
                    }
                };
                Path dockerfilePath = target.resolve("Dockerfile").normalize();
                if (Files.exists(dockerfilePath) && Files.isRegularFile(dockerfilePath)) {
                    log.debug("Dockerfile: {}", dockerfilePath.toString());
                    try (DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build()) {
                        return dockerClient
                                .buildImageCmd()
                                .withDockerfile(dockerfilePath.toFile())
                                .withTags(Collections.singleton(tag))
                                .exec(new BuildImageResultCallback())
                                .awaitImageId();
                    }
                } else {
                    throw new IllegalArgumentException("Dockerfile not present");
                }
            }
        } catch (Throwable th) {
            log.error("unexpected error", th);
            throw new RuntimeException(th);
        } finally {
            try {
                FileUtils.deleteDirectory(target.toFile());
            } catch (IOException e) {
                log.error("delete error", e);
            }
        }

    }

    @PostMapping("/run")
    public String run(@RequestParam("tag") String tag, @RequestParam("ports") String ports) {
        try (DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build()) {
            CreateContainerResponse container = dockerClient
                    .createContainerCmd(tag)
                    .withPortBindings(PortBinding.parse(ports))
                    .exec();
            dockerClient.startContainerCmd(container.getId()).exec();
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(container.getId()).exec();
            return inspect.toString();
        } catch (IOException e) {
            log.error("unexpected error", e);
            throw new RuntimeException(e);
        }
    }
}