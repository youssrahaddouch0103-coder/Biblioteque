package com.bibliotech;

import com.bibliotech.model.Book;
import com.bibliotech.model.Member;
import com.bibliotech.model.Reservation;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReservationsController {

    @FXML
    private TableView<Reservation> reservationsTable;

    @FXML
    private TableColumn<Reservation, Integer> colId;
    @FXML
    private TableColumn<Reservation, String> colBook;
    @FXML
    private TableColumn<Reservation, String> colMember;
    @FXML
    private TableColumn<Reservation, String> colDate;
    @FXML
    private TableColumn<Reservation, String> colStatus;

    @FXML
    private TextField bookIdField;
    @FXML
    private TextField memberIdField;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> statusFilterCombo;

    @FXML
    private Label statusLabel;

    private final ObservableList<Reservation> allReservations = FXCollections.observableArrayList();
    private final ObservableList<Reservation> filteredReservations = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        // Configuration des colonnes
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colBook.setCellValueFactory(data -> Bindings.createObjectBinding(
                () -> data.getValue().getBook() != null ? data.getValue().getBook().getTitle() : "N/A"
        ));
        colMember.setCellValueFactory(data -> Bindings.createObjectBinding(
                () -> data.getValue().getMember() != null ? data.getValue().getMember().getName() : "N/A"
        ));
        colDate.setCellValueFactory(data -> Bindings.createObjectBinding(
                () -> data.getValue().getReservationDate() != null ?
                        data.getValue().getReservationDate().toString() : "N/A"
        ));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Configuration du filtre de statut
        statusFilterCombo.getItems().addAll("TOUS", "ACTIVE", "EN_ATTENTE", "CANCELLED", "HONOREE");
        statusFilterCombo.setValue("TOUS");

        // Listeners pour les filtres
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            applyFilters();
        });

        statusFilterCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            applyFilters();
        });

        reservationsTable.setItems(filteredReservations);
        loadReservations();
    }

    private void loadReservations() {
        allReservations.clear();

        String sql = "SELECT r.id, r.reservation_date, r.status," +
                "       b.id AS b_id, b.title, b.author, b.isbn, b.category," +
                "       m.id AS m_id, m.name, m.email " +
                "FROM reservations r " +
                "LEFT JOIN books b ON r.book_id = b.id " +
                "LEFT JOIN members m ON r.member_id = m.id " +
                "ORDER BY r.reservation_date DESC";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Book book = new Book(
                        rs.getInt("b_id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("isbn"),
                        rs.getString("category")
                );
                Member member = new Member(
                        rs.getInt("m_id"),
                        rs.getString("name"),
                        rs.getString("email")
                );

                String dateStr = rs.getString("reservation_date");
                LocalDate date = dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now();

                String status = rs.getString("status");
                if (status == null) status = "ACTIVE";

                Reservation reservation = new Reservation(
                        rs.getInt("id"),
                        book,
                        member,
                        date,
                        status
                );
                allReservations.add(reservation);
            }

            updateStatus("Chargé: " + allReservations.size() + " réservations", "green");
            applyFilters();

        } catch (SQLException e) {
            e.printStackTrace();
            updateStatus("Erreur de chargement: " + e.getMessage(), "red");
        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("Erreur de format de données", "red");
        }
    }

    private void applyFilters() {
        filteredReservations.clear();

        String searchText = searchField.getText().toLowerCase();
        String statusFilter = statusFilterCombo.getValue();

        for (Reservation reservation : allReservations) {
            // Filtre par statut
            if (!statusFilter.equals("TOUS") && !reservation.getStatus().equals(statusFilter)) {
                continue;
            }

            // Filtre par recherche
            boolean matchesSearch = searchText.isEmpty() ||
                    (reservation.getBook() != null && reservation.getBook().getTitle().toLowerCase().contains(searchText)) ||
                    (reservation.getMember() != null && reservation.getMember().getName().toLowerCase().contains(searchText)) ||
                    reservation.getStatus().toLowerCase().contains(searchText);

            if (matchesSearch) {
                filteredReservations.add(reservation);
            }
        }

        updateStatus("Affichage: " + filteredReservations.size() + " réservation(s)", "blue");
    }

    @FXML
    private void onCreateReservation() {
        String bookIdText = bookIdField.getText().trim();
        String memberIdText = memberIdField.getText().trim();

        if (bookIdText.isEmpty() || memberIdText.isEmpty()) {
            updateStatus("Veuillez entrer les IDs livre et adhérent", "red");
            return;
        }

        try {
            int bookId = Integer.parseInt(bookIdText);
            int memberId = Integer.parseInt(memberIdText);

            // Vérifier si le livre existe
            if (!bookExists(bookId)) {
                updateStatus("Livre non trouvé avec ID: " + bookId, "red");
                return;
            }

            // Vérifier si le membre existe
            if (!memberExists(memberId)) {
                updateStatus("Membre non trouvé avec ID: " + memberId, "red");
                return;
            }

            // Vérifier si l'utilisateur a déjà une réservation active pour ce livre
            if (hasActiveReservation(bookId, memberId)) {
                updateStatus("Ce membre a déjà une réservation active pour ce livre.", "orange");
                return;
            }

            // Vérifier si le livre est disponible
            boolean isBookAvailable = isBookAvailable(bookId);
            String status = isBookAvailable ? "ACTIVE" : "EN_ATTENTE";

            // Créer la réservation
            String sql = "INSERT INTO reservations(book_id, member_id, reservation_date, status) " +
                    "VALUES (?, ?, ?, ?)";

            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, bookId);
                ps.setInt(2, memberId);
                ps.setString(3, LocalDate.now().toString());
                ps.setString(4, status);
                ps.executeUpdate();

                // Message de confirmation
                String message = isBookAvailable ?
                        "Réservation créée (livre disponible)" :
                        "Réservation créée (en attente)";
                updateStatus(message, "green");
            }

            // Rafraîchir et nettoyer
            loadReservations();
            bookIdField.clear();
            memberIdField.clear();

        } catch (NumberFormatException e) {
            updateStatus("Les IDs doivent être des nombres valides", "red");
        } catch (SQLException e) {
            e.printStackTrace();
            updateStatus("Erreur SQL: " + e.getMessage(), "red");
        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("Erreur: " + e.getMessage(), "red");
        }
    }

    @FXML
    private void onCancelSelected() {
        Reservation selected = reservationsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            updateStatus("Sélectionnez une réservation à annuler", "orange");
            return;
        }

        // Confirmation
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation d'annulation");
        alert.setHeaderText("Annuler la réservation");
        alert.setContentText("Êtes-vous sûr de vouloir annuler cette réservation ?\n" +
                "Livre: " + selected.getBook().getTitle() + "\n" +
                "Adhérent: " + selected.getMember().getName());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                String sql = "UPDATE reservations SET status = 'CANCELLED' WHERE id = ?";

                try (Connection conn = Database.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {

                    ps.setInt(1, selected.getId());
                    int rowsAffected = ps.executeUpdate();

                    if (rowsAffected > 0) {
                        updateStatus("Réservation annulée avec succès", "green");
                        loadReservations();
                    } else {
                        updateStatus("Aucune réservation trouvée", "orange");
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
                updateStatus("Erreur lors de l'annulation: " + e.getMessage(), "red");
            }
        }
    }

    @FXML
    private void onMarkAsHonored() {
        Reservation selected = reservationsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            updateStatus("Sélectionnez une réservation", "orange");
            return;
        }

        if (selected.getStatus().equals("HONOREE")) {
            updateStatus("Cette réservation est déjà honorée", "orange");
            return;
        }

        try {
            String sql = "UPDATE reservations SET status = 'HONOREE' WHERE id = ?";

            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, selected.getId());
                int rowsAffected = ps.executeUpdate();

                if (rowsAffected > 0) {
                    updateStatus("Réservation marquée comme honorée", "green");
                    loadReservations();
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            updateStatus("Erreur: " + e.getMessage(), "red");
        }
    }

    @FXML
    private void onRefresh() {
        loadReservations();
        searchField.clear();
        statusFilterCombo.setValue("TOUS");
        updateStatus("Liste rafraîchie", "blue");
    }

    // ========= MÉTHODES UTILITAIRES =========

    private boolean bookExists(int bookId) {
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT COUNT(*) FROM books WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean memberExists(int memberId) {
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT COUNT(*) FROM members WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, memberId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean hasActiveReservation(int bookId, int memberId) {
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT COUNT(*) FROM reservations " +
                    "WHERE book_id = ? AND member_id = ? AND status IN ('ACTIVE', 'EN_ATTENTE')";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, bookId);
                ps.setInt(2, memberId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isBookAvailable(int bookId) {
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT COUNT(*) FROM loans WHERE book_id = ? AND return_date IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) == 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void updateStatus(String message, String color) {
        statusLabel.setText(message);

        switch (color.toLowerCase()) {
            case "green":
                statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                break;
            case "red":
                statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                break;
            case "orange":
                statusLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                break;
            case "blue":
                statusLabel.setStyle("-fx-text-fill: blue; -fx-font-weight: bold;");
                break;
            default:
                statusLabel.setStyle("-fx-text-fill: black;");
        }
    }
}