#HOME=/home/javaapps/NGrams
HOME=/home/heitor/Projetos/NGrams

cd $HOME

java -cp dist/NGrams.jar:dist/lib/lucene-analyzers-common-6.1.0.jar:dist/lib/lucene-queryparser-6.1.0.jar:dist/lib/lucene-core-6.1.0.jar:dist/lib/lucene-suggest-6.1.0.jar  br.bireme.ngrams.NGrams index lilacs_Sas ./configLILACS_Sas.cfg UTF-8 ./LILACS_Sas.txt ISO-8859-1

cd -
