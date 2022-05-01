package com.rzmeu.epubfixer.service;

import com.rzmeu.epubfixer.config.Properties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.util.FileSystemUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Enumeration;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public record FixEpubRunnable(Properties properties, String source, String fileName) implements Runnable {

    private static final String EPUB_EXTENSION = ".epub";
    public static final String FIXED_EPUB_EXTENSION = "-fixed.epub";

    @Override
    public void run() {
        if (!fileName.endsWith(EPUB_EXTENSION)) {
            return;
        }

        System.out.println("Started processing : " + fileName);
        long startTimestamp = Instant.now().toEpochMilli();

        String newFileName = fileName.replace(EPUB_EXTENSION, FIXED_EPUB_EXTENSION);

        Path tempPath = new File(UUID.randomUUID().toString()).toPath();
        Path sourcePath = new File(properties.getBaseDirectory()).toPath().resolve(source);
        Path completePath = new File(properties.getCompleteDirectory()).toPath();
        Path filePath = sourcePath.resolve(fileName);

        tempPath.toFile().mkdir();

        try {
            waitUntilLockReleased(filePath.toFile());
            Files.copy(filePath, tempPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            fixEpubFile(tempPath, fileName, newFileName);

            Files.move(tempPath.resolve(newFileName), completePath.resolve(newFileName).toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
            FileSystemUtils.deleteRecursively(tempPath);
        } catch (IOException e) {
            throw new RuntimeException("Cannot move file", e);
        }

        long endTimestamp = Instant.now().toEpochMilli();
        System.out.println("Processing finished: " + newFileName + " in " + (endTimestamp - startTimestamp) / 1000 + " seconds");
    }

    private void waitUntilLockReleased(File file) {
        int tryNumber = 0;
        int waitUntilNextTry = 100;

        while (tryNumber < 10) {
            try {
                Thread.sleep(waitUntilNextTry);
                RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
                randomAccessFile.close();
                return;
            } catch (IOException | InterruptedException e) {
                System.out.println("File locked, try after " + waitUntilNextTry + " ms ");
            }
            tryNumber++;
        }
    }

    void fixEpubFile(Path folderPath, String fileName, String newFileName) throws IOException {
        ZipFile originalZip = new ZipFile(folderPath.resolve(fileName).toFile());
        final ZipOutputStream fixedZipOutputStream = new ZipOutputStream(new FileOutputStream(folderPath.resolve(newFileName).toFile()));

        for (Enumeration e = originalZip.entries(); e.hasMoreElements(); ) {
            ZipEntry entryIn = (ZipEntry) e.nextElement();

            if (!isEpubPage(entryIn.getName())) {
                fixedZipOutputStream.putNextEntry(new ZipEntry(entryIn.getName()));
                InputStream is = originalZip.getInputStream(entryIn);
                byte[] buf = new byte[1024];
                int len;
                while ((len = is.read(buf)) > 0) {
                    fixedZipOutputStream.write(buf, 0, len);
                }
            } else {
                fixedZipOutputStream.putNextEntry(new ZipEntry(entryIn.getName()));

                InputStream is = originalZip.getInputStream(entryIn);
                String text = fixEpubHtml(new String(is.readAllBytes(), StandardCharsets.UTF_8));

                fixedZipOutputStream.write(text.getBytes());
            }
            fixedZipOutputStream.closeEntry();
            ;
        }
        fixedZipOutputStream.close();
        originalZip.close();
    }

    private String fixEpubHtml(String text) {
        Document doc = Jsoup.parse(text);

        properties.getSources().get(source).getSelectors().forEach(selector -> doc.select(selector).remove());

        return doc.html();
    }

    private boolean isEpubPage(String fileName) {
        return fileName.startsWith("OEBPS/Text/") && fileName.endsWith("html");
    }
}
