name := "NGrams"

version := "1.0"

<<<<<<< HEAD
scalaVersion := "2.13.18"

val commonsCsvVersion = "1.14.1"
val commonsLangVersion = "3.20.0" //"3.12.0"
val commonsTextVersion = "1.15.0" //"1.10.0"
//val jacksonVersion = "2.17.0" //"2.17.0" "2.15.2"
val luceneVersion = "10.4.0" //"10.3.2" //"9.7.0"
=======
scalaVersion := "2.13.13"

val commonsCsvVersion = "1.10.0"
val commonsLangVersion = "3.14.0" //"3.12.0"
val commonsTextVersion = "1.12.0" //"1.10.0"
val jacksonVersion = "2.17.0" //"2.15.2"
val luceneVersion = "9.10.0" //"9.7.0"
>>>>>>> d13f84982cdde7dcd486bcebc44cff7f23cf8df5
val stringDistVersion = "1.2.7"

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-csv" % commonsCsvVersion,
  "org.apache.commons" % "commons-lang3" % commonsLangVersion,
  "org.apache.commons" % "commons-text" % commonsTextVersion,
  //"com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % "3.0-rc5",
  //"com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "tools.jackson.core" % "jackson-databind" % "3.1.0",
  "org.apache.lucene" % "lucene-analysis-common" % luceneVersion,
  "org.apache.lucene" % "lucene-backward-codecs" % luceneVersion,
  "org.apache.lucene" % "lucene-core" % luceneVersion,
  "org.apache.lucene" % "lucene-queryparser" % luceneVersion,
  "org.apache.lucene" % "lucene-suggest" % luceneVersion,
  "com.github.vickumar1981" %% "stringdistance" % stringDistVersion
)

/*assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _                        => MergeStrategy.first
}*/

assembly / assemblyMergeStrategy := {
  // JPMS
  case "module-info.class" => MergeStrategy.discard
  // multi-release jars
  case PathList("META-INF", "versions", _ @ _*) => MergeStrategy.discard

  // serviços SPI
  case PathList("META-INF", "services", _ @ _*) => MergeStrategy.concat

  // resto do META-INF
  case PathList("META-INF", _ @ _*) => MergeStrategy.discard

  // fallback
  case x => (assembly / assemblyMergeStrategy).value(x)
}


scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Ywarn-unused", "-Xlint:unchecked", "-Xlint:deprecation")
