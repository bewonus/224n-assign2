package cs224n.assignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import cs224n.assignment.Grammar.BinaryRule;
import cs224n.assignment.Grammar.UnaryRule;
import cs224n.ling.Constituent;
import cs224n.ling.Tree;
import cs224n.util.Pair;
import cs224n.util.Triplet;

public class PCFGParser implements Parser {

  private Grammar grammar;
  private Lexicon lexicon;

  /**
   * Markovize/binarize the training trees, then learn a lexicon and grammar from these trees.
   * @param trainTrees list of annotated trees to train on
   */
  public void train(List<Tree<String>> trainTrees) {

    // Set the vertical and horizontal markovization orders (default is v = 1, h = -1)
    int v = 1;
    int h = -1;

    // Binarize (and markovize) the training trees
    for (int i = 0; i < trainTrees.size(); i++) {
      trainTrees.set(i, TreeAnnotations.annotateTree(trainTrees.get(i), v, h));
    }

    // Build lexicon and grammar
    lexicon = new Lexicon(trainTrees);
    grammar = new Grammar(trainTrees);
  }

  /**
   * Scores preterminal rules on the given sentence.
   * @param sentence the sentence to parse
   * @param i index of word in sentence
   * @param scoreBack map containing scores and backpointers for a given constituent
   * @param seen map containing constituents in a given span
   */
  public void getPretermRules(
    List<String> sentence,
    int i,
    HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>> scoreBack,
    HashMap<Pair<Integer, Integer>, HashSet<String>> seen)
  {
    for (String A : lexicon.getAllTags()) {
      double score = lexicon.scoreTagging(sentence.get(i), A);
      addSeen(seen, i, i + 1, A);
      scoreBack.put(new Constituent<String>(A, i, i + 1), new Triplet<Constituent<String>, Constituent<String>, Double>(
        new Constituent<String>(sentence.get(i), i, i), null, score));
    }
  }

  /**
   * Scores unary rules.
   * @param begin begin index of span
   * @param end end index of span
   * @param scoreBack map containing scores and backpointers for a given constituent
   * @param onlyUnary whether we are handling unary rules for the first time
   * @param seen map containing constituents in a given span
   */
  public void handleUnaries(
    int begin,
    int end,
    HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>> scoreBack,
    boolean onlyUnary,
    HashMap<Pair<Integer, Integer>, HashSet<String>> seen)
  {
    boolean added = true;
    HashSet<String> cands = new HashSet<String>();
    HashSet<String> toAdd = seen.get(new Pair<Integer, Integer>(begin, end));

    while (added) {
      added = false;
      if (toAdd == null) break;
      cands.addAll(toAdd);
      for (String child : cands) {
        for (UnaryRule uRule : grammar.getUnaryRulesByChild(child)) {
          Constituent<String> childConstituent = new Constituent<String>(uRule.getChild(), begin, end);
          Constituent<String> parentConstituent = new Constituent<String>(uRule.getParent(), begin, end);
          Triplet<Constituent<String>, Constituent<String>, Double> scoreTriplet = scoreBack.get(childConstituent);
          double childScores = scoreTriplet == null ? 0 : scoreTriplet.getThird();
          if (!onlyUnary || childScores > 0) {
            double prob = childScores * uRule.getScore();
            scoreTriplet = scoreBack.get(parentConstituent);
            double parentScore = scoreTriplet == null ? 0 : scoreTriplet.getThird();
            if (prob > parentScore) {
              added = true;
              addSeen(seen, begin, end, parentConstituent.getLabel());
              scoreBack.put(parentConstituent, new Triplet<Constituent<String>, Constituent<String>, Double>(
                childConstituent, null, prob));
            }
          }
        }
      }
    }
  }

  /**
   * Scores binary rules.
   * @param begin begin index of span
   * @param end end index of span
   * @param scoreBack map containing scores and backpointers for a given constituent
   * @param seen map containing constituents in a given span
   */
  public void getBinaryRules(
    int begin,
    int end,
    HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>> scoreBack,
    HashMap<Pair<Integer, Integer>, HashSet<String>> seen)
  {
    for (int split = begin + 1; split < end; split++) {
      HashSet<String> seenStrings = seen.get(new Pair<Integer, Integer>(begin, split));
      HashSet<String> seenStringsEnd = seen.get(new Pair<Integer, Integer>(split, end));
      if (seenStringsEnd == null) continue;
      if (seenStrings == null) continue;
      HashSet<String> first = seenStrings;
      HashSet<String> second = seenStringsEnd;
      boolean isRightChild = false;

      if (seenStringsEnd.size() < seenStrings.size()) {
        isRightChild = true;
        first = seenStringsEnd;
        second = seenStrings;
      }

      for (String B : first) {
        List<BinaryRule> rules;
        if (isRightChild) {
          rules = grammar.getBinaryRulesByRightChild(B);

        } else {
          rules = grammar.getBinaryRulesByLeftChild(B);
        }
        for (BinaryRule bRule : rules) {
          if (isRightChild && !second.contains(bRule.getLeftChild())) {
            continue;
          } else if (!isRightChild && !second.contains(bRule.getRightChild())) {
            continue;
          }

          Constituent<String> bConstituent = new Constituent<String>(bRule.getLeftChild(), begin, split);
          Constituent<String> cConstituent = new Constituent<String>(bRule.getRightChild(), split, end);
          Constituent<String> aConstituent = new Constituent<String>(bRule.getParent(), begin, end);

          double bScore = scoreBack.get(bConstituent).getThird();
          double prob = bScore * scoreBack.get(cConstituent).getThird();

          //prior on rule
          prob *= bRule.getScore();

          Triplet<Constituent<String>, Constituent<String>, Double> aTriplet = scoreBack.get(aConstituent);
          double aScore = aTriplet == null ? 0 : aTriplet.getThird();
          if (prob > aScore) {
            addSeen(seen, aConstituent.getStart(), aConstituent.getEnd(), aConstituent.getLabel());
            scoreBack.put(aConstituent, new Triplet<Constituent<String>, Constituent<String>, Double>(
              bConstituent, cConstituent, prob));
          }
        }
      }
    }
  }

  /**
   * Adds a new label tag to the set of seen tags for a given span.
   * @param seen map containing constituents in a given span
   * @param begin begin index of span
   * @param end end index of span
   * @param label tag to be added to the hashset for this span
   */
  private void addSeen(HashMap<Pair<Integer, Integer>, HashSet<String>> seen, int begin, int end, String label) {
    if (seen.get(new Pair<Integer, Integer>(begin, end)) == null) {
      HashSet<String> toAdd = new HashSet<String>();
      toAdd.add(label);
      seen.put(new Pair<Integer, Integer>(begin, end), toAdd);
    } else {
      seen.get(new Pair<Integer, Integer>(begin, end)).add(label);
    }
  }

  /**
   * Merges a left tree and a right tree together under the current node.
   * @param t1 first tree (left)
   * @param t2 second tree (right)
   * @param currentNode name of the new tree's node
   * @return a merged tree
   */
  private Tree<String> merge(Tree<String> t1, Tree<String> t2, Constituent<String> currentNode) {
    List<Tree<String>> children = new ArrayList<Tree<String>>();
    if (t1 != null) children.add(t1);
    if (t2 != null) children.add(t2);
    return new Tree<String>(currentNode.getLabel(), children);
  }

  /**
   * Recursively build a parse tree using the scoreBack HashMap of backpointers.
   * @param currentNode the node currently being processed
   * @param scoreBack map containing scores and backpointers for a given constituent
   * @return a parse tree
   */
  private Tree<String> recursiveBuildTree(
    Constituent<String> currentNode,
    HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>> scoreBack)
  {
    if (currentNode == null) return null;
    Triplet<Constituent<String>, Constituent<String>, Double> children = scoreBack.get(currentNode);

    //at a terminal, I hope!
    if (children == null) return new Tree<String>(currentNode.getLabel());

    //else, not not a terminal
    Constituent<String> child1 = children.getFirst();
    Constituent<String> child2 = children.getSecond();

    //call recursive build to remove unary rules, as long as child1 is a nonterminal.
    Tree<String> subTree1 = recursiveBuildTree(child1, scoreBack);
    Tree<String> subTree2 = recursiveBuildTree(child2, scoreBack);
    return merge(subTree1, subTree2, currentNode);
  }

  /**
   * Build a parse tree using the scoreBack HashMap of backpointers. Note that the root node is set to "ROOT".
   * @param len length of the sentence to be parsed
   * @param scoreBack map containing scores and backpointers for a given constituent
   * @return a parse tree
   */
  private Tree<String> buildTree(
    int len,
    HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>> scoreBack)
  {
    Constituent<String> bestRoot = new Constituent<String>("ROOT", 0, len);
    return recursiveBuildTree(bestRoot, scoreBack);
  }

  /**
   * Parse a sentence according to the CKY algorithm.
   * @param sentence sentence to parse
   * @return most probable parse tree of sentence
   */
  public Tree<String> getBestParse(List<String> sentence) {

    HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>> scoreBack =
      new HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>>();
    HashMap<Pair<Integer, Integer>, HashSet<String>> seen =
      new HashMap<Pair<Integer, Integer>, HashSet<String>>();

    int len = sentence.size();

    //populate lowest layer of the parse tree
    for (int i = 0; i < len; i++) {
      getPretermRules(sentence, i, scoreBack, seen);
      handleUnaries(i, i + 1, scoreBack, true, seen);
    }

    for (int span = 2; span <= len; span++) {
      for (int begin = 0; begin <= len - span; begin++) {
        getBinaryRules(begin, begin + span, scoreBack, seen);
        handleUnaries(begin, begin + span, scoreBack, false, seen);
      }
    }
    Tree<String> bestParse = buildTree(len, scoreBack);
    return TreeAnnotations.unAnnotateTree(bestParse);
  }
}
