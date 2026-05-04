package commands;

import utils.GitObjectUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DeflaterOutputStream;

/**
 * Implements the {@code hash-object -w} command.
 *
 * <p>Reads a file, computes its SHA-1 blob hash, stores the compressed blob object in the
 * {@code .git/objects} store, and prints the resulting hash to standard output.
 */
public class HashObjectCommand implements GitCommand {

  /**
   * Hashes a file as a git blob object, writes it to the object store, and prints its hash.
   *
   * <p>Expects {@code args[1]} to be {@code -w} and {@code args[2]} to be the path to the
   * file to store. The file content is wrapped in a {@code blob <size>\0} header, SHA-1
   * hashed, DEFLATE-compressed, and saved under {@code .git/objects/<prefix>/<suffix>}.
   *
   * @param args command-line arguments passed from the git dispatcher
   * @throws GitCommandException if the file cannot be read, the SHA-1 algorithm is
   *                             unavailable, or writing the object to disk fails
   */
  @Override
  public void execute(String[] args) throws GitCommandException {
    if (args.length < 3 || !args[1].equals("-w")) {
      System.out.println("Usage: hash-object -w <file>");
      return;
    }
    
    String filename = args[2];
    File file = new File(filename);
    
    try {
      byte[] fileContent = Files.readAllBytes(file.toPath());

      String header = "blob " + fileContent.length + "\0";
      byte[] headerBytes = header.getBytes();

      byte[] blobData = new byte[headerBytes.length + fileContent.length];
      System.arraycopy(headerBytes, 0, blobData, 0, headerBytes.length);
      System.arraycopy(fileContent, 0, blobData, headerBytes.length, fileContent.length);

      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] hashBytes = digest.digest(blobData);
      String hash = GitObjectUtils.bytesToHex(hashBytes);

      String dirName = hash.substring(0, 2);
      String fileName = hash.substring(2);
      File objectDir = new File(".git/objects/" + dirName);
      objectDir.mkdirs();

      File objectFile = new File(objectDir, fileName);
      try (FileOutputStream fos = new FileOutputStream(objectFile);
           DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
        dos.write(blobData);
      }

      System.out.println(hash);
      
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new GitCommandException("Failed to hash object: " + e.getMessage(), e);
    }
  }
}
