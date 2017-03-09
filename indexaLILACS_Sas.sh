LUCENE_VERSION=6.4.2

#HOME=/home/javaapps/NGrams
HOME=/home/heitor/Projetos/NGrams

cd $HOME

java -cp dist/NGrams.jar:dist/lib/lucene-analyzers-common-$LUCENE_VERSION.jar:dist/lib/lucene-queryparser-$LUCENE_VERSION.jar:dist/lib/lucene-core-$LUCENE_VERSION.jar:dist/lib/lucene-suggest-$LUCENE_VERSION.jar  br.bireme.ngrams.NGrams index lilacs_Sas ./configLILACS_Sas.cfg UTF-8 ./LILACS_Sas.txt ISO-8859-1

cd -
