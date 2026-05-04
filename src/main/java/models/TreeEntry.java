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
  
  /** 
   * @return String
   */
  public String getMode() {
    return mode;
  }
  
  /** 
   * @return String
   */
  public String getName() {
    return name;
  }
  
  /** 
   * @return String
   */
  public String getHash() {
    return hash;
  }
  
  /** 
   * @param other
   * @return int
   */
  @Override
  public int compareTo(TreeEntry other) {
    return this.name.compareTo(other.name);
  }
}
