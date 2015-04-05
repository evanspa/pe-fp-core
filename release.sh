#!/bin/bash

readonly NEXT_TAG=$1

lein release :patch
git checkout tags/${NEXT_TAG}
lein doc
git checkout gh-pages
mv -f doc/* .
git add *.html
git add css
git add js
git commit -m "updated docs"
git push --set-upstream origin gh-pages
git checkout master
