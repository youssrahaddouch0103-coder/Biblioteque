package com.bibliotech;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    // Fichier SQLite créé dans le dossier de lancement de l'application
    private static final String URL = "jdbc:sqlite:bibliotech.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void init() {
        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {

            // Debug (facultatif)
            File f = new File("bibliotech.db");
            System.out.println("=== BASE UTILISÉE ===");
            System.out.println(f.getAbsolutePath());
            System.out.println("======================");

            // Active les clés étrangères
            st.execute("PRAGMA foreign_keys = ON");

            /* ==========================================================
               TABLE ADMIN + ADMIN PAR DÉFAUT
               ========================================================== */
            String sqlAdmin =
                    "CREATE TABLE IF NOT EXISTS admin (" +
                            "  id       INTEGER PRIMARY KEY CHECK(id = 1)," +
                            "  username TEXT NOT NULL," +
                            "  password TEXT NOT NULL" +
                            ")";
            st.execute(sqlAdmin);

            // Créer un admin par défaut s'il n'existe pas encore
            String insertDefaultAdmin =
                    "INSERT INTO admin (id, username, password) " +
                            "SELECT 1, 'admin', 'admin' " +
                            "WHERE NOT EXISTS (SELECT 1 FROM admin WHERE id = 1)";
            st.execute(insertDefaultAdmin);

            /* ==========================================================
               TABLE MEMBERS
               ========================================================== */
            String sqlMembers =
                    "CREATE TABLE IF NOT EXISTS members (" +
                            "  id       INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  name     TEXT NOT NULL," +
                            "  email    TEXT NOT NULL UNIQUE," +
                            "  password TEXT NOT NULL" +
                            ")";
            st.execute(sqlMembers);

            /* ==========================================================
               TABLE BOOKS
               ========================================================== */
            String sqlBooks =
                    "CREATE TABLE IF NOT EXISTS books (" +
                            "  id       INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  title    TEXT NOT NULL," +
                            "  author   TEXT NOT NULL," +
                            "  isbn     TEXT," +
                            "  category TEXT" +
                            ")";
            st.execute(sqlBooks);

            /* ==========================================================
               TABLE LOANS
               ========================================================== */
            String sqlLoans =
                    "CREATE TABLE IF NOT EXISTS loans (" +
                            "  id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  book_id     INTEGER NOT NULL," +
                            "  member_id   INTEGER NOT NULL," +
                            "  loan_date   TEXT NOT NULL," +
                            "  due_date    TEXT NOT NULL," +
                            "  return_date TEXT," +
                            "  FOREIGN KEY(book_id)   REFERENCES books(id)," +
                            "  FOREIGN KEY(member_id) REFERENCES members(id)" +
                            ")";
            st.execute(sqlLoans);

            /* ==========================================================
               TABLE RESERVATIONS
               ========================================================== */
            String sqlReservations =
                    "CREATE TABLE IF NOT EXISTS reservations (" +
                            "  id               INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  book_id          INTEGER NOT NULL," +
                            "  member_id        INTEGER NOT NULL," +
                            "  reservation_date TEXT NOT NULL," +
                            "  status           TEXT NOT NULL DEFAULT 'ACTIVE'," +
                            "  FOREIGN KEY(book_id)   REFERENCES books(id)," +
                            "  FOREIGN KEY(member_id) REFERENCES members(id)" +
                            ")";
            st.execute(sqlReservations);

            System.out.println("Base SQLite PROPRE initialisée.");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
