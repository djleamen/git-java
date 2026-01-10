/**
 * 
 */

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

public class Main {
  public static void main(String[] args){    
    final String command = args[0];
    
    switch (command) {
      // git init
      case "init" -> {
        final File root = new File(".git");
        new File(root, "objects").mkdirs();
        new File(root, "refs").mkdirs();
        final File head = new File(root, "HEAD");
    
        try {
          head.createNewFile();
          Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());
          System.out.println("Initialized git directory");
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      // cat-file -p <hash>
      case "cat-file" -> {
        if (args.length < 3 || !args[1].equals("-p")) {
          System.out.println("Usage: cat-file -p <hash>");
          return;
        }
        
        String hash = args[2];
        String dirName = hash.substring(0, 2);
        String fileName = hash.substring(2);
        File objectFile = new File(".git/objects/" + dirName + "/" + fileName);
        
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
          
          if (nullIndex == -1) {
            throw new RuntimeException("Invalid object file format");
          }
          
          // Extract everything after the null byte
          byte[] content = Arrays.copyOfRange(decompressed, nullIndex + 1, decompressed.length);
          
          System.out.print(new String(content));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      // hash-object -w <file>
      case "hash-object" -> {
        if (args.length < 3 || !args[1].equals("-w")) {
          System.out.println("Usage: hash-object -w <file>");
          return;
        }
        
        String filename = args[2];
        File file = new File(filename);
        
        try {
          // Read file
          byte[] fileContent = Files.readAllBytes(file.toPath());
          
          // Create blob header
          String header = "blob " + fileContent.length + "\0";
          byte[] headerBytes = header.getBytes();
          
          // Combine header and content
          byte[] blobData = new byte[headerBytes.length + fileContent.length];
          System.arraycopy(headerBytes, 0, blobData, 0, headerBytes.length);
          System.arraycopy(fileContent, 0, blobData, headerBytes.length, fileContent.length);
          
          // Compute SHA-1 hash
          MessageDigest digest = MessageDigest.getInstance("SHA-1");
          byte[] hashBytes = digest.digest(blobData);
          
          // Convert hash to hex string
          StringBuilder hashHex = new StringBuilder();
          for (byte b : hashBytes) {
            hashHex.append(String.format("%02x", b));
          }
          String hash = hashHex.toString();
          
          // Create directory structure
          String dirName = hash.substring(0, 2);
          String fileName = hash.substring(2);
          File objectDir = new File(".git/objects/" + dirName);
          objectDir.mkdirs();
          
          // Write compressed blob to file
          File objectFile = new File(objectDir, fileName);
          try (FileOutputStream fos = new FileOutputStream(objectFile);
               DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
            dos.write(blobData);
          }
          
          System.out.println(hash);
          
        } catch (IOException | NoSuchAlgorithmException e) {
          throw new RuntimeException(e);
        }
      }
      // ls-tree --name-only <tree_sha>
      case "ls-tree" -> {
        if (args.length < 3 || !args[1].equals("--name-only")) {
          System.out.println("Usage: ls-tree --name-only <tree_sha>");
          return;
        }
        
        String hash = args[2];
        String dirName = hash.substring(0, 2);
        String fileName = hash.substring(2);
        File objectFile = new File(".git/objects/" + dirName + "/" + fileName);
        
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
          
          if (nullIndex == -1) {
            throw new RuntimeException("Invalid tree object format");
          }
          
          // Parse tree entries
          int pos = nullIndex + 1;
          while (pos < decompressed.length) {
            int nameStart = pos;
            while (nameStart < decompressed.length && decompressed[nameStart] != ' ') {
              nameStart++;
            }
            nameStart++;
            
            int nameEnd = nameStart;
            while (nameEnd < decompressed.length && decompressed[nameEnd] != 0) {
              nameEnd++;
            }
            
            String name = new String(Arrays.copyOfRange(decompressed, nameStart, nameEnd));
            System.out.println(name);
            
            // Skip the 20-byte SHA-1 hash
            pos = nameEnd + 1 + 20;
          }
          
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      // write-tree
      case "write-tree" -> {
        try {
          String hash = writeTree(new File("."));
          System.out.println(hash);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      // commit-tree <tree_sha> -p <commit_sha> -m <message>
      case "commit-tree" -> {
        if (args.length < 6 || !args[2].equals("-p") || !args[4].equals("-m")) {
          System.out.println("Usage: commit-tree <tree_sha> -p <commit_sha> -m <message>");
          return;
        }
        
        String treeSha = args[1];
        String parentSha = args[3];
        String message = args[5];
        
        try {
          // Build commit content
          StringBuilder content = new StringBuilder();
          content.append("tree ").append(treeSha).append("\n");
          content.append("parent ").append(parentSha).append("\n");
          
          // Hardcoded author and committer (for simplicity)
          String authorLine = "author John Doe <john@example.com> 1234567890 +0000\n";
          String committerLine = "committer John Doe <john@example.com> 1234567890 +0000\n";
          content.append(authorLine);
          content.append(committerLine);
          
          content.append("\n");
          content.append(message).append("\n");
          
          byte[] contentBytes = content.toString().getBytes();
          
          String header = "commit " + contentBytes.length + "\0";
          byte[] headerBytes = header.getBytes();
          
          byte[] commitData = new byte[headerBytes.length + contentBytes.length];
          System.arraycopy(headerBytes, 0, commitData, 0, headerBytes.length);
          System.arraycopy(contentBytes, 0, commitData, headerBytes.length, contentBytes.length);
          
          MessageDigest digest = MessageDigest.getInstance("SHA-1");
          byte[] hashBytes = digest.digest(commitData);
          
          StringBuilder hashHex = new StringBuilder();
          for (byte b : hashBytes) {
            hashHex.append(String.format("%02x", b));
          }
          String hash = hashHex.toString();
          
          String dirName = hash.substring(0, 2);
          String fileName = hash.substring(2);
          File objectDir = new File(".git/objects/" + dirName);
          objectDir.mkdirs();
          
          // Write compressed commit to file
          File objectFile = new File(objectDir, fileName);
          try (FileOutputStream fos = new FileOutputStream(objectFile);
               DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
            dos.write(commitData);
          }
          
          System.out.println(hash);
          
        } catch (IOException | NoSuchAlgorithmException e) {
          throw new RuntimeException(e);
        }
      }
      default -> System.out.println("Unknown command: " + command);
    }
  }
  
  // Helper class - tree entry
  static class TreeEntry implements Comparable<TreeEntry> {
    String mode;
    String name;
    String hash;
    
    TreeEntry(String mode, String name, String hash) {
      this.mode = mode;
      this.name = name;
      this.hash = hash;
    }
    
    @Override
    public int compareTo(TreeEntry other) {
      return this.name.compareTo(other.name);
    }
  }
  
  // Create a blob object from a file and return its hash
  static String createBlob(File file) throws IOException, NoSuchAlgorithmException {
    byte[] fileContent = Files.readAllBytes(file.toPath());
    
    String header = "blob " + fileContent.length + "\0";
    byte[] headerBytes = header.getBytes();
    
    byte[] blobData = new byte[headerBytes.length + fileContent.length];
    System.arraycopy(headerBytes, 0, blobData, 0, headerBytes.length);
    System.arraycopy(fileContent, 0, blobData, headerBytes.length, fileContent.length);
    
    MessageDigest digest = MessageDigest.getInstance("SHA-1");
    byte[] hashBytes = digest.digest(blobData);
    
    StringBuilder hashHex = new StringBuilder();
    for (byte b : hashBytes) {
      hashHex.append(String.format("%02x", b));
    }
    String hash = hashHex.toString();
    
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
  
  // Recursively write a tree object and return its hash
  static String writeTree(File directory) throws IOException, NoSuchAlgorithmException {
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
      String entryPrefix = entry.mode + " " + entry.name + "\0";
      byte[] entryPrefixBytes = entryPrefix.getBytes();
      
      byte[] hashBytes = new byte[20];
      for (int i = 0; i < 20; i++) {
        hashBytes[i] = (byte) Integer.parseInt(entry.hash.substring(i * 2, i * 2 + 2), 16);
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
    
    StringBuilder hashHex = new StringBuilder();
    for (byte b : hashBytes) {
      hashHex.append(String.format("%02x", b));
    }
    String hash = hashHex.toString();
    
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
}
