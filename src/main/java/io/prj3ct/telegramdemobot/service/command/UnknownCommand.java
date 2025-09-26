package io.prj3ct.telegramdemobot.service.command;

import io.prj3ct.telegramdemobot.service.TelegramBot;
import org.telegram.telegrambots.meta.api.objects.Update;

public class UnknownCommand implements Command {

    private final TelegramBot telegramBot;

    public static final String UNKNOWN_MESSAGE = "Не понимаю вас \uD83D\uDE1F, напишите /start";

    public UnknownCommand(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    @Override
    public void execute(Update update) {
        telegramBot.sendMessage(update.getMessage().getChatId(), UNKNOWN_MESSAGE);
    }
}
