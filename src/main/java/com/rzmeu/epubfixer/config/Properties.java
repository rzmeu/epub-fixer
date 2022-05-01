package com.rzmeu.epubfixer.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties("epubfixer")
public class Properties {
    private String baseDirectory;
    private String completeDirectory;
    private Map<String, Source> sources = new HashMap<>();

    @Data
    @NoArgsConstructor
    public static class Source {
        private List<String> selectors;
    }

}

