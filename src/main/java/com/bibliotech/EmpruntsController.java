package com.bibliotech;

import com.bibliotech.model.Book;
import com.bibliotech.model.Loan;
import com.bibliotech.model.Member;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class EmpruntsController {

    @FXML private TableView<Loan> loansTable;
    @FXML private TableColumn<Loan, Integer> colId;
    @FXML private TableColumn<Loan, String> colBook;
    @FXML private TableColumn<Loan, String> colMember;
    @FXML private TableColumn<Loan, String> colLoanDate;
    @FXML private TableColumn<Loan, String> colDueDate;
    @FXML private TableColumn<Loan, String> colReturnDate;
    @FXML private TableColumn<Loan, String> colStatus;
    @FXML private TableColumn<Loan, Void> colAction; // COLONNE POUR ACTION

    // CHAMPS SUPPRIMÉS: création d'emprunt
    // @FXML private TextField bookIdField;
    // @FXML private TextField memberIdField;
    // @FXML private TextField daysField;

    // NOUVEAU CHAMP pour suppression d'adhérent
    @FXML private TextField adminMemberIdField;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private Label statusLabel;

    private final ObservableList<Loan> allLoans = FXCollections.observableArrayList();
    private final ObservableList<Loan> filteredLoans = FXCollections.observableArrayList();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private boolean isAdmin = false;

    @FXML
    private void initialize() {
        // Vérifier si l'utilisateur est admin
        isAdmin = checkIfUserIsAdmin();

        // Configuration des colonnes
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colBook.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getBook() != null ?
                        data.getValue().getBook().getTitle() : "N/A"));
        colMember.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getMember() != null ?
                        data.getValue().getMember().getName() : "N/A"));
        colLoanDate.setCellValueFactory(data ->
                new SimpleStringProperty(formatDate(data.getValue().getLoanDate())));
        colDueDate.setCellValueFactory(data ->
                new SimpleStringProperty(formatDate(data.getValue().getDueDate())));
        colReturnDate.setCellValueFactory(data -> {
            LocalDate returnDate = data.getValue().getReturnDate();
            return new SimpleStringProperty(returnDate != null ?
                    formatDate(returnDate) : "Non rendu");
        });
        colStatus.setCellValueFactory(data -> {
            boolean isReturned = data.getValue().getReturnDate() != null;
            boolean isOverdue = !isReturned && LocalDate.now().isAfter(data.getValue().getDueDate());
            String status = isReturned ? "RENDU" : (isOverdue ? "EN RETARD" : "EN COURS");
            return new SimpleStringProperty(status);
        });

        // COLONNE ACTION UNIQUEMENT POUR ADMIN
        colAction.setCellFactory(param -> new TableCell<Loan, Void>() {
            private final Button deleteButton = new Button("Supprimer");

            {
                deleteButton.setOnAction(event -> {
                    Loan loan = getTableView().getItems().get(getIndex());
                    onDeleteLoan(loan);
                });

                // Cacher le bouton si l'utilisateur n'est pas admin
                deleteButton.setVisible(isAdmin);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || !isAdmin) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteButton);
                }
            }
        });

        // Configuration des filtres
        statusFilterCombo.getItems().addAll("TOUS", "EN COURS", "EN RETARD", "RENDU");
        statusFilterCombo.setValue("TOUS");

        // Listeners pour les filtres
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            applyFilters();
        });

        statusFilterCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            applyFilters();
        });

        // Listener pour la sélection
        loansTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> onRowSelected()
        );

        loansTable.setItems(filteredLoans);
        loadLoans();
    }

    // NOUVELLE MÉTHODE: Vérifier si l'utilisateur est admin
    private boolean checkIfUserIsAdmin() {
        // À ADAPTER selon votre système d'authentification
        // Pour l'instant, retourner true car c'est la vue admin
        return true;
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : "";
    }

    private void loadLoans() {
        allLoans.clear();

        String sql = "SELECT l.id, l.book_id, l.member_id, l.loan_date, l.due_date, l.return_date, " +
                "       b.title, b.author, b.isbn, b.category, " +
                "       m.name, m.email " +
                "FROM loans l " +
                "LEFT JOIN books b ON l.book_id = b.id " +
                "LEFT JOIN members m ON l.member_id = m.id " +
                "ORDER BY l.loan_date DESC";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Book book = new Book(
                        rs.getInt("book_id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("isbn"),
                        rs.getString("category")
                );
                Member member = new Member(
                        rs.getInt("member_id"),
                        rs.getString("name"),
                        rs.getString("email")
                );

                LocalDate loanDate = parseDate(rs.getString("loan_date"));
                LocalDate dueDate = parseDate(rs.getString("due_date"));
                LocalDate returnDate = parseDate(rs.getString("return_date"));

                Loan loan = new Loan(
                        rs.getInt("id"),
                        book,
                        member,
                        loanDate,
                        dueDate,
                        returnDate
                );
                allLoans.add(loan);
            }

            updateStatus("Chargé: " + allLoans.size() + " emprunts", "green");
            applyFilters();

        } catch (SQLException e) {
            e.printStackTrace();
            updateStatus("Erreur de chargement: " + e.getMessage(), "red");
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            // Essayer le format ISO (yyyy-MM-dd)
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            // Essayer le format français (dd/MM/yyyy)
            try {
                return LocalDate.parse(dateStr, DATE_FORMATTER);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private void applyFilters() {
        filteredLoans.clear();

        String searchText = searchField.getText().toLowerCase();
        String statusFilter = statusFilterCombo.getValue();

        for (Loan loan : allLoans) {
            // Filtre par statut
            boolean isReturned = loan.getReturnDate() != null;
            boolean isOverdue = !isReturned && LocalDate.now().isAfter(loan.getDueDate());
            String status = isReturned ? "RENDU" : (isOverdue ? "EN RETARD" : "EN COURS");

            if (!statusFilter.equals("TOUS") && !status.equals(statusFilter)) {
                continue;
            }

            // Filtre par recherche
            boolean matchesSearch = searchText.isEmpty() ||
                    (loan.getBook() != null && loan.getBook().getTitle().toLowerCase().contains(searchText)) ||
                    (loan.getMember() != null && loan.getMember().getName().toLowerCase().contains(searchText)) ||
                    loan.getId().toString().contains(searchText);

            if (matchesSearch) {
                filteredLoans.add(loan);
            }
        }

        updateStatus("Affichage: " + filteredLoans.size() + " emprunt(s)", "blue");
    }

    private void onRowSelected() {
        Loan selected = loansTable.getSelectionModel().getSelectedItem();
        if (selected != null && adminMemberIdField != null) {
            // Remplir seulement le champ admin avec l'ID de l'adhérent
            adminMemberIdField.setText(String.valueOf(selected.getMember().getId()));
        }
    }

    // -------- MÉTHODES SUPPRIMÉES: Création d'emprunt --------
    // @FXML private void onCreateLoan() { ... }
    // @FXML private void onClearFields() { ... }

    // -------- Marquer un emprunt comme rendu --------
    @FXML
    private void onMarkReturned() {
        Loan selected = loansTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            updateStatus("Sélectionnez un emprunt à marquer comme rendu", "orange");
            return;
        }

        if (selected.getReturnDate() != null) {
            updateStatus("Cet emprunt est déjà marqué comme rendu", "orange");
            return;
        }

        LocalDate now = LocalDate.now();

        // Confirmation
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation de retour");
        alert.setHeaderText("Marquer comme rendu");
        alert.setContentText("Êtes-vous sûr de vouloir marquer cet emprunt comme rendu ?\n" +
                "Livre: " + selected.getBook().getTitle() + "\n" +
                "Retour le: " + formatDate(now));

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Connection conn = Database.getConnection()) {
                // Mettre à jour l'emprunt
                String sql = "UPDATE loans SET return_date = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, now.toString());
                    ps.setInt(2, selected.getId());
                    ps.executeUpdate();
                }

                // Traiter les réservations pour ce livre
                processReservationsForBook(conn, selected.getBook().getId(), selected.getBook().getTitle());

                updateStatus("Emprunt marqué comme rendu", "green");
                loadLoans();

            } catch (SQLException e) {
                e.printStackTrace();
                updateStatus("Erreur lors de la mise à jour: " + e.getMessage(), "red");
            }
        }
    }

    // -------- Supprimer un emprunt (Admin seulement) --------
    private void onDeleteLoan(Loan loan) {
        if (!isAdmin) {
            updateStatus("Action non autorisée. Admin seulement.", "red");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation de suppression");
        alert.setHeaderText("Supprimer l'emprunt");
        alert.setContentText("Êtes-vous sûr de vouloir supprimer cet emprunt ?\n" +
                "ID: " + loan.getId() + "\n" +
                "Livre: " + loan.getBook().getTitle() + "\n" +
                "Adhérent: " + loan.getMember().getName() + "\n\n" +
                "Cette action est irréversible !");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Connection conn = Database.getConnection()) {
                String sql = "DELETE FROM loans WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, loan.getId());
                    ps.executeUpdate();
                }

                updateStatus("Emprunt #" + loan.getId() + " supprimé avec succès", "green");
                loadLoans();

            } catch (SQLException e) {
                e.printStackTrace();
                updateStatus("Erreur lors de la suppression: " + e.getMessage(), "red");
            }
        }
    }

    // -------- Supprimer un adhérent avec tous ses emprunts (Admin seulement) --------
    @FXML
    private void onDeleteMemberWithLoans() {
        if (!isAdmin) {
            updateStatus("Action non autorisée. Admin seulement.", "red");
            return;
        }

        // CORRECTION: utiliser adminMemberIdField au lieu de memberIdField
        String memberIdText = adminMemberIdField.getText().trim();
        if (memberIdText.isEmpty()) {
            updateStatus("Entrez l'ID de l'adhérent à supprimer", "orange");
            return;
        }

        try {
            int memberId = Integer.parseInt(memberIdText);

            // Vérifier si l'adhérent existe
            if (!memberExists(memberId)) {
                updateStatus("Adhérent introuvable (ID: " + memberId + ")", "red");
                return;
            }

            // Vérifier si l'adhérent a des emprunts en cours
            if (hasActiveLoans(memberId)) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("ATTENTION - Emprunts en cours");
                alert.setHeaderText("L'adhérent a des emprunts en cours");
                alert.setContentText("Cet adhérent a des emprunts non rendus.\n" +
                        "Voulez-vous quand même supprimer ?\n\n" +
                        "Cela supprimera également tous ses emprunts (historique inclus).");

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) {
                    updateStatus("Suppression annulée", "blue");
                    return;
                }
            }

            Alert finalAlert = new Alert(Alert.AlertType.CONFIRMATION);
            finalAlert.setTitle("Confirmation de suppression");
            finalAlert.setHeaderText("Supprimer l'adhérent et tous ses emprunts");
            finalAlert.setContentText("Êtes-vous sûr de vouloir supprimer cet adhérent ?\n" +
                    "ID: " + memberId + "\n\n" +
                    "Cela supprimera :\n" +
                    "• L'adhérent\n" +
                    "• Tous ses emprunts (historique)\n" +
                    "• Ses réservations\n\n" +
                    "Cette action est irréversible !");

            Optional<ButtonType> finalResult = finalAlert.showAndWait();
            if (finalResult.isPresent() && finalResult.get() == ButtonType.OK) {
                deleteMemberWithAllData(memberId);
            }

        } catch (NumberFormatException e) {
            updateStatus("L'ID doit être un nombre valide", "red");
        }
    }

    // -------- Méthode pour supprimer un adhérent et toutes ses données --------
    private void deleteMemberWithAllData(int memberId) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false); // Début de transaction

            try {
                // 1. Supprimer les réservations de l'adhérent
                String deleteReservations = "DELETE FROM reservations WHERE member_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteReservations)) {
                    ps.setInt(1, memberId);
                    ps.executeUpdate();
                }

                // 2. Supprimer les emprunts de l'adhérent
                String deleteLoans = "DELETE FROM loans WHERE member_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteLoans)) {
                    ps.setInt(1, memberId);
                    int loansDeleted = ps.executeUpdate();
                    updateStatus("Emprunts supprimés: " + loansDeleted, "blue");
                }

                // 3. Supprimer l'adhérent
                String deleteMember = "DELETE FROM members WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteMember)) {
                    ps.setInt(1, memberId);
                    ps.executeUpdate();
                }

                conn.commit(); // Valider la transaction
                updateStatus("Adhérent #" + memberId + " et toutes ses données supprimés avec succès", "green");
                loadLoans();

            } catch (SQLException e) {
                conn.rollback(); // Annuler en cas d'erreur
                throw e;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            updateStatus("Erreur lors de la suppression: " + e.getMessage(), "red");
        }
    }

    // -------- Vérifier si l'adhérent a des emprunts en cours --------
    private boolean hasActiveLoans(int memberId) {
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT COUNT(*) FROM loans WHERE member_id = ? AND return_date IS NULL";
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

    // -------- Méthode onClearFields modifiée --------
    @FXML
    private void onClearFields() {
        // Nettoyer seulement le champ admin
        if (adminMemberIdField != null) {
            adminMemberIdField.clear();
        }
        loansTable.getSelectionModel().clearSelection();
        updateStatus("Champs nettoyés", "blue");
    }

    @FXML
    private void onRefresh() {
        loadLoans();
        searchField.clear();
        statusFilterCombo.setValue("TOUS");
        // Nettoyer aussi le champ admin
        if (adminMemberIdField != null) {
            adminMemberIdField.clear();
        }
        updateStatus("Liste rafraîchie", "blue");
    }

    private void processReservationsForBook(Connection conn, int bookId, String bookTitle) throws SQLException {
        // Trouver la réservation la plus ancienne en attente pour ce livre
        String sqlFind = "SELECT r.id, m.name, m.email " +
                "FROM reservations r " +
                "JOIN members m ON r.member_id = m.id " +
                "WHERE r.book_id = ? AND r.status IN ('EN_ATTENTE', 'ACTIVE') " +
                "ORDER BY r.reservation_date ASC LIMIT 1";

        Integer reservationId = null;
        String memberName = null;
        String memberEmail = null;

        try (PreparedStatement ps = conn.prepareStatement(sqlFind)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    reservationId = rs.getInt("id");
                    memberName = rs.getString("name");
                    memberEmail = rs.getString("email");
                }
            }
        }

        if (reservationId != null) {
            // Mettre à jour le statut de la réservation
            String sqlUpdate = "UPDATE reservations SET status = 'DISPONIBLE' WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
                ps.setInt(1, reservationId);
                ps.executeUpdate();
            }

            // Notification à l'admin
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Livre disponible pour réservation");
            alert.setHeaderText("Réservation à traiter");
            alert.setContentText("Le livre \"" + bookTitle + "\" est maintenant disponible.\n" +
                    "Il est réservé par: " + memberName + "\n" +
                    "Email: " + memberEmail + "\n\n" +
                    "Merci de contacter l'adhérent.");
            alert.showAndWait();
        }
    }

    private void updateReservationStatus(Connection conn, int bookId, String status) throws SQLException {
        String sql = "UPDATE reservations SET status = ? WHERE book_id = ? AND status IN ('ACTIVE', 'DISPONIBLE')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, bookId);
            ps.executeUpdate();
        }
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

    private boolean isBookBorrowed(int bookId) {
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT COUNT(*) FROM loans WHERE book_id = ? AND return_date IS NULL";
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