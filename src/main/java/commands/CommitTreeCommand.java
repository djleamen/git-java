package commands;

import utils.GitObjectUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DeflaterOutputStream;

public class CommitTreeCommand implements GitCommand {
  
  @Override
  public void execute(String[] args) {
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
      String hash = GitObjectUtils.bytesToHex(hashBytes);
      
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
}
