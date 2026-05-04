package commands;

import utils.GitObjectUtils;
import utils.NetworkUtils;
import utils.PackfileParser;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

public class CloneCommand implements GitCommand {
  
  @Override
  public void execute(String[] args) {
    if (args.length < 3) {
      System.out.println("Usage: clone <url> <directory>");
      return;
    }
    
    String repoUrl = args[1];
    String targetDir = args[2];
    
    try {
      cloneRepository(repoUrl, targetDir);
    } catch (GitCommandException e) {
      throw e;
    } catch (Exception e) {
      throw new GitCommandException("Clone failed: " + e.getMessage(), e);
    }
  }
  
  private void cloneRepository(String repoUrl, String targetDir) throws GitCommandException, Exception {
    // Create target directory
    File dir = new File(targetDir);
    if (!dir.mkdir()) {
      throw new GitCommandException("Failed to create directory: " + targetDir);
    }
    
    // Initialize git repository
    File gitDir = new File(dir, ".git");
    new File(gitDir, "objects").mkdirs();
    new File(gitDir, "refs/heads").mkdirs();
    
    // Discover refs from remote
    String discoverUrl = repoUrl + "/info/refs?service=git-upload-pack";
    Map<String, String> refs = NetworkUtils.discoverRefs(discoverUrl);
    
    // Find the actual commit SHA to fetch
    // Look for HEAD symref first, or fallback to main/master branch
    String headRef = null;
    String targetBranch = null;
    
    // Try to find a valid branch ref
    if (refs.containsKey("refs/heads/main")) {
      headRef = refs.get("refs/heads/main");
      targetBranch = "refs/heads/main";
    } else if (refs.containsKey("refs/heads/master")) {
      headRef = refs.get("refs/heads/master");
      targetBranch = "refs/heads/master";
    } else {
      // Find any head ref
      for (Map.Entry<String, String> entry : refs.entrySet()) {
        if (entry.getKey().startsWith("refs/heads/")) {
          headRef = entry.getValue();
          targetBranch = entry.getKey();
          break;
        }
      }
    }
    
    if (headRef == null) {
      throw new GitCommandException("No branch refs found in repository");
    }
    
    String uploadPackUrl = repoUrl + "/git-upload-pack";
    byte[] packfile = NetworkUtils.fetchPackfile(uploadPackUrl, headRef);
    
    PackfileParser.unpackPackfile(packfile, gitDir);
    
    // Set HEAD
    File headFile = new File(gitDir, "HEAD");
    Files.write(headFile.toPath(), ("ref: " + targetBranch + "\n").getBytes());
    
    // Write refs
    for (Map.Entry<String, String> entry : refs.entrySet()) {
      String ref = entry.getKey();
      String sha = entry.getValue();
      
      if (ref.startsWith("refs/heads/") || ref.startsWith("refs/tags/")) {
        File refFile = new File(gitDir, ref);
        refFile.getParentFile().mkdirs();
        Files.write(refFile.toPath(), (sha + "\n").getBytes());
      }
    }
    
    // Checkout HEAD commit
    GitObjectUtils.checkoutCommit(dir, gitDir, headRef);
  }
}
