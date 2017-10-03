name := "lambda-actors"
version := "0.1.0"
scalaVersion := "2.11.6"


resolvers ++= Seq( "mvnrepository" at "https://mvnrepository.com/artifact" )
resolvers += Resolver.sonatypeRepo("releases")


libraryDependencies ++= Seq(
  /*
  "com.typesafe.akka" %% "akka-actor" % "2.5.6",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.6" % Test,
  */
  "com.typesafe.akka" %% "akka-actor" % "2.4.16",
  "com.danielasfregola" %% "twitter4s" % "5.1",
  "com.ibm" %% "couchdb-scala" % "0.7.2",
  "joda-time" % "joda-time" % "2.9.9"
)
