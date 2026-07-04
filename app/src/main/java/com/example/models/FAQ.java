package com.example.models;

import java.util.Arrays;
import java.util.List;

/**
 * A frequently-asked-question entry used by the local chatbot.
 *
 * <p>{@link #keywords} are normalized (diacritic-insensitive) tokens the bot
 * uses to match a free-text question to this FAQ answer.</p>
 */
public class FAQ {

    private String id;
    private String question;
    private String answer;
    private List<String> keywords;

    public FAQ() {
    }

    public FAQ(String id, String question, String answer, String... keywords) {
        this.id = id;
        this.question = question;
        this.answer = answer;
        this.keywords = Arrays.asList(keywords);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
}
