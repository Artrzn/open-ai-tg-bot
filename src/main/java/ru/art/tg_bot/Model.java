package ru.art.tg_bot;

public enum Model {
    GPT_5("gpt-5", false),
    GPT_5_2_CHAT("gpt-5.2-chat-latest", false),
    GPT_5_3_CHAT("gpt-5.3-chat-latest", false),
    GPT_5_4("gpt-5.4", true);

    private final String modelName;
    private final boolean temperatureSupport;

    Model(String modelName, boolean temperatureSupport) {
        this.modelName = modelName;
        this.temperatureSupport = temperatureSupport;
    }

    public String getName() {
        return modelName;
    }

    public boolean isTemperatureSupport() {
        return temperatureSupport;
    }

    static Model getByName(String modelName) {
        switch (modelName) {
            case "gpt-5":
                return GPT_5;
            case "gpt-5.2-chat-latest":
                return GPT_5_2_CHAT;
            case "gpt-5.3-chat-latest":
                return GPT_5_3_CHAT;
            case "gpt-5.4":
                return GPT_5_4;
            default:
                return Model.valueOf(modelName);
        }
    }
}