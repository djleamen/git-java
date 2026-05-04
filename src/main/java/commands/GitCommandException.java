package commands;

public class GitCommandException extends Exception {
  public GitCommandException(String message) {
    super(message);
  }

  public GitCommandException(String message, Throwable cause) {
    super(message, cause);
  }
}
