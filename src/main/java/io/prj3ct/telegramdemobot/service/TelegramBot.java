package io.prj3ct.telegramdemobot.service;

import io.prj3ct.telegramdemobot.config.BotConfig;
import io.prj3ct.telegramdemobot.dto.Cocktail;
import io.prj3ct.telegramdemobot.dto.CocktailDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final BotConfig botConfig;
    private final CocktailDBService cocktailDBService;
    private final Map<Long, List<Cocktail>> userLastSearchResult = new ConcurrentHashMap<>();

    private static final String START_MESSAGE = "Привет! Я бот для поиска коктейлей. " +
            "Просто отправь мне название ингредиента (например, 'водка'), " +
            "и я найду коктейли, в которых он есть.\n\n" +
            "Ты также можешь искать по нескольким ингредиентам, перечислив их через запятую (например, 'ром, мята, лайм').";

    public TelegramBot(BotConfig botConfig, CocktailDBService cocktailDBService, TranslationService translationService) {
        super(botConfig.getBotToken());
        this.botConfig = botConfig;
        this.cocktailDBService = cocktailDBService;
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.equals("/start")) {
                sendMessage(chatId, START_MESSAGE);
            } else {
                try {
                    Integer.parseInt(messageText);
                    handleNumberSelection(chatId, messageText);
                } catch (NumberFormatException e) {
                    handleIngredientSearch(chatId, messageText);
                }
            }
        }
    }

    private void handleIngredientSearch(long chatId, String messageText) {
        List<String> ingredients = Arrays.asList(messageText.split(",\\s*"));
        List<Cocktail> cocktails = cocktailDBService.findByMultipleIngredients(ingredients);

        if (cocktails.isEmpty()) {
            sendMessage(chatId, "К сожалению, по вашему запросу ничего не найдено. Попробуйте другие ингредиенты.");
        } else {
            userLastSearchResult.put(chatId, cocktails);
            String responseText = "Вот что я нашел:\n" +
                    IntStream.range(0, cocktails.size())
                            .mapToObj(i -> (i + 1) + ". " + cocktails.get(i).getName())
                            .collect(Collectors.joining("\n"));
            responseText += "\n\nОтправьте номер, чтобы получить рецепт.";
            sendMessage(chatId, responseText);
        }
    }

    private void handleNumberSelection(long chatId, String messageText) {
        List<Cocktail> lastResult = userLastSearchResult.get(chatId);
        if (lastResult == null || lastResult.isEmpty()) {
            sendMessage(chatId, "Сначала выполните поиск, чтобы я мог показать вам рецепт. Введите ингредиент.");
            return;
        }

        int choice;
        try {
            choice = Integer.parseInt(messageText);
            if (choice < 1 || choice > lastResult.size()) {
                sendMessage(chatId, "Пожалуйста, выберите номер из списка.");
                return;
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "Это не похоже на номер. Пожалуйста, выберите номер из списка.");
            return;
        }

        Cocktail selectedCocktail = lastResult.get(choice - 1);
        CocktailDetails details = cocktailDBService.findCocktailDetailsById(selectedCocktail.getId());

        if (details != null) {
            String recipe = "Название: " + details.getName() + "\n\n" +
                    "Ингредиенты:\n" + String.join("\n", details.getIngredients()) + "\n\n" +
                    "Инструкция:\n" + details.getInstructions();

            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(String.valueOf(chatId));
            sendPhoto.setPhoto(new InputFile(details.getImageUrl()));
            sendPhoto.setCaption(recipe);

            try {
                execute(sendPhoto);
            } catch (TelegramApiException e) {
                log.error("Failed to send photo for cocktail ID {}: {}", details.getId(), e.getMessage());
                // Если отправка фото не удалась, отправляем только текст
                sendMessage(chatId, recipe);
            }
        } else {
            sendMessage(chatId, "Не удалось получить детали рецепта. Попробуйте еще раз.");
        }
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error occurred: " + e.getMessage());
        }
    }
}
