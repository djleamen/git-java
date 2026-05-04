package commands;

import utils.GitObjectUtils;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/**
 * Implements the {@code write-tree} command.
 *
 * <p>Recursively creates git tree objects for the current working directory and stores
 * them in the {@code .git/objects} store, then prints the root tree SHA-1 hash to
 * standard output.
 */
public class WriteTreeCommand implements GitCommand {

  /**
   * Writes the current working directory as a git tree object and prints its hash.
   *
   * <p>Delegates to {@link utils.GitObjectUtils#writeTree(File)} starting from the
   * current directory. The {@code args} parameter is accepted for interface compatibility
   * but is not used.
   *
   * @param args command-line arguments passed from the git dispatcher (unused)
   * @throws GitCommandException if any file cannot be read, any object cannot be written,
   *                             or the SHA-1 algorithm is unavailable
   */
  @Override
  public void execute(String[] args) throws GitCommandException {
    try {
      String hash = GitObjectUtils.writeTree(new File("."));
      System.out.println(hash);
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new GitCommandException("Failed to write tree: " + e.getMessage(), e);
    }
  }
}
