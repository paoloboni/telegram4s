#!/usr/bin/env bash

GPG_TTY=$(tty)
sbt "release with-defaults skip-tests"
