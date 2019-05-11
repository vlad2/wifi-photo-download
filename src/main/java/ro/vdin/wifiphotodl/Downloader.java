package ro.vdin.wifiphotodl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class Downloader {
    private static final Logger log = LoggerFactory.getLogger(Downloader.class);

    private static final byte[] COPY_BUFFER = new byte[4 * 1024];

    private ObjectMapper objectMapper;

    private RestTemplate restTemplate;

    private AppConfig appConfig;

    public Downloader(ObjectMapper objectMapper, RestTemplate restTemplate, AppConfig appConfig) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.appConfig = appConfig;
    }

    public void download() throws URISyntaxException, JsonParseException, JsonMappingException, IOException {
        log.info("Starting download");

        int lib = appConfig.getLib();
        int startIndex = appConfig.getStartIndex();
        int endIndex = appConfig.getEndIndex();
        int selectionSize = appConfig.getSelectionSize();
        File saveDir = new File(appConfig.getSaveDir());

        if (!saveDir.exists()) {
            log.warn("Directory {} doesn't exist, creating", saveDir);
            saveDir.mkdir();
        }

        if (!saveDir.isDirectory()) {
            throw new RuntimeException("Directory: " + saveDir + " is not a directory!");
        }

        URI baseUrl = new URI(appConfig.getBaseUrl());

        for (int i = startIndex; i < endIndex; i += selectionSize) {
            IntStream range = IntStream.range(i, i + selectionSize);
            String selection = range.boxed().map(String::valueOf).collect(Collectors.joining(","));

            log.debug("selection=" + selection);

            File filename = new File(saveDir, String.format("images-%d-%d.zip", i, i + selectionSize - 1));
            if (filename.exists()) {
                throw new RuntimeException("File " + filename + " exists");
            }

            long fileBytes = downloadSingle(baseUrl, lib, selection, filename);
            if (isEmptyZip(fileBytes)) {
                log.info("Empty zip downloaded, stopping download.");
                break;
            }
        }
    }

    private boolean isEmptyZip(long fileBytes) {
        return fileBytes == 49;
    }

    private long downloadSingle(URI baseUrl, int lib, String selection, File filename) throws JsonParseException,
                                                                                       JsonMappingException,
                                                                                       IOException {
        var selectionId = startDownload(baseUrl, lib, selection);
        log.info("Selection ID: " + selectionId);

        pollUntilReadyToDownload(baseUrl, selectionId);
        return saveSelection(baseUrl, selectionId, filename);
    }

    private long saveSelection(URI baseUrl, String selectionId, File filename) throws IOException {
        // curl http://192.168.0.100:15555/zipdownload/KRWBHRTKDANRGMIM/images.zip

        log.info("Saving to: {}", filename);

        String url = String.format("%s/zipdownload/%s/images.zip", baseUrl, selectionId);

        try (OutputStream outputStream = new FileOutputStream(filename)) {
            ResponseExtractor<?> responseCallback = responseExtractor -> {
                log.trace("Callback called, headers: {}", responseExtractor.getHeaders());
                try {
                    long expectedFileSize = responseExtractor.getHeaders().getContentLength();

                    Thread t = startProgressThread(filename, expectedFileSize);

                    IOUtils.copyLarge(responseExtractor.getBody(), outputStream, COPY_BUFFER);

                    endProgressThread(t);
                } catch (Throwable t) {
                    log.error("Error: {}", t.getMessage(), t);
                }
                return null;
            };

            restTemplate.execute(url, HttpMethod.GET, null, responseCallback);
        }

        long imageBytes = filename.length();

        log.info("Saved to: {}, {} bytes", filename, imageBytes);
        return imageBytes;
    }

    private Thread startProgressThread(File filename, long expectedFileSize) {
        var thread = new Thread(() -> {
            long fileSize = 0;
            while (fileSize < expectedFileSize) {
                fileSize = filename.length();
                int percent = (int) (fileSize * 100 / expectedFileSize);
                log.debug("Downloaded {} MiB / {} MiB, {}% complete.",
                          toMebiByte(fileSize),
                          toMebiByte(expectedFileSize),
                          percent);
                sleep();
            }
        });
        thread.start();
        return thread;
    }

    private void endProgressThread(Thread t) throws InterruptedException {
        t.join();
    }

    private long toMebiByte(long fileSize) {
        return fileSize / 1024 / 1024;
    }

    private void pollUntilReadyToDownload(URI baseUrl, String selectionId) throws JsonParseException,
                                                                           JsonMappingException,
                                                                           IOException {
        //        curl http://192.168.0.100:15555/compressprogress5784304?HBJEPPDHHPHMSUVK

        while (!isReadyForDownload(baseUrl, selectionId)) {
            log.info("Waiting for download to be ready..");
            sleep();
        }

        log.info("Download ready!");
    }

    private void sleep() {
        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
        }
    }

    private boolean isReadyForDownload(URI url,
                                       String selId) throws IOException, JsonParseException, JsonMappingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON_UTF8));

        String compressUri = String.format("%s/compressprogress?%s", url, selId);

        ResponseEntity<String> response = restTemplate.getForEntity(compressUri, String.class);

        /*
        {
           "statusString": "Waiting for download to start...",
           "readyForDownload": true,
           "done": false,
           "cancelled": false,
           "timestamp": 33978
          }
          */

        Map<String, Object> responseMap = objectMapper.readValue(response.getBody(),
                                                                 new TypeReference<Map<String, Object>>() {
                                                                 });

        Boolean readyForDownload = (Boolean) responseMap.get("readyForDownload");
        return readyForDownload;
    }

    private String startDownload(URI baseUrl, int lib, String selection) throws IOException,
                                                                         JsonParseException,
                                                                         JsonMappingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON_UTF8));

        String formBody = String.format("lib=%s&sel=%s", lib, selection);

        var request = new HttpEntity<String>(formBody, headers);

        //        log.info("Request factory: " + restTemplate.getRequestFactory().getClass());

        baseUrl = baseUrl.resolve("startcompressing");

        log.info("Starting to download photos!, baseUrl=" + baseUrl);
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, request, String.class);

        log.debug("Response: " + response);

        @SuppressWarnings("unchecked")
        Map<String, Object> readValue = objectMapper.readValue(response.getBody(), Map.class);

        String selId = (String) readValue.get("selid");
        return selId;
    }
}
