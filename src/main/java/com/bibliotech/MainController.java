package com.bibliotech;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class MainController {

    @FXML
    private BorderPane contentArea;

    @FXML
    public void initialize() {
        onDashboardClicked();
    }

    // --- Navigation ---
    @FXML
    private void onDashboardClicked() { loadView("dashboard-view.fxml"); }

    @FXML
    private void onCatalogueClicked() { loadView("catalogue-view.fxml"); }

    @FXML
    private void onEmpruntsClicked() { loadView("emprunts-view.fxml"); }

    @FXML
    private void onAdherentsClicked() { loadView("adherents-view.fxml"); }

    @FXML
    private void onReservationsClicked() {
        // Attention au nom : adapte si ton FXML s'appelle "reservations-view.fxml"
        loadView("reservations-view.fxml");
    }

    @FXML
    private void onLogout() {
        try {
            if (contentArea.getScene() != null) {
                ((Stage) contentArea.getScene().getWindow()).close();
            }
            MainApp.showLoginView();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Chargement avec diagnostic d'erreur ---
    private void loadView(String fxmlFileName) {
        try {
            URL fileUrl = getClass().getResource(fxmlFileName);

            if (fileUrl == null) {
                showError(
                        "Fichier manquant",
                        "Impossible de trouver le fichier : " + fxmlFileName +
                                "\n\nVérifiez qu'il est bien dans src/main/resources/com/bibliotech/"
                );
                return;
            }

            FXMLLoader loader = new FXMLLoader(fileUrl);
            Parent view = loader.load();
            contentArea.setCenter(view);

        } catch (Exception e) {
            e.printStackTrace();
            showExceptionDialog(fxmlFileName, e);
        }
    }

    // Affiche une boîte de dialogue avec l'erreur technique complète
    private void showExceptionDialog(String fileName, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur de chargement");
        alert.setHeaderText("Impossible d'ouvrir la fenêtre : " + fileName);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        String exceptionText = sw.toString();

        TextArea textArea = new TextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane content = new GridPane();
        content.setMaxWidth(Double.MAX_VALUE);
        content.add(textArea, 0, 0);

        alert.getDialogPane().setExpandableContent(content);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ============================
    //  Changement mot de passe admin
    // ============================
    @FXML
    private void onChangeAdminPassword() {

        // 1) Ancien mot de passe
        TextInputDialog oldPwdDialog = new TextInputDialog();
        oldPwdDialog.setTitle("Changement mot de passe admin");
        oldPwdDialog.setHeaderText("Sécurité : saisir l'ancien mot de passe");
        oldPwdDialog.setContentText("Ancien mot de passe :");

        String oldPwd = oldPwdDialog.showAndWait().orElse(null);
        if (oldPwd == null || oldPwd.isBlank()) return;

        // Vérifier ancien mot de passe dans la base
        try (Connection conn = Database.getConnection()) {

            String sql = "SELECT password FROM admin WHERE id = 1";
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                showAlert(Alert.AlertType.ERROR, "Erreur", "Admin introuvable !");
                return;
            }

            String realPwd = rs.getString("password");

            if (!oldPwd.equals(realPwd)) {
                showAlert(Alert.AlertType.ERROR, "Erreur", "Ancien mot de passe incorrect.");
                return;
            }

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de vérifier le mot de passe.");
            return;
        }

        // 2) Nouveau mot de passe
        TextInputDialog newPwdDialog = new TextInputDialog();
        newPwdDialog.setTitle("Nouveau mot de passe");
        newPwdDialog.setHeaderText("Entrer un nouveau mot de passe");
        newPwdDialog.setContentText("Nouveau mot de passe :");

        String newPwd = newPwdDialog.showAndWait().orElse(null);
        if (newPwd == null || newPwd.isBlank()) return;

        // 3) Confirmation
        TextInputDialog confirmPwdDialog = new TextInputDialog();
        confirmPwdDialog.setTitle("Confirmation");
        confirmPwdDialog.setHeaderText("Confirmer le nouveau mot de passe");
        confirmPwdDialog.setContentText("Confirmer :");

        String confirmPwd = confirmPwdDialog.showAndWait().orElse(null);
        if (!newPwd.equals(confirmPwd)) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Les mots de passe ne correspondent pas.");
            return;
        }

        // 4) Mise à jour SQL
        try (Connection conn = Database.getConnection()) {

            String updateSql = "UPDATE admin SET password = ? WHERE id = 1";
            PreparedStatement ps = conn.prepareStatement(updateSql);
            ps.setString(1, newPwd);
            ps.executeUpdate();

            showAlert(Alert.AlertType.INFORMATION, "Succès", "Mot de passe mis à jour.");

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur", "La mise à jour a échoué.");
        }
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
