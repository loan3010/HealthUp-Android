package com.example.models;

import java.util.Arrays;
import java.util.List;

/**
 * FAQ entry used by both the in-app FAQ screen and the local chatbot matcher.
 */
public class FAQ {

    private String id;
    private String question;
    private String answer;
    private List<String> keywords;
    private String category;
    private boolean expanded;

    public FAQ() {
    }

    /** Chatbot FAQ with keyword matching. */
    public FAQ(String id, String question, String answer, String... keywords) {
        this.id = id;
        this.question = question;
        this.answer = answer;
        this.keywords = Arrays.asList(keywords);
    }

    /** UI FAQ loaded from Firestore or static lists. */
    public FAQ(String question, String answer, String category) {
        this.question = question;
        this.answer = answer;
        this.category = category;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
}
