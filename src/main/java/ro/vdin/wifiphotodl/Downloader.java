package ro.vdin.wifiphotodl;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class Downloader {
    private static final Logger log = LoggerFactory.getLogger(Downloader.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private AppConfig appConfig; 

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
            String selection = range.boxed().map(i2 -> String.valueOf(i2)).collect(Collectors.joining(","));

            log.debug("selection=" + selection);

            File filename = new File(saveDir, String.format("images-%d-%d.zip", i, i + selectionSize - 1));
            if (filename.exists()) {
                throw new RuntimeException("File " + filename + " exists");
            }

            int fileBytes = downloadSingle(baseUrl, lib, selection, filename);
            if (isEmptyZip(fileBytes)) {
                log.info("Empty zip downloaded, stopping download.");
                break;
            }
        }
    }

    private boolean isEmptyZip(int fileBytes) {
        return fileBytes == 49;
    }

    private int downloadSingle(URI baseUrl, int lib, String selection, File filename) throws JsonParseException,
                                                                                      JsonMappingException,
                                                                                      IOException {
        var selectionId = startDownload(baseUrl, lib, selection);
        log.info("Selection ID: " + selectionId);

        pollUntilReadyToDownload(baseUrl, selectionId);
        return saveSelection(baseUrl, selectionId, filename);
    }

    private int saveSelection(URI baseUrl, String selectionId, File filename) throws IOException {
        // curl http://192.168.0.100:15555/zipdownload/KRWBHRTKDANRGMIM/images.zip

        String url = String.format("%s/zipdownload/%s/images.zip", baseUrl, selectionId);

        byte[] imageBytes = restTemplate.getForObject(url, byte[].class);
        Files.write(Paths.get(filename.getAbsolutePath()), imageBytes);
        log.info("Saved to: {}, {} bytes", filename, imageBytes.length);
        return imageBytes.length;
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

        String compressUri = String.format("%s/compressprogress%s?%s", url, randomString(), selId);

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

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);

        Boolean readyForDownload = (Boolean) responseMap.get("readyForDownload");
        return readyForDownload;
    }

    private String randomString() {
        // TODO: implement
        return "";
    }

    private String startDownload(URI baseUrl, int lib, String selection) throws IOException,
                                                                         JsonParseException,
                                                                         JsonMappingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON_UTF8));

        var formBody = new LinkedMultiValueMap<String, String>();
        formBody.add("lib", String.valueOf(lib));
        formBody.add("sel", selection);

        var request = new HttpEntity<MultiValueMap<String, String>>(formBody, headers);

        log.info("Request factory: " + restTemplate.getRequestFactory().getClass());

        log.info("Resolving url");

        baseUrl = baseUrl.resolve("startcompressing");

        log.info("Going at it!, url=" + baseUrl);
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, request, String.class);

        log.info("Response: " + response);

        @SuppressWarnings("unchecked")
        Map<String, Object> readValue = objectMapper.readValue(response.getBody(), Map.class);

        String selId = (String) readValue.get("selid");
        return selId;
    }
}
