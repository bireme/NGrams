LUCENE_VERSION=6.4.2

HOME=/home/javaapps/NGrams

cd $HOME

java -cp dist/NGrams.jar:dist/lib/lucene-analyzers-common-$LUCENE_VERSION.jar:dist/lib/lucene-queryparser-$LUCENE_VERSION.jar:dist/lib/lucene-core-$LUCENE_VERSION.jar:dist/lib/lucene-suggest-$LUCENE_VERSION.jar  br.bireme.ngrams.NGrams index resource ./resource_config.cfg UTF-8 ./resource_output.txt UTF-8

cd -
