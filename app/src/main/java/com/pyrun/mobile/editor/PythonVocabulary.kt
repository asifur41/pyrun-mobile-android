package com.pyrun.mobile.editor

object PythonVocabulary {
    val KEYWORDS: Set<String> = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
        "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
        "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
        "return", "try", "while", "with", "yield"
    )
    val BUILTINS: Set<String> = setOf(
        "abs", "all", "any", "bin", "bool", "bytearray", "bytes", "callable", "chr", "classmethod",
        "compile", "complex", "delattr", "dict", "dir", "divmod", "enumerate", "eval", "exec",
        "filter", "float", "format", "frozenset", "getattr", "globals", "hasattr", "hash", "hex",
        "id", "input", "int", "isinstance", "issubclass", "iter", "len", "list", "locals", "map",
        "max", "min", "next", "object", "oct", "open", "ord", "pow", "print", "property", "range",
        "repr", "reversed", "round", "set", "setattr", "slice", "sorted", "staticmethod", "str",
        "sum", "super", "tuple", "type", "vars", "zip",
        "Exception", "ValueError", "TypeError", "KeyError", "IndexError", "RuntimeError",
        "StopIteration", "OSError", "ImportError", "AttributeError", "NameError", "ZeroDivisionError"
    )
    val ALL: Set<String> = KEYWORDS + BUILTINS
}
