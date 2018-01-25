package ch.heigvd.prr.termination;

import java.io.IOException;
import java.util.InputMismatchException;
import java.util.Scanner;

/**
 * Classe utilisée par le client qui utilise le Manager pour effectuer des tâches
 */
public class App {

   public static void main(String... args) {

      // Au lancement de l'application, on demande à l'utilisateur le
      // numéro du site courant
      Scanner scanner = new Scanner(System.in);
      boolean ok = false;
      int host = 0;
      do {
         System.out.print("No de site courant: ");
         try {
            host = scanner.nextInt();

            if (host > 4 || host < 1) {
               throw new IndexOutOfBoundsException();
            }

            ok = true;
         } catch (IndexOutOfBoundsException e) {
            System.out.println("No de site incorrecte, réessayer");
         } catch (InputMismatchException e) {
            System.out.println("Veuillez saisir un nombre");
            scanner.nextLine();
         }
      } while (!ok);

      // On lance le manager
      DynamicThreadManager manager = null;
      try {
         manager = new DynamicThreadManager((byte) (host - 1));
      } catch (IOException ex) {
         // erreur critique à l'application
         System.err.println("Problème lors de la création du manager");
         ex.printStackTrace();
         System.exit(-1);
      }

      // Saisie utilisateur pour le site courant
      boolean exit = false;
      do {
         System.out.println("\t1. Lancer une tâche");
         System.out.println("\t2. Initier la terminaison");
         System.out.println("Entrer le numéro correspondant à l'action voulue: ");

         try {
            int num = scanner.nextInt();

            if (num > 2 || num < 1) {
               throw new IndexOutOfBoundsException();
            }

            // Handling menu action
            switch (num) {
               case 1:
                  System.out.println("Lancement d'une nouvelle tâche");
                  manager.initiateTask();
                  break;
               case 2:
                  System.out.println("Demande de terminaison");
                  manager.initiateTerminaison();
                  break;
            }

         } catch (IndexOutOfBoundsException e) {
            System.out.println("No incorrecte, réessayer");
         } catch (InputMismatchException e) {
            System.out.println("Veuillez saisir un nombre");
            scanner.nextLine();
         }
      } while (!exit);

      // On quitte, on ferme les connexion
      scanner.close();
      System.exit(0);
   }
}
