package com.smartlab.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartlab.agent")
public class AgentProperties {
    private boolean enabled = true;
    private String provider = "openai-compatible";
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private int maxRounds = 12;
    private int validateRetries = 3;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public int getValidateRetries() {
        return validateRetries;
    }

    public void setValidateRetries(int validateRetries) {
        this.validateRetries = validateRetries;
    }

    public boolean llmConfigured() {
        return enabled
                && notBlank(baseUrl)
                && notBlank(apiKey)
                && notBlank(model);
    }

    public String missingConfigurationMessage() {
        if (!enabled) return "工作流生成代理未启用";
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (!notBlank(baseUrl)) missing.add("SMARTLAB_AGENT_BASE_URL");
        if (!notBlank(apiKey)) missing.add("SMARTLAB_AGENT_API_KEY");
        if (!notBlank(model)) missing.add("SMARTLAB_AGENT_MODEL");
        return "未配置大模型接入：" + String.join("、", missing);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
