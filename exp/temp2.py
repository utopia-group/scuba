import ConfigParser
import os
from subprocess import PIPE, Popen

# Script for running experiments on Chord and Scuba. 
# Make sure rebuild scuba.jar when you run this script.

def main():

    dacapoRoot = '/home/yufeng/research/benchmarks/ScubaFinal/benchmarks/'
    projects = ['luindex', 'antlr', 'hedc', 'avrora', 'polyglot', 'toba-s', 'weblech', 'xalan', 'hsqldb', 'sunflow']
    k = 0
    h = 2

    for proj in projects:
        benchLoc = dacapoRoot + proj
        runBenchmark(proj, benchLoc, str(k), str(k), str(h), 'obj')

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

    chordLoc = config.get("default", "chordLoc")
    chordDir = logLoc + benchName + '/' + kval + chordType + scubaH + 'h' 
    print "----------Begin one iteration----------------------"
    print "----------Runtime information----------------------"
    print "Benchmark: " + benchName
    print "Benchmark Location: " + benchLoc
    print "Scuba's analysis: " + scubaH + "h"
    print "Logs Location: " + chordDir


    grepLoc = chordDir + "/log.txt"

    cmd = 'java -cp ' + chordLoc + ' -Dchord.work.dir=' + benchLoc + ' -Dchord.out.dir=' + chordDir + ' -Dchord.run.analyses=cipa-java,cipa-downcast-java,cipa-alias-resolve-dlog,sum-java -Dchord.scuba.H=' + scubaH + ' -Dchord.scuba.proj=' + benchName  + ' chord.project.Boot'

    print cmd 
    #process = Popen(cmd.split(" "), preexec_fn=os.setsid, stdout=PIPE, stderr=PIPE)
    os.system(cmd)

    # Dump all experiment results
    print "result for downcast analysis-----------------------------"
    grepdc = "grep '\[downcast-result\]' " + grepLoc
    os.system(grepdc)

    print "result for may-alias anlysis-----------------------------"
    grepMay = "grep '\[mayAlias-result\]' " + grepLoc
    os.system(grepMay)

    print "Scuba's running time:----------------------"
    grepScubaTime = "grep 'running time' " + grepLoc
    os.system(grepScubaTime)
    print "----------Finish one iteration----------------------"

if __name__ == "__main__":
        main()

