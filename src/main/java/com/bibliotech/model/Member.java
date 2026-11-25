package com.bibliotech.model;

import javafx.beans.property.*;

public class Member {

    private final IntegerProperty id;
    private final StringProperty name;
    private final StringProperty email;

    // Constructeur avec ID
    public Member(Integer id, String name, String email) {
        this.id = new SimpleIntegerProperty(id);
        this.name = new SimpleStringProperty(name);
        this.email = new SimpleStringProperty(email);
    }

    // Constructeur sans ID (pour création)
    public Member(String name, String email) {
        this(null, name, email);
    }

    // ========= GETTERS =========
    public int getId() {
        return id.get();
    }

    public String getName() {
        return name.get();
    }

    public String getEmail() {
        return email.get();
    }

    // ========= SETTERS =========
    public void setId(Integer id) {
        this.id.set(id);
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public void setEmail(String email) {
        this.email.set(email);
    }

    // ========= PROPERTIES POUR JAVAFX =========
    public IntegerProperty idProperty() {
        return id;
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty emailProperty() {
        return email;
    }

    // ========= MÉTHODE UTILITAIRE =========
    @Override
    public String toString() {
        return name.get() + " (" + email.get() + ")";
    }
}