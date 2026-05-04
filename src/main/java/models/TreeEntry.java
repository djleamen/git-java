package models;

/**
 * An entry within a git tree object, representing a single file or subdirectory.
 *
 * <p>Each entry records the git file mode, the entry name, and the 40-character hex SHA-1
 * hash of the referenced blob (file) or tree (directory). Implements {@link Comparable}
 * so that entries can be sorted alphabetically by name before a tree object is written.
 */
public class TreeEntry implements Comparable<TreeEntry> {
  private String mode;
  private String name;
  private String hash;

  /**
   * Creates a new tree entry.
   *
   * @param mode the git file mode (e.g. {@code "100644"} for a regular file,
   *             {@code "100755"} for an executable, {@code "40000"} for a directory)
   * @param name the entry name (the last path component, without any directory separator)
   * @param hash the 40-character hex SHA-1 hash of the referenced object
   */
  public TreeEntry(String mode, String name, String hash) {
    this.mode = mode;
    this.name = name;
    this.hash = hash;
  }

  /**
   * Returns the git file mode of this entry.
   *
   * @return the mode string (e.g. {@code "100644"}, {@code "100755"}, or {@code "40000"})
   */
  public String getMode() {
    return mode;
  }

  /**
   * Returns the name of this entry.
   *
   * @return the entry name (last path component)
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the 40-character hex SHA-1 hash of the object referenced by this entry.
   *
   * @return the object hash
   */
  public String getHash() {
    return hash;
  }

  /**
   * Compares this entry to another by name, enabling alphabetical sorting of tree entries.
   *
   * @param other the entry to compare against
   * @return a negative integer, zero, or a positive integer as this entry's name is
   *         lexicographically less than, equal to, or greater than the other entry's name
   */
  @Override
  public int compareTo(TreeEntry other) {
    return this.name.compareTo(other.name);
  }
}
