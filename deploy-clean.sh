#!/bin/bash
set -e
set -u
rm -rf target/mvn-repo
git branch -D mvn-repo || true
git push origin --delete mvn-repo || true
mvn deploy "$@"
git pull
