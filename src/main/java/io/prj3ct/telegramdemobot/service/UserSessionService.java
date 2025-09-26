package io.prj3ct.telegramdemobot.service;

import io.prj3ct.telegramdemobot.dto.Cocktail;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserSessionService {

    private final Map<Long, List<Cocktail>> userLastSearchResult = new ConcurrentHashMap<>();

    public void saveUserSearchResult(long chatId, List<Cocktail> cocktails) {
        userLastSearchResult.put(chatId, cocktails);
    }

    public List<Cocktail> getUserSearchResult(long chatId) {
        return userLastSearchResult.get(chatId);
    }
}
