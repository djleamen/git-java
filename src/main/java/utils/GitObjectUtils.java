package utils;

import models.TreeEntry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Utility class for creating, reading, and materialising git objects stored in the
 * {@code .git/objects} loose-object format.
 *
 * <p>All objects are stored as DEFLATE-compressed files under a two-character prefix
 * directory derived from their SHA-1 hash.
 */
public class GitObjectUtils {
  
  private GitObjectUtils() {}
  
  /**
   * Converts a byte array to its lowercase hexadecimal string representation.
   *
   * @param bytes the byte array to convert; must not be {@code null}
   * @return a lowercase hex string with exactly {@code bytes.length * 2} characters
   */
  public static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
  
  /**
   * Creates a git blob object from a file and stores it in the {@code .git/objects} store.
   *
   * <p>The file content is wrapped in a {@code blob <size>\0} header, SHA-1 hashed,
   * DEFLATE-compressed, and saved under {@code .git/objects/<prefix>/<suffix>}.
   *
   * @param file the file to store as a blob
   * @return the 40-character hex SHA-1 hash of the resulting blob object
   * @throws IOException              if the file cannot be read or the object cannot be written
   * @throws NoSuchAlgorithmException if the SHA-1 algorithm is unavailable
   */
  public static String createBlob(File file) throws IOException, NoSuchAlgorithmException {
    byte[] fileContent = Files.readAllBytes(file.toPath());
    
    String header = "blob " + fileContent.length + "\0";
    byte[] headerBytes = header.getBytes();
    
    byte[] blobData = new byte[headerBytes.length + fileContent.length];
    System.arraycopy(headerBytes, 0, blobData, 0, headerBytes.length);
    System.arraycopy(fileContent, 0, blobData, headerBytes.length, fileContent.length);
    
    MessageDigest digest = MessageDigest.getInstance("SHA-1");
    byte[] hashBytes = digest.digest(blobData);
    String hash = bytesToHex(hashBytes);
    
    String dirName = hash.substring(0, 2);
    String fileName = hash.substring(2);
    File objectDir = new File(".git/objects/" + dirName);
    objectDir.mkdirs();
    
    File objectFile = new File(objectDir, fileName);
    try (FileOutputStream fos = new FileOutputStream(objectFile);
         DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
      dos.write(blobData);
    }
    
    return hash;
  }
  
  /**
   * Recursively creates git tree objects for a directory and stores them in the object store.
   *
   * <p>Skips the {@code .git} directory. Entries are sorted alphabetically before being
   * written. Blobs are created for regular files and sub-trees for sub-directories.
   *
   * @param directory the directory to write as a tree
   * @return the 40-character hex SHA-1 hash of the root tree object
   * @throws IOException              if a file or directory cannot be read or written
   * @throws NoSuchAlgorithmException if the SHA-1 algorithm is unavailable
   */
  public static String writeTree(File directory) throws IOException, NoSuchAlgorithmException {
    List<TreeEntry> entries = new ArrayList<>();
    
    File[] files = directory.listFiles();
    if (files == null) {
      throw new IOException("Cannot read directory: " + directory);
    }
    
    for (File file : files) {
      if (file.getName().equals(".git")) {
        continue;
      }
      
      if (file.isFile()) {
        String hash = createBlob(file);
        String mode = file.canExecute() ? "100755" : "100644";
        entries.add(new TreeEntry(mode, file.getName(), hash));
      } else if (file.isDirectory()) {
        String hash = writeTree(file);
        entries.add(new TreeEntry("40000", file.getName(), hash));
      }
    }
    
    Collections.sort(entries);

    List<byte[]> contentParts = new ArrayList<>();
    int totalSize = 0;

    for (TreeEntry entry : entries) {
      String entryPrefix = entry.getMode() + " " + entry.getName() + "\0";
      byte[] entryPrefixBytes = entryPrefix.getBytes();
      
      byte[] hashBytes = new byte[20];
      for (int i = 0; i < 20; i++) {
        hashBytes[i] = (byte) Integer.parseInt(entry.getHash().substring(i * 2, i * 2 + 2), 16);
      }
      
      contentParts.add(entryPrefixBytes);
      contentParts.add(hashBytes);
      totalSize += entryPrefixBytes.length + hashBytes.length;
    }
    
    String header = "tree " + totalSize + "\0";
    byte[] headerBytes = header.getBytes();
    
    byte[] treeData = new byte[headerBytes.length + totalSize];
    int pos = 0;
    System.arraycopy(headerBytes, 0, treeData, pos, headerBytes.length);
    pos += headerBytes.length;
    
    for (byte[] part : contentParts) {
      System.arraycopy(part, 0, treeData, pos, part.length);
      pos += part.length;
    }
    
    MessageDigest digest = MessageDigest.getInstance("SHA-1");
    byte[] hashBytes = digest.digest(treeData);
    String hash = bytesToHex(hashBytes);
    
    String dirName = hash.substring(0, 2);
    String fileName = hash.substring(2);
    File objectDir = new File(".git/objects/" + dirName);
    objectDir.mkdirs();
    
    File objectFile = new File(objectDir, fileName);
    try (FileOutputStream fos = new FileOutputStream(objectFile);
         DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
      dos.write(treeData);
    }
    
    return hash;
  }
  
  /**
   * Loads the raw content (post-header) of a git object from the loose-object store.
   *
   * @param gitDir the {@code .git} directory of the repository
   * @param hash   the 40-character hex SHA-1 hash of the object
   * @return the raw content bytes with the object header stripped, or an empty array if
   *         the object is not found or cannot be read
   */
  public static byte[] loadObjectFromDisk(File gitDir, String hash) {
    try {
      String dirName = hash.substring(0, 2);
      String fileName = hash.substring(2);
      File objectFile = new File(gitDir, "objects" + File.separator + dirName + File.separator + fileName);
      
      if (!objectFile.exists()) return new byte[0];
      
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
        
        if (nullIndex == -1) return new byte[0];
        
        return Arrays.copyOfRange(decompressed, nullIndex + 1, decompressed.length);
      }
    } catch (IOException e) {
      return new byte[0];
    }
  }
  
  /**
   * Extracts the object type string from a fully-qualified git object (header + content).
   *
   * <p>The object header has the form {@code <type> <size>\0}; this method returns the
   * {@code <type>} portion.
   *
   * @param fullObjectWithHeader the raw bytes of the git object including its header
   * @return the type string (e.g., {@code "blob"}, {@code "tree"}, {@code "commit"}),
   *         or {@code "blob"} as a fallback if no space character is found
   */
  public static String getObjectType(byte[] fullObjectWithHeader) {
    for (int i = 0; i < fullObjectWithHeader.length; i++) {
      if (fullObjectWithHeader[i] == ' ') {
        return new String(fullObjectWithHeader, 0, i);
      }
    }
    return "blob";
  }
  
  /**
   * Checks out the working-tree state described by a commit object.
   *
   * <p>Reads the commit, extracts the tree SHA from the {@code tree} header line, and
   * delegates to {@link #checkoutTree(File, File, String, String)} to materialise the
   * tree contents.
   *
   * @param workDir   the root directory of the working tree
   * @param gitDir    the {@code .git} directory of the repository
   * @param commitSha the 40-character hex SHA-1 hash of the commit to check out
   * @throws Exception if the commit is not found, its tree reference is missing, or
   *                   any file cannot be written
   */
  public static void checkoutCommit(File workDir, File gitDir, String commitSha) throws Exception {
    byte[] commitData = loadObjectFromDisk(gitDir, commitSha);
    if (commitData.length == 0) {
      throw new IOException("Commit not found: " + commitSha);
    }

    String commitContent = new String(commitData);
    String[] lines = commitContent.split("\n");
    String treeSha = null;

    for (String line : lines) {
      if (line.startsWith("tree ")) {
        treeSha = line.substring(5).trim();
        break;
      }
    }

    if (treeSha == null) {
      throw new IOException("No tree found in commit");
    }

    checkoutTree(workDir, gitDir, treeSha, "");
  }
  
  /**
   * Recursively materialises the contents of a git tree object into the working directory.
   *
   * <p>For each entry in the tree: directories (mode {@code 40000}) are created and
   * recursed into; all other entries are written as regular files via
   * {@link #writeFileEntry(File, String, File, String)}.
   *
   * @param workDir the root working-tree directory
   * @param gitDir  the {@code .git} directory of the repository
   * @param treeSha the 40-character hex SHA-1 hash of the tree object to materialise
   * @param prefix  relative path prefix to prepend when constructing file paths
   * @throws Exception if the tree is not found or any file cannot be written
   */
  public static void checkoutTree(File workDir, File gitDir, String treeSha, String prefix) throws Exception {
    byte[] treeData = loadObjectFromDisk(gitDir, treeSha);
    if (treeData.length == 0) {
      throw new IOException("Tree not found: " + treeSha);
    }

    int pos = 0;
    while (pos < treeData.length) {
      int spacePos = pos;
      while (spacePos < treeData.length && treeData[spacePos] != ' ') {
        spacePos++;
      }
      String mode = new String(treeData, pos, spacePos - pos);
      pos = spacePos + 1;

      int nullPos = pos;
      while (nullPos < treeData.length && treeData[nullPos] != 0) {
        nullPos++;
      }
      String name = new String(treeData, pos, nullPos - pos);
      pos = nullPos + 1;

      if (pos + 20 > treeData.length) break;
      byte[] hashBytes = Arrays.copyOfRange(treeData, pos, pos + 20);
      String hash = bytesToHex(hashBytes);
      pos += 20;

      String path = prefix + name;
      File file = new File(workDir, path);

      if (mode.equals("40000")) {
        file.mkdir();
        checkoutTree(workDir, gitDir, hash, path + "/");
      } else {
        writeFileEntry(file, mode, gitDir, hash);
      }
    }
  }

  /**
   * Writes a single blob object to a file and optionally sets its executable permission.
   *
   * @param file   the target file to create or overwrite
   * @param mode   the git mode string ({@code "100755"} sets the executable bit)
   * @param gitDir the {@code .git} directory of the repository
   * @param hash   the 40-character hex SHA-1 hash of the blob object
   * @throws IOException if the blob cannot be loaded or the file cannot be written
   */
  private static void writeFileEntry(File file, String mode, File gitDir, String hash) throws IOException {
    byte[] fileData = loadObjectFromDisk(gitDir, hash);
    if (fileData.length > 0) {
      Files.write(file.toPath(), fileData);
      if (mode.equals("100755") && !file.setExecutable(true)) {
        throw new IOException("Failed to set executable permission on: " + file.getPath());
      }
    }
  }
}
