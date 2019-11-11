import ConfigParser
import os
from subprocess import PIPE, Popen

# Script for running experiments on Chord and Scuba. 
# Make sure rebuild scuba.jar when you run this script.

def main():

    dacapoRoot = '/home/yufeng/research/benchmarks/pjbench-read-only/dacapo/benchmarks/'
    #projects = ['hsqldb', 'luindex', 'antlr', 'lusearch', 'avrora', 'pmd', 'xalan']
    projects = ['luindex', 'antlr', 'lusearch', 'avrora', 'pmd', 'fop', 'bloat', 'batik','chart']
    anaTypes = ['cfa', 'obj']

    for k in [1,2,3,4]:
        for proj in projects:
            benchLoc = dacapoRoot + proj
            runBenchmark(proj, benchLoc, str(k), str(k), str(k), 'cfa')
            runBenchmark(proj, benchLoc, str(k), str(k), str(k), 'obj')

def runBenchmark(benchName, benchLoc, kval, hval, scubaH, chordType):
    # Default information from Chord.
    #chord.max.heap=16g
    #chord.scope.exclude=java.util.,sun.,com.sun.,com.ibm.,org.apache.harmony.
    #chord.kcfa.k=1
    #chord.kobj.k=1
    #chord.ctxt.kind=cs

    # Running information from user.
    config = ConfigParser.RawConfigParser()
    config.read('config.property')
    logLoc = config.get("default", "log")

    #chordType = config.get("default", "type")
    #kval = config.get("default", "chordK")
    #hval = config.get("default", "chordH")
    #scubaH = config.get("default", "scubaH")
    #benchLoc = config.get("default", "benchLoc")
    #benchName = config.get("default", "benchName")

    chordLoc = config.get("default", "chordLoc")
    chordDir = logLoc + benchName + '/' + kval + chordType + hval + 'h' 
    print "----------Begin one iteration----------------------"
    print "----------Runtime information----------------------"
    print "Benchmark: " + benchName
    print "Benchmark Location: " + benchLoc
    print "Chord's analysis: " + kval + chordType + hval + "h"
    print "Scuba's analysis: " + scubaH + "h"
    print "Logs Location: " + chordDir


    kobjK = kval
    kcfaK = kval
    ## cs for kcfa and co for kobj
    ctxtKind = 'cs'
    ## using kcfa by default
    dlog = 'cspa-kcfa-dlog'

    #kobj
    if(chordType == 'obj'):
        dlog = 'cspa-kobj-dlog'
        ctxtKind = 'co'

    grepLoc = chordDir + "/log.txt"

    cmd = 'java -cp ' + chordLoc + ' -Dchord.work.dir=' + benchLoc + ' -Dchord.out.dir=' + chordDir + ' -Dchord.run.analyses=cipa-java,' + dlog + ',vv-refine-java,cspa-downcast-java,cspa-query-resolve-dlog,sum-java' + ' -Dchord.kcfa.k=' + kcfaK + ' -Dchord.kobj.k=' + kobjK +  ' -Dchord.ctxt.kind=' + ctxtKind +  ' -Dchord.scuba.H=' + scubaH + ' chord.project.Boot'

    print cmd 
    #process = Popen(cmd.split(" "), preexec_fn=os.setsid, stdout=PIPE, stderr=PIPE)
    os.system(cmd)

    # Dump all experiment results
    print "result for downcast analysis-----------------------------"
    grepdc = "grep '\[downcast-result\]' " + grepLoc
    os.system(grepdc)

    print "result for may-alias anlysis-----------------------------"
    print "Chord's may-alias result---------------"
    grepMayChord = "grep 'Relation cspaVVnotalias' " + grepLoc
    os.system(grepMayChord)

    grepMay = "grep '\[mayAlias-result\]' " + grepLoc
    os.system(grepMay)

    print "result for resolving virtual call-----------------------------"
    grepVirt = "grep '\[VirtCall-result\]' " + grepLoc
    os.system(grepVirt)

    print "Scuba's running time:----------------------"
    grepScubaTime = "grep 'running time' " + grepLoc
    os.system(grepScubaTime)

    print "Chord's running time:----------------------"
    grepChordTime = "grep -A 2 'LEAVE: '" + dlog + ' ' + grepLoc
    os.system(grepChordTime)
    print "----------Finish one iteration----------------------"

if __name__ == "__main__":
        main()

