package net.caffeinelab

import sbt.PlayExceptions.AssetCompilationException
import java.io.File
import scala.sys.process._
import sbt.IO
import io.Source
import io.Source._
import org.jruby.embed.ScriptingContainer

import scala.collection.JavaConversions._

object SassCompiler {

  // Don't reuse for thread safety
  def scriptingContainer(loadPaths: Seq[String]) = {
      val container = new ScriptingContainer(org.jruby.embed.LocalContextScope.SINGLETON)
      container.getProvider().setLoadPaths(loadPaths)
      container
  }

  def compile(sassFile: File, loadPaths: Seq[String], sassPaths: Seq[String]): (String, Option[String], Seq[File]) = {
    val cssOutput = compile(sassFile, loadPaths, sassPaths, false)
    val compressedCssOutput = compile(sassFile, loadPaths, sassPaths, true)
    (cssOutput, Some(compressedCssOutput), dependencies(sassFile, sassPaths.head))
  }

  val ErrorPattern = """.*Sass\:\:SyntaxError\:\s(.*)\n.*""".r

  def compile(sassFile: File, loadPaths: Seq[String], sassPaths: Seq[String], compressed: Boolean): String = {
    val errors = new java.io.StringWriter()
    val result = new StringBuffer()
    val container = scriptingContainer(loadPaths)
    container.setErrorWriter(errors)
    container.put("@result", result)
    try {
      container.runScriptlet("""
        require 'sass'
        require 'compass'

        options = {}
        options[:load_paths] = %LOADPATH%
        options[:update] = true
        options[:style] = %STYLE%
        options[:line_comments] = '%COMMENTS%'
        options[:syntax] = %SYNTAX%
        input = File.new('%ABSPATH%', 'r')
        tree = ::Sass::Engine.new(input.read(), options).to_tree
        @result.append(tree.render)
      """.replace("%LOADPATH%", "[" + sassPaths.map("'" + _ + "'").mkString(",") + "]")
         .replace("%ABSPATH%", sassFile.getAbsolutePath())
         .replace("%STYLE%", if (compressed) ":compressed" else ":nested")
         .replace("%COMMENTS%", if (compressed) "false" else "true")
         .replace("%SYNTAX%", if (sassFile.getAbsolutePath().endsWith(".scss")) ":scss" else ":sass"))
      result.toString
    } catch {
      case e: Exception =>
        println(e)
        val sassError: String = (ErrorPattern findAllIn errors.toString).toSeq.headOption.getOrElse("")
        throw new AssetCompilationException(Some(sassFile),
          "SASS Compilation of " + sassFile + " failed: " + sassError,
          Some(0), // TODO: real line number (if possible)
          None)
    }
  }

  val importsPattern = """@import\s+["']?([^\s'";]+)["']?""".r

  private def imports(sass: File): Seq[String] = {
    val body = Source.fromFile(sass).mkString
    importsPattern.findAllIn(body).matchData.map(_.group(1)).toSeq
  }

  private val importPattern: Seq[String => String] = Seq(
    identity,
    _ + ".sass",
    _ + ".scss",
    "_" + _,
    "_" + _ + ".sass",
    "_" + _ + ".scss"
  )

  // For a given import name, find the one corresponding to an existing file
  private def findImport(name: String, root: String): Option[File] = importPattern
    .map(f => new File(root + File.separator + f(name)))
    .filter(_.exists)
    .headOption

  private def dependencies(sass: File, root: String): Seq[File] = {
    imports(sass).map(findImport(_, root)).flatten match {
      case Nil => Seq(sass)
      case deps => Seq(sass) ++ deps.flatMap(d => dependencies(d, root))
    }
  }

}

