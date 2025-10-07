package io.prj3ct.telegramdemobot.service;

import io.prj3ct.telegramdemobot.dto.Cocktail;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserSessionService {

    public enum UserState {
        DEFAULT,
        AWAITING_INGREDIENT_SELECTION
    }

    private final Map<Long, List<Cocktail>> userLastSearchResult = new ConcurrentHashMap<>();
    private final Map<Long, UserState> userStates = new ConcurrentHashMap<>();

    public void saveUserSearchResult(long chatId, List<Cocktail> cocktails) {
        userLastSearchResult.put(chatId, cocktails);
    }

    public List<Cocktail> getUserSearchResult(long chatId) {
        return userLastSearchResult.get(chatId);
    }

    public UserState getUserState(long chatId) {
        return userStates.getOrDefault(chatId, UserState.DEFAULT);
    }

    public void setUserState(long chatId, UserState state) {
        userStates.put(chatId, state);
    }

    public void clearUserState(long chatId) {
        userStates.remove(chatId);
    }
}
