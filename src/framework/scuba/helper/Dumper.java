package framework.scuba.helper;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.field.FieldSelector;
import framework.scuba.domain.memgraph.MemEdge;
import framework.scuba.domain.memgraph.MemGraph;
import framework.scuba.domain.memgraph.MemNode;

public class Dumper {

	private static Dumper instance = new Dumper();

	public static Dumper d() {
		return instance;
	}

	public void dumpSumToFile(MemGraph memGraph, String file) {
		StringBuilder sb = new StringBuilder("digraph Summary {\n");
		sb.append("  rankdir = LR;\n");
		// label nodes
		Set<MemNode> nodes = new HashSet<MemNode>();
		Iterator<MemEdge> it = memGraph.sumEdgesIterator();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			nodes.add(edge.getSrc());
			nodes.add(edge.getTgt());
		}
		for (MemNode node : nodes) {
			if (node.isAPNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=circle,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else if (node.isAllocNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=rectangle,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else if (node.isGlobalNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=oval,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else if (node.isPropLocalVarNode() || node.isAppLocalVarNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=triangle,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else if (node.isLibLocalVarNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=triangle,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else if (node.isParamNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=oval,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else if (node.isRetNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=diamond,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else {
				assert false : node;
			}
		}
		// label edges
		it = memGraph.sumEdgesIterator();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			MemNode src = edge.getSrc();
			MemNode tgt = edge.getTgt();
			FieldSelector field = edge.getField();
			BoolExpr cst = edge.getCst();
			sb.append("  ").append("\"" + src.getNumber() + "\"");
			sb.append(" -> ").append("\"" + tgt.getNumber() + "\"")
					.append(" [label=\"");
			sb.append("(" + field + "," + cst + ")");
			sb.append("\"]\n");
		}

		sb.append("}\n");
		// write to file
		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + file + ".dot"));
			bufw.write(sb.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public void dumpHeapToFile(MemGraph memGraph, String file) {
		StringBuilder sb = new StringBuilder("digraph Heap {\n");
		sb.append("  rankdir = LR;\n");
		// label nodes
		Set<MemNode> nodes = new HashSet<MemNode>();
		Iterator<MemEdge> it = memGraph.heapEdgesIterator();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			nodes.add(edge.getSrc());
			nodes.add(edge.getTgt());
		}
		for (MemNode node : nodes) {
			if (node.isAPNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=circle,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else if (node.isAllocNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=rectangle,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else if (node.isGlobalNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=oval,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else if (node.isPropLocalVarNode() || node.isAppLocalVarNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=triangle,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else if (node.isLibLocalVarNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=triangle,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else if (node.isParamNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=oval,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else if (node.isRetNode()) {
				sb.append("  ").append("\"" + node.getNumber() + "\"");
				sb.append(" [shape=diamond,label=\"");
				sb.append(node.toString());
				sb.append("\"];\n");
			} else {
				assert false : node;
			}
		}
		// label edges
		it = memGraph.heapEdgesIterator();
		while (it.hasNext()) {
			MemEdge edge = it.next();
			MemNode src = edge.getSrc();
			MemNode tgt = edge.getTgt();
			FieldSelector field = edge.getField();
			BoolExpr cst = edge.getCst();
			sb.append("  ").append("\"" + src.getNumber() + "\"");
			sb.append(" -> ").append("\"" + tgt.getNumber() + "\"")
					.append(" [label=\"");
			sb.append("(" + field + "," + cst + ")");
			sb.append("\"]\n");
		}
		sb.append("}\n");
		// write to file
		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + file + ".dot"));
			bufw.write(sb.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

}
