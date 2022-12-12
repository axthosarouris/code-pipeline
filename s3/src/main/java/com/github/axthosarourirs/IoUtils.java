package com.github.axthosarourirs;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class IoUtils {

    public static List<String> readFileLines(Path path) throws IOException {
        var file = path.toAbsolutePath().toFile();
        try (var reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            return reader.lines().collect(Collectors.toList());
        }
    }

    public static List<String> readFileLines(URI filePath) throws IOException {
        return readFileLines(uriToPath(filePath));
    }

    public static URI writeToFile(URI outputFilePath, List<String> lines) throws IOException {
        var file = uriToFile(outputFilePath);
        try (var writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)))) {
            for (var line : lines) {
                writer.write(line);
                writer.newLine();
            }
            writer.flush();
        }
        return file.toURI();
    }

    private static File uriToFile(URI outputFilePath) {
        return uriToPath(outputFilePath).toAbsolutePath().toFile();
    }

    private static Path uriToPath(URI outputFilePath) {
        return Path.of(outputFilePath.getPath());
    }
}
