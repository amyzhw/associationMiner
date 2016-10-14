package nlp.associationMiner;

import  nlp.associationMiner.fig.LispTree;

/**
 * Corresponds to a variable reference.
 *
 * @author Percy Liang
 */
class VariableFormula extends PrimitiveFormula {
  public final String name;  // Name of variable.
  public VariableFormula(String name) { this.name = name; }
  public LispTree toLispTree() { return LispTree.proto.newList("var", name); }

  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof VariableFormula)) return false;
    VariableFormula that = (VariableFormula) thatObj;
    return this.name.equals(that.name);
  }
  
  public int computeHashCode() { return name.hashCode(); }
}
