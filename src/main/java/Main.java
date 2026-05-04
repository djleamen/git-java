/**
 *  Git - A simple Git implementation in Java
 *  From CodeCrafters.io build-your-own-git (Java)
 *  
 *  Modular implementation with separate command classes
 */

import commands.*;
import java.util.logging.Logger;

public class Main {
  private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

  public static void main(String[] args) {
    if (args.length == 0) {
      LOGGER.warning("Usage: java Main <command> [args...]");
      return;
    }
    
    final String command = args[0];
    
    GitCommand gitCommand = switch (command) {
      case "init" -> new InitCommand();
      case "cat-file" -> new CatFileCommand();
      case "hash-object" -> new HashObjectCommand();
      case "ls-tree" -> new LsTreeCommand();
      case "write-tree" -> new WriteTreeCommand();
      case "commit-tree" -> new CommitTreeCommand();
      case "clone" -> new CloneCommand();
      default -> null;
    };
    
    if (gitCommand == null) {
      LOGGER.warning("Unknown command: " + command);
      return;
    }
    
    gitCommand.execute(args);
  }
}
