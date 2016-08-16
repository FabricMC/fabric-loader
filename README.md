chorus-base [![Build Status](http://modmuss50.me:8080/job/Chorus/job/Chorus-Base/badge/icon)](http://modmuss50.me:8080/job/Chorus/job/Chorus-Base/)
===========

The base API module for the Chorus Mod Loader. Licensed under the Apache License 2.0.

## Prerequisites

- Java 8

## Setup

Before you can build Chorus or set it up for an IDE, you must setup the chorus environment.

- Run `gradle setupChorus`

### IDE Setup

#### IntelliJ Idea

- Import project into IntelliJ
- Run `gradle genIdeaRuns`
- Reload project

#### Eclipse

**Currently our Gradle plugin does not generate any run configurations for Eclipse**

- Import project into Eclipse

## Building

- `gradle build`

You will now find the compiled jars under the `./build/libs` directory.
