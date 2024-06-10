package ru.art.config;

import com.theokanning.openai.service.OpenAiService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.art.ChatGptBot;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "tg.bot")
public class BotConfiguration {

    private String openAiApiKey;
    private long acceptedTgUserId;
    private String botToken;
    private String botUserName;

    public String getOpenAiApiKey() {
        return openAiApiKey;
    }

    public void setOpenAiApiKey(String openAiApiKey) {
        this.openAiApiKey = openAiApiKey;
    }

    public long getAcceptedTgUserId() {
        return acceptedTgUserId;
    }

    public void setAcceptedTgUserId(long acceptedTgUserId) {
        this.acceptedTgUserId = acceptedTgUserId;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getBotUserName() {
        return botUserName;
    }

    public void setBotUserName(String botUserName) {
        this.botUserName = botUserName;
    }

    @Bean
    public OpenAiService getOpenAi() {
        return new OpenAiService(openAiApiKey, Duration.ofSeconds(60));
    }

    @Bean
    public ChatGptBot getBot(OpenAiService openAiService) {
        return new ChatGptBot(openAiService, acceptedTgUserId, botToken, botUserName);
    }
}
