"""Executes user code inside the :python worker process.

Called from Kotlin: Python.getInstance().getModule("runner").callAttr("run", code, filename, workdir, sink)
`sink` is a com.pyrun.mobile.runtime.OutputSink Java object (sink.write(stream, text)).
Returns an int exit code (0 = success).
"""
import builtins
import importlib
import os
import sys
import traceback

STDOUT, STDERR = 0, 1


class _Stream:
    def __init__(self, sink, stream_id):
        self._sink = sink
        self._id = stream_id
        self.encoding = "utf-8"

    def write(self, s):
        if not isinstance(s, str):
            s = str(s)
        if s:
            self._sink.write(self._id, s)
        return len(s)

    def flush(self):
        pass

    def isatty(self):
        return False

    def writable(self):
        return True


class _EmptyStdin:
    """input() in v1 raises EOFError."""
    encoding = "utf-8"

    def readline(self, *args):
        return ""

    def read(self, *args):
        return ""

    def isatty(self):
        return False

    def __iter__(self):
        return iter(())


def _block_network():
    import socket

    def _blocked(*args, **kwargs):
        raise OSError("Network access is disabled in PyRun Mobile (offline mode)")

    socket.socket.connect = _blocked
    socket.socket.connect_ex = _blocked
    socket.create_connection = _blocked
    socket.getaddrinfo = _blocked


def _purge_user_modules(workdir):
    """The worker process is reused between runs; drop modules imported from the project dir
    so edited helper files are re-imported."""
    prefix = os.path.realpath(workdir) + os.sep
    for name, mod in list(sys.modules.items()):
        path = getattr(mod, "__file__", None)
        if path and os.path.realpath(path).startswith(prefix):
            sys.modules.pop(name, None)


def run(code, filename, workdir, sink):
    old = (sys.stdout, sys.stderr, sys.stdin, sys.argv, list(sys.path), os.getcwd())
    old_dont_write = sys.dont_write_bytecode
    # The .pyc cache trusts mtime+size at 1 s granularity, so editing "1" -> "2" and re-running within a second
    # would import the stale helper. Never write bytecode for user modules.
    sys.dont_write_bytecode = True
    sys.stdout = _Stream(sink, STDOUT)
    sys.stderr = _Stream(sink, STDERR)
    sys.stdin = _EmptyStdin()
    sys.argv = [filename]
    try:
        os.chdir(workdir)
        if workdir not in sys.path:
            sys.path.insert(0, workdir)
        importlib.invalidate_caches()  # pick up files created/changed since the previous run
        _purge_user_modules(workdir)
        _block_network()
        namespace = {"__name__": "__main__", "__file__": filename, "__builtins__": builtins}
        try:
            compiled = compile(code, filename, "exec")
            exec(compiled, namespace)
            return 0
        except SystemExit as e:
            if e.code is None:
                return 0
            if isinstance(e.code, int):
                return e.code
            print(e.code, file=sys.stderr)
            return 1
        except BaseException:
            etype, evalue, tb = sys.exc_info()
            # Skip this runner's own frame so the traceback starts at user code.
            traceback.print_exception(etype, evalue, tb.tb_next if tb else None)
            return 1
    finally:
        sys.stdout, sys.stderr, sys.stdin, sys.argv = old[0], old[1], old[2], old[3]
        sys.path[:] = old[4]
        sys.dont_write_bytecode = old_dont_write
        try:
            os.chdir(old[5])
        except OSError:
            pass
