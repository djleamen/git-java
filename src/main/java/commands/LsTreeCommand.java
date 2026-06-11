package commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.InflaterInputStream;

/**
 * Implements the {@code ls-tree --name-only} command.
 *
 * <p>Reads a tree object from the {@code .git/objects} store and prints the name of each
 * entry to standard output, one per line.
 */
public class LsTreeCommand implements GitCommand {

  /**
   * Lists the names of all entries in a git tree object.
   *
   * <p>Expects {@code args[1]} to be {@code --name-only} and {@code args[2]} to be the
   * 40-character hex SHA-1 hash of the tree object. Each entry name is printed on its
   * own line to standard output.
   *
   * @param args command-line arguments passed from the git dispatcher
   * @throws GitCommandException if the tree object cannot be found, its format is invalid,
   *                             or an I/O error occurs while reading the object
   */
  @Override
  public void execute(String[] args) throws GitCommandException {
    if (args.length < 3 || !args[1].equals("--name-only")) {
      System.out.println("Usage: ls-tree --name-only <tree_sha>");
      return;
    }
    
    String hash = args[2];
    if (hash.length() != 40) {
      throw new GitCommandException("Invalid object hash: " + hash);
    }
    String dirName = hash.substring(0, 2);
    String fileName = hash.substring(2);
    File objectFile = new File(".git" + File.separator + "objects" + File.separator + dirName + File.separator + fileName);
    
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
        throw new GitCommandException("Invalid object format");
      }

      int pos = nullIndex + 1;
      while (pos < decompressed.length) {
        int spacePos = pos;
        while (spacePos < decompressed.length && decompressed[spacePos] != ' ') {
          spacePos++;
        }
        pos = spacePos + 1;

        int nullPos = pos;
        while (nullPos < decompressed.length && decompressed[nullPos] != 0) {
          nullPos++;
        }
        String name = new String(decompressed, pos, nullPos - pos);
        System.out.println(name);
        pos = nullPos + 1;

        pos += 20;
      }
      
    } catch (IOException e) {
      throw new GitCommandException("Failed to read tree object: " + e.getMessage(), e);
    }
  }
}
