#!/bin/bash
echo Username $CIRCLE_USERNAME
if [ "$CIRCLE_USERNAME" != "circleci" ]; then
  #get highest tags across all branches, not just the current branch
  NEW_VERSION=$(mvn -q -Dexec.executable="echo" -Dexec.args='${project.version}' --non-recursive org.codehaus.mojo:exec-maven-plugin:1.3.1:exec)

  git checkout testtagging

  mvn --batch-mode release:update-versions -DdevelopmentVersion=$NEW_DEV_VERSION

  git config --global user.email "info@kauri.io"
  git config --global user.name "circleci"

  git add .
  git commit -m "CircleCI build $CIRCLE_BUILD_NUM updating version after tag"
  git push origin testtagging
fi
