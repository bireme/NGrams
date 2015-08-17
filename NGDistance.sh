echo $1
echo $2
java -cp dist/NGrams.jar:dist/lib/lucene-analyzers-common-5.2.1.jar:dist/lib/lucene-queryparser-5.2.1.jar:dist/lib/lucene-core-5.2.1.jar:dist/lib/lucene-suggest-5.2.1.jar  br.bireme.ngrams.Tools $1 $2

