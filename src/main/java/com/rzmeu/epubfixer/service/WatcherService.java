package com.rzmeu.epubfixer.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

@Service
public class WatcherService {

    Map<WatchKey, Path> keys = new HashMap<>();

    public void start() throws IOException, InterruptedException {
        WatchService watchService = FileSystems.getDefault().newWatchService();

        Path path = Paths.get("C:\\watch");
        WatchKey watchKey = path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
        keys.put(watchKey, path);


        while (true) {
            WatchKey key = watchService.take();
            for (WatchEvent<?> event : key.pollEvents()) {
                new FixEpubRunnable(keys.get(key).toAbsolutePath(), event.context().toString()).run();
            }
            key.reset();
        }
    }
}
