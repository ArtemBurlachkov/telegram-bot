package io.prj3ct.telegramdemobot.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.prj3ct.telegramdemobot.config.TranslationConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final TranslationConfig config;
    private final RestTemplate restTemplate = new RestTemplate();

    @Data
    private static class TranslationRequest {
        private String q;
        private String source;
        private String target;
        private final String format = "text";

        TranslationRequest(String q, String source, String target) {
            this.q = q;
            this.source = source;
            this.target = target;
        }
    }

    public String translate(String textToTranslate, String targetLang) {
        // LibreTranslate умеет автоматически определять исходный язык
        return translate(textToTranslate, "auto", targetLang);
    }

    public String translate(String textToTranslate, String sourceLang, String targetLang) {
        if (!config.isEnabled() || textToTranslate == null || textToTranslate.isBlank()) {
            log.trace("Translation skipped (disabled or empty text).");
            return textToTranslate;
        }

        try {
            TranslationRequest request = new TranslationRequest(textToTranslate, sourceLang, targetLang);

            // Отправляем POST-запрос на локальный сервер LibreTranslate
            JsonNode response = restTemplate.postForObject(config.getUrl(), request, JsonNode.class);

            if (response != null && response.has("translatedText")) {
                String translated = response.get("translatedText").asText();
                log.info("Translated '{}' to '{}'", textToTranslate, translated);
                return translated;
            } else if (response != null && response.has("error")) {
                log.error("Error from LibreTranslate API: {}", response.get("error").asText());
                return textToTranslate; // В случае ошибки возвращаем исходный текст
            } else {
                log.error("Unexpected response from LibreTranslate API: {}", response);
                return textToTranslate; // В случае странного ответа возвращаем исходный текст
            }

        } catch (Exception e) {
            log.error("Failed to call LibreTranslate API for text: '{}'", textToTranslate, e);
            return textToTranslate; // В случае исключения возвращаем исходный текст
        }
    }
}
