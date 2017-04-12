LUCENE_VERSION=6.5.0

HOME=/home/javaapps/NGrams

cd $HOME

java -cp dist/NGrams.jar:dist/lib/lucene-analyzers-common-$LUCENE_VERSION.jar:dist/lib/lucene-queryparser-$LUCENE_VERSION.jar:dist/lib/lucene-core-$LUCENE_VERSION.jar:dist/lib/lucene-suggest-$LUCENE_VERSION.jar  br.bireme.ngrams.NGrams export $1 $2 $3 $4 $5

cd -
