ant clean
ant jar
LD_LIBRARY_PATH=/home/yufeng/research/Scuba/lib java -cp lib/chord.jar -Dchord.work.dir=/home/yufeng/research/benchmarks/pjbench-read-only/dacapo/benchmarks/antlr -Dchord.run.analyses=cspa-0cfa-dlog,prune-dlog,vv-refine-java,cspa-downcast-java,cspa-query-resolve-dlog,cipa-java,sum-java chord.project.Boot
