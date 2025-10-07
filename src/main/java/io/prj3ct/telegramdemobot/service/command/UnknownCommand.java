package io.prj3ct.telegramdemobot.service.command;

import io.prj3ct.telegramdemobot.service.TelegramBot;
import org.telegram.telegrambots.meta.api.objects.Update;

public record UnknownCommand(TelegramBot telegramBot) implements Command {

    public static final String UNKNOWN_MESSAGE = "Не понимаю вас \uD83D\uDE1F, напишите /start";

    @Override
    public void execute(Update update) {
        telegramBot.sendMessage(update.getMessage().getChatId(), UNKNOWN_MESSAGE);
    }
}

