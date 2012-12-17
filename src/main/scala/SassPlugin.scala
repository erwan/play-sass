package net.caffeinelab

import sbt._
import sbt.Keys._
import play._
import PlayExceptions._


object SassPlugin extends Plugin {
  val sassEntryPoints = SettingKey[PathFinder]("play-sass-entry-points")
  val sassOptions = SettingKey[Seq[String]]("play-sass-options")

  private def expand(directories: Seq[File]): Seq[File] = directories.flatMap { _ match {
    case f if f.isDirectory => Seq(f) ++ expand(f.listFiles)
    case f => Nil
  }}

  override val settings = Seq(
    sassEntryPoints <<= (sourceDirectory in Compile)(base => ((base / "assets" ** "*.sass") +++ (base / "assets" ** "*.scss") --- base / "assets" ** "_*")),
    sassOptions := Seq.empty[String],
    resourceGenerators in Compile <+= (target, state, sourceDirectory in Compile, resourceManaged in Compile, cacheDirectory, streams, sassEntryPoints) map { (target, state, src, resources, cache, streams, files) =>

      val logger = streams.log
      val sassWorkingDir = target / "sass-compass"

      if (!sassWorkingDir.exists) {
        val maybeSass = this.getClass.getClassLoader.asInstanceOf[java.net.URLClassLoader].getURLs.map(_.getFile).map(file).find { file =>
          file.getAbsolutePath.contains("jruby-complete")
        }

        maybeSass.map { zipFile =>
          IO.unzip(zipFile, sassWorkingDir)
        }.orElse {
          sys.error("OOPS. missing SASS?")
        }
      }

      val rubyPaths = (sassWorkingDir / "gems").listFiles.map(_ / "lib").map(_.getAbsolutePath)
      val sassPaths = Seq(src / "assets" / "stylesheets").map(_.getAbsolutePath)

      // Here `sassWorkingDir` reference a directory containing the whole SASS Stuff, so you can work with it as process resources as usual.
      import java.io._

      val name = "sass"
      val naming:  (String, Boolean) => String = (name, min) => name.replaceAll(".s[ac]ss", if (min) ".min.css" else ".css")
      val watch: File => PathFinder = (file => (file ** "*.sass") +++ (file ** "*.scss"))
      val cacheFile = cache / name
      val currentInfos = watch(src).get.map(f => f -> FileInfo.lastModified(f)).toMap

      val (previousRelation, previousInfo) = Sync.readInfo(cacheFile)(FileInfo.lastModified.format)

      if (previousInfo != currentInfos) {

        //a changed file can be either a new file, a deleted file or a modified one
        lazy val changedFiles: Seq[File] = currentInfos.filter(e => !previousInfo.get(e._1).isDefined || previousInfo(e._1).lastModified < e._2.lastModified).map(_._1).toSeq ++ previousInfo.filter(e => !currentInfos.get(e._1).isDefined).map(_._1).toSeq

        //erease dependencies that belong to changed files
        val dependencies = previousRelation.filter((original, compiled) => changedFiles.contains(original))._2s
        dependencies.foreach(IO.delete)

        /**
         * If the given file was changed or
         * if the given file was a dependency,
         * otherwise calculate dependencies based on previous relation graph
         */
        val generated: Seq[(File, java.io.File)] = (files x relativeTo(Seq(src / "assets"))).flatMap {
          case (sourceFile, name) => {
            if (changedFiles.contains(sourceFile) || dependencies.contains(new File(resources, "public/" + naming(name, false)))) {
              val (debug, min, deps) = try {
                SassCompiler.compile(sourceFile, rubyPaths, sassPaths)
              } catch {
                case e: AssetCompilationException => throw play.Project.reportCompilationError(state, e)
              }
              val out = new File(resources, "public/" + naming(name, false))
              IO.write(out, debug)
              (deps ++ Seq(sourceFile)).toSet[File].map(_ -> out) ++ min.map { minified =>
                val outMin = new File(resources, "public/" + naming(name, true))
                IO.write(outMin, minified)
                deps.map(_ -> outMin)
              }.getOrElse(Nil)
            } else {
              previousRelation.filter((original, compiled) => original == sourceFile)._2s.map(sourceFile -> _)
            }
          }
        }

        //write object graph to cache file 
        Sync.writeInfo(cacheFile,
          Relation.empty[File, File] ++ generated,
          currentInfos)(FileInfo.lastModified.format)

        // Return new files
        generated.map(_._2).distinct.toList

      } else {
        // Return previously generated files
        previousRelation._2s.toSeq
      }

    })
  
}

