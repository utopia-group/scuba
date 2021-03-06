package framework.scuba.helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;

public class SCCHelper {

	protected final List<Set<BasicBlock>> componentList = new ArrayList<Set<BasicBlock>>();

	protected int index = 0;

	protected Map<Object, Integer> indexForNode, lowlinkForNode;

	protected Stack<BasicBlock> s;

	protected ControlFlowGraph g;

	/**
	 * @param g
	 *            : a CFG for which we want to compute the strongly connected
	 *            components.
	 */
	public SCCHelper(ControlFlowGraph g, Set<BasicBlock> roots) {
		this.g = g;
		s = new Stack<BasicBlock>();
		Set<BasicBlock> heads = roots;

		indexForNode = new HashMap<Object, Integer>();
		lowlinkForNode = new HashMap<Object, Integer>();

		for (Iterator<BasicBlock> headsIt = heads.iterator(); headsIt.hasNext();) {
			Object head = headsIt.next();
			if (!indexForNode.containsKey(head)) {
				recurse((BasicBlock) head);
			}
		}

		// free memory
		indexForNode = null;
		lowlinkForNode = null;
		s = null;
		g = null;
	}

	protected void recurse(BasicBlock v) {
		indexForNode.put(v, index);
		lowlinkForNode.put(v, index);
		index++;
		s.push(v);

		for (BasicBlock succ : v.getSuccessors()) {
			if (!indexForNode.containsKey(succ)) {
				recurse(succ);
				lowlinkForNode.put(
						v,
						Math.min(lowlinkForNode.get(v),
								lowlinkForNode.get(succ)));
			} else if (s.contains(succ)) {
				lowlinkForNode
						.put(v,
								Math.min(lowlinkForNode.get(v),
										indexForNode.get(succ)));
			}
		}
		if (lowlinkForNode.get(v).intValue() == indexForNode.get(v).intValue()) {
			Set<BasicBlock> scc = new HashSet<BasicBlock>();
			BasicBlock v2;
			do {
				v2 = s.pop();
				scc.add(v2);
			} while (v != v2);
			componentList.add(scc);
		}
	}

	/**
	 * @return the list of the strongly-connected components
	 */
	public List<Set<BasicBlock>> getComponents() {
		return componentList;
	}
}