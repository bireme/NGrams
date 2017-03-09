LUCENE_VERSION=6.4.1

HOME=/home/javaapps/NGrams

cd $HOME

java -cp dist/NGrams.jar:dist/lib/lucene-analyzers-common-$LUCENE_VERSION.jar:dist/lib/lucene-queryparser-$LUCENE_VERSION.jar:dist/lib/lucene-core-$LUCENE_VERSION.jar:dist/lib/lucene-suggest-$LUCENE_VERSION.jar  br.bireme.ngrams.NGrams index medline medline ./config.cfg ISO-8859-1 ./medline.txt ISO-8859-1

cd -
