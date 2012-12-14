package net.litola

import sbt.PlayExceptions.AssetCompilationException
import java.io.File
import scala.sys.process._
import sbt.IO
import io.Source._
import org.jruby.embed.ScriptingContainer

import scala.collection.JavaConversions._

object SassCompiler {

  // Don't reuse for thread safety
  def scriptingContainer = {
      val container = new ScriptingContainer(org.jruby.embed.LocalContextScope.SINGLETON)
      // container.getProvider().setLoadPaths();
      container
  }

  def compile(sassFile: File, options: Seq[String]): (String, Option[String], Seq[File]) = {
    println("Compiling: " + sassFile)
    val cssOutput = compile(sassFile, false)
    val compressedCssOutput = compile(sassFile, true)
    (cssOutput, Some(compressedCssOutput), dependencies(sassFile))
  }

  def compile(sassFile: File, compressed: Boolean): String = {
    val result = new StringBuffer();
    scriptingContainer.put("@result", result);
    scriptingContainer.runScriptlet("""
      require 'sass'
      require 'compass'

      @result.append("boo")
""") /*
      options = {}
      options[:load_paths] = ${sassFile.getAbsolutePath()}
      options[:update] = true
      options[:style] = ${if (compressed) "compressed" else "expanded"}
      options[:line_comments] = ${if (compressed) "false" else "true"}
      options[:syntax] = ${if (sassFile.getAbsolutePath().endsWith(".scss")) ":scss" else ":sass"}
      input = File.new('${css.getAbsolutePath()}', 'r')
      tree = ::Sass::Engine.new(input.read(), options).to_tree
      @result.append(tree.render)
    """)*/
    result.toString

  }

  private def dependencies(sass: File): Seq[File] = Nil

}

