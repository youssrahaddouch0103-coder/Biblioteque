package com.bibliotech;

import com.bibliotech.model.Member;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AdherentsController {

    @FXML
    private TableView<Member> membersTable;

    @FXML
    private TableColumn<Member, Integer> colId;

    @FXML
    private TableColumn<Member, String> colName;

    @FXML
    private TableColumn<Member, String> colEmail;

    @FXML
    private TextField nameField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

    private final ObservableList<Member> membersList = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        // CORRECTION : Utiliser PropertyValueFactory avec les bons noms de propriétés
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));

        membersTable.setItems(membersList);

        // Ajouter un listener pour la sélection
        membersTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> onRowSelected()
        );

        loadMembers();
    }

    // ------------------- Charger tous les adhérents -------------------
    private void loadMembers() {
        membersList.clear();

        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT id, name, email FROM members ORDER BY name";
            ResultSet rs = conn.createStatement().executeQuery(sql);

            while (rs.next()) {
                membersList.add(new Member(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("email")
                ));
            }

            statusLabel.setText("Liste actualisée (" + membersList.size() + " adhérents)");
            statusLabel.setStyle("-fx-text-fill: green;");

        } catch (SQLException e) {
            e.printStackTrace();
            statusLabel.setText("Erreur de chargement: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    // ------------------- Ajouter un adhérent -------------------
    @FXML
    private void onAddMember() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String pwd = passwordField.getText().trim();

        if (name.isEmpty() || email.isEmpty() || pwd.isEmpty()) {
            statusLabel.setText("Tous les champs sont obligatoires.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        // Validation email
        if (!email.contains("@") || !email.contains(".")) {
            statusLabel.setText("Format d'email invalide.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        // Vérifier si l'email existe déjà
        if (emailExists(email)) {
            statusLabel.setText("Cet email est déjà utilisé.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        try (Connection conn = Database.getConnection()) {
            String sql = "INSERT INTO members(name, email, password) VALUES (?, ?, ?)";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, pwd);
            ps.executeUpdate();

            statusLabel.setText("Adhérent ajouté avec succès !");
            statusLabel.setStyle("-fx-text-fill: green;");

            clearFields();
            loadMembers();

        } catch (SQLException e) {
            e.printStackTrace();
            statusLabel.setText("Erreur lors de l'ajout: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    // Vérifier si l'email existe déjà
    private boolean emailExists(String email) {
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT COUNT(*) FROM members WHERE email = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ------------------- Supprimer -------------------
    @FXML
    private void onDeleteMember() {
        Member selected = membersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Sélectionnez un adhérent à supprimer.");
            statusLabel.setStyle("-fx-text-fill: orange;");
            return;
        }

        // Confirmation avant suppression
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation de suppression");
        alert.setHeaderText("Supprimer l'adhérent");
        alert.setContentText("Êtes-vous sûr de vouloir supprimer l'adhérent : " + selected.getName() + " ?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Connection conn = Database.getConnection()) {
                String sql = "DELETE FROM members WHERE id = ?";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setInt(1, selected.getId());
                ps.executeUpdate();

                statusLabel.setText("Adhérent supprimé avec succès.");
                statusLabel.setStyle("-fx-text-fill: green;");
                loadMembers();

            } catch (SQLException e) {
                e.printStackTrace();
                statusLabel.setText("Erreur lors de la suppression: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: red;");
            }
        }
    }

    // ------------------- Remplir les champs lors sélection -------------------
    private void onRowSelected() {
        Member m = membersTable.getSelectionModel().getSelectedItem();
        if (m != null) {
            nameField.setText(m.getName());
            emailField.setText(m.getEmail());
            passwordField.clear(); // Clear password field for security
        }
    }

    // ------------------- Modifier un adhérent -------------------
    @FXML
    private void onUpdateMember() {
        Member selected = membersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Sélectionnez un adhérent à modifier.");
            statusLabel.setStyle("-fx-text-fill: orange;");
            return;
        }

        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String pwd = passwordField.getText().trim();

        if (name.isEmpty() || email.isEmpty()) {
            statusLabel.setText("Le nom et l'email sont obligatoires.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        // Vérifier si l'email est déjà utilisé par un autre adhérent
        if (!email.equals(selected.getEmail()) && emailExists(email)) {
            statusLabel.setText("Cet email est déjà utilisé par un autre adhérent.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        try (Connection conn = Database.getConnection()) {
            String sql;
            PreparedStatement ps;

            if (pwd.isEmpty()) {
                // Update without password
                sql = "UPDATE members SET name = ?, email = ? WHERE id = ?";
                ps = conn.prepareStatement(sql);
                ps.setString(1, name);
                ps.setString(2, email);
                ps.setInt(3, selected.getId());
            } else {
                // Update with password
                sql = "UPDATE members SET name = ?, email = ?, password = ? WHERE id = ?";
                ps = conn.prepareStatement(sql);
                ps.setString(1, name);
                ps.setString(2, email);
                ps.setString(3, pwd);
                ps.setInt(4, selected.getId());
            }

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                statusLabel.setText("Adhérent modifié avec succès.");
                statusLabel.setStyle("-fx-text-fill: green;");
                clearFields();
                loadMembers();
            } else {
                statusLabel.setText("Aucune modification effectuée.");
                statusLabel.setStyle("-fx-text-fill: orange;");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            statusLabel.setText("Erreur lors de la modification: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    // ------------------- Nettoyer les champs -------------------
    @FXML
    private void onClearFields() {
        clearFields();
        membersTable.getSelectionModel().clearSelection();
        statusLabel.setText("Champs nettoyés.");
        statusLabel.setStyle("-fx-text-fill: blue;");
    }

    private void clearFields() {
        nameField.clear();
        emailField.clear();
        passwordField.clear();
    }

    // ------------------- Bouton retour vers admin -------------------
    @FXML
    private void onBackToAdmin() {
        try {
            Stage stage = (Stage) membersTable.getScene().getWindow();
            stage.close(); // Fermer la fenêtre actuelle

            // Réouvrir l'interface admin via MainApp
            MainApp.showView("/com/bibliotech/main-view.fxml", "Bibliotech - Administration");

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Erreur lors du retour: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    // ------------------- Rafraîchir la liste -------------------
    @FXML
    private void onRefresh() {
        loadMembers();
        clearFields();
        statusLabel.setText("Liste rafraîchie.");
        statusLabel.setStyle("-fx-text-fill: blue;");
    }
}