HOME=/home/javaapps/NGrams

cd $HOME

java -cp dist/NGrams.jar:dist/lib/lucene-analyzers-common-6.1.0.jar:dist/lib/lucene-queryparser-6.1.0.jar:dist/lib/lucene-core-6.1.0.jar:dist/lib/lucene-suggest-6.1.0.jar  br.bireme.ngrams.NGrams index resource ./resource_config.cfg UTF-8 ./resource_output.txt UTF-8

cd -
