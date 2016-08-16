fabric-base [![Build Status](http://modmuss50.me:8080/job/Fabric/job/Fabric-Base/badge/icon)](http://modmuss50.me:8080/job/Fabric/job/Fabric-Base/)
===========

The base API module for the Fabric Mod Loader. Licensed under the Apache License 2.0.

## Prerequisites

- Java 8

## Setup

Before you can build Fabric or set it up for an IDE, you must setup the Fabric environment.

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
