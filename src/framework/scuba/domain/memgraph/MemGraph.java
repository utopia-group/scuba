package framework.scuba.domain.memgraph;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import joeq.Class.jq_Method;
import framework.scuba.controller.MemGraphController;

public abstract class MemGraph {

	// controller controlling this memory graph
	protected final MemGraphController controller;

	// all nodes in the heap
	protected final Set<MemNode> heap = new HashSet<MemNode>();
	// all edges in the heap
	protected final Set<MemEdge> heapEdges = new HashSet<MemEdge>();
	// all nodes in the summary
	public final Set<MemNode> summary = new HashSet<MemNode>();
	// all edges in the summary
	public final Set<MemEdge> sumEdges = new HashSet<MemEdge>();

	// flags used for SCC termination
	protected boolean terminated = false;
	protected boolean hasBeenAnalyzed = false;

	public MemGraph(MemGraphController controller) {
		this.controller = controller;
	}

	public MemGraphController getController() {
		return controller;
	}

	/*----------- termination -----------*/
	public boolean isTerminated() {
		return terminated;
	}

	public void setTerminated() {
		this.terminated = true;
	}

	public boolean hasBeenAnalyzed() {
		return hasBeenAnalyzed;
	}

	public void setHasBeenAnalyzed() {
		this.hasBeenAnalyzed = true;
	}

	/*----------- memory graph operations -----------*/
	public abstract jq_Method getMethod();

	public Set<MemNode> getSummary() {
		return summary;
	}

	public Iterator<MemNode> sumNodesIterator() {
		return summary.iterator();
	}

	public Iterator<MemNode> heapNodesIterator() {
		return heap.iterator();
	}

	public Iterator<MemEdge> sumEdgesIterator() {
		return sumEdges.iterator();
	}

	public Iterator<MemEdge> heapEdgesIterator() {
		return heapEdges.iterator();
	}

	public boolean hasHeapNode(MemNode node) {
		return heap.contains(node);
	}

	public boolean hasHeapEdge(MemEdge edge) {
		return sumEdges.contains(edge);
	}

	public boolean hasSumNode(MemNode node) {
		return summary.contains(node);
	}

	public boolean hasSumEdge(MemEdge edge) {
		return sumEdges.contains(edge);
	}

	public void addHeapEdge(MemEdge edge) {
		heapEdges.add(edge);
	}

	public void addSumEdge(MemEdge edge) {
		sumEdges.add(edge);
	}

	// this is the MOST important method for MemGraph
	public void addHeapNode(MemNode node) {
		heap.add(node);
	}

	public void addSumNode(MemNode node) {
		summary.add(node);
	}

	public void clearSumNodes() {
		summary.clear();
	}

	public void clearSumEdges() {
		sumEdges.clear();
	}

	public int getHeapNodesNum() {
		return heap.size();
	}

	public int getSumNodesNum() {
		return summary.size();
	}

	public int getHeapEdgesNum() {
		return heapEdges.size();
	}

	public int getSumEdgesNum() {
		return sumEdges.size();
	}
}
