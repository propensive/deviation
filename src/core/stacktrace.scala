package deviation

import rudiments.*

import language.experimental.captureChecking

extension [TupleType <: Tuple](error: Error[TupleType]) def stackTrace: StackTrace = StackTrace(error)

object StackTrace:
  case class Frame(className: Text, method: Text, file: Text, line: Maybe[Int], native: Boolean)
  
  val legend: Map[Text, Text] = Map(
    Text("λₙ") -> Text("anonymous function"),
    Text("αₙ") -> Text("anonymous class"),
    Text("ι")  -> Text("initialization"),
    Text("ς")  -> Text("super reference"),
    Text("ε")  -> Text("extension method"),
    Text("ϕ")  -> Text("direct"),
    Text("π")  -> Text("package file")
  )

  def rewrite(name: String, method: Boolean = false): Text =
    val sb: StringBuilder = StringBuilder()
    
    inline def char(idx: Int): Maybe[Char] = if idx < 0 || idx >= name.length then Unset else name.charAt(idx)

    @tailrec
    def recur(idx: Int, digits: Boolean = false): Text =
      inline def token(idx: Int, str: String, text: String, digits: Boolean = false): Text =
        if (0 until str.length).forall { i => char(idx + i) == str(i) }
        then
          sb.append(text)
          recur(idx + str.length, digits)
        else
          sb.append('#')
          recur(idx + 1, digits)
      
      inline def skip(): Text = token(idx, "$", if method then "()." else "#")

      if idx >= name.length then Text(sb.toString+(if method then "()" else ""))
      else if digits then char(idx) match
        case '0' => token(idx, "0", "₀", true)
        case '1' => token(idx, "1", "₁", true)
        case '2' => token(idx, "2", "₂", true)
        case '3' => token(idx, "3", "₃", true)
        case '4' => token(idx, "4", "₄", true)
        case '5' => token(idx, "5", "₅", true)
        case '6' => token(idx, "6", "₆", true)
        case '7' => token(idx, "7", "₇", true)
        case '8' => token(idx, "8", "₈", true)
        case '9' => token(idx, "9", "₉", true)
        case _   => recur(idx, false)
      else char(idx) match
        case 'i' =>
          if (0 until 8).forall { i => char(idx + i) == "initial$"(i) }
          then
            sb.append("ι")
            recur(idx + 8)
          else
            sb.append("i")
            recur(idx + 1)
        case 's' =>
          if (0 until 6).forall { i => char(idx + i) == "super$"(i) }
          then
            sb.append("ς")
            recur(idx + 6)
          else
            sb.append("s")
            recur(idx + 1)
        case '$' => char(idx + 1) match
          case '_' => token(idx,           "$_avoid_name_clash_$", "′")
          case 'a' => char(idx + 2) match
            case 'm' => token(idx,         "$amp",                 "&")
            case 'n' => char(idx + 5) match
              case 'f' => token(idx,       "$anonfun",             "λ")
              case _   => token(idx,       "$anon",                "α")
            case 't' => token(idx,         "$at",                  "@")
            case _   => skip()
          case 'b' => char(idx + 2) match
            case 'a' => char(idx + 3) match
              case 'n' => token(idx,       "$bang",                "!")
              case 'r' => token(idx,       "$bar",                 "|")
              case _   => skip()
            case 's' => token(idx,         "$bslash",              "\\")
            case _   => skip()
          case 'c' => token(idx,           "$colon",               ":")
          case 'd' => char(idx + 2) match
            case 'e' => token(idx,         "$default",             "δ")
            case 'i' => char(idx + 3) match
              case 'r' => token(idx,       "$direct",              "⋮ϕ")
              case 'v' => token(idx,       "$div",                 "/")
              case _   => skip()
            case _   => skip()
          case 'e' => char(idx + 2) match
            case 'q' => token(idx,         "$eq",                  "=")
            case 'x' => token(idx,         "$extension",           "⋮ε")
            case _   => skip()
          case 'g' => token(idx,           "$greater",             ">")
          case 'h' => token(idx,           "$hash",                "#")
          case 'l' => token(idx,           "$less",                "<")
          case 'm' => token(idx,           "$minus",               "-")
          case 'p' => char(idx + 2) match
            case 'a' => token(idx,         "$package",             "⋮π")
            case 'e' => token(idx,         "$percent",             "%")
            case 'l' => token(idx,         "$plus",                "+")
            case _   => skip()
          case 'q' => token(idx,           "$qmark",               "?")
          case 't' => char(idx + 2) match
            case 'i' => char(idx + 3) match
              case 'l' => token(idx,       "$tilde",               "~")
              case 'm' => token(idx,       "$times",               "*")
              case _   => skip()
            case _  => skip()
          case 'u' => token(idx,           "$up",                  "^")
          case '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' => recur(idx + 1, true)
          case _   => skip()
        case ch  =>
          sb.append(ch.toString)
          recur(idx + 1)
    
    val rewritten = recur(0)

    def primitive(char: Char) = char match
      case 'Z' => "Boolean"
      case 'I' => "Int"
      case 'J' => "Long"
      case 'V' => "Unit"
      case 'S' => "Short"
      case 'F' => "Float"
      case 'C' => "Char"
      case 'D' => "Double"
      case 'L' => "Any"
      case _   => "?"

    if rewritten.s.startsWith("scala.runtime.java8.JFunction") && rewritten.s.endsWith("#sp") then
      val types: String = rewritten.s.drop(29).dropRight(3)
      types.indexOf("#mc") match
        case -1 | 0 =>
          rewritten
        
        case i =>
          Text:
            val types2 = types.drop(i + 3).to(List).map(primitive)
            if types2.length <= 2 then types2.mkString("(", " => ", ")")
            else types2.init.mkString("((", ", ", s") => ${types2.last})")
    
    else if rewritten.s.startsWith("scala.runtime.function.JProcedure") then Text:
      val n = safely(rewritten.s.drop(33).toInt).or(0)
      "("+(if n < 2 then s"Any" else List.fill(n)("Any").mkString("(", ", ", ")"))+" => Unit)"
    
    else rewritten

  def apply(exception: Throwable): StackTrace =
    val frames = List(exception.getStackTrace.nn.map(_.nn)*).map: frame =>
      StackTrace.Frame(
        rewrite(frame.getClassName.nn),
        rewrite(frame.getMethodName.nn, method = true),
        Text(Option(frame.getFileName).map(_.nn).getOrElse("[no file]")),
        if frame.getLineNumber < 0 then Unset else frame.getLineNumber,
        frame.isNativeMethod
      )
    
    val cause = Option(exception.getCause)
    val fullClassName: Text = rewrite(exception.getClass.nn.getName.nn)
    val fullClass: List[Text] = List(fullClassName.s.split("\\.").nn.map(_.nn).map(Text(_))*)
    val className: Text = fullClass.last
    val component: Text = Text(fullClassName.s.substring(0, 0 max (fullClassName.s.length - className.s.length - 1)).nn)
    val message = Text(Option(exception.getMessage).map(_.nn).getOrElse(""))
    
    StackTrace(component, className, message, frames, cause.map(_.nn).map(StackTrace(_)).maybe)

case class StackTrace(component: Text, className: Text, message: Text, frames: List[StackTrace.Frame],
                          cause: Maybe[StackTrace]):
  def crop(cutClassName: Text, cutMethod: Text): StackTrace =
    val frames2 = frames.takeWhile { f => f.className != cutClassName || f.method != cutMethod }
    StackTrace(component, className, message, frames2, cause)
  
  def drop(n: Int): StackTrace =
    StackTrace(component, className, message, frames.drop(n), cause)
  
  def dropRight(n: Int): StackTrace =
    StackTrace(component, className, message, frames.dropRight(n), cause)
