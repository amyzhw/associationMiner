package nlp.associationMiner;

import nlp.associationMiner.fig.LispTree;
import com.google.common.base.Function;

/**
 * If |expr| denotes a set of pairs S,
 * then (reverse |expr|) denotes the set of pairs {(y,x) : (x,y) \in S}.
 * Example:
 *   (rev fb:people.person.date_of_birth)
 *   (rev (lambda x (fb:location.statistical_region.population (fb:measurement_unit.dated_integer.number (var x)))))
 *
 * @author Percy Liang
 */
public class ReverseFormula extends Formula {
  public final Formula child;

  public ReverseFormula(Formula child) { this.child = child; }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("reverse");
    tree.addChild(child.toLispTree());
    return tree;
  }

  public Formula map(Function<Formula, Formula> func) {
    Formula result = func.apply(this);
    return result == null ? new ReverseFormula(child.map(func)) : result;
  }

  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof ReverseFormula)) return false;
    ReverseFormula that = (ReverseFormula) thatObj;
    if (!this.child.equals(that.child)) return false;
    return true;
  }
  
  public int computeHashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + child.hashCode();
    return hash;
  }
}
