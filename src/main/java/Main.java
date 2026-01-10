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
import java.util.Arrays;
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
      default -> System.out.println("Unknown command: " + command);
    }
  }
}
