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
parser.add_argument('--test-class',default='LiteUiTest',choices=['LiteUiTest','LiteRuntimeUiTest','LiteRestoreTest','SyncStatusUiTest'])
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
# Android's runtime JAR also contains java.lang stubs. Keep them at runtime for
# Robolectric, but exclude them from the compile path on modular host JDKs.
compile_jars = []
for jar in jars:
    if jar.name.startswith('android-all-'):
        target = build / 'android-compile.jar'
        with zipfile.ZipFile(jar) as src, zipfile.ZipFile(target, 'w') as dst:
            for entry in src.infolist():
                if not entry.filename.startswith(('java/', 'javax/', 'sun/', 'jdk/', 'com/sun/')):
                    dst.writestr(entry, src.read(entry))
        compile_jars.append(target)
    else:
        compile_jars.append(jar)
compile_classpath = os.pathsep.join(str(p.resolve()) for p in
                                    compile_jars + [compiled, args.classes])
subprocess.run(['java', '-jar', str(args.ecj.resolve()), '-17', '-nowarn',
                '-cp', compile_classpath, '-d', str(compiled),
                str(root / ('tests/ui/'+args.test_class+'.java'))], check=True)
subprocess.run(['java', '-Xmx2g',
                '-Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2',
                '-Dteleno.ui.output=' + str(args.output.resolve()),
                '-cp', classpath, 'org.junit.runner.JUnitCore',
                'com.koinos.teleno.'+args.test_class], check=True)
