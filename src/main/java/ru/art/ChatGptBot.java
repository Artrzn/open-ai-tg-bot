package ru.art;

import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ChatGptBot extends TelegramLongPollingBot {

    private final static Logger LOGGER = LogManager.getLogger(ChatGptBot.class);

    private OpenAiService service;
    private long acceptedUserId;
    private String token;
    private String userName;

    public ChatGptBot(String openAiKey, long acceptedUserId, String token, String userName) {
        this.service = new OpenAiService(openAiKey, Duration.ofSeconds(60));
        this.acceptedUserId = acceptedUserId;
        this.token = token;
        this.userName = userName;
    }

    @Override
    public String getBotUsername() {
        return userName;
    }

    @Override
    public String getBotToken() {
        return token;
    }


    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        User messageAuthor = message.getFrom();
        Long userId = messageAuthor.getId();
        if (acceptedUserId == userId) {
            String question = message.getText();
            LOGGER.info("Accepted question: \"{}\"", question);
            SendMessage waitMessage = new SendMessage();
            waitMessage.setChatId(String.valueOf(acceptedUserId));
            waitMessage.setText("Жди...");
            sendMessage(waitMessage);
            sendAnswer(question);
        } else {
            LOGGER.info("Accepted request from unauthorized user: {}. Request: {}.", userId, message.getText());
            SendMessage unauthorizedMessage = new SendMessage();
            unauthorizedMessage.setChatId(String.valueOf(acceptedUserId));
            unauthorizedMessage.setText("Это частная вечеринка.");
            sendMessage(unauthorizedMessage);
        }
    }

    private void sendAnswer(String question) {
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), question);
        messages.add(systemMessage);
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("gpt-3.5-turbo")
                .messages(messages)
                .n(1)
                .logitBias(new HashMap<>())
                .build();
        StringBuilder answer = new StringBuilder();
        service.streamChatCompletion(chatCompletionRequest)
                .doOnError(throwable -> {
                    LOGGER.error("Error while get answer from apenAi.", throwable);
                    SendMessage errorMessage = new SendMessage();
                    errorMessage.setChatId(String.valueOf(acceptedUserId));
                    errorMessage.setText(String.format("Ошибка выполнения запроса к openAi. %s", throwable.getMessage()));
                    sendMessage(errorMessage);
                })
                .blockingForEach(chatCompletionChunk -> {
                    List<ChatCompletionChoice> choices = chatCompletionChunk.getChoices();
                    for (ChatCompletionChoice choice : choices) {
                        if (choice.getMessage().getContent() != null) {
                            answer.append(choice.getMessage().getContent());
                        }
                    }
                });
        SendMessage answerMessage = new SendMessage();
        answerMessage.setChatId(String.valueOf(acceptedUserId));
        answerMessage.setText(answer.toString());
        sendMessage(answerMessage);
    }

    private void sendMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

}
