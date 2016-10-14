package nlp.associationMiner;

import nlp.associationMiner.fig.LispTree;
import nlp.associationMiner.fig.LogInfo;

/**
 * Represents a logical predicate.
 * @author Percy Liang
 */
public class NameValue extends Value {
  public final String id;  // Identifier (e.g., "fb:en.barack_obama")
  public final String description;  // Readable description (e.g., "Barack Obama")

  public NameValue(LispTree tree) {
    this.id = tree.child(1).value;
    if (tree.children.size() > 2)
      this.description = tree.child(2).value;
    else
      this.description = null;
    assert (this.id != null) : tree;
  }

  public NameValue(String id) {
    this(id, null);
  }

  public NameValue(String id, String description) {
    if (id == null) {
      LogInfo.errors("Got null id, description is %s", description);
      id = "fb:en.null";
    }
    this.id = id;
    this.description = description;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("name");
    tree.addChild(id);
    if (description != null) tree.addChild(description);
    return tree;
  }

  @Override public int hashCode() { return id.hashCode(); }
  @Override public boolean equals(Object thatObj) {
    if (!(thatObj instanceof NameValue)) return false;
    NameValue that = (NameValue)thatObj;
    return this.id.equals(that.id);
  }
}
