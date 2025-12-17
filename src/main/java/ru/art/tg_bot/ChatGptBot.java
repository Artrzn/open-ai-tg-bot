package ru.art.tg_bot;

import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.service.OpenAiService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class ChatGptBot extends TelegramLongPollingBot {

    private final static Logger LOGGER = LogManager.getLogger(ChatGptBot.class);

    private final OpenAiService openAi;
    private final long ownerUserId;
    private final String botUserName;
    private double temperature = 0.7;
    private Model actualModel = Model.GPT_5_2_CHAT;
    private boolean isUseContext;
    private final Function<Boolean, String> getContextState = b -> b ? "On" : "Off";
    private String actualRole = ChatMessageRole.USER.value();
    private final List<ChatMessage> savedMessages = new ArrayList<>();

    public ChatGptBot(OpenAiService openAi, long ownerUserId, String botToken, String botUserName) {
        super(botToken);
        this.openAi = openAi;
        this.ownerUserId = ownerUserId;
        this.botUserName = botUserName;
    }

    @Override
    public String getBotUsername() {
        return botUserName;
    }

    public void onUpdateReceived(Update update) {
        try {
            Message message = update.getMessage();
            CallbackQuery callbackQuery = update.getCallbackQuery();
            if (message != null) {
                User messageAuthor = message.getFrom();
                Long userId = messageAuthor.getId();
                if (ownerUserId == userId) {
                    String text = message.getText();
                    LOGGER.info("Accepted text message: \"{}\"", text);
                    if (message.isCommand() && text.equals("/choose_model")) {
                        changeModel();
                    } else if (message.isCommand() && text.equals("/choose_context_mode")) {
                        changeContextMode();
                    } else if (message.isCommand() && text.equals("/choose_role")) {
                        changeRole();
                    } else if (message.isCommand() && text.equals("/choose_temperature")) {
                        changeTemperature();
                    } else if (message.isCommand() && text.startsWith("/")) {
                        sendTextMessage(String.format("Не поддерживаемая команда: %s\n ヽ(°□° )ノ", text));
                    } else {
                        askOpenAi(text);
                    }
                } else {
                    LOGGER.info("Accepted request from unauthorized user: {}. Request: {}", userId, message.getText());
                    sendTextMessage("Это частная вечеринка\n ヽ(°□° )ノ user: " + userId);
                }
            } else if (callbackQuery != null) {
                String callbackData = callbackQuery.getData();
                if (callbackData.equals("true") || callbackData.equals("false")) {
                    boolean newState = Boolean.parseBoolean(callbackData);
                    if (isUseContext != newState) {
                        LOGGER.info("Context support state was change from: {} to: {}", getContextState.apply(isUseContext), getContextState.apply(newState));
                    }
                    LOGGER.info("Clear messages");
                    savedMessages.clear();
                    isUseContext = newState;
                } else if (callbackData.equals("system") || callbackData.equals("user")) {
                    if (!actualRole.equals(callbackData)) {
                        LOGGER.info("Role was change from: {} to: {}.", actualRole, callbackData);
                    }
                    actualRole = callbackData;
                } else if (callbackData.contains("Temp_")) {
                    temperature = Double.parseDouble(callbackData.replace("Temp_", ""));
                    LOGGER.info("Chosen temperature: {}.", temperature);
                } else {
                    actualModel = Model.getByName(callbackData);
                    LOGGER.info("Chosen model: {}.", actualModel.getName());
                }
                sendTextMessage(String.format("Режим контекста: %s. Роль: %s. Выбранная модель: %s%s", getContextState.apply(isUseContext),
                        actualRole, actualModel.getName(), actualModel == Model.GPT_5_MINI || actualModel == Model.GPT_5 || actualModel == Model.GPT_5_2_CHAT ? "" : String.format(" Температура: %s.", temperature)));
            }
        } catch (Throwable t) {
            LOGGER.error("Some error: ", t);
        }
    }

    private void changeModel() throws TelegramApiException {
        sendMessage(SendMessage.builder()
                .chatId(ownerUserId)
                .text("Выбери модель")
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(Arrays.asList(InlineKeyboardButton
                                                .builder()
                                                .text(Model.GPT_5_MINI.getName())
                                                .callbackData(Model.GPT_5_MINI.getName())
                                                .build(),
                                        InlineKeyboardButton
                                                .builder()
                                                .text(Model.GPT_5.getName())
                                                .callbackData(Model.GPT_5.getName())
                                                .build(),
                                        InlineKeyboardButton
                                                .builder()
                                                .text(Model.GPT_5_CHAT.getName())
                                                .callbackData(Model.GPT_5_CHAT.getName())
                                                .build(),
                                        InlineKeyboardButton
                                                .builder()
                                                .text(Model.GPT_5_2_CHAT.getName())
                                                .callbackData(Model.GPT_5_2_CHAT.getName())
                                                .build()
                                )
                        )
                        .build())
                .build());
    }

    private void changeContextMode() throws TelegramApiException {
        sendMessage(SendMessage.builder()
                .chatId(ownerUserId)
                .text("Выбор режима контекста")
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(Arrays.asList(InlineKeyboardButton
                                        .builder()
                                        .text("On")
                                        .callbackData("true")
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text("Off")
                                        .callbackData("false")
                                        .build())
                        )
                        .build())
                .build());
    }

    private void changeRole() throws TelegramApiException {
        sendMessage(SendMessage.builder()
                .chatId(ownerUserId)
                .text("Выбор роли")
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(Arrays.asList(InlineKeyboardButton
                                        .builder()
                                        .text(ChatMessageRole.SYSTEM.value())
                                        .callbackData(ChatMessageRole.SYSTEM.value())
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text(ChatMessageRole.USER.value())
                                        .callbackData(ChatMessageRole.USER.value())
                                        .build())
                        )
                        .build())
                .build());
    }

    private void changeTemperature() throws TelegramApiException {
        sendMessage(SendMessage.builder()
                .chatId(ownerUserId)
                .text("Выбор температуры")
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(Arrays.asList(
                                        InlineKeyboardButton
                                                .builder()
                                                .text("0.0")
                                                .callbackData("0.0")
                                                .build(),
                                        InlineKeyboardButton
                                                .builder()
                                                .text("0.1")
                                                .callbackData("Temp_0.1")
                                                .build(),
                                        InlineKeyboardButton
                                                .builder()
                                                .text("0.2")
                                                .callbackData("Temp_0.2")
                                                .build(),
                                        InlineKeyboardButton
                                                .builder()
                                                .text("0.3")
                                                .callbackData("Temp_0.3")
                                                .build(),
                                        InlineKeyboardButton
                                                .builder()
                                                .text("0.4")
                                                .callbackData("Temp_0.4")
                                                .build(),
                                        InlineKeyboardButton
                                                .builder()
                                                .text("0.5")
                                                .callbackData("Temp_0.5")
                                                .build()
                                )
                        )
                        .keyboardRow(Arrays.asList(
                                InlineKeyboardButton
                                        .builder()
                                        .text("0.6")
                                        .callbackData("Temp_0.6")
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text("0.7")
                                        .callbackData("Temp_0.7")
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text("0.8")
                                        .callbackData("Temp_0.8")
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text("0.9")
                                        .callbackData("Temp_0.9")
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text("1.0")
                                        .callbackData("Temp_1.0")
                                        .build()
                        ))
                        .build())
                .build());
    }

    private void askOpenAi(String ask) throws TelegramApiException {
        sendTextMessage(String.format("Жди... использована модель: %s. Режим контекста: %s. Роль: %s.%s", actualModel.getName(),
                getContextState.apply(isUseContext), actualRole, actualModel == Model.GPT_5_MINI || actualModel == Model.GPT_5 ? "" : String.format(" Температура: %s.", temperature)));

        List<ChatMessage> messages = isUseContext ? savedMessages : new ArrayList<>();
        ChatMessage userMessage = new ChatMessage(actualRole, ask);
        messages.add(userMessage);
        ChatCompletionRequest chatCompletionRequest = buildRequest(messages);
        StringBuilder answer = new StringBuilder();
        try {
            ChatCompletionResult chatCompletion = openAi.createChatCompletion(chatCompletionRequest);
            List<ChatCompletionChoice> choices = chatCompletion.getChoices();
            for (ChatCompletionChoice choice : choices) {
                ChatMessage assistantMessage = choice.getMessage();
                if (assistantMessage.getContent() != null) {
                    answer.append(choice.getMessage().getContent());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error while get answer from apenAi", e);
            sendTextMessage(String.format("Ошибка выполнения запроса к openAi. %s", e.getMessage()));
        }
        LOGGER.info("Accepted answer: {}", answer.toString());
        if (isUseContext) {
            messages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), answer.toString()));
        }
        sendTextMessage(answer.toString());
    }

    private ChatCompletionRequest buildRequest(List<ChatMessage> messages) {
        return ChatCompletionRequest
                .builder()
                .model(actualModel.getName())
                .messages(messages)
                .n(1)
                .temperature(actualModel == Model.GPT_5_MINI || actualModel == Model.GPT_5 || actualModel == Model.GPT_5_2_CHAT ? null : temperature)
                .stream(false)
                .build();
    }

    private void sendTextMessage(String text) throws TelegramApiException {
        String escapedText = escapeMarkdown(text);
        int length = escapedText.length();
        //todo экранирование и разметка не учтены
        if (length <= 4096) {
            sendMessage(SendMessage.builder()
                    .chatId(ownerUserId)
                    .parseMode(ParseMode.MARKDOWNV2)
                    .text(escapedText)
                    .build());
        } else {
            int index = 0;
            while (index < length) {
                if (length - (index + 4096) <= 0) {
                    sendMessage(SendMessage.builder()
                            .chatId(ownerUserId)
//                            .parseMode(ParseMode.MARKDOWNV2)
                            .text(escapedText.substring(index))
                            .build());
                } else {
                    sendMessage(SendMessage.builder()
                            .chatId(ownerUserId)
//                            .parseMode(ParseMode.MARKDOWNV2)
                            .text(escapedText.substring(index, index + 4096))
                            .build());
                }
                index += 4096;
            }
        }
    }

    private String escapeMarkdown(String text) {
        String[] specialChars = {"\\", "_", "*", "[", "]", "(", ")", "~", ">", "#", "+", "-", "=", "|", "{", "}", ".", "!"};
        for (String specialChar : specialChars) {
            text = text.replace(specialChar, "\\" + specialChar);
        }
        return text;
    }

    private void sendMessage(SendMessage message) throws TelegramApiException {
        execute(message);
    }
}