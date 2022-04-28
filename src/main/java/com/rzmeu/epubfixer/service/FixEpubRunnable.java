package com.rzmeu.epubfixer.service;

import org.apache.commons.lang3.RegExUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class FixEpubRunnable implements Runnable {
    private final Path directory;
    private final String fileName;
    private List<Pattern> regexPatternList = new ArrayList<>();

    public FixEpubRunnable(Path directory, String fileName) {
        this.directory = directory;
        this.fileName = fileName;

        regexPatternList.add(Pattern.compile("<style type=\\\"text\\/css\\\">[\\n@a-z ()-:\\{\\};]*<\\/style>"));
        regexPatternList.add(Pattern.compile("<div[A-Za-z =\"\\/>\\n@()-:\\{;\\}#<]*<\\/a><\\/div><br \\/><\\/div>"));
        regexPatternList.add(Pattern.compile("<p[A-Za-z =\"\\/>\\n@()-:\\{;\\}#<_]*<strong>[A-Za-z =\"\\/>\\n@()-:\\{;\\}#<_]*?<\\/p>"));
    }

    @Override
    public void run() {
        if(!fileName.endsWith(".epub")) {
            return;
        }

        System.out.println("New file: " + fileName + ", at path: " + directory.toString());
        long startTimestamp = Instant.now().toEpochMilli();

        initCommonDirectories();

        String tempFolder = UUID.randomUUID().toString();
        boolean tempDirectoryCreated = new File(directory.toString(), tempFolder).mkdir();

        if (!tempDirectoryCreated) {
            throw new RuntimeException("Directory not created");
        }

        try {
            Path filePath = directory.resolve(fileName);
            waitUntilLockReleased(filePath.toFile());
            Files.move(directory.resolve(fileName), directory.resolve(tempFolder).resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

            String newFileName = fileName.replace(".epub", "-fixed.epub");
            Path tempDirectory = directory.resolve(tempFolder);

            fixEpubFile(tempDirectory, fileName, newFileName);

            Files.move(tempDirectory.resolve(fileName), directory.resolve("processed").resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempDirectory.resolve(newFileName), directory.resolve("fixed").resolve(newFileName), StandardCopyOption.REPLACE_EXISTING);
            Files.delete(tempDirectory);

            long endTimestamp = Instant.now().toEpochMilli();
            System.out.println("Epub file: " + newFileName + " fixed in " + (endTimestamp -startTimestamp)/1000 + " seconds");
        } catch (IOException e) {
            throw new RuntimeException("Cannot move file");
        }
    }

    private void initCommonDirectories() {
        File processedDirectory = new File(directory.toString(), "processed");
        File fixedDirectory = new File(directory.toString(), "fixed");

        if(!processedDirectory.exists()) {
            processedDirectory.mkdir();
        }

        if(!fixedDirectory.exists()) {
            fixedDirectory.mkdir();
        }
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

        for(Enumeration e = originalZip.entries(); e.hasMoreElements();) {
            ZipEntry entryIn = (ZipEntry) e.nextElement();

            if (!isEpubPage(entryIn.getName())) {
                fixedZipOutputStream.putNextEntry(entryIn);
                InputStream is = originalZip.getInputStream(entryIn);
                byte[] buf = new byte[1024];
                int len;
                while((len = is.read(buf)) > 0) {
                    fixedZipOutputStream.write(buf, 0, len);
                }
            } else {
                fixedZipOutputStream.putNextEntry(new ZipEntry(entryIn.getName()));

                InputStream is = originalZip.getInputStream(entryIn);
                String text = fixEpubHtml(new String(is.readAllBytes(), StandardCharsets.UTF_8));

                fixedZipOutputStream.write(text.getBytes());
            }
            fixedZipOutputStream.closeEntry();;
        }
        fixedZipOutputStream.close();
        originalZip.close();
    }

    private String fixEpubHtml(String text) {
        for (int i = 0; i < regexPatternList.size(); i++) {
            text = RegExUtils.removeAll(text, regexPatternList.get(i));
        }

        return text;
    }

    private boolean isEpubPage(String fileName) {
        return fileName.startsWith("OEBPS/Text/") && fileName.endsWith(".xhtml");
    }
}
