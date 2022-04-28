package com.rzmeu.epubfixer;

import com.rzmeu.epubfixer.service.WatcherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EpubFixerApplication implements CommandLineRunner {

    @Autowired
    private WatcherService watcherService;

    public static void main(String[] args) {
        SpringApplication.run(EpubFixerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        watcherService.start();
    }
}
