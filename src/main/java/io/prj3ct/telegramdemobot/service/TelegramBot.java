package io.prj3ct.telegramdemobot.service;

import io.prj3ct.telegramdemobot.config.BotConfig;
import io.prj3ct.telegramdemobot.dto.CocktailDetails;
import io.prj3ct.telegramdemobot.service.command.CommandDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final BotConfig botConfig;
    private final CommandDispatcher commandDispatcher;

    public TelegramBot(BotConfig botConfig, @Lazy CommandDispatcher commandDispatcher) {
        super(botConfig.getToken());
        this.botConfig = botConfig;
        this.commandDispatcher = commandDispatcher;
        setBotCommands();
    }

    @Override
    public String getBotUsername() {
        return botConfig.getName();
    }

    @Override
    public void onUpdateReceived(Update update) {
        commandDispatcher.dispatch(update);
    }
    private void setBotCommands() {
        List<BotCommand> commands = new ArrayList<>();
        commands.add(new BotCommand("/start", "начать работу с ботом"));
        commands.add(new BotCommand("/search", "поиск коктейля по названию"));
        try{
            this.execute(new SetMyCommands(commands, new BotCommandScopeDefault(),null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: {}", e.getMessage());
        }
    }

    public void sendCocktailDetails(long chatId, CocktailDetails details) {
        String recipe = "Название: " + details.getName() + "\n\n" +
                "Ингредиенты:\n" + String.join("\n", details.getIngredients()) + "\n\n" +
                "Инструкция:\n" + details.getInstructions();

        if (details.getImage() != null && details.getImage().length > 0) {
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(String.valueOf(chatId));
            sendPhoto.setPhoto(new InputFile(new ByteArrayInputStream(details.getImage()), "photo.jpg"));
            sendPhoto.setCaption(recipe);

            try {
                execute(sendPhoto);
            } catch (TelegramApiException e) {
                log.error("Failed to send photo for cocktail ID {}: {}", details.getId(), e.getMessage());
                sendMessage(chatId, recipe);
            }
        } else {
            sendMessage(chatId, recipe);
        }
    }

    public void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error occurred: {}", e.getMessage());
        }
    }
}
