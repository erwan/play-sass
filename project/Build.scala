import sbt._
import sbt.Keys._

import scalariform.formatter.preferences._
import com.typesafe.sbtscalariform.ScalariformPlugin._

object PluginBuild extends Build {

  lazy val projectScalariformSettings = defaultScalariformSettings ++ Seq(
    ScalariformKeys.preferences := FormattingPreferences()
      .setPreference(AlignParameters, true)
      .setPreference(FormatXml, false)
    )


  lazy val playSass = Project(
    id = "play-sass", base = file("."), settings = Project.defaultSettings ++ projectScalariformSettings
  )

}
