package nlp.associationMiner;

//import com.fasterxml.jackson.annotation.JsonFormat.Value;
import nlp.associationMiner.fig.*;

/**
 * Represents a boolean.
 * @author Percy Liang
 **/
public class BooleanValue extends Value {
  public final boolean value;

  public BooleanValue(boolean value) { this.value = value; }
  public BooleanValue(LispTree tree) { this.value = Boolean.parseBoolean(tree.child(1).value); }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("boolean");
    tree.addChild(value+"");
    return tree;
  }

  @Override public int hashCode() { return Boolean.valueOf(value).hashCode(); }
  @Override public boolean equals(Object thatObj) {
    if (!(thatObj instanceof BooleanValue)) return false;
    BooleanValue that = (BooleanValue)thatObj;
    return this.value == that.value;
  }
}
