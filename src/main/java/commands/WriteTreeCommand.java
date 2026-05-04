package commands;

import utils.GitObjectUtils;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class WriteTreeCommand implements GitCommand {
  
  /** 
   * @param args
   * @throws GitCommandException
   */
  @Override
  public void execute(String[] args) throws GitCommandException {
    try {
      String hash = GitObjectUtils.writeTree(new File("."));
      System.out.println(hash);
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new GitCommandException("Failed to write tree: " + e.getMessage(), e);
    }
  }
}
