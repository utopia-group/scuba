import ConfigParser
import os
import os.path
import datetime
import time
from subprocess import PIPE, Popen
from tabulate import tabulate

# Script for generating report.

class Benchmark(object):

    # non-alias pairs and running time
    # 1cfa: 233/22 2obj: 22/33, etc
    results = {}
    methods = ''
    aliasPair = ''

    def __init__(self, name):
        self.name = name
        self.results = {}

    def setResult(self, key, value):
        #assert key not in self.results
        self.results[key] = value

    def getResult(self, key):
        res = 'N/A'
        if key in self.results:
            res = self.results[key]

        return res

    def setMethods(self, num):
        self.methods = num

    def getMethods(self):
        return self.methods

    def setPairs(self, num):
        self.aliasPair = num

    def getPairs(self):
        return self.aliasPair 
    
    def hasKey(self, k):
        return (k in self.results)


def main():

    logRoot = "/home/yufeng/research/xwang/Scuba/exp/10_7/"
    #projects = ['bloat'] 
    projects = ['luindex', 'antlr', 'lusearch', 'avrora', 'pmd', 'batik', 'chart', 'bloat', 'fop', 'hsqldb', 'xalan', 'sunflow']
    anaTypes = ['cfa', 'obj']
    kRanges = ['1', '2', '3', '4']
    #kRanges = ['4']
    blist = []

    # keys to grep the log file.....
    # total alias pair
    pairKey = 'Relation VValias' 
    # total reachable methods.
    reachMKey = 'Relation reachableM' 
    # Running time for kcfa or kobj
    chordTimeKey = ''
    chordNonAliasKey = 'Relation cspaVVnotalias'

    # Running time for Scuba
    scubaTimeKey = 'analysis running time' 
    scubaNonAliasKey = 'Total non-alias by scuba'
    unknow = 'N/A'

    for proj in projects:
        pj = Benchmark(proj)
        blist.append(pj)
        for k in kRanges:
            # S1 : 1H
            scubaKey = 'S' + k

            for t in anaTypes:
                log = logRoot + proj + '/' + k + t + k + 'h' + '/log.txt'
                mykey = k + t
                if os.path.isfile(log): 
                    if not pj.hasKey(scubaKey):
                        scubaTime = int(grepResult(scubaTimeKey,log, 0) / 1000)
                        if scubaTime == 0:
                            continue
                        scubaNonAlias = grepResult(scubaNonAliasKey,log, 0) 
                        pj.setResult(scubaKey, str(int(scubaNonAlias)) + '/' + str(scubaTime))

                    if pj.getMethods() == '':
                        pj.setMethods(str(int(grepResult(reachMKey,log, 1))))

                    if pj.getPairs() == '':
                        pj.setPairs(str(int(grepResult(pairKey,log, 1))))

                    chordTimeKey = 'LEAVE: cspa-k'
                    chordTime = grepResult(chordTimeKey,log, -1) 
                    chordNonAlias = grepResult(chordNonAliasKey,log, 1) 
                    # e.g., 2obj or 3cfa
                    pj.setResult(mykey, str(int(chordNonAlias)) + '/' + str(chordTime))
                else:
                    pj.setResult(mykey, unknow)
                    pj.setResult(scubaKey, unknow)

    # tabulate the result.
    print 'generating report....'
    headers = ["Project", "ReachableM", "AliasPairs"]
    content = []

    for k in kRanges:
        for t in anaTypes:
            headers.append(k+t)

        headers.append('S' + k)

    for pj in blist:
        cell = []
        pjName = pj.name
        reachM = pj.getMethods()
        pair = pj.getPairs()
        cell.append(pjName)
        cell.append(reachM)
        cell.append(pair)
        
        for k in kRanges:
            for t in anaTypes:
                cKey = k + t
                result = pj.getResult(cKey)
                cell.append(result)

            sKey = 'S' + k
            sr = pj.getResult(sKey)
            cell.append(sr)

        content.append(cell)


    report = tabulate(content, headers, tablefmt="grid")
    print report


    # TODO
    # 1. Runing experiments on chord's kcfa and kobj where k from 1 to 4
    # 1.1. Generate some common information, e.g, reachable methods, etc. 

    # 1.2. Running analysis on all benchmarks and settings in chord.

    # 2. Running scuba's H from 1 to 4.
    # 2.1. Running analysis on all benchmarks and settings in Scuba.


# Give me a keyword and log location, i will grep the result for you.
def grepResult(keyW, log, index):
    grep = 'grep ' + "'" + keyW + "' " + log
    # special case for grep chord's running time
    if index == -1:
        grep = 'grep -A 2 ' + "'" + keyW + "' " + log
        
    print grep
    p = Popen(grep, stdout=PIPE, shell=True)
    (output, err) = p.communicate()

    print output
    l = []
    for t in output.split():
        try:
            l.append(float(t))
        except ValueError:
            pass

    if index == -1:
        arr = output.split(" ")
        stime = 'N/A'
        if len(arr) > 2:
            tt = arr[6]
            x = time.strptime(tt, '%H:%M:%S:%f')
            delta = datetime.timedelta(hours=x.tm_hour,minutes=x.tm_min,seconds=x.tm_sec).total_seconds()
            stime = str(int(delta))
        return stime

    res = 0;
    if len(l) == 0:
        return res

    if index == 1:
        res = l[1]

    if index == 0:
        res = l[0]

    return res

if __name__ == "__main__":
        main()


