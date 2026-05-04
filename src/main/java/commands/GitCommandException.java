package commands;

/**
 * Checked exception thrown by {@link GitCommand} implementations to signal a failure
 * specific to a git command (invalid arguments, object-store errors, network failures, etc.).
 *
 * <p>Where an underlying {@link Exception} is available it should be supplied as the cause
 * so that the full stack trace is preserved for diagnostic logging.
 */
public class GitCommandException extends Exception {

  /**
   * Constructs an exception with the given detail message.
   *
   * @param message a human-readable description of the failure
   */
  public GitCommandException(String message) {
    super(message);
  }

  /**
   * Constructs an exception with a detail message and an underlying cause.
   *
   * @param message a human-readable description of the failure
   * @param cause   the exception that triggered this failure
   */
  public GitCommandException(String message, Throwable cause) {
    super(message, cause);
  }
}
