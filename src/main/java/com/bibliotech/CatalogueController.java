package com.bibliotech;

import com.bibliotech.model.Book;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CatalogueController {

    @FXML
    private TableView<Book> booksTable;

    @FXML
    private TableColumn<Book, Integer> colId;
    @FXML
    private TableColumn<Book, String> colTitle;
    @FXML
    private TableColumn<Book, String> colAuthor;
    @FXML
    private TableColumn<Book, String> colIsbn;
    @FXML
    private TableColumn<Book, String> colCategory;

    @FXML
    private TextField titleField;
    @FXML
    private TextField authorField;
    @FXML
    private TextField isbnField;
    @FXML
    private TextField categoryField;

    @FXML
    private TextField searchField;

    @FXML
    private Label statusLabel;

    private final ObservableList<Book> books = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        // Configuration des colonnes
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
        colIsbn.setCellValueFactory(new PropertyValueFactory<>("isbn"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));

        // Ajouter un listener pour la recherche
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterBooks(newValue);
        });

        // Listener pour la sélection
        booksTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> onRowSelected()
        );

        booksTable.setItems(books);
        loadBooks();
    }

    private void loadBooks() {
        books.clear();

        String sql = "SELECT id, title, author, isbn, category FROM books ORDER BY title";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Book book = new Book(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("isbn"),
                        rs.getString("category")
                );
                books.add(book);
            }

            updateStatus("Chargé: " + books.size() + " livres", "green");

        } catch (SQLException e) {
            e.printStackTrace();
            updateStatus("Erreur de chargement: " + e.getMessage(), "red");
        }
    }

    private void filterBooks(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            booksTable.setItems(books);
            return;
        }

        String searchLower = searchText.toLowerCase();
        ObservableList<Book> filteredList = FXCollections.observableArrayList();

        for (Book book : books) {
            if (book.getTitle().toLowerCase().contains(searchLower) ||
                    book.getAuthor().toLowerCase().contains(searchLower) ||
                    book.getCategory().toLowerCase().contains(searchLower) ||
                    book.getIsbn().toLowerCase().contains(searchLower)) {
                filteredList.add(book);
            }
        }

        booksTable.setItems(filteredList);
        updateStatus("Trouvé: " + filteredList.size() + " livre(s)", "blue");
    }

    @FXML
    private void onAddBook() {
        String title = titleField.getText().trim();
        String author = authorField.getText().trim();
        String isbn = isbnField.getText().trim();
        String category = categoryField.getText().trim();

        // Validation
        if (title.isEmpty() || author.isEmpty()) {
            updateStatus("Titre et auteur sont obligatoires", "red");
            return;
        }

        // Vérifier si l'ISBN existe déjà
        if (!isbn.isEmpty() && isbnExists(isbn)) {
            updateStatus("Cet ISBN existe déjà", "red");
            return;
        }

        try (Connection conn = Database.getConnection()) {
            String sql = "INSERT INTO books(title, author, isbn, category) VALUES (?, ?, ?, ?)";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, title);
            ps.setString(2, author);
            ps.setString(3, isbn.isEmpty() ? null : isbn);
            ps.setString(4, category.isEmpty() ? null : category);
            ps.executeUpdate();

            updateStatus("Livre ajouté avec succès", "green");
            clearFields();
            loadBooks();

        } catch (SQLException e) {
            e.printStackTrace();
            updateStatus("Erreur lors de l'ajout: " + e.getMessage(), "red");
        }
    }

    @FXML
    private void onUpdateBook() {
        Book selected = booksTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            updateStatus("Sélectionnez un livre à modifier", "orange");
            return;
        }

        String title = titleField.getText().trim();
        String author = authorField.getText().trim();
        String isbn = isbnField.getText().trim();
        String category = categoryField.getText().trim();

        if (title.isEmpty() || author.isEmpty()) {
            updateStatus("Titre et auteur sont obligatoires", "red");
            return;
        }

        // Vérifier si l'ISBN est utilisé par un autre livre
        if (!isbn.isEmpty() && isbnExists(isbn) && !isbn.equals(selected.getIsbn())) {
            updateStatus("Cet ISBN est déjà utilisé par un autre livre", "red");
            return;
        }

        try (Connection conn = Database.getConnection()) {
            String sql = "UPDATE books SET title = ?, author = ?, isbn = ?, category = ? WHERE id = ?";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, title);
            ps.setString(2, author);
            ps.setString(3, isbn.isEmpty() ? null : isbn);
            ps.setString(4, category.isEmpty() ? null : category);
            ps.setInt(5, selected.getId());

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                updateStatus("Livre modifié avec succès", "green");
                clearFields();
                loadBooks();
            } else {
                updateStatus("Aucune modification effectuée", "orange");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            updateStatus("Erreur lors de la modification: " + e.getMessage(), "red");
        }
    }

    @FXML
    private void onDeleteSelected() {
        Book selected = booksTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            updateStatus("Sélectionnez un livre à supprimer", "orange");
            return;
        }

        // Vérifier si le livre est emprunté
        if (isBookBorrowed(selected.getId())) {
            updateStatus("Impossible de supprimer: livre actuellement emprunté", "red");
            return;
        }

        // Vérifier si le livre a des réservations actives
        if (hasActiveReservations(selected.getId())) {
            updateStatus("Impossible de supprimer: des réservations existent", "red");
            return;
        }

        // Confirmation
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation de suppression");
        alert.setHeaderText("Supprimer le livre");
        alert.setContentText("Êtes-vous sûr de vouloir supprimer le livre: \"" + selected.getTitle() + "\" ?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Connection conn = Database.getConnection()) {
                String sql = "DELETE FROM books WHERE id = ?";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setInt(1, selected.getId());
                ps.executeUpdate();

                updateStatus("Livre supprimé avec succès", "green");
                clearFields();
                loadBooks();

            } catch (SQLException e) {
                e.printStackTrace();
                updateStatus("Erreur lors de la suppression: " + e.getMessage(), "red");
            }
        }
    }

    @FXML
    private void onClearFields() {
        clearFields();
        booksTable.getSelectionModel().clearSelection();
        updateStatus("Champs nettoyés", "blue");
    }

    @FXML
    private void onRefresh() {
        loadBooks();
        searchField.clear();
        updateStatus("Liste rafraîchie", "blue");
    }

    private void onRowSelected() {
        Book selected = booksTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            titleField.setText(selected.getTitle());
            authorField.setText(selected.getAuthor());
            isbnField.setText(selected.getIsbn() != null ? selected.getIsbn() : "");
            categoryField.setText(selected.getCategory() != null ? selected.getCategory() : "");
        }
    }

    // ========= MÉTHODES UTILITAIRES =========

    private boolean isbnExists(String isbn) {
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT COUNT(*) FROM books WHERE isbn = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, isbn);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isBookBorrowed(int bookId) {
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT COUNT(*) FROM loans WHERE book_id = ? AND return_date IS NULL";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, bookId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean hasActiveReservations(int bookId) {
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT COUNT(*) FROM reservations WHERE book_id = ? AND status IN ('ACTIVE', 'EN_ATTENTE')";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, bookId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void clearFields() {
        titleField.clear();
        authorField.clear();
        isbnField.clear();
        categoryField.clear();
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