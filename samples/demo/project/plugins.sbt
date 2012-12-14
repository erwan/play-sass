// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository 
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// Local repository
resolvers += Resolver.url("Local ivy2 Repository", url("file://" + Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)

addSbtPlugin("net.caffeinelab" % "play-sass" % "1.0-SNAPSHOT")

addSbtPlugin("play" % "sbt-plugin" % "2.1-RC1")

