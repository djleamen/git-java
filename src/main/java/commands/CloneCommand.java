package commands;

import utils.GitObjectUtils;
import utils.NetworkUtils;
import utils.PackfileParser;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

/**
 * Implements the {@code clone} command.
 *
 * <p>Clones a remote git repository into a local directory using the Git Smart HTTP
 * protocol. Fetches refs, downloads a packfile, unpacks objects, and checks out the
 * HEAD commit.
 */
public class CloneCommand implements GitCommand {

  /**
   * Clones a remote repository to a local directory.
   *
   * <p>Expects {@code args[1]} to be the remote URL and {@code args[2]} to be the target
   * directory path. The target directory must not already exist.
   *
   * @param args command-line arguments passed from the git dispatcher
   * @throws GitCommandException if cloning fails due to an invalid URL, a network error,
   *                             a missing target branch, or an I/O error
   */
  @Override
  public void execute(String[] args) throws GitCommandException {
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
  
  /**
   * Performs the full clone workflow: creates the directory, initialises the git
   * repository, discovers remote refs, fetches the packfile, resolves objects, writes
   * refs, and checks out the HEAD commit.
   *
   * <p>Prefers {@code refs/heads/main}, then {@code refs/heads/master}, then the first
   * available branch found in the ref advertisement.
   *
   * @param repoUrl   the remote repository URL (HTTP/HTTPS)
   * @param targetDir path to the local directory to create and populate
   * @throws Exception if any step of the clone operation fails
   */
  private void cloneRepository(String repoUrl, String targetDir) throws Exception {
    File dir = new File(targetDir);
    if (!dir.mkdir()) {
      throw new GitCommandException("Failed to create directory: " + targetDir);
    }

    File gitDir = new File(dir, ".git");
    new File(gitDir, "objects").mkdirs();
    new File(gitDir, "refs/heads").mkdirs();

    String discoverUrl = repoUrl + "/info/refs?service=git-upload-pack";
    Map<String, String> refs = NetworkUtils.discoverRefs(discoverUrl);

    String headRef = null;
    String targetBranch = null;

    if (refs.containsKey("refs/heads/main")) {
      headRef = refs.get("refs/heads/main");
      targetBranch = "refs/heads/main";
    } else if (refs.containsKey("refs/heads/master")) {
      headRef = refs.get("refs/heads/master");
      targetBranch = "refs/heads/master";
    } else {
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

    File headFile = new File(gitDir, "HEAD");
    Files.write(headFile.toPath(), ("ref: " + targetBranch + "\n").getBytes());

    for (Map.Entry<String, String> entry : refs.entrySet()) {
      String ref = entry.getKey();
      String sha = entry.getValue();

      if (ref.startsWith("refs/heads/") || ref.startsWith("refs/tags/")) {
        File refFile = new File(gitDir, ref);
        refFile.getParentFile().mkdirs();
        Files.write(refFile.toPath(), (sha + "\n").getBytes());
      }
    }

    GitObjectUtils.checkoutCommit(dir, gitDir, headRef);
  }
}
