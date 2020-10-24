package com.eydlin.lmbd;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@Slf4j
public class LmbdController {
    
    @GetMapping("/hello")
    public String hello() {
        return "Hello world";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) {
        processFile(file);

        return "upload successful";
    }

    private void processFile(@NonNull MultipartFile file) {
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
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
//            try {
//                FileUtils.deleteDirectory(target.toFile());
//            } catch (IOException e) {
//                log.error("delete error", e);
//            }
        }

    }
}