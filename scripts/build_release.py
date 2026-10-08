#!/usr/bin/env python3
"""Build without printing signing credentials. Keep the keystore and password outside Git."""
import argparse, os, subprocess
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--keystore',required=True)
parser.add_argument('--password-file',required=True)
parser.add_argument('--gradle',default='./gradlew')
parser.add_argument('--signed-debug',action='store_true')
parser.add_argument('--with-tests',action='store_true')
parser.add_argument('--version-code',type=int)
parser.add_argument('--version-name')
args=parser.parse_args()
env=os.environ.copy()
secret=Path(args.password_file).read_text().strip()
if not secret: raise SystemExit('Signing password file is empty.')
env.update(KEYSTORE_PATH=str(Path(args.keystore).resolve()),STORE_PASSWORD=secret,KEY_PASSWORD=secret)
if args.signed_debug: env['SMS_SYNC_SIGNED_TEST']='1'
if args.version_code: env['SMS_SYNC_VERSION_CODE']=str(args.version_code)
if args.version_name: env['SMS_SYNC_VERSION_NAME']=args.version_name
tasks=[':app:assembleDebug' if args.signed_debug else ':app:assembleRelease']
if args.with_tests: tasks += [':app:assembleDebugAndroidTest', ':app:testDebugUnitTest', ':app:lintDebug']
subprocess.run([args.gradle,*tasks],env=env,check=True)
