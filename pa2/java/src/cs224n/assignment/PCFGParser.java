package cs224n.assignment;

import cs224n.assignment.Grammar.BinaryRule;
import cs224n.assignment.Grammar.UnaryRule;
import cs224n.ling.Tree;
import cs224n.util.Counter;
import cs224n.util.Pair;
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

      // Stores set of tags based on their span (i.e. which
      // tags are in a specific square of the parse triangle)
      HashMap<Pair<Integer, Integer>, Set<String>> spanToTags = new HashMap<Pair<Integer, Integer>, Set<String>>();

      // TODO: setup back pointers data structure

      // Iterate through words and lexicon and add first layer of scores to parse triangle
      int numWords = sentence.size();
      for (int i = 0; i < numWords; i++) {
        for (String tag : lexicon.tagCounter.keySet()) {
          scores.incrementCount(
            new Triplet<Integer, Integer, String>(i, i+1, sentence.get(i)),
            Math.log(lexicon.scoreTagging(sentence.get(i), tag)));

          // Add tag to the span map
          Pair<Integer, Integer> span = new Pair<Integer, Integer>(i, i+1);
          Set<String> tags = spanToTags.get(span);
          if (tags == null) {
            tags = new HashSet<String>();
          }
          tags.add(tag);
          spanToTags.put(span, tags);
        }

        // Handle unaries
        boolean added = true;
        while (added) {
          added = false;

          // Iterate over all grammar rules (uses both for loops)
          for (String child : grammar.unaryRulesByChild.keySet()) {
            for (UnaryRule unaryRule : grammar.getUnaryRulesByChild(child)) {
              double prob = Math.log(unaryRule.getScore()) +
                scores.getCount(new Triplet<Integer, Integer, String>(i, i+1, child));
              Triplet<Integer, Integer, String> parentTriplet =
                new Triplet<Integer, Integer, String>(i, i+1, unaryRule.getParent());

              // Update scores if we've found a better unary promotion rule
              if (prob > scores.getCount(parentTriplet)) {
                scores.setCount(parentTriplet, prob);

                // Add tag to the span map
                Pair<Integer, Integer> spanKey = new Pair<Integer, Integer>(i, i+1);
                Set<String> tags = spanToTags.get(spanKey);
                if (tags == null) {
                  tags = new HashSet<String>();
                }
                tags.add(unaryRule.getParent());
                spanToTags.put(spanKey, tags);

                // TODO: update back pointers

                added = true;
              }
            }
          }
        }
      }

      // Fill in the rest of the parse triangle scores
      for (int span = 2; span <= numWords; span++) {
        for (int begin = 0; begin <= numWords - span; begin++) {
          int end = begin + span;
          for (int split = begin + 1; split <= end - 1; split++) {

            // Get left children and right children, and all potential binary rules from these
            Set<String> leftChildren = spanToTags.get(new Pair<Integer, Integer>(begin, split));
            Set<String> rightChildren = spanToTags.get(new Pair<Integer, Integer>(split, end));
            Set<BinaryRule> leftRules = new HashSet<BinaryRule>();
            Set<BinaryRule> rightRules = new HashSet<BinaryRule>();
            for (String leftChild : leftChildren) {
              leftRules.addAll(grammar.getBinaryRulesByLeftChild(leftChild));
            }
            for (String rightChild : rightChildren) {
              rightRules.addAll(grammar.getBinaryRulesByRightChild(rightChild));
            }

            // Keep all rules in leftRules intersect rightRules
            // (these are the relevant rules to check)
            leftRules.retainAll(rightRules);

            // Iterate through relevant rules and update scores
            for (BinaryRule binaryRule : leftRules) {
              double prob = scores.getCount(new Triplet<Integer, Integer, String>(
                begin, split, binaryRule.getLeftChild())) +
                scores.getCount(new Triplet<Integer, Integer, String>(
                  split, end, binaryRule.getRightChild()));

              // Update scores if we've found a more probable parse
              Triplet<Integer, Integer, String> parentTriple = new Triplet<Integer, Integer, String>(begin, end, binaryRule.getParent());
              if (prob > scores.getCount(parentTriple)) {
                scores.setCount(parentTriple, prob);

                // Add tag to the span map
                Pair<Integer, Integer> spanKey = new Pair<Integer, Integer>(begin, end);
                Set<String> tags = spanToTags.get(spanKey);
                if (tags == null) {
                  tags = new HashSet<String>();
                }
                tags.add(binaryRule.getParent());
                spanToTags.put(spanKey, tags);

                // TODO: update back pointers

              }
            }
          }

          // Handle unaries
          boolean added = true;
          while (added) {
            added = false;

            // Iterate over all grammar rules (uses both for loops)
            for (String child : grammar.unaryRulesByChild.keySet()) {
              for (UnaryRule unaryRule : grammar.getUnaryRulesByChild(child)) {
                double prob = Math.log(unaryRule.getScore()) +
                  scores.getCount(new Triplet<Integer, Integer, String>(begin, end, child));
                Triplet<Integer, Integer, String> parentTriplet =
                  new Triplet<Integer, Integer, String>(begin, end, unaryRule.getParent());

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
      }

      // TODO: build tree
      return null;
    }
}
