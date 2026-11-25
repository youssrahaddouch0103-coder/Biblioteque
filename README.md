# Bibliotech — Library Management Desktop App

Desktop application for managing a small library, built with **Java 17**, **JavaFX** and **SQLite**.
It has two spaces: an administrator panel to run the library, and a member space where readers follow their loans and reserve books.

## Features

**Administrator**
- Dashboard with quick access to every module
- Book catalogue: add, edit, delete, search and filter by category or availability
- Member accounts: create, update and delete members (with their loans)
- Loans: create a loan with a duration in days, mark it as returned, filter by status (*in progress*, *overdue*, *returned*), with overdue loans detected automatically
- Reservations: create and cancel reservations
- Change the administrator password

**Member**
- Sign up and log in
- See my loans and my reservations
- Reserve an available book

## Tech stack

| Layer | Technology |
|---|---|
| UI | JavaFX 22 (FXML views + CSS) |
| Language | Java 17 |
| Database | SQLite (via `sqlite-jdbc`), schema created automatically at startup |
| Build | Maven, `javafx-maven-plugin` |

## Project structure

```
src/main/java/com/bibliotech/
├── MainApp.java                 # entry point, scene navigation
├── Database.java                # SQLite connection and schema (admin, members, books, loans, reservations)
├── *Controller.java             # one controller per screen (login, catalogue, members, loans, reservations…)
└── model/                       # Book, Member, Loan, Reservation
src/main/resources/com/bibliotech/
├── *-view.fxml                  # screens
└── app.css                      # styling
```

## Getting started

Prerequisites: JDK 17+ and Maven.

```bash
git clone https://github.com/youssrahaddouch0103-coder/Biblioteque.git
cd Biblioteque
mvn clean javafx:run
```

On first launch the app creates `bibliotech.db` in the working directory, with a demo administrator account:
**username** `admin` / **password** `admin` (change it from the admin panel).

## Possible improvements

- Hash passwords (e.g. BCrypt) instead of storing them in plain text
- Add unit tests for the data-access layer
- Package the app as a native installer with `jpackage`

## Author

**Youssra Haddouch** — Computer Engineering student at ENSAM Meknès
[GitHub](https://github.com/youssrahaddouch0103-coder) · [LinkedIn](https://www.linkedin.com/in/youssra-haddouch-830565297)
