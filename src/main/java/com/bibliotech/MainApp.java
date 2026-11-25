package com.bibliotech;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        // INITIALISATION DE LA BASE
        Database.init();

        // Afficher la vue de login
        showLoginView();
    }

    // ========= MÉTHODE POUR AFFICHER LA VUE DE LOGIN =========
    public static void showLoginView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    MainApp.class.getResource("/com/bibliotech/login-view.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);

            // Créer une nouvelle fenêtre
            Stage loginStage = new Stage();
            loginStage.setTitle("Bibliotech - Connexion");
            loginStage.setScene(scene);
            loginStage.show();

            // Fermer l'ancienne fenêtre si elle existe
            if (primaryStage != null) {
                primaryStage.close();
            }
            primaryStage = loginStage;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ========= MÉTHODE POUR CHANGER DE VUE =========
    public static void showView(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    MainApp.class.getResource(fxmlPath));
            Parent root = loader.load();

            Scene scene = new Scene(root);

            Stage newStage = new Stage();
            newStage.setTitle(title);
            newStage.setScene(scene);
            newStage.show();

            // Fermer l'ancienne fenêtre si elle existe
            if (primaryStage != null) {
                primaryStage.close();
            }
            primaryStage = newStage;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ========= MÉTHODE POUR OBTENIR LA FENÊTRE PRINCIPALE =========
    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}