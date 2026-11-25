package com.bibliotech;

import com.bibliotech.model.Member;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController {

    @FXML
    private TextField usernameField;   // email ou username admin

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    @FXML
    private ToggleGroup roleGroup;

    @FXML
    private RadioButton adminRadio;

    @FXML
    private RadioButton memberRadio;

    @FXML
    private Button signupButton;

    @FXML
    private void initialize() {
        // Masquer le message d'erreur au démarrage
        if (errorLabel != null) {
            errorLabel.setText("");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }

        updateSignupVisibility();
        roleGroup.selectedToggleProperty()
                .addListener((obs, o, n) -> updateSignupVisibility());
    }

    private void updateSignupVisibility() {
        boolean memberSelected = memberRadio != null && memberRadio.isSelected();
        signupButton.setVisible(memberSelected);
        signupButton.setManaged(memberSelected);
    }

    private void showError(String msg) {
        if (errorLabel != null) {
            errorLabel.setText(msg);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    @FXML
    private void onLoginClicked() {
        String emailOrUser = usernameField.getText();
        String password = passwordField.getText();

        if (emailOrUser == null || emailOrUser.isBlank()
                || password == null || password.isBlank()) {
            showError("Veuillez saisir email et mot de passe.");
            return;
        }

        if (roleGroup.getSelectedToggle() == adminRadio) {
            handleAdminLogin(emailOrUser, password);
        } else {
            handleMemberLogin(emailOrUser, password);
        }
    }

    /* =======================
       LOGIN ADMIN VIA SQLITE
       ======================= */

    private void handleAdminLogin(String user, String password) {
        if (checkAdminInDatabase(user, password)) {
            // effacer le message d'erreur si tout va bien
            if (errorLabel != null) {
                errorLabel.setText("");
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);
            }
            openAdminWindow();
        } else {
            showError("Identifiants administrateur incorrects.");
        }
    }

    private boolean checkAdminInDatabase(String username, String password) {
        String sql = "SELECT 1 FROM admin WHERE id = 1 AND username = ? AND password = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();  // true si une ligne existe
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showError("Erreur de connexion à la base (admin).");
            return false;
        }
    }

    /* =======================
       LOGIN ADHÉRENT
       ======================= */

    private void handleMemberLogin(String email, String password) {
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT id, name, email, password FROM members WHERE email = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        showError("Aucun adhérent avec cet email.");
                        return;
                    }
                    String storedPwd = rs.getString("password");
                    if (!password.equals(storedPwd)) {
                        showError("Mot de passe incorrect.");
                        return;
                    }
                    Member member = new Member(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("email")
                    );

                    if (errorLabel != null) {
                        errorLabel.setText("");
                        errorLabel.setVisible(false);
                        errorLabel.setManaged(false);
                    }

                    openMemberWindow(member);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Erreur de connexion à la base.");
        }
    }

    /* =======================
       OUVERTURE FENÊTRES
       ======================= */

    private void openAdminWindow() {
        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(
                    MainApp.class.getResource("/com/bibliotech/main-view.fxml"));
            Scene scene = new Scene(loader.load());
            stage.setTitle("Bibliotech - Administration");
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Erreur lors de l'ouverture de la fenêtre admin.");
        }
    }

    private void openMemberWindow(Member member) {
        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(
                    MainApp.class.getResource("/com/bibliotech/member-main-view.fxml"));
            Scene scene = new Scene(loader.load());

            MemberMainController controller = loader.getController();
            controller.setCurrentMember(member);

            stage.setTitle("Bibliotech - Espace adhérent");
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Erreur lors de l'ouverture de l'espace adhérent.");
        }
    }

    @FXML
    private void onSignupClicked() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    MainApp.class.getResource("/com/bibliotech/member-signup-view.fxml"));
            Scene scene = new Scene(loader.load());
            Stage signupStage = new Stage();
            signupStage.setTitle("Créer un compte adhérent");
            signupStage.setScene(scene);
            signupStage.initOwner(usernameField.getScene().getWindow());
            signupStage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Erreur lors de l'ouverture de l'inscription.");
        }
    }
}
