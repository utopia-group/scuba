package framework.scuba.utils;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class Node {

	private String id;

	private boolean terminated;

	private boolean selfLoop;

	Set<Node> successors;

	Set<Node> preds;

	public Node(String nodeId) {
		id = nodeId;
		preds = new TreeSet<Node>(new Comparator<Node>() {
			public int compare(Node a, Node b) {
				return a.toString().compareTo(b.toString());
			}
		});
		successors = new TreeSet<Node>(new Comparator<Node>() {
			public int compare(Node a, Node b) {
				return a.toString().compareTo(b.toString());
			}
		});
	}

	public Set<Node> getSuccessors() {
		return successors;
	}

	public Set<Node> getPreds() {
		return preds;
	}

	public void addPred(Node pred) {
		preds.add(pred);
	}

	public void addSuccessor(Node succ) {
		successors.add(succ);
	}

	public boolean isTerminated() {
		return terminated;
	}

	public void setTerminated(boolean terminated) {
		this.terminated = terminated;
	}

	public boolean isSelfLoop() {
		return selfLoop;
	}

	public void setSelfLoop(boolean selfLoop) {
		this.selfLoop = selfLoop;
	}

	public String toString() {
		return "[Node:]" + id;
	}
}
