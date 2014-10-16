package cs224n.assignment;

import cs224n.assignment.Grammar.UnaryRule;
import cs224n.ling.Tree;
import cs224n.math.SloppyMath;
import cs224n.util.Counter;
import cs224n.util.Triplet;

import java.util.*;

/**
 * The CKY PCFG Parser you will implement.
 */
public class PCFGParser implements Parser {

    private Grammar grammar;
    private Lexicon lexicon;

  /**
   * Build a lexicon and grammar from training trees.
   * @param trainTrees list of annotated trees to train on
   */
    public void train(List<Tree<String>> trainTrees) {

      // Binarize the training trees
      for (int i = 0; i < trainTrees.size(); i++) {
        trainTrees.set(i, TreeAnnotations.annotateTree(trainTrees.get(i)));
      }

      // Build lexicon and grammar
      lexicon = new Lexicon(trainTrees);
      grammar = new Grammar(trainTrees);
    }

  /**
   * Parse a sentence according to the CKY algorithm.
   * @param sentence sentence to parse
   * @return most probable parse tree of sentence
   */
    public Tree<String> getBestParse(List<String> sentence) {

      // Stores log-probability scores associated with a given span and tag (triple)
      Counter<Triplet<Integer, Integer, String>> scores =
        new Counter<Triplet<Integer, Integer, String>>();

      // TODO: setup back pointers data structure

      // Iterate through words and lexicon and add first layer of scores
      int numWords = sentence.size();
      for (int i = 0; i < numWords; i++) {
        for (String tag : lexicon.tagCounter.keySet()) {
          scores.incrementCount(
            new Triplet<Integer, Integer, String>(i, i+1, sentence.get(i)),
            Math.log(lexicon.scoreTagging(sentence.get(i), tag)));
        }

        // Handle unaries
        boolean added = true;
        while (added) {
          added = false;

          // Iterate over all grammar rules (uses both for loops)
          for (String child : grammar.unaryRulesByChild.keySet()) {
            for (UnaryRule unaryRule : grammar.getUnaryRulesByChild(child)) {
              double prob = SloppyMath.logAdd(
                Math.log(unaryRule.getScore()),
                scores.getCount(new Triplet<Integer, Integer, String>(i, i+1, child)));
              Triplet<Integer, Integer, String> parentTriplet =
                new Triplet<Integer, Integer, String>(i, i+1, unaryRule.getParent());

              // Update scores if we've found a better unary promotion rule
              if (prob > scores.getCount(parentTriplet)) {
                scores.setCount(parentTriplet, prob);

                // TODO: update back pointers

                added = true;
              }
            }
          }
        }
      }

      // TODO: finish the algo!


      return null;
    }
}
