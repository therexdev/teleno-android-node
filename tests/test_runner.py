#!/usr/bin/env python3
"""Host Linux lifetime tests of the exact supervisor source; no blockchain network."""
import os, pathlib, signal, subprocess, sys, tempfile, time
runner = pathlib.Path(sys.argv[1]).resolve()

def wait_for(path):
    limit = time.monotonic() + 8
    while time.monotonic() < limit:
        if path.exists(): return
        time.sleep(.02)
    raise AssertionError(f'fixture did not become ready: {path.name}')

with tempfile.TemporaryDirectory(prefix='teleno-runner-test-') as d:
    root = pathlib.Path(d)
    script = root/'child.py'
    script.write_text('import os,signal,time,pathlib,sys\np=pathlib.Path(sys.argv[1])\nsignal.signal(signal.SIGTERM,lambda s,f: sys.exit(0))\np.write_text(str(os.getpid()))\nwhile True: time.sleep(.1)\n')
    def command(marker):
        return [str(runner), str(root/'lock'), str(root/'private.key'), sys.executable, str(script), str(marker)]
    (root/'private.key').write_text('fixture only')
    p = subprocess.Popen(command(root/'ready'))
    try:
        wait_for(root/'ready')
        other = subprocess.run(command(root/'other-ready'), capture_output=True, timeout=5)
        assert other.returncode == 73 and b'ALREADY_RUNNING' in other.stderr
        assert not (root/'other-ready').exists()
        assert (root/'private.key').exists(), 'refused second launch must not remove active key'
        p.terminate(); assert p.wait(timeout=8) == 0
        assert not (root/'private.key').exists()
    finally:
        if p.poll() is None: p.kill(); p.wait()
    # A signing value travels only on inherited stdin. No key file is created.
    pipe_reader = pathlib.Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else None
    if pipe_reader is not None:
        result = subprocess.run([str(runner), str(root/'lock'), str(root/'private.key'), str(pipe_reader)],
            input=b'public-fixture-only\n', capture_output=True, timeout=8)
        assert result.returncode == 0, result.stderr
        assert not (root/'private.key').exists()
        assert b'public-fixture-only' not in result.stdout + result.stderr
    # Death of the app parent must signal the wrapper and stop its child.
    marker = root/'death-ready'
    parent_code = 'import subprocess,time,sys\np=subprocess.Popen(sys.argv[1:])\nprint(p.pid,flush=True)\ntime.sleep(60)\n'
    parent = subprocess.Popen([sys.executable,'-c',parent_code,*command(marker)], stdout=subprocess.PIPE, text=True)
    try:
        supervisor_pid = int(parent.stdout.readline()); wait_for(marker)
        node_pid = int(marker.read_text()); parent.kill(); parent.wait(timeout=5)
        limit = time.monotonic() + 8
        while time.monotonic() < limit:
            try: os.kill(node_pid, 0)
            except ProcessLookupError: break
            time.sleep(.02)
        else: raise AssertionError('node survived parent death')
    finally:
        if parent.poll() is None: parent.kill(); parent.wait()
    print('PASS: supervisor exclusive ownership, graceful stop, transient key cleanup, parent-death shutdown, and private stdin transport (host Linux).')
