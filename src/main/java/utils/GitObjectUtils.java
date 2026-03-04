package utils;

import models.TreeEntry;

import java.io.ByteArrayOutputStream;
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

public class GitObjectUtils {
  
  /**
   * Convert bytes to hex string
   */
  public static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
  
  /**
   * Validate that the given hash is a 40-character hexadecimal SHA-1 string.
   */
  private static boolean isValidHexSha(String hash) {
    if (hash == null || hash.length() != 40) {
      return false;
    }
    for (int i = 0; i < hash.length(); i++) {
      char c = hash.charAt(i);
      boolean isDigit = c >= '0' && c <= '9';
      boolean isLowerHex = c >= 'a' && c <= 'f';
      boolean isUpperHex = c >= 'A' && c <= 'F';
      if (!isDigit && !isLowerHex && !isUpperHex) {
        return false;
      }
    }
    return true;
  }
  
  /**
   * Create a blob object from a file and return its hash
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
   * Recursively write a tree object and return its hash
   */
  public static String writeTree(File directory) throws IOException, NoSuchAlgorithmException {
    List<TreeEntry> entries = new ArrayList<>();
    
    File[] files = directory.listFiles();
    if (files == null) {
      throw new RuntimeException("Cannot read directory: " + directory);
    }
    
    for (File file : files) {
      // Skip .git directory
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
    
    // Sort entries alphabetically
    Collections.sort(entries);
    
    List<byte[]> contentParts = new ArrayList<>();
    int totalSize = 0;
    
    for (TreeEntry entry : entries) {
      // Format: <mode> <name>\0<20_byte_sha>
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
   * Load object from disk
   */
  public static byte[] loadObjectFromDisk(File gitDir, String hash) {
    if (!isValidHexSha(hash)) {
      return null;
    }
    try {
      String dirName = hash.substring(0, 2);
      String fileName = hash.substring(2);
      File objectFile = new File(gitDir, "objects/" + dirName + "/" + fileName);
      
      if (!objectFile.exists()) return null;
      
      try (FileInputStream fis = new FileInputStream(objectFile);
           InflaterInputStream iis = new InflaterInputStream(fis)) {
        
        byte[] decompressed = iis.readAllBytes();
        
        // Find null byte that separates header from content
        int nullIndex = -1;
        for (int i = 0; i < decompressed.length; i++) {
          if (decompressed[i] == 0) {
            nullIndex = i;
            break;
          }
        }
        
        if (nullIndex == -1) return null;
        
        return Arrays.copyOfRange(decompressed, nullIndex + 1, decompressed.length);
      }
    } catch (IOException e) {
      return null;
    }
  }
  
  /**
   * Get object type from object data
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
   * Checkout commit to working directory
   */
  public static void checkoutCommit(File workDir, File gitDir, String commitSha) throws Exception {
    if (!isValidHexSha(commitSha)) {
      throw new RuntimeException("Invalid commit SHA: " + commitSha);
    }
    // Read commit object
    byte[] commitData = loadObjectFromDisk(gitDir, commitSha);
    if (commitData == null) {
      throw new RuntimeException("Commit not found: " + commitSha);
    }
    
    // Parse commit to find tree
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
      throw new RuntimeException("No tree found in commit");
    }
    
    // Checkout tree
    checkoutTree(workDir, gitDir, treeSha, "");
  }
  
  /**
   * Recursively checkout tree
   */
  public static void checkoutTree(File workDir, File gitDir, String treeSha, String prefix) throws Exception {
    byte[] treeData = loadObjectFromDisk(gitDir, treeSha);
    if (treeData == null) {
      throw new RuntimeException("Tree not found: " + treeSha);
    }
    
    // Parse tree entries
    int pos = 0;
    while (pos < treeData.length) {
      // Read mode
      int spacePos = pos;
      while (spacePos < treeData.length && treeData[spacePos] != ' ') {
        spacePos++;
      }
      String mode = new String(treeData, pos, spacePos - pos);
      pos = spacePos + 1;
      
      // Read name
      int nullPos = pos;
      while (nullPos < treeData.length && treeData[nullPos] != 0) {
        nullPos++;
      }
      String name = new String(treeData, pos, nullPos - pos);
      pos = nullPos + 1;
      
      // Read hash
      if (pos + 20 > treeData.length) break;
      byte[] hashBytes = Arrays.copyOfRange(treeData, pos, pos + 20);
      String hash = bytesToHex(hashBytes);
      pos += 20;
      
      // Create file or directory
      String path = prefix + name;
      File file = new File(workDir, path);
      
      if (mode.equals("40000")) {
        // Directory
        file.mkdir();
        checkoutTree(workDir, gitDir, hash, path + "/");
      } else {
        // File
        byte[] fileData = loadObjectFromDisk(gitDir, hash);
        if (fileData != null) {
          Files.write(file.toPath(), fileData);
          if (mode.equals("100755")) {
            file.setExecutable(true);
          }
        }
      }
    }
  }
}
