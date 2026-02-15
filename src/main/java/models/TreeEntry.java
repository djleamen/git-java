package models;

public class TreeEntry implements Comparable<TreeEntry> {
  private String mode;
  private String name;
  private String hash;
  
  public TreeEntry(String mode, String name, String hash) {
    this.mode = mode;
    this.name = name;
    this.hash = hash;
  }
  
  public String getMode() {
    return mode;
  }
  
  public String getName() {
    return name;
  }
  
  public String getHash() {
    return hash;
  }
  
  @Override
  public int compareTo(TreeEntry other) {
    return this.name.compareTo(other.name);
  }
}
