package com.bibliotech.model;

import java.time.LocalDate;

public class Reservation {

    private Integer id;
    private Book book;
    private Member member;
    private LocalDate reservationDate;
    private String status; // ACTIVE, ANNULEE, HONOREE...

    public Reservation(Integer id, Book book, Member member,
                       LocalDate reservationDate, String status) {
        this.id = id;
        this.book = book;
        this.member = member;
        this.reservationDate = reservationDate;
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public Member getMember() {
        return member;
    }

    public LocalDate getReservationDate() {
        return reservationDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}