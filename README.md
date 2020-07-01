# Scalafix Rewrites for Scala

## How to run the rewrites

Add the `sbt-scalafix` sbt plugin, with the SemanticDB compiler plugin enabled ([official docs][1]):

```scala
// project/plugins.sbt
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.17")
```

```scala
// build.sbt
inThisBuild(List(
  semanticdbEnabled := true,
  semanticdbOptions += "-P:semanticdb:synthetics:on", // make sure to add this
  semanticdbVersion := scalafixSemanticdb.revision,
))
```

Then run the desired rewrite(s) ([official docs][2]), in sbt:

```scala
> scalafix dependency:fix.scala213.ExplicitNonNullaryApply@org.scala-lang:scala-rewrites:0.1.0`
> Test/scalafix dependency:fix.scala213.ExplicitNonNullaryApply@org.scala-lang:scala-rewrites:0.1.0
```

You can also add the following to your `build.sbt`:

```scala
ThisBuild / scalafixDependencies += "org.scala-lang" %% "scala-rewrites" % "0.1.0"
```

and then:

```scala
> scalafix fix.scala213.ExplicitNonNullaryApply
> Test/scalafix fix.scala213.ExplicitNonNullaryApply
```

[1]: https://scalacenter.github.io/scalafix/docs/users/installation.html
[2]: https://scalacenter.github.io/scalafix/docs/rules/external-rules.html

## To develop/contribute to any of the rewrites

```
sbt ~tests/test
# edit rewrites/src/main/scala/...
```
