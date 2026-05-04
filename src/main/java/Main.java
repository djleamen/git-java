import commands.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for this Git implementation.
 *
 * <p>Parses the first command-line argument to select a {@link commands.GitCommand}
 * implementation and delegates execution to it. Supported sub-commands: {@code init},
 * {@code cat-file}, {@code hash-object}, {@code ls-tree}, {@code write-tree},
 * {@code commit-tree}, {@code clone}.
 */
public class Main {
  private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

  /**
   * Dispatches the git sub-command specified as the first command-line argument.
   *
   * @param args command-line arguments where {@code args[0]} is the git sub-command and
   *             any subsequent elements are command-specific options and operands
   */
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
    
    try {
      gitCommand.execute(args);
    } catch (GitCommandException e) {
      LOGGER.log(Level.SEVERE, e.getMessage(), e);
    }
  }
}
