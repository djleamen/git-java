package commands;

/**
 * Common interface for all git sub-command implementations.
 *
 * <p>Implementations are instantiated by {@link Main} and invoked with the full
 * command-line argument array (including the sub-command name at index 0).
 */
public interface GitCommand {

  /**
   * Executes this git command with the supplied arguments.
   *
   * @param args the full command-line argument array; {@code args[0]} is the sub-command name
   * @throws GitCommandException if the command cannot complete successfully
   */
  void execute(String[] args) throws GitCommandException;
}
