#!/usr/bin/env python3
"""Run native Android UI smoke tests against the compiled APK Java classes.

Resolve tests/ui/pom.xml dependencies separately. Test libraries are never
packaged into the application. See tests/ui/README.md.
"""
import argparse
import os
from pathlib import Path
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--deps', type=Path, required=True)
parser.add_argument('--ecj', type=Path, required=True)
parser.add_argument('--classes', type=Path, default=root / 'build/apk/classes')
parser.add_argument('--output', type=Path, default=root / 'evidence/ui')
args = parser.parse_args()
deps = args.deps.resolve()
build = root / 'build/ui-tests'
build.mkdir(parents=True, exist_ok=True)
compiled = build / 'classes'
compiled.mkdir(exist_ok=True)
jars = sorted(deps.glob('*.jar'))
for aar in sorted(deps.glob('*.aar')):
    with zipfile.ZipFile(aar) as archive:
        target = build / (aar.stem + '-classes.jar')
        target.write_bytes(archive.read('classes.jar'))
        jars.append(target)
if not jars or not args.classes.is_dir():
    parser.error('Resolve the UI dependencies and build the APK classes first.')
classpath = os.pathsep.join(str(p.resolve()) for p in
                            jars + [compiled, args.classes])
subprocess.run(['java', '-jar', str(args.ecj.resolve()), '-17', '-nowarn',
                '-cp', classpath, '-d', str(compiled),
                str(root / 'tests/ui/UiSmokeTest.java')], check=True)
subprocess.run(['java', '-Xmx2g',
                '-Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2',
                '-Dteleno.ui.output=' + str(args.output.resolve()),
                '-cp', classpath, 'org.junit.runner.JUnitCore',
                'com.koinos.teleno.UiSmokeTest'], check=True)
