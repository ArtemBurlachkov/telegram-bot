package io.prj3ct.telegramdemobot.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class TranslationConfig {

    private static final Logger logger = LoggerFactory.getLogger(TranslationConfig.class);

    @Value("${libretranslate.url}")
    private String url;

    @Value("${translation.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void logUrl() {
        logger.info("--- LibreTranslate URL in use: {} ---", url);
    }
}
