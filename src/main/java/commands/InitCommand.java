package commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Implements the {@code init} command.
 *
 * <p>Creates the {@code .git} directory structure ({@code objects/} and {@code refs/}),
 * writes the {@code HEAD} file pointing to {@code refs/heads/main}, and prints a
 * confirmation message. Fails if the repository is already initialised.
 */
public class InitCommand implements GitCommand {

  /**
   * Initialises a new git repository in the current working directory.
   *
   * <p>Creates {@code .git/objects}, {@code .git/refs}, and {@code .git/HEAD} (pointing
   * to {@code refs/heads/main}). The {@code args} parameter is accepted for interface
   * compatibility but is not used.
   *
   * @param args command-line arguments (unused for {@code init})
   * @throws GitCommandException if {@code HEAD} already exists or cannot be written
   */
  @Override
  public void execute(String[] args) throws GitCommandException {
    final File root = new File(".git");
    new File(root, "objects").mkdirs();
    new File(root, "refs").mkdirs();
    final File head = new File(root, "HEAD");

    try {
      if (!head.createNewFile()) {
        throw new GitCommandException("HEAD file already exists: " + head.getPath());
      }
      Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());
      System.out.println("Initialized git directory");
    } catch (IOException e) {
      throw new GitCommandException("Failed to initialize git directory: " + e.getMessage(), e);
    }
  }
}
