package commands;

import utils.GitObjectUtils;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class WriteTreeCommand implements GitCommand {
  
  @Override
  public void execute(String[] args) {
    try {
      String hash = GitObjectUtils.writeTree(new File("."));
      System.out.println(hash);
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
