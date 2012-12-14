name := "play-sass"

version := "1.0-SNAPSHOT"

sbtPlugin := true

organization := "net.caffeinelab"

description := "Sass assets plugin for Play 2.1"

libraryDependencies += "org.jruby" % "jruby-complete" % "1.7.1"

addSbtPlugin("play" %% "sbt-plugin" % "2.1-RC1")

