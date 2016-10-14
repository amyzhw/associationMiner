package nlp.associationMiner;

import com.fasterxml.jackson.annotation.*;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import nlp.associationMiner.fig.LispTree;
import nlp.associationMiner.fig.LogInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * An example corresponds roughly to an input-output pair, the basic unit which
 * we make predictions on.  The Example object stores both the input,
 * preprocessing, and output of the parser.
 *
 * @author Percy Liang
 * @author Roy Frostig
 */
@JsonIgnoreProperties(ignoreUnknown=true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Example {
  public static class JsonViews {
    public static class WithDerivations {}
    public static class WithDPChart {}
  }

  //// Information from the input file.

  // Unique identifier for this example.
  @JsonProperty public final String id;

  // Input utterance
  @JsonProperty public final String utterance;

  // Provides
  @JsonProperty public final DerivationConstraint derivConstraint;

  // What we should try to predict.
  @JsonProperty public Formula targetFormula;  // Logical form
  @JsonProperty public Value targetValue;  // Answer

  //// Information after preprocessing (e.g., tokenization, POS tagging, NER, syntactic parsing, etc.).
  @JsonProperty public LanguageInfo languageInfo = null;

  // Tokens come from languageInfo, but if we don't have one,
  // they go here (usually due to deserialization).
  // DELETE
  private List<String> backupTokens = null;

  //// Output of the parser.

  // Predicted derivations: sorted by score.
  @JsonProperty @JsonView(JsonViews.WithDerivations.class)
  List<Derivation> predDerivations;

  // To debug amount of ordering change due to executing and obtaining
  // denotation features.
  List<Derivation> predDerivationsAfterParse;

  // Statistics about how well we did during parsing and execution.
  private Evaluation parseEvaluation;
  private Evaluation evaluation;

  // Maximum position in any cell of the chart of any sub-derivation of a
  // correct derivation.  Under the current parameters, we would need to set
  // the beam size to at least this to get it right.
  int correctMaxBeamPosition = -1;
  // Beam size to use for parsing
  int beamSize = -1;

  public static class Builder {
    private String id;
    private String utterance;
    private DerivationConstraint derivConstraint;
    private Formula targetFormula;
    private Value targetValue;
    private List<Derivation> predDerivations;
    private LanguageInfo languageInfo;

    public Builder setId(String id) {
      this.id = id;
      return this;
    }
    public Builder setUtterance(String utterance) {
      this.utterance = utterance;
      return this;
    }
    public Builder setDerivConstraint(DerivationConstraint derivConstraint) {
      this.derivConstraint = derivConstraint;
      return this;
    }
    public Builder setTargetFormula(Formula targetFormula) {
      this.targetFormula = targetFormula;
      return this;
    }
    public Builder setTargetValue(Value targetValue) {
      this.targetValue = targetValue;
      return this;
    }
    public Builder setPredDerivations(List<Derivation> predDerivations) {
      this.predDerivations = predDerivations;
      return this;
    }
    public Builder setLanguageInfo(LanguageInfo languageInfo) {
      this.languageInfo = languageInfo;
      return this;
    }
    public Builder withExample(Example ex) {
      setId(ex.id);
      setUtterance(ex.utterance);
      setDerivConstraint(ex.derivConstraint);
      setTargetFormula(ex.targetFormula);
      setTargetValue(ex.targetValue);
      setPredDerivations(ex.predDerivations);
      return this;
    }

    public Example createExample() {
      return new Example(
          id, utterance, derivConstraint, targetFormula,
          targetValue, predDerivations, languageInfo);
    }
  }

  @JsonCreator
  public Example(@JsonProperty("id") String id,
                 @JsonProperty("utterance") String utterance,
                 @JsonProperty("derivConstraint") DerivationConstraint derivConstraint,
                 @JsonProperty("targetFormula") Formula targetFormula,
                 @JsonProperty("targetValue") Value targetValue,
                 @JsonProperty("predDerivations") List<Derivation> predDerivations,
                 @JsonProperty("languageInfo") LanguageInfo languageInfo) {
    this.id = id;
    this.utterance = utterance;
    this.derivConstraint = derivConstraint;
    this.targetFormula = targetFormula;
    this.targetValue = targetValue;
    this.predDerivations = predDerivations;
    this.languageInfo = languageInfo;
  }

  // Accessors
  public String getId() { return id; }
  public String getUtterance() { return utterance; }
  public Evaluation getEvaluation() { return evaluation; }
  public int numTokens() { return languageInfo.tokens.size(); }
  public List<Derivation> getPredDerivations() { return predDerivations; }
  public void setTargetFormula(Formula targetFormula) {
    this.targetFormula = targetFormula;
  }
  public void setTargetValue(Value targetValue) {
    this.targetValue = targetValue;
  }

  public String spanString(int start, int end) {
    return String.format("%d:%d[%s]", start, end, phraseString(start, end));
  }
  public String phraseString(int start, int end) {
    return Joiner.on(' ').join(languageInfo.tokens.subList(start, end));
  }

  // Return a string representing the tokens between start and end.
  public List<String> getTokens() {
    return (languageInfo != null) ? languageInfo.tokens : backupTokens;
  }
  public List<String> getLemmaTokens() { return languageInfo.lemmaTokens; }
  public String token(int i) { return languageInfo.tokens.get(i); }
  public String lemmaToken(int i) { return languageInfo.lemmaTokens.get(i); }
  public String posTag(int i) { return languageInfo.posTags.get(i); }
  public String phrase(int start, int end) {
    return languageInfo.phrase(start, end);
  }
  public String lemmaPhrase(int start, int end) {
    return languageInfo.lemmaPhrase(start, end);
  }

  void setParseEvaluation(Evaluation eval) { parseEvaluation = eval; }
  public void setEvaluation(Evaluation eval) { evaluation = eval; }

  public Evaluation computeTotalEvaluation() {
    Evaluation eval = new Evaluation();
    if (parseEvaluation != null)
      eval.add(parseEvaluation);
    if (evaluation != null)
      eval.add(evaluation);
    return eval;
  }

  void rescoreAndSortPredDerivations(Params params) {
    for (Derivation deriv : predDerivations)
      deriv.computeScore(params);
    Derivation.sortByScore(predDerivations);
  }

  public String toJson() {
    return Json.writeValueAsStringHard(this);
  }

  public static Example fromJson(String json) {
    return Json.readValueHard(json, Example.class);
  }

  /** Use JSON instead. */
  @Deprecated
  public static Example fromLispTree(LispTree tree) {
    return fromLispTree(tree, null);
  }

  @Deprecated
  public static Example fromLispTree(LispTree tree, String defaultId) {
    Builder b = new Builder().setId(defaultId);

    for (int i = 1; i < tree.children.size(); i++) {
      LispTree arg = tree.child(i);
      String label = arg.child(0).value;
      if ("id".equals(label)) {
        b.setId(arg.child(1).value);
      } else if ("utterance".equals(label)) {
        b.setUtterance(arg.child(1).value);
      } else if ("targetFormula".equals(label)) {
        b.setTargetFormula(Formulas.fromLispTree(arg.child(1)));
      } else if ("targetValue".equals(label) || "targetValues".equals(label)) {
        if (arg.children.size() != 2)
          throw new RuntimeException("Expect one target value");
        b.setTargetValue(Values.fromLispTree(arg.child(1)));
      }
    }

    Example ex = b.createExample();

    for (int i = 1; i < tree.children.size(); i++) {
      LispTree arg = tree.child(i);
      String label = arg.child(0).value;
      if ("posTags".equals(label) || "nerTags".equals(label) || "url".equals(label)) {
        // Do nothing
      } else if ("tokens".equals(label)) {
        int n = arg.child(1).children.size();
        ex.backupTokens = new ArrayList<String>(n);
        for (int j = 0; j < n; j++)
          ex.backupTokens.add(arg.child(1).child(j).value);
      } else if ("evaluation".equals(label)) {
        ex.evaluation = Evaluation.fromLispTree(arg.child(1));
      } else if ("parseEvaluation".equals(label)) {
        ex.parseEvaluation = Evaluation.fromLispTree(arg.child(1));
      } else if ("predDerivations".equals(label)) {
        ex.predDerivations = new ArrayList<Derivation>();
        for (int j = 1; j < arg.children.size(); j++)
          ex.predDerivations.add(derivationFromLispTree(arg.child(j)));
      } else if (!Sets.newHashSet("id", "utterance", "targetFormula", "targetValue", "targetValues").contains(label)) {
        throw new RuntimeException("Invalid example argument: " + arg);
      }
    }

    return ex;
  }

  public void preprocess() {
    if (this.languageInfo == null)
      this.languageInfo = new LanguageInfo();
    this.languageInfo.analyze(this.utterance);
    this.log();
  }

  public void log() {
    LogInfo.begin_track("Example: %s", utterance);
    LogInfo.logs("Tokens: %s", getTokens());
    LogInfo.logs("Lemmatized tokens: %s", getLemmaTokens());
    LogInfo.logs("POS tags: %s", languageInfo.posTags);
    LogInfo.logs("NER tags: %s", languageInfo.nerTags);
    LogInfo.logs("NER values: %s", languageInfo.nerValues);
    if (targetFormula != null)
      LogInfo.logs("targetFormula: %s", targetFormula);
    if (targetValue != null)
      LogInfo.logs("targetValue: %s", targetValue);
    LogInfo.end_track();
  }

  public void clearPredDerivations() {
    predDerivations.clear();
    predDerivationsAfterParse.clear();
  }

  /** Use JSON serialization instead. */
  @Deprecated
  public LispTree toLispTree(boolean outputPredDerivations) {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("example");

    if (id != null)
      tree.addChild(LispTree.proto.newList("id", id));
    if (utterance != null)
      tree.addChild(LispTree.proto.newList("utterance", utterance));
    if (targetFormula != null)
      tree.addChild(LispTree.proto.newList("targetFormula", targetFormula.toLispTree()));
    if (targetValue != null)
      tree.addChild(LispTree.proto.newList("targetValue", targetValue.toLispTree()));

    if (languageInfo != null) {
      if (languageInfo.tokens != null)
        tree.addChild(LispTree.proto.newList("tokens", LispTree.proto.newList(languageInfo.tokens)));
      if (languageInfo.posTags != null)
        tree.addChild(LispTree.proto.newList("posTags", Joiner.on(' ').join(languageInfo.posTags)));
      if (languageInfo.nerTags != null)
        tree.addChild(LispTree.proto.newList("nerTags", Joiner.on(' ').join(languageInfo.nerTags)));
    }

    if (evaluation != null)
      tree.addChild(LispTree.proto.newList("evaluation", evaluation.toLispTree()));
    if (parseEvaluation != null)
      tree.addChild(LispTree.proto.newList("parseEvaluation", parseEvaluation.toLispTree()));

    if (predDerivations != null && outputPredDerivations) {
      LispTree list = LispTree.proto.newList();
      list.addChild("predDerivations");
      for (Derivation deriv : predDerivations)
        list.addChild(derivationToLispTree(deriv));
      tree.addChild(list);
    }

    return tree;
  }

  @Deprecated
  private static Derivation derivationFromLispTree(LispTree item) {
    Derivation.Builder b = new Derivation.Builder()
        .cat(Rule.rootCat)
        .start(-1)
        .end(-1)
        .rule(Rule.nullRule)
        .children(new ArrayList<Derivation>());
    int i = 0;

    b.compatibility(Double.parseDouble(item.child(i++).value));
    b.prob(Double.parseDouble(item.child(i++).value));
    b.score(Double.parseDouble(item.child(i++).value));

    LispTree valueTree = item.child(i++);
    if (!valueTree.isLeaf() || !"null".equals(valueTree.value))
      b.value(Values.fromLispTree(valueTree));

    b.formula(Formulas.fromLispTree(item.child(i++)));

    FeatureVector fv = new FeatureVector();
    LispTree features = item.child(i++);
    for (int j = 0; j < features.children.size(); j++)
      fv.addFromString(features.child(j).child(0).value, Double.parseDouble(features.child(j).child(1).value));

    b.localFeatureVector(fv);

    return b.createDerivation();
  }

  @Deprecated
  private static LispTree derivationToLispTree(Derivation deriv) {
    LispTree item = LispTree.proto.newList();

    // TODO: label scores and compatibilities in derivations to make output
    // more self-documenting.
    item.addChild(deriv.compatibility + "");
    item.addChild(deriv.prob + "");
    item.addChild(deriv.score + "");
    if (deriv.value != null)
      item.addChild(deriv.value.toLispTree());
    else
      item.addChild("null");
    item.addChild(deriv.formula.toLispTree());

    HashMap<String, Double> features = new HashMap<String, Double>();
    deriv.incrementAllFeatureVector(1, features);
    item.addChild(LispTree.proto.newList(features));

    return item;
  }
}
