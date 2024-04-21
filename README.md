# Stella Type Checker

Type Checker for [Stella](https://fizruk.github.io/stella/) programming language

## Features

Type Checker supports language core features with following extensions:
* [Natural Literals](https://fizruk.github.io/stella/site/extensions/syntax/#natural-literals)
* [Unit Type](https://fizruk.github.io/stella/site/extensions/simple-types/#unit-type)
* [Pairs](https://fizruk.github.io/stella/site/extensions/simple-types/#pairs) and [Tuples](https://fizruk.github.io/stella/site/extensions/simple-types/#tuples)
* [Records](https://fizruk.github.io/stella/site/extensions/simple-types/#records)
* [Lists](https://fizruk.github.io/stella/site/extensions/simple-types/#lists)
* [let-Bindings](https://fizruk.github.io/stella/site/extensions/syntax/#let-bindings)
* [Type Ascriptions](https://fizruk.github.io/stella/site/extensions/simple-types/#type-ascriptions)
* [Sum Types](https://fizruk.github.io/stella/site/extensions/simple-types/#sum-types)
* [Variants](https://fizruk.github.io/stella/site/extensions/simple-types/#variants)
* [Fixed-point combinator](https://fizruk.github.io/stella/site/extensions/syntax/#primitive-recursion-loops)
* [Nested Function Declarations](https://fizruk.github.io/stella/site/extensions/syntax/#nested-function-declarations)
* [Nullary Functions](https://fizruk.github.io/stella/site/extensions/syntax/#nullary-functions)
* [Multiparameter Functions](https://fizruk.github.io/stella/site/extensions/syntax/#multiparameter-functions)
* [Sequencing](https://fizruk.github.io/stella/site/extensions/syntax/#sequencing)
* [References](https://fizruk.github.io/stella/site/extensions/references/)
* [Exceptions](https://fizruk.github.io/stella/site/extensions/exceptions/) (except `#open-variant-exceptions`)
* [Structural Subtyping](https://fizruk.github.io/stella/site/extensions/subtyping/#structural-subtyping)
* [Top](https://fizruk.github.io/stella/site/extensions/subtyping/#top-type) and [Bottom](https://fizruk.github.io/stella/site/extensions/subtyping/#bottom-type) Type
* Other:
  * `#ambiguous-type-as-bottom`
  * `#type-cast` and `#try-cast-as`

## Build and run

Build:
```shell
gradlew jar
```

Run:
```shell
java -jar build/libs/stella-type-checker.jar
```

Type program source code and then send `EOF` (`Ctrl+D` for **\*nix**; `Enter`, `Ctrl+Z`, `Enter` for **Windows**)