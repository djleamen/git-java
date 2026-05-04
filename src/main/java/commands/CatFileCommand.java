package commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.zip.InflaterInputStream;

/**
 * Implements the {@code cat-file -p} command.
 *
 * <p>Reads a git object from the {@code .git/objects} store, decompresses it,
 * strips the object header, and prints its content to standard output.
 */
public class CatFileCommand implements GitCommand {

  private static final Logger LOGGER = Logger.getLogger(CatFileCommand.class.getName());

  /**
   * Prints the content of a git object identified by its SHA-1 hash.
   *
   * <p>Expects {@code args[1]} to be {@code -p} and {@code args[2]} to be the 40-character
   * hex SHA-1 hash of the object to display. The object header ({@code <type> <size>\0})
   * is stripped and the raw content is written to standard output.
   *
   * @param args command-line arguments passed from the git dispatcher
   * @throws GitCommandException if the object file cannot be found, its format is invalid
   *                             (missing null-byte separator), or an I/O error occurs
   */
  @Override
  public void execute(String[] args) throws GitCommandException {
    if (args.length < 3 || !args[1].equals("-p")) {
      LOGGER.warning("Usage: cat-file -p <hash>");
      return;
    }

    String hash = args[2];
    String dirName = hash.substring(0, 2);
    String fileName = hash.substring(2);
    File objectFile = new File(".git", "objects" + File.separator + dirName + File.separator + fileName);

    try (FileInputStream fis = new FileInputStream(objectFile);
         InflaterInputStream iis = new InflaterInputStream(fis)) {

      byte[] decompressed = iis.readAllBytes();

      int nullIndex = -1;
      for (int i = 0; i < decompressed.length; i++) {
        if (decompressed[i] == 0) {
          nullIndex = i;
          break;
        }
      }

      if (nullIndex == -1) {
        throw new GitCommandException("Invalid object format: missing null byte separator in object " + hash);
      }

      byte[] content = Arrays.copyOfRange(decompressed, nullIndex + 1, decompressed.length);

      System.out.print(new String(content));
    } catch (IOException e) {
      throw new GitCommandException("Failed to read git object: " + hash, e);
    }
  }
}
