// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.
//
// Copyright (c) 2011-2019 ETH Zurich.

import org.scalatest.FunSuite
import viper.silver.verifier.ConsistencyError
import viper.silver.ast._
import org.scalatest.Matchers

class ConsistencyTests extends FunSuite with Matchers {

    /* These tests invoke errors from consistency checks made at AST nodes.
    At the moment, many of these errors cannot be invoked by parsing a Silver program
    because the corresponding checks are also made at the type-checking stage, and thus
    the final AST is never created. It is desirable to remove these duplicate checks from
    the type-checker at some point.
   */

  test("While loop condition"){
    val loop : While = While(IntLit(1)(NoPosition, NoInfo, NoTrafos), Seq(),
      Seqn(Seq(), Seq())(NoPosition, NoInfo, NoTrafos))(NoPosition, NoInfo, NoTrafos)
    loop.checkTransitively should be (Seq(
      ConsistencyError("While loop condition must be of type Bool, but found Int", NoPosition)))
  }

  test("Missing and duplicate identifiers"){
    val funcapp1 : FuncApp = FuncApp("f1", Seq())(NoPosition, NoInfo, Int, Seq(), NoTrafos)
    val methodcall1: MethodCall = MethodCall("m2", Seq(), Seq())(NoPosition, NoInfo, NoTrafos)
    val method1 : Method = Method("m1", Seq(), Seq(), Seq(), Seq(),
      Some(Seqn(Seq[Stmt](LocalVarAssign(LocalVar("i")(Int, NoPosition, NoInfo, NoTrafos), funcapp1)(NoPosition, NoInfo,
        NoTrafos), Goto("lbl1")(NoPosition, NoInfo, NoTrafos), methodcall1), Seq())(NoPosition, NoInfo, NoTrafos)))(NoPosition, NoInfo, NoTrafos)
    val prog : Program = Program(Seq(), Seq(Field("j", Int)(NoPosition, NoInfo, NoTrafos), Field("j", Bool)(NoPosition, NoInfo, NoTrafos)),
      Seq(), Seq(), Seq(method1))(NoPosition, NoInfo, NoTrafos)

    prog.checkTransitively should be (Seq(
      ConsistencyError("Duplicate identifier j found.", NoPosition),
      ConsistencyError("Local variable i not found.", NoPosition),
      ConsistencyError("No matching identifier f1 found of type Function.", NoPosition),
      ConsistencyError("No matching identifier lbl1 found of type Label.", NoPosition),
      ConsistencyError("No matching identifier m2 found of type Method.", NoPosition)))
  }

  test("Type mismatched identifiers"){
    val funcapp1 : FuncApp = FuncApp("f1", Seq())(NoPosition, NoInfo, Int, Seq(), NoTrafos)
    val method1 : Method = Method("m1", Seq(), Seq(), Seq(), Seq(),
      Some(Seqn(Seq[Stmt](LocalVarAssign(LocalVar("i")(Int, NoPosition, NoInfo, NoTrafos), funcapp1)(NoPosition, NoInfo,
        NoTrafos), LocalVarAssign(LocalVar("j")(Int, NoPosition, NoInfo, NoTrafos), IntLit(5)(NoPosition))(NoPosition, NoInfo,
        NoTrafos)), Seq(LocalVarDecl("i", Bool)(NoPosition)))(NoPosition, NoInfo, NoTrafos)))(NoPosition, NoInfo, NoTrafos)
    val method2: Method = Method("j", Seq(), Seq(), Seq(), Seq(), Some(Seqn(Seq(), Seq())(NoPosition)))(NoPosition)
    val prog : Program = Program(Seq(), Seq(Field("f1", Int)(NoPosition, NoInfo, NoTrafos)),
      Seq(), Seq(), Seq(method1, method2))(NoPosition, NoInfo, NoTrafos)

    prog.checkTransitively should be (Seq(
      ConsistencyError("No matching local variable i found with type Int, instead found Bool.", NoPosition),
      ConsistencyError("No matching identifier f1 found of type Function, instead found of type Field.", NoPosition),
      ConsistencyError("No matching local variable j found with type Int, instead found other identifier of type Method.", NoPosition)))
  }

  test("Conditional expression"){
    val condexp1 : CondExp = CondExp(BoolLit(true)(NoPosition, NoInfo, NoTrafos),
      IntLit(5)(NoPosition, NoInfo, NoTrafos), FuncApp("f1", Seq())(NoPosition, NoInfo, Bool, Seq(), NoTrafos))(NoPosition, NoInfo, NoTrafos)

    condexp1.checkTransitively should be (Seq(
      ConsistencyError("Second and third parameter types of conditional expression must match, but found Int and Bool", NoPosition)
    ))
  }

  test("Field assignment"){
    val fieldassign1 : FieldAssign = FieldAssign(FieldAccess(IntLit(5)(NoPosition, NoInfo, NoTrafos),
      Field("i", Int)(NoPosition, NoInfo, NoTrafos))(NoPosition, NoInfo, NoTrafos),
      BoolLit(false)(NoPosition, NoInfo, NoTrafos))(NoPosition, NoInfo, NoTrafos)

    fieldassign1.checkTransitively should be (Seq(
      ConsistencyError("Right-hand side false is not assignable to left-hand side 5.i.", NoPosition)))
  }

  test("Method arity") {
    val callee =
      Method(
        name          = "callee",
        formalArgs    = Seq(LocalVarDecl("x", Ref)()),
        formalReturns = Seq(),
        pres          = Seq(),
        posts         = Seq(),
        body          = None
      )()

    val callerBody =
      Seqn(
        Seq(
          MethodCall(callee, Seq(), Seq())(), // Wrong: zero arguments
          MethodCall(callee, Seq(NullLit()(), NullLit()()), Seq())(), // Wrong: two arguments
          MethodCall(callee, Seq(NullLit()()), Seq())(), // Right: one argument
          MethodCall(callee, Seq(TrueLit()()), Seq())() // Wrong: incorrect type
        ),
        Seq()
      )()

    val caller =
      Method(
        name          = "caller",
        formalArgs    = Seq(),
        formalReturns = Seq(),
        pres          = Seq(),
        posts         = Seq(),
        body          = Some(callerBody)
      )()

    val program =
      Program(
        domains    = Seq(),
        fields     = Seq(),
        functions  = Seq(),
        predicates = Seq(),
        methods    = Seq(callee, caller)
      )()

    program.checkTransitively shouldBe Seq(
      ConsistencyError("Arguments List() are not assignable to formal arguments List(x: Ref) of method callee", NoPosition),
      ConsistencyError("Arguments List(null, null) are not assignable to formal arguments List(x: Ref) of method callee", NoPosition),
      ConsistencyError("Arguments List(true) are not assignable to formal arguments List(x: Ref) of method callee", NoPosition)
    )
  }
}
