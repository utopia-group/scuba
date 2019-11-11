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
    # result map for mayalias
    results = {}
    methods = ''
    aliasPair = ''
    stmt = ''
    allcast = ''
    allvirt = ''
    downcast = {}
    virt = {}
    may = {}
    time = {}

    def __init__(self, name):
        self.name = name
        self.results = {}
        self.downcast = {}
        self.virt = {}
        self.may = {}
        self.time = {}

    def setResult(self, key, value):
        #assert key not in self.results
        self.results[key] = value

    def getResult(self, key):
        res = 'N/A'
        if key in self.results:
            res = self.results[key]

        return res

    def setTime(self, key, value):
        #assert key not in self.results
        self.time[key] = value

    def getTime(self, key):
        res = 'N/A'
        if key in self.time:
            res = self.time[key]

        return res

    def setMay(self, key, value):
        #assert key not in self.results
        self.may[key] = value

    def getMay(self, key):
        res = 'N/A'
        if key in self.may:
            res = self.may[key]

        return res

    def setDowncast(self, key, value):
        #assert key not in self.results
        self.downcast[key] = value

    def getDowncast(self, key):
        res = 'N/A'
        if key in self.downcast:
            res = self.downcast[key]

        return res

    def setVirt(self, key, value):
        #assert key not in self.results
        self.virt[key] = value

    def getVirt(self, key):
        res = 'N/A'
        if key in self.virt:
            res = self.virt[key]

        return res

    def setMethods(self, num):
        self.methods = num

    def getMethods(self):
        return self.methods

    def setAllvirt(self, num):
        self.allvirt = num

    def getAllvirt(self):
        return self.allvirt

    def setAllcast(self, num):
        self.allcast= num

    def getAllcast(self):
        return self.allcast

    def setPairs(self, num):
        self.aliasPair = num

    def getPairs(self):
        return self.aliasPair 

    def setStmt(self, num):
        self.stmt = num

    def getStmt(self):
        return self.stmt
    
    def hasKey(self, k):
        return (k in self.results)

    def hasCastKey(self, k):
        return (k in self.downcast)

    def hasVirtKey(self, k):
        return (k in self.virt)

def avg(num):
    return round(num/10.0)

def avg2(s):
    if s == 'N/A':
        return s
    else:
        num = int(s)
        return round(num/10.0)

def main():

    logRoot = "/home/yufeng/research/xwang/Scuba/exp/11_4/"
    #projects = ['sunflow'] 
    no4Objs = ['luindex', 'hedc', 'weblech', 'hsqldb', 'sunflow']
    projects = ['luindex', 'antlr', 'hedc', 'avrora', 'polyglot', 'toba-s', 'weblech', 'xalan', 'hsqldb', 'sunflow']
    anaTypes = ['cfa', 'obj']
    #kRanges = ['2', '3', '4']
    kRanges = ['2']
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
    scubaConcludeKey = 'Conclusion running time' 
    scubaNonAliasKey = 'Total non-alias by scuba'
    # total downcast key
    allCastKey = 'Number of downcast'
    chordCastKey = 'safe cast by chord'
    scubaCastKey = 'safe cast by scuba'
    # total virtual calls key
    allVirtKey = 'Total virtual callsites'
    chordVirtKey = 'Chord(Single tgt)'
    scubaVirtKey = 'Scuba(Single tgt)'
    stmtKey = 'Total quads:'

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
                    if (k=='4' and (proj in no4Objs)) or (proj == 'sunflow' and k=='3'):
                        newK = '1'
                        log = logRoot + proj + '/' + newK + t + k + 'h' + '/log.txt'
                        
                    if not pj.hasKey(scubaKey):
                        scubaTime = int(grepResult(scubaTimeKey,log, 0) / 1000)
                        concludeTime = int(grepResult(scubaConcludeKey,log, 0) / 1000)
                        if scubaTime == 0:
                            continue
                        scubaNonAlias = grepResult(scubaNonAliasKey,log, 0) 
                        #pj.setResult(scubaKey, str(int(scubaNonAlias)) + '/' + str(scubaTime))
                        #pj.setResult(scubaKey, str(int(scubaNonAlias)) + '/' + str(scubaTime) + '+' + str(concludeTime))
                        pj.setMay(scubaKey, str(int(scubaNonAlias)))
                        #pj.setTime(scubaKey, str(scubaTime) + '+' + str(concludeTime))
                        pj.setTime(scubaKey, str(scubaTime+concludeTime))

                    if not pj.hasCastKey(scubaKey):
                        scubaCast = grepResult(scubaCastKey,log, 0) 
                        pj.setDowncast(scubaKey, str(int(scubaCast)))

                    if not pj.hasVirtKey(scubaKey):
                        scubaVirt = grepResult(scubaVirtKey,log, 0) 
                        pj.setVirt(scubaKey, str(int(scubaVirt)))

                    if pj.getMethods() == '':
                        pj.setMethods(str(int(grepResult(reachMKey,log, 1))))

                    if pj.getStmt() == '':
                        pj.setStmt(str(int(grepResult(stmtKey,log, 0))))

                    if pj.getPairs() == '':
                        pj.setPairs(str(int(grepResult(pairKey,log, 1))))

                    if pj.getAllcast() == '':
                        pj.setAllcast(str(int(grepResult(allCastKey,log, 0))))

                    if pj.getAllvirt() == '':
                        pj.setAllvirt(str(int(grepResult(allVirtKey,log, 0))))

                    chordTimeKey = 'LEAVE: cspa-k'
                    chordTime = grepResult(chordTimeKey,log, -1) 
                    chordNonAlias = grepResult(chordNonAliasKey,log, 1) 

                    chordCast = grepResult(chordCastKey,log, 0) 
                    chordVirt = grepResult(chordVirtKey,log, 0) 
                    pj.setDowncast(mykey, str(int(chordCast)))
                    pj.setVirt(mykey, str(int(chordVirt)))

                    # e.g., 2obj or 3cfa. may alias
                    #pj.setResult(mykey, str(int(chordNonAlias)) + '/' + str(chordTime))
                    pj.setMay(mykey, str(int(chordNonAlias)))
                    pj.setTime(mykey, str(chordTime))
                    if (k=='4' and (proj in no4Objs)) or (proj == 'sunflow' and k=='3'):
                        pj.setMay(mykey, unknow)
                        pj.setTime(mykey, unknow)
                        pj.setDowncast(mykey, unknow)
                        pj.setVirt(mykey, unknow)

                else:
                    #pj.setResult(mykey, unknow)
                    #pj.setResult(scubaKey, unknow)
                    pj.setMay(mykey, unknow)
                    pj.setTime(mykey, unknow)
                    pj.setMay(scubaKey, unknow)
                    pj.setTime(scubaKey, unknow)

    # tabulate the result.
    print 'generating report for benchmark information....'
    headers = ["Project", "ReachableM","Statements"]
    content = []
    footer = ["Average"]
    num1 = 0
    num2 = 0
    mapInfo = {}

    #headers.append('CIPA')

    for k in kRanges:
        for t in anaTypes:
            headers.append(k+t)

        headers.append('S' + k)

    for pj in blist:
        cell = []
        pjName = pj.name
        reachM = pj.getMethods()
        if not str(num1) == 'N/A':
            num1 += int(reachM)

        stmt = pj.getStmt()
        if not str(num2) == 'N/A':
            num2 += int(stmt)

        cell.append(pjName)
        cell.append(reachM)
        cell.append(stmt)
        
        for k in kRanges:
            for t in anaTypes:
                cKey = k + t
                result = pj.getTime(cKey)
                cell.append(result)
                if cKey in mapInfo:
                    val = mapInfo[cKey]
                    if result == 'N/A':
                        #mapInfo[cKey] = result
                        val = int(val) + 3600
                        mapInfo[cKey] = str(val) 
                    else:
                        if not str(val) == 'N/A':
                            val = int(val) + int(result)
                            mapInfo[cKey] = str(val) 
                else:
                    #mapInfo[cKey] = result
                    if result == 'N/A':
                        mapInfo[cKey] = '3600' 
                    else:
                        mapInfo[cKey] = result

            sKey = 'S' + k
            sr = pj.getTime(sKey)
            cell.append(sr)
            if sKey in mapInfo:
                sval = mapInfo[sKey]
                if sr == 'N/A':
                    mapInfo[sKey] = sr
                else:
                    if not str(sval) == 'N/A':
                        sval = int(sval) + int(sr)
                        mapInfo[sKey] = str(sval) 
            else:
                mapInfo[sKey] = sr


        content.append(cell)

    footer.append(avg(num1))
    footer.append(avg(num2))

    for k in kRanges:
        sKey = 'S' + k
        for t in anaTypes:
            cKey = k + t
            footer.append(avg2(mapInfo[cKey]))
 
        footer.append(avg2(mapInfo[sKey]))

    content.append(footer)

    report = tabulate(content, headers, tablefmt="grid")
    print report


    print 'generating report for mayalias....'
    headers = ["Project", "AliasPairs"]
    content = []
    num3 = 0
    aliasInfo = {}
    footer2 = ["Average"]

    for k in kRanges:
        for t in anaTypes:
            headers.append(k+t)

        headers.append('S' + k)

    for pj in blist:
        cell = []
        pjName = pj.name
        pair = pj.getPairs()
        cell.append(pjName)
        cell.append(pair)
        if not str(num3) == 'N/A':
            num3 += int(pair)

        for k in kRanges:
            for t in anaTypes:
                cKey = k + t
                result = pj.getMay(cKey)
                cell.append(result)
                if cKey in aliasInfo:
                    val = aliasInfo[cKey]
                    if result == 'N/A':
                        aliasInfo[cKey] = result
                    else:
                        if not str(val) == 'N/A':
                            val = int(val) + int(result)
                            aliasInfo[cKey] = str(val) 
                else:
                    aliasInfo[cKey] = result

            sKey = 'S' + k
            sr = pj.getMay(sKey)
            cell.append(sr)

            if sKey in aliasInfo:
                sval = aliasInfo[sKey]
                if sr == 'N/A':
                    aliasInfo[sKey] = sr
                else:
                    if not str(sval) == 'N/A':
                        sval = int(sval) + int(sr)
                        aliasInfo[sKey] = str(sval) 
            else:
                aliasInfo[sKey] = sr


        content.append(cell)

    footer2.append(avg(num3))

    for k in kRanges:
        sKey = 'S' + k
        for t in anaTypes:
            cKey = k + t
            footer2.append(avg2(aliasInfo[cKey]))
 
        footer2.append(avg2(aliasInfo[sKey]))

    content.append(footer2)



    report = tabulate(content, headers, tablefmt="grid")
    print report

    print 'generating report for Downcast....'
    headers = ["Project", "Downcasts#"]
    content = []
    num4 = 0
    dkInfo = {}
    footer3 = ["Average"]

    for k in kRanges:
        for t in anaTypes:
            headers.append(k+t)

        headers.append('S' + k)

    for pj in blist:
        cell = []
        pjName = pj.name
        allcast = pj.getAllcast()
        pair = pj.getPairs()
        cell.append(pjName)
        cell.append(allcast)
        if not str(num4) == 'N/A':
            num4 += int(allcast)
        
        for k in kRanges:
            for t in anaTypes:
                cKey = k + t
                result = pj.getDowncast(cKey)
                cell.append(result)
                if cKey in dkInfo:
                    val = dkInfo[cKey]
                    if result == 'N/A':
                        dkInfo[cKey] = result
                    else:
                        if not str(val) == 'N/A':
                            val = int(val) + int(result)
                            dkInfo[cKey] = str(val) 
                else:
                    dkInfo[cKey] = result


            sKey = 'S' + k
            sr = pj.getDowncast(sKey)
            cell.append(sr)

            if sKey in dkInfo:
                sval = dkInfo[sKey]
                if sr == 'N/A':
                    dkInfo[sKey] = sr
                else:
                    if not str(sval) == 'N/A':
                        sval = int(sval) + int(sr)
                        dkInfo[sKey] = str(sval) 
            else:
                dkInfo[sKey] = sr


        content.append(cell)

    footer3.append(avg(num4))

    for k in kRanges:
        sKey = 'S' + k
        for t in anaTypes:
            cKey = k + t
            footer3.append(avg2(dkInfo[cKey]))
 
        footer3.append(avg2(dkInfo[sKey]))

    content.append(footer3)



    report = tabulate(content, headers, tablefmt="grid")
    print report


    print 'generating report for VirtualCall....'
    headers = ["Project", "VirtCall#"]
    content = []

    for k in kRanges:
        for t in anaTypes:
            headers.append(k+t)

        headers.append('S' + k)

    for pj in blist:
        cell = []
        pjName = pj.name
        allvirt = pj.getAllvirt()
        pair = pj.getPairs()
        cell.append(pjName)
        cell.append(allvirt)
        
        for k in kRanges:
            for t in anaTypes:
                cKey = k + t
                result = pj.getVirt(cKey)
                cell.append(result)

            sKey = 'S' + k
            sr = pj.getVirt(sKey)
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


