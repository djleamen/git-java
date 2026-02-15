/**
 *  Git - A simple Git implementation in Java
 *  From CodeCrafters.io build-your-own-git (Java)
 *  
 *  Modular implementation with separate command classes
 */

import commands.*;

public class Main {
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("Usage: java Main <command> [args...]");
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
      System.out.println("Unknown command: " + command);
      return;
    }
    
    gitCommand.execute(args);
  }
}
