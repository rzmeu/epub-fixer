package com.rzmeu.epubfixer.service;

import com.rzmeu.epubfixer.config.Properties;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

@Service
@AllArgsConstructor
public class WatcherService {

    private final Properties properties;
    private Map<WatchKey, String> keys;

    public void start() throws IOException, InterruptedException {
        WatchService watchService = FileSystems.getDefault().newWatchService();

        File baseDirectory = new File(properties.getBaseDirectory());
        File completeDirectory = new File(properties.getCompleteDirectory());

        if(!baseDirectory.exists()) {
            baseDirectory.mkdir();
        }

        if(!completeDirectory.exists()) {
            completeDirectory.mkdir();
        }

        properties.getSources().forEach((sourceName, source) -> {
            File sourceDirectory = baseDirectory.toPath().resolve(sourceName).toFile();

            if(!sourceDirectory.exists()) {
                sourceDirectory.mkdir();
            }

            Path path = sourceDirectory.toPath();

            try {
                WatchKey watchKey = path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
                keys.put(watchKey, sourceName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });


        while (true) {
            WatchKey key = watchService.take();
            for (WatchEvent<?> event : key.pollEvents()) {
                new FixEpubRunnable(properties, keys.get(key), event.context().toString()).run();
            }
            key.reset();
        }
    }
}
