package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MethodSourceExtractorTest {

    private Path writeSample(Path root) throws Exception {
        Path dir = root.resolve("com/example");
        Files.createDirectories(dir);
        Path file = dir.resolve("Sample.java");
        Files.writeString(file, """
                package com.example;
                class Sample {
                    String greet(String name) {
                        String v = normalize(name);
                        return repo.save(v);
                    }
                    String normalize(String s) { return s.trim(); }
                }
                """);
        return file;
    }

    @Test
    void extractsMethodSourceAndCalleeNames(@TempDir Path root) throws Exception {
        writeSample(root);
        MethodSourceExtractor extractor = new MethodSourceExtractor(List.of(root));

        Optional<MethodSourceExtractor.Extracted> got =
                extractor.extract("com.example.Sample#greet(String)");

        assertTrue(got.isPresent());
        assertTrue(got.get().source().contains("greet"), "源码应含方法名");
        assertTrue(got.get().source().contains("normalize"), "源码应含方法体内容");
        assertTrue(got.get().calleeNames().contains("normalize"), "被调名应含 normalize");
        assertTrue(got.get().calleeNames().contains("save"), "被调名应含 save");
    }

    @Test
    void returnsEmptyWhenFileMissing(@TempDir Path root) {
        MethodSourceExtractor extractor = new MethodSourceExtractor(List.of(root));
        assertTrue(extractor.extract("com.example.Nope#x()").isEmpty());
    }

    @Test
    void returnsEmptyOnMalformedSignature(@TempDir Path root) {
        MethodSourceExtractor extractor = new MethodSourceExtractor(List.of(root));
        assertTrue(extractor.extract("garbage").isEmpty());
    }
}
