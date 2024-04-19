name := "NGrams"

version := "1.0"

scalaVersion := "2.13.13"

val commonsCsvVersion = "1.10.0"
val commonsLangVersion = "3.14.0" //"3.12.0"
val commonsTextVersion = "1.12.0" //"1.10.0"
val jacksonVersion = "2.17.0" //"2.15.2"
val luceneVersion = "9.10.0" //"9.7.0"
val stringDistVersion = "1.2.7"


libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-csv" % commonsCsvVersion,
  "org.apache.commons" % "commons-lang3" % commonsLangVersion,
  "org.apache.commons" % "commons-text" % commonsTextVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  //"org.apache.lucene" % "lucene-analyzers-common" % luceneVersion,   // version before 9.0
  "org.apache.lucene" % "lucene-analysis-common" % luceneVersion,     // version 9.0
  "org.apache.lucene" % "lucene-backward-codecs" % luceneVersion,
  "org.apache.lucene" % "lucene-core" % luceneVersion,
  "org.apache.lucene" % "lucene-queryparser" % luceneVersion,
  "org.apache.lucene" % "lucene-suggest" % luceneVersion,
  "com.github.vickumar1981" %% "stringdistance" % stringDistVersion
)

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _                        => MergeStrategy.first
}

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Ywarn-unused", "-Xlint:unchecked", "-Xlint:deprecation")
