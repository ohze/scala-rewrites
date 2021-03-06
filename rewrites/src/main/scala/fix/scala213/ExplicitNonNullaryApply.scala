package fix.scala213

import scala.PartialFunction.{ cond, condOpt }
import scala.collection.mutable

import metaconfig.Configured

import scala.meta._
import scala.meta.internal.pc.ScalafixGlobal

import scalafix.v1._
import scalafix.internal.v1.LazyValue

/** Explicitly insert () to non-nullary method applications that lack it.
 *  https://dotty.epfl.ch/docs/reference/dropped-features/auto-apply.html
 *  https://github.com/scala/scala/pull/8833
 */
final class ExplicitNonNullaryApply(global: LazyValue[ScalafixGlobal])
    extends impl.CompilerDependentRule(global, "fix.scala213.ExplicitNonNullaryApply")
{
  def this() = this(LazyValue.later(() => ScalafixGlobal.newCompiler(Nil, Nil, Map.empty)))

  protected def unsafeFix()(implicit doc: SemanticDocument) = {
    lazy val power = new impl.Power(global.value)
    doc.tree.collect(collector(power)).asPatch
  }

  def collector(power: => impl.IPower)(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = {
    val handled = mutable.Set.empty[Term.Name]

    def isJavaDefined(name: Term.Name): Boolean = name.value match {
      case "toString"     => true // fast-track known, common cases
      case "getClass"     => true
      case "hashCode"     => true
      case "asInstanceOf" => true
      case "isInstanceOf" => true
      case _              => power.isJavaDefined(name)
    }

    def fix(tree: Tree, meth: Term, noTypeArgs: Boolean, noArgs: Boolean) = {
      for {
        name <- termName(meth)
        if name.value != "##" // fast-track https://github.com/scala/scala/pull/8814
        if handled.add(name)
        if noArgs
        if name.isReference
        if !cond(name.parent) { case Some(Term.ApplyInfix(_, `name`, _, _)) => true }
        if !tree.parent.exists(_.is[Term.Eta]) // else rewrites `meth _` to `meth() _`, or requires running ExplicitNullaryEtaExpansion first
        info <- name.symbol.info
        if !info.isJava // shallow, isJavaDefined (below) checks overrides
        if cond(info.signature) {
          case MethodSignature(_, Nil :: _, _) => true
          case ClassSignature(_, _, _, decls) if tree.isInstanceOf[Term.ApplyType] =>
            decls.exists { decl =>
              decl.displayName == "apply" &&
                cond(decl.signature) { case MethodSignature(_, Nil :: _, _) => true }
            }
        }
        if !isJavaDefined(name) // full check, using the presentation compiler :O
      } yield {
        val optAddDot = name.parent.collect {
          case PostfixSelect(qual, `name`) =>
            Patch.removeTokens(doc.tokenList.trailingSpaces(qual.tokens.last)) +
              Patch.addLeft(name.tokens.head, ".")
        }
        val target = if (noTypeArgs) name else tree
        // WORKAROUND scalameta/scalameta#1083
        // Fixes `lhs op (arg)` from being rewritten as `lhs op (arg)()` (instead of `lhs op (arg())`)
        val token = target.tokens.reverseIterator.find(!_.is[Token.RightParen]).get
        optAddDot.asPatch + Patch.addRight(token, "()")
      }
    }.asPatch

    {
      case t @ q"$meth[..$targs](...$args)" => fix(t, meth, targs.isEmpty, args.isEmpty)
      case t @ q"$meth(...$args)"           => fix(t, meth, true,          args.isEmpty)
    }
  }

  // No PostfixSelect in Scalameta, so build one
  private object PostfixSelect {
    def unapply(t: Tree)(implicit doc: SemanticDocument): Option[(Term, Term.Name)] = t match {
      case Term.Select(qual, name) =>
        val tokenList = doc.tokenList
        val inBetweenSlice = tokenList.slice(tokenList.next(qual.tokens.last), name.tokens.head)
        if (inBetweenSlice.exists(_.is[Token.Dot])) None
        else Some((qual, name))
      case _ => None
    }
  }

  private def termName(term: Term): Option[Term.Name] = condOpt(term) {
    case name: Term.Name                 => name
    case Term.Select(_, name: Term.Name) => name
  }

  override def withConfiguration(config: Configuration) = {
    val compileSv = config.scalaVersion
    val runtimeSv = scala.util.Properties.versionNumberString
    if ((compileSv.take(4) != runtimeSv.take(4)) && config.scalacOptions.nonEmpty) {
      Configured.error(
        s"Scala version mismatch: " +
          s"(1) the target sources were compiled with Scala $compileSv; " +
          s"(2) Scalafix is running on Scala $runtimeSv. " +
          s"To fix make scalafixScalaBinaryVersion == ${compileSv.take(4)}. " +
          "Try `ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)`."
      )
    } else {
      Configured.ok(new ExplicitNonNullaryApply(LazyValue.later { () =>
        ScalafixGlobal.newCompiler(config.scalacClasspath, config.scalacOptions, Map.empty)
      }))
    }
  }
}
