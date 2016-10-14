package nlp.associationMiner.paraphrase;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import nlp.associationMiner.Evaluation;
import nlp.associationMiner.Executor;
import nlp.associationMiner.FeatureVector;
import nlp.associationMiner.Formula;
import nlp.associationMiner.FormulaGenerationInfo;
import nlp.associationMiner.LanguageInfo;
import nlp.associationMiner.Params;
import nlp.associationMiner.Value;
import nlp.associationMiner.paraphrase.Aligner.Alignment;
import nlp.associationMiner.fig.LispTree;
import nlp.associationMiner.fig.NumUtils;
import nlp.associationMiner.fig.StopWatchSet;

/**
 * Derivation paraphrase, canonical utterance and logical form
 */
public class ParaphraseDerivation {

  public final LanguageInfo langInfo;
  public final FeatureSimilarity vsSimilarity;
  public final Alignment alignment;

  public final Formula formula;
  public final FormulaGenerationInfo fgInfo;

  FeatureVector featureVector;
  double score = Double.NaN;
  double prob = Double.NaN;
  Value value;
  Evaluation executorStats;
  double compatibility = Double.NaN;

  public ParaphraseDerivation(LanguageInfo lInfo,
                              ParaphraseExample paraEx, FormulaGenerationInfo fgInfo,
      ParaphraseFeatureExtractor fExtractor, Params params) {
    this.langInfo = lInfo;
    this.vsSimilarity=paraEx.featureSimilarity;
    this.alignment=paraEx.alignment;
    this.fgInfo=fgInfo;
    this.formula = fgInfo.generateFormula();
    //extract feature vector
    featureVector = new FeatureVector();
    fExtractor.extractParaphraseDerivationFeatures(this);
    //compute score - we can do this here since all features are extracted in the constructor
    scoreProofDeriv(params);
  }

  public void ensureExecuted(Executor executor) {
    if (!isExecuted()) {
      StopWatchSet.begin("Executor.execute");
      Executor.Response response = executor.execute(formula);
      StopWatchSet.end();
      value = response.value;
      executorStats = response.stats;
    }
  }

  public boolean isExecuted() { return value != null; }

  //Generate a probability distribution over derivations given their scores.
  public static double[] getProbs(List<ParaphraseDerivation> derivations, double temperature) {
    double[] probs = new double[derivations.size()];
    for (int i = 0; i < derivations.size(); i++)
      probs[i] = derivations.get(i).score / temperature;
    if (probs.length > 0)
      NumUtils.expNormalize(probs);
    return probs;
  }

  public String toString() { return toLispTree().toString(); }

  private LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("derivation");

    if (formula != null)
      tree.addChild(LispTree.proto.newList("formula", formula.toLispTree()));
    if (value != null) {
      tree.addChild(LispTree.proto.newList("value", value.toLispTree()));
    }
    if(vsSimilarity!=null)
      tree.addChild(vsSimilarity.toLispTree());
    if(alignment!=null)
      tree.addChild(alignment.toLispTree());
    return tree;
  }

  public void incrementAllFeatureVector(double incr, Map<String, Double> counts) {
    //increment all feature components
    featureVector.increment(incr, counts);
    if(vsSimilarity!=null)
      vsSimilarity.featureVector.increment(incr, counts);
    if(alignment!=null)
      alignment.featureVector.increment(incr, counts);
  }

  private void scoreProofDeriv(Params params) {

    double vsmScore = vsSimilarity!=null ? vsSimilarity.featureVector.dotProduct(params) : 0.0;
    double alignmentScore = alignment!=null ? alignment.featureVector.dotProduct(params) :0.0;
    double derivationScore = featureVector.dotProduct(params);
    if(vsmScore==Double.NaN ||
        alignmentScore==Double.NaN ||
        derivationScore==Double.NaN
            )
      throw new RuntimeException("One of the scores is not a number");
    score =
            vsmScore+alignmentScore+derivationScore;
  }

  public void clear() {
    if(vsSimilarity!=null)
      vsSimilarity.clear();
    if(alignment!=null)
      alignment.clear();
    featureVector.clear();
  }
  
  public static class ParaphraseDerivationComparator implements Comparator<ParaphraseDerivation> {
    @Override
    public int compare(ParaphraseDerivation o1, ParaphraseDerivation o2) {
      if(o1.score>o2.score)
        return -1;
      if(o2.score>o1.score)
        return 1;
      return 0;
    }
  }


}
