package commands;

import utils.GitObjectUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DeflaterOutputStream;

public class HashObjectCommand implements GitCommand {
  
  @Override
  public void execute(String[] args) {
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
      String hash = GitObjectUtils.bytesToHex(hashBytes);
      
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
}
