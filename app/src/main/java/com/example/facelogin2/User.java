package com.example.facelogin2;

import java.io.Serializable;

public class User implements Serializable {
    private String name;
    private String cardId;
    private String similarity;
    public User() {
    }

    public User(String name, String cardId, String similarity) {
        this.name = name;
        this.cardId = cardId;
        this.similarity = similarity;
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getCardId() {
        return cardId;
    }

    public String getSimilarity() {
        return similarity;
    }

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public void setDepartment(String similarity) {
        this.similarity = similarity;
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                ", cardId='" + cardId + '\'' +
                ", similarity='" + similarity + '\'' +
                '}';
    }
}
