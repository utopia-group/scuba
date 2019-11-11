package framework.scuba.helper;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Method;

public class G {
	// analyze each method exactly once
	public static boolean analyzeOnce = false;
	// fix point for instantiation
	public static boolean instnFixPoint = true;
	// fix point for all <clinit>'s
	public static boolean clinitFixPoint = true;
	// transitive closure
	public static boolean doTranClosure = true;

	// options
	public static int dumpCounter = 0;
	public static int countScc = 0;
	public static boolean tuning = true;
	public static boolean dump = true;
	public static boolean handleStmt = true;
	public static boolean instn = true;
	public static boolean dumpToFile = false;
	public static boolean dumpTopMethodsToFile = false;
	public static boolean dumpConclusionToFile = false;
	public static boolean warning = false;
	public static boolean assertion = false;
	public static boolean check = false;
	public static Map<jq_Method, Integer> dumpMap = new HashMap<jq_Method, Integer>();
	public static String dotOutputPath = "/home/yufeng/research/xwang/Scuba/output/";

	// time
	public static long instnTime = 0;
	public static long intraTime = 0;
	public static long instnNodeTime = 0;
	public static long instnCstTime = 0;
	public static long instnEdgeTime = 0;
	public static long ccldNodeTime = 0;
	public static long ccldCstTime = 0;
	public static long ccldEdgeTime = 0;
	// weak update efficiency
	public static long totalWK = 0;
	public static long wk = 0;
	public static long doneWK = 0;
	public static long trueDoneWK = 0;
	// instnSkip effectiveness
	public static long hit = 0;
	public static long hitNodeCache = 0;
	public static long hitCstCache = 0;
	public static long hitHashTypeCache = 0;
	public static long hitEdgeCache = 0;
	public static long ccldHit = 0;
	public static long ccldHitNodeCache = 0;
	public static long ccldHitCstCache = 0;
	public static long ccldHitEdgeCache = 0;
	public static Map<Integer, Integer> instnIts = new HashMap<Integer, Integer>();
	// cipa summary
	public static int constructNum = 0;
	public static boolean collect = false;
	public static StringBuilder readDetails = new StringBuilder();

	// debug
	public static boolean debug = true;

}