package commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class InitCommand implements GitCommand {
  
  @Override
  public void execute(String[] args) throws GitCommandException {
    final File root = new File(".git");
    new File(root, "objects").mkdirs();
    new File(root, "refs").mkdirs();
    final File head = new File(root, "HEAD");

    try {
      if (!head.createNewFile()) {
        throw new GitCommandException("HEAD file already exists: " + head.getPath());
      }
      Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());
      System.out.println("Initialized git directory");
    } catch (IOException e) {
      throw new GitCommandException("Failed to initialize git directory: " + e.getMessage(), e);
    }
  }
}
