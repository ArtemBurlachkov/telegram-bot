package io.prj3ct.telegramdemobot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class ImageService {

    private final RestTemplate restTemplate;

    public ImageService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public byte[] downloadImage(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, null, byte[].class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Successfully downloaded image from URL: {}", url);
                return response.getBody();
            } else {
                log.warn("Failed to download image from URL: {}. Status: {}", url, response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Error downloading image from URL {}: {}", url, e.getMessage());
            return null;
        }
    }
}
