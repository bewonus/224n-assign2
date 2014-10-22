package cs224n.assignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cs224n.ling.Tree;
import cs224n.ling.Trees;
import cs224n.ling.Trees.MarkovizationAnnotationStripper;
import cs224n.util.Filter;

/**
 * Class which contains code for annotating and binarizing trees for
 * the parser's use, and debinarizing and unannotating them for
 * scoring.
 */
public class TreeAnnotations {

  /**
   * Return a binarized tree with the appropriate vertical and horizontal markovization orders.
   * @param unAnnotatedTree
   * @param v vertical markovization order (default is 1)
   * @param h horizontal markovization order (default is -1)
   * @return
   */
	public static Tree<String> annotateTree(Tree<String> unAnnotatedTree, int v, int h) {

    // No horizontal markovization
    if (h == -1) {
      switch (v) {
        case 1:
          // First order vertical markovization (just a binary tree; this is the default)
          // v = 1, h = infinity tree (-1)
          return binarizeTree(unAnnotatedTree);
        case 2:
          // Second order vertical markovization (marks nodes with their parent tag, i.e. NP^S)
          // v = 2, h = infinity tree (-1)
          return binarizeTree(secondVerticalMarkovization(unAnnotatedTree, null));
        case 3:
          // Third order vertical markovization (marks nodes with their parent and grandparent tags, i.e. N^NP^S)
          // v = 3, h = infinity tree (-1)
          return binarizeTree(thirdVerticalMarkovization(unAnnotatedTree, null, null));
        default:
          return null;
      }
    } else { // Use horizontal markovization

      // First order horizontal markovization (maintains one node of context, i.e. S->...NP, but stored as @S->_NP)
      Tree<String> hTree = horizontalMarkovization(unAnnotatedTree);
      switch (v) {
        case 1:
          // Only horizontal markovization; v = 1, h = 1 tree
          return hTree;
        case 2:
          // Mixed v = 2, h = 1 tree
          return secondVerticalMarkovization(hTree, null);
        case 3:
          // Mixed v = 3, h = 1 tree
          return thirdVerticalMarkovization(hTree, null, null);
        default:
          return null;
      }
    }

	}

  /**
   * First order horizontal markovization. Marks tags according to the description in the
   * Klein, Manning paper.
   */
  private static Tree<String> horizontalMarkovization(Tree<String> tree) {
    String label = tree.getLabel();
    if (tree.isLeaf())
      return new Tree<String>(label);
    if (tree.getChildren().size() == 1) {
      return new Tree<String>
        (label,
          Collections.singletonList(horizontalMarkovization(tree.getChildren().get(0))));
    }
    // otherwise, it's a binary-or-more local tree,
    // so decompose it into a sequence of binary and unary trees.
    String intermediateLabel = "@"+label+"->";
    Tree<String> intermediateTree =
      horizontalMarkovizationHelper(tree, 0, intermediateLabel);
    return new Tree<String>(label, intermediateTree.getChildren());
  }

  /**
   * Helper method for the horizontal markovization method.
   */
  private static Tree<String> horizontalMarkovizationHelper(
    Tree<String> tree,
    int numChildrenGenerated,
    String intermediateLabel)
  {
    Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
    List<Tree<String>> children = new ArrayList<Tree<String>>();
    children.add(horizontalMarkovization(leftTree));

    int end = intermediateLabel.indexOf(">") + 1;
    String newLabel = (end == 0)
      ? intermediateLabel
      : intermediateLabel.substring(0, end);

    if (numChildrenGenerated < tree.getChildren().size() - 1) {
      Tree<String> rightTree =
        horizontalMarkovizationHelper(tree, numChildrenGenerated + 1,
          newLabel + "_" + leftTree.getLabel());
      children.add(rightTree);
    }
    return new Tree<String>(intermediateLabel, children);
  }



  /**
   * Second order vertical markovization. Marks all non-terminal nodes (including preterminals,
   * but not the ROOT or terminals) with the label of their parent tag.
   */
  private static Tree<String> secondVerticalMarkovization(Tree<String> tree, String parentTag) {
    if (tree.isLeaf()) {
      return new Tree<String>(tree.getLabel());
    }
    List<Tree<String>> children = new ArrayList<Tree<String>>();
    for (Tree<String> child : tree.getChildren()) {
      children.add(secondVerticalMarkovization(child, tree.getLabel()));
    }
    if (parentTag != null) {
      tree.setLabel(tree.getLabel() + "^" + parentTag);
    }

    return new Tree<String>(tree.getLabel(), children);
  }

  /**
   * Third order vertical markovization. Marks all non-terminals (including preterminals,
   * but not the ROOT or terminals) with the label of their parent tag and the label of their
   * grandparent tag.
   */
  private static Tree<String> thirdVerticalMarkovization(Tree<String> tree, String parentTag, String grandParentTag) {
    if (tree.isLeaf()) {
      return new Tree<String>(tree.getLabel());
    }
    List<Tree<String>> children = new ArrayList<Tree<String>>();
    for (Tree<String> child : tree.getChildren()) {
      children.add(thirdVerticalMarkovization(child, tree.getLabel(), parentTag));
    }
    if (parentTag != null) {
      tree.setLabel(tree.getLabel() + "^" + parentTag);
    }
    if (grandParentTag != null) {
      tree.setLabel(tree.getLabel() + "^" + grandParentTag);
    }

    return new Tree<String>(tree.getLabel(), children);
  }


	private static Tree<String> binarizeTree(Tree<String> tree) {
		String label = tree.getLabel();
		if (tree.isLeaf())
			return new Tree<String>(label);
		if (tree.getChildren().size() == 1) {
			return new Tree<String>
			(label, 
					Collections.singletonList(binarizeTree(tree.getChildren().get(0))));
		}
		// otherwise, it's a binary-or-more local tree, 
		// so decompose it into a sequence of binary and unary trees.
		String intermediateLabel = "@"+label+"->";
		Tree<String> intermediateTree =
				binarizeTreeHelper(tree, 0, intermediateLabel);
		return new Tree<String>(label, intermediateTree.getChildren());
	}

	private static Tree<String> binarizeTreeHelper(Tree<String> tree,
			int numChildrenGenerated, 
			String intermediateLabel) {
		Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
		List<Tree<String>> children = new ArrayList<Tree<String>>();
		children.add(binarizeTree(leftTree));
		if (numChildrenGenerated < tree.getChildren().size() - 1) {
			Tree<String> rightTree = 
					binarizeTreeHelper(tree, numChildrenGenerated + 1, 
							intermediateLabel + "_" + leftTree.getLabel());
			children.add(rightTree);
		}
		return new Tree<String>(intermediateLabel, children);
	} 

	public static Tree<String> unAnnotateTree(Tree<String> annotatedTree) {

		// Remove intermediate nodes (labels beginning with "@"
		// Remove all material on node labels which follow their base symbol
		// (cuts at the leftmost - or ^ character)
		// Examples: a node with label @NP->DT_JJ will be spliced out, 
		// and a node with label NP^S will be reduced to NP

		Tree<String> debinarizedTree =
				Trees.spliceNodes(annotatedTree, new Filter<String>() {
					public boolean accept(String s) {
						return s.startsWith("@");
					}
				});
		Tree<String> unAnnotatedTree = 
				(new Trees.FunctionNodeStripper()).transformTree(debinarizedTree);
    Tree<String> unMarkovizedTree =
        (new Trees.MarkovizationAnnotationStripper()).transformTree(unAnnotatedTree);
		return unMarkovizedTree;
	}
}
