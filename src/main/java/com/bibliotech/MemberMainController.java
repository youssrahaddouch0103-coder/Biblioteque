package com.bibliotech;

import com.bibliotech.model.Book;
import com.bibliotech.model.Loan;
import com.bibliotech.model.Member;
import com.bibliotech.model.Reservation;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

public class MemberMainController {

    @FXML private Label welcomeLabel;

    // -------- Catalogue --------
    @FXML private TableView<Book> catalogueTable;
    @FXML private TableColumn<Book, Integer> catColId;
    @FXML private TableColumn<Book, String> catColTitle;
    @FXML private TableColumn<Book, String> catColAuthor;
    @FXML private TableColumn<Book, String> catColIsbn;
    @FXML private TableColumn<Book, String> catColCategory;

    // Recherche / filtres
    @FXML private TextField searchTitleField;
    @FXML private TextField searchAuthorField;
    @FXML private TextField searchIsbnField;
    @FXML private TextField searchCategoryField;

    @FXML private ToggleGroup filterGroup;
    @FXML private RadioButton filterAllRadio;
    @FXML private RadioButton filterAvailableRadio;
    @FXML private RadioButton filterLoanedRadio;
    @FXML private RadioButton filterReservableRadio;

    @FXML private Label reservationMessageLabel;

    // -------- Mes emprunts --------
    @FXML private TableView<Loan> loansTable;
    @FXML private TableColumn<Loan, Integer> loanColId;
    @FXML private TableColumn<Loan, String> loanColBook;
    @FXML private TableColumn<Loan, String> loanColLoanDate;
    @FXML private TableColumn<Loan, String> loanColDueDate;
    @FXML private TableColumn<Loan, String> loanColReturnDate;

    // -------- Mes réservations --------
    @FXML private TableView<Reservation> reservationsTable;
    @FXML private TableColumn<Reservation, Integer> resColId;
    @FXML private TableColumn<Reservation, String> resColBook;
    @FXML private TableColumn<Reservation, String> resColDate;
    @FXML private TableColumn<Reservation, String> resColStatus;

    // NOUVEAU: Champ pour la durée d'emprunt
    @FXML private TextField loanDurationField;
    @FXML private Label loanMessageLabel;

    private Member currentMember;

    @FXML
    private void initialize() {
        if (welcomeLabel != null && currentMember == null) {
            welcomeLabel.setText("Bienvenue !");
        }

        // Config colonnes Catalogue
        if (catalogueTable != null) {
            catColId.setCellValueFactory(new PropertyValueFactory<>("id"));
            catColTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
            catColAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
            catColIsbn.setCellValueFactory(new PropertyValueFactory<>("isbn"));
            catColCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        }

        // Config colonnes Emprunts
        if (loansTable != null) {
            loanColId.setCellValueFactory(new PropertyValueFactory<>("id"));
            loanColBook.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getBook().getTitle()));
            loanColLoanDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getLoanDate().toString()));
            loanColDueDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDueDate().toString()));
            loanColReturnDate.setCellValueFactory(data -> new SimpleStringProperty(
                    data.getValue().getReturnDate() != null ? data.getValue().getReturnDate().toString() : "Non rendu"
            ));
        }

        // Config colonnes Réservations
        if (reservationsTable != null) {
            resColId.setCellValueFactory(new PropertyValueFactory<>("id"));
            resColBook.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getBook().getTitle()));
            resColDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getReservationDate().toString()));
            resColStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        }

        loadCatalogue();
        if (currentMember != null) {
            loadLoansForMember();
            loadReservationsForMember();
        }
    }

    public void setCurrentMember(Member member) {
        this.currentMember = member;
        if (welcomeLabel != null) {
            if (member != null && member.getName() != null) {
                welcomeLabel.setText("Bienvenue " + member.getName() + " !");
            } else {
                welcomeLabel.setText("Bienvenue !");
            }
        }
        loadCatalogue();
        loadLoansForMember();
        loadReservationsForMember();
    }

    // ========= CATALOGUE =========
    private void loadCatalogue() {
        reloadCatalogueWithFilters();
    }

    @FXML private void onApplyFilters() { reloadCatalogueWithFilters(); }
    @FXML private void onResetFilters() {
        if (searchTitleField != null) searchTitleField.clear();
        if (searchAuthorField != null) searchAuthorField.clear();
        if (searchIsbnField != null) searchIsbnField.clear();
        if (searchCategoryField != null) searchCategoryField.clear();
        if (filterAllRadio != null) filterAllRadio.setSelected(true);
        reloadCatalogueWithFilters();
    }

    private void reloadCatalogueWithFilters() {
        if (catalogueTable == null) return;
        catalogueTable.getItems().clear();

        String titleFilter = searchTitleField != null ? searchTitleField.getText().trim() : "";
        String authorFilter = searchAuthorField != null ? searchAuthorField.getText().trim() : "";
        String isbnFilter = searchIsbnField != null ? searchIsbnField.getText().trim() : "";
        String catFilter = searchCategoryField != null ? searchCategoryField.getText().trim() : "";

        // CORRECTION MAJEURE ICI : Suppression de "&& currentMember.getId() != null"
        boolean hasMember = currentMember != null;

        StringBuilder sql = new StringBuilder(
                "SELECT b.id, b.title, b.author, b.isbn, b.category, " +
                        "(SELECT COUNT(*) FROM loans l WHERE l.book_id = b.id AND l.return_date IS NULL) AS nbActiveLoans"
        );

        if (hasMember) {
            sql.append(", (SELECT COUNT(*) FROM reservations r WHERE r.book_id = b.id AND r.member_id = ? AND r.status IN ('ACTIVE', 'EN_ATTENTE')) AS nbResMember");
        } else {
            sql.append(", 0 AS nbResMember");
        }

        sql.append(" FROM books b WHERE 1=1");

        if (!titleFilter.isEmpty()) sql.append(" AND LOWER(b.title) LIKE LOWER(?)");
        if (!authorFilter.isEmpty()) sql.append(" AND LOWER(b.author) LIKE LOWER(?)");
        if (!isbnFilter.isEmpty()) sql.append(" AND b.isbn LIKE ?");
        if (!catFilter.isEmpty()) sql.append(" AND LOWER(b.category) LIKE LOWER(?)");

        sql.append(" ORDER BY b.title");

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (hasMember) ps.setInt(paramIndex++, currentMember.getId());
            if (!titleFilter.isEmpty()) ps.setString(paramIndex++, "%" + titleFilter + "%");
            if (!authorFilter.isEmpty()) ps.setString(paramIndex++, "%" + authorFilter + "%");
            if (!isbnFilter.isEmpty()) ps.setString(paramIndex++, "%" + isbnFilter + "%");
            if (!catFilter.isEmpty()) ps.setString(paramIndex++, "%" + catFilter + "%");

            try (ResultSet rs = ps.executeQuery()) {
                String mode = "ALL";
                if (filterGroup != null && filterGroup.getSelectedToggle() != null) {
                    if (filterAvailableRadio.isSelected()) mode = "AVAILABLE";
                    else if (filterLoanedRadio.isSelected()) mode = "LOANED";
                    else if (filterReservableRadio.isSelected()) mode = "RESERVABLE";
                }

                while (rs.next()) {
                    int nbActiveLoans = rs.getInt("nbActiveLoans");
                    int nbResMember = rs.getInt("nbResMember");
                    boolean isLoaned = nbActiveLoans > 0;
                    boolean isReservable = isLoaned && nbResMember == 0;

                    if (mode.equals("AVAILABLE") && isLoaned) continue;
                    if (mode.equals("LOANED") && !isLoaned) continue;
                    if (mode.equals("RESERVABLE") && !isReservable) continue;

                    catalogueTable.getItems().add(new Book(
                            rs.getInt("id"), rs.getString("title"), rs.getString("author"),
                            rs.getString("isbn"), rs.getString("category")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Erreur", "Erreur catalogue.");
        }
    }

    // ========= EMPRUNTS =========
    private void loadLoansForMember() {
        if (loansTable == null || currentMember == null) return;
        loansTable.getItems().clear();

        String sql = "SELECT l.id, b.id AS book_id, b.title, b.author, b.isbn, b.category, l.loan_date, l.due_date, l.return_date " +
                "FROM loans l JOIN books b ON l.book_id = b.id WHERE l.member_id = ? ORDER BY l.loan_date DESC";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, currentMember.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Book book = new Book(rs.getInt("book_id"), rs.getString("title"), rs.getString("author"), rs.getString("isbn"), rs.getString("category"));
                    LocalDate rDate = rs.getString("return_date") != null ? LocalDate.parse(rs.getString("return_date")) : null;
                    loansTable.getItems().add(new Loan(
                            rs.getInt("id"), book, currentMember,
                            LocalDate.parse(rs.getString("loan_date")), LocalDate.parse(rs.getString("due_date")), rDate
                    ));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ========= RESERVATIONS =========
    private void loadReservationsForMember() {
        if (reservationsTable == null || currentMember == null) return;
        reservationsTable.getItems().clear();

        String sql = "SELECT r.id, b.id AS book_id, b.title, b.author, b.isbn, b.category, r.reservation_date, r.status " +
                "FROM reservations r JOIN books b ON r.book_id = b.id WHERE r.member_id = ? ORDER BY r.reservation_date DESC";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, currentMember.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Book book = new Book(rs.getInt("book_id"), rs.getString("title"), rs.getString("author"), rs.getString("isbn"), rs.getString("category"));
                    reservationsTable.getItems().add(new Reservation(
                            rs.getInt("id"), book, currentMember,
                            LocalDate.parse(rs.getString("reservation_date")), rs.getString("status")
                    ));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ========= NOUVELLE FONCTIONNALITÉ : CRÉER UN EMPRUNT =========
    @FXML
    private void onCreateLoan() {
        clearLoanMessage();

        if (currentMember == null) {
            showLoanMessage("Erreur : vous devez être connecté.", true);
            return;
        }

        Book selectedBook = catalogueTable.getSelectionModel().getSelectedItem();
        if (selectedBook == null) {
            showLoanMessage("Veuillez sélectionner un livre dans le catalogue.", true);
            return;
        }

        String durationText = loanDurationField != null ? loanDurationField.getText().trim() : "";
        if (durationText.isEmpty()) {
            showLoanMessage("Veuillez entrer une durée d'emprunt (en jours).", true);
            return;
        }

        try {
            int duration = Integer.parseInt(durationText);
            if (duration <= 0) {
                showLoanMessage("La durée doit être supérieure à 0 jours.", true);
                return;
            }
            if (duration > 60) { // Limite de 60 jours pour un emprunt
                showLoanMessage("La durée maximale d'emprunt est de 60 jours.", true);
                return;
            }

            try (Connection conn = Database.getConnection()) {
                // Vérifier si le livre est disponible
                if (!isBookAvailableForLoan(conn, selectedBook.getId())) {
                    showLoanMessage("Ce livre n'est pas disponible pour l'emprunt.", true);
                    return;
                }

                // Vérifier si l'adhérent a déjà emprunté ce livre
                if (hasActiveLoanForBook(conn, currentMember.getId(), selectedBook.getId())) {
                    showLoanMessage("Vous avez déjà emprunté ce livre.", true);
                    return;
                }

                // Vérifier le nombre d'emprunts en cours de l'adhérent
                if (getActiveLoansCount(conn, currentMember.getId()) >= 5) { // Limite de 5 emprunts
                    showLoanMessage("Vous avez atteint la limite de 5 emprunts simultanés.", true);
                    return;
                }

                // Demander confirmation
                Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                confirmAlert.setTitle("Confirmation d'emprunt");
                confirmAlert.setHeaderText("Créer un emprunt");
                confirmAlert.setContentText("Êtes-vous sûr de vouloir emprunter ce livre ?\n" +
                        "Livre : " + selectedBook.getTitle() + "\n" +
                        "Durée : " + duration + " jour(s)\n" +
                        "Date de retour prévue : " + LocalDate.now().plusDays(duration));

                Optional<ButtonType> result = confirmAlert.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) {
                    return;
                }

                // Créer l'emprunt
                LocalDate today = LocalDate.now();
                LocalDate dueDate = today.plusDays(duration);

                String sql = "INSERT INTO loans (book_id, member_id, loan_date, due_date, return_date) VALUES (?, ?, ?, ?, NULL)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, selectedBook.getId());
                    ps.setInt(2, currentMember.getId());
                    ps.setString(3, today.toString());
                    ps.setString(4, dueDate.toString());
                    int rows = ps.executeUpdate();

                    if (rows > 0) {
                        // Mettre à jour les réservations pour ce livre
                        updateReservationStatus(conn, selectedBook.getId(), "EMPRUNTÉ");

                        showLoanMessage("Emprunt créé avec succès ! Date de retour : " + dueDate, false);
                        clearLoanFields();
                        loadLoansForMember();
                        loadCatalogue();
                    } else {
                        showLoanMessage("Erreur lors de la création de l'emprunt.", true);
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
                showLoanMessage("Erreur de base de données : " + e.getMessage(), true);
            }

        } catch (NumberFormatException e) {
            showLoanMessage("La durée doit être un nombre valide.", true);
        }
    }

    // Vérifier si un livre est disponible pour l'emprunt
    private boolean isBookAvailableForLoan(Connection conn, int bookId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM loans WHERE book_id = ? AND return_date IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    // Vérifier si l'adhérent a déjà emprunté ce livre
    private boolean hasActiveLoanForBook(Connection conn, int memberId, int bookId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM loans WHERE member_id = ? AND book_id = ? AND return_date IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.setInt(2, bookId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    // Compter le nombre d'emprunts actifs d'un adhérent
    private int getActiveLoansCount(Connection conn, int memberId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM loans WHERE member_id = ? AND return_date IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // Mettre à jour le statut des réservations
    private void updateReservationStatus(Connection conn, int bookId, String status) throws SQLException {
        String sql = "UPDATE reservations SET status = ? WHERE book_id = ? AND status IN ('ACTIVE', 'DISPONIBLE')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, bookId);
            ps.executeUpdate();
        }
    }

    // Nettoyer les champs d'emprunt
    private void clearLoanFields() {
        if (loanDurationField != null) {
            loanDurationField.clear();
        }
    }

    // Afficher un message d'emprunt
    private void showLoanMessage(String message, boolean isError) {
        if (loanMessageLabel != null) {
            loanMessageLabel.setText(message);
            loanMessageLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
        }
    }

    // Effacer le message d'emprunt
    private void clearLoanMessage() {
        if (loanMessageLabel != null) {
            loanMessageLabel.setText("");
        }
    }

    @FXML
    private void onReserveSelected() {
        clearReservationMessage();
        if (currentMember == null) { // Correction: juste currentMember == null
            showReservationMessage("Erreur : membre non connecté.", true);
            return;
        }

        Book selectedBook = catalogueTable.getSelectionModel().getSelectedItem();
        if (selectedBook == null) {
            showReservationMessage("Veuillez sélectionner un livre.", true);
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmation");
        confirmAlert.setContentText("Réserver : \"" + selectedBook.getTitle() + "\" ?");
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try (Connection conn = Database.getConnection()) {
            if (hasActiveReservation(conn, selectedBook.getId(), currentMember.getId())) {
                showReservationMessage("Vous avez déjà une réservation active pour ce livre.", true);
                return;
            }
            boolean isBookAvailable = isBookAvailable(conn, selectedBook.getId());
            String status = isBookAvailable ? "ACTIVE" : "EN_ATTENTE";

            createReservation(conn, selectedBook.getId(), currentMember.getId(), status);
            showReservationMessage(isBookAvailable ? "Réservation réussie ! Livre disponible." : "Réservation réussie ! En liste d'attente.", false);

            loadReservationsForMember();
            reloadCatalogueWithFilters();

        } catch (SQLException e) {
            e.printStackTrace();
            showReservationMessage("Erreur réservation.", true);
        }
    }

    private boolean hasActiveReservation(Connection conn, int bookId, int memberId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM reservations WHERE book_id = ? AND member_id = ? AND status IN ('ACTIVE', 'EN_ATTENTE')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            ps.setInt(2, memberId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean isBookAvailable(Connection conn, int bookId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM loans WHERE book_id = ? AND return_date IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    private void createReservation(Connection conn, int bookId, int memberId, String status) throws SQLException {
        String sql = "INSERT INTO reservations (book_id, member_id, reservation_date, status) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            ps.setInt(2, memberId);
            ps.setString(3, LocalDate.now().toString());
            ps.setString(4, status);
            ps.executeUpdate();
        }
    }

    @FXML
    private void onCancelReservation() {
        if (currentMember == null) return; // Correction
        Reservation selected = reservationsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Erreur", "Sélectionnez une réservation.");
            return;
        }

        try (Connection conn = Database.getConnection()) {
            String sql = "UPDATE reservations SET status = 'CANCELLED' WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, selected.getId());
                ps.executeUpdate();
                showReservationMessage("Réservation annulée.", false);
                loadReservationsForMember();
                reloadCatalogueWithFilters();
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @FXML
    private void onLogout() {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) welcomeLabel.getScene().getWindow();
            stage.close();
            MainApp.showLoginView();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML private void onRefresh() {
        loadCatalogue();
        if(currentMember != null) {
            loadLoansForMember();
            loadReservationsForMember();
        }
    }

    private void clearReservationMessage() { if (reservationMessageLabel != null) reservationMessageLabel.setText(""); }
    private void showReservationMessage(String msg, boolean isError) {
        if (reservationMessageLabel != null) {
            reservationMessageLabel.setText(msg);
            reservationMessageLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
        }
    }
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}