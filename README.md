# pe-gasjot-core

[![Build Status](https://travis-ci.org/evanspa/pe-gasjot-core.svg)](https://travis-ci.org/evanspa/pe-gasjot-core)

A Clojure library encapsulating the server-side core logic, model and data
access functionality of the Gas Jot system.  pe-gasjot-core leverages
PostgreSQL as the underlying backend.

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**

- [About the Gas Jot System](#about-the-gas-jot-system)
  - [Client Applications](#client-applications)
  - [Server Application](#server-application)
- [Documentation](#documentation)
- [Installation](#installation)
- [pe-* Clojure Library Suite](#pe--clojure-library-suite)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## About the Gas Jot System

The Gas Jot system provides the ability to record and analyze your vehicle's
gas usage.  It currently consists of an iOS (iPhone) application, as well as a
server-side REST application (*which the iOS app communicates with*).

### Client Applications

Currently there only exists an iOS application for the Gas Jot system:
[Gas Jot iOS App](https://github.com/evanspa/GasJot-ios).

### Server Application

The server-side REST application is implemented using Clojure: [pe-gasjot-app](https://github.com/evanspa/pe-gasjot-app).  This repository, *pe-gasjot-core*, is used by it indirectly through the [pe-gasjot-rest](https://github.com/evanspa/pe-gasjot-rest) library.

pe-gasjot-core makes use of the [pe-* Clojure library suite](#pe--clojure-library-suite).

## Documentation

* [API Docs](http://evanspa.github.com/pe-gasjot-core)

## Installation

pe-gasjot-core is available from Clojars.  Add the following dependency to your
`project.clj` file:

```
[pe-fp-core "0.0.46"]
```

## pe-* Clojure Library Suite
The pe-* Clojure library suite is a set of Clojure libraries to aid in the
development of Clojure based applications.
*(Each library is available on Clojars.)*
+ **[pe-core-utils](https://github.com/evanspa/pe-core-utils)**: provides a set
of various collection-related, date-related and other helpers functions.
+ **[pe-jdbc-utils](https://github.com/evanspa/pe-jdbc-utils)**: provides
  a set of helper functions for working with JDBC.
+ **[pe-datomic-utils](https://github.com/evanspa/pe-datomic-utils)**: provides
  a set of helper functions for working with [Datomic](https://www.datomic.com).
+ **[pe-datomic-testutils](https://github.com/evanspa/pe-datomic-testutils)**: provides
  a set of helper functions to aid in unit testing Datomic-enabled functions.
+ **[pe-user-core](https://github.com/evanspa/pe-user-core)**: provides
  a set of functions for modeling a generic user, leveraging PostgreSQL as the
  underlying backend.
+ **[pe-user-testutils](https://github.com/evanspa/pe-user-testutils)**: a set of helper functions to aid in unit testing
code that depends on the functionality of the pe-user-* libraries
([pe-user-core](https://github.com/evanspa/pe-user-core) and [pe-user-rest](https://github.com/evanspa/pe-user-rest)).
+ **[pe-apptxn-core](https://github.com/evanspa/pe-apptxn-core)**: provides a
  set of functions implementing the server-side core data layer of the
  PEAppTransaction Logging Framework.
+ **[pe-rest-utils](https://github.com/evanspa/pe-rest-utils)**: provides a set
  of functions for building easy-to-version hypermedia REST services (built on
  top of [Liberator](http://clojure-liberator.github.io/liberator/)).
+ **[pe-rest-testutils](https://github.com/evanspa/pe-rest-testutils)**: provides
  a set of helper functions for unit testing web services.
+ **[pe-user-rest](https://github.com/evanspa/pe-user-rest)**: provides a set of
  functions encapsulating an abstraction modeling a user within a REST API.
+ **[pe-apptxn-restsupport](https://github.com/evanspa/pe-apptxn-restsupport)**:
  provides a set of functions implementing the server-side REST layer of the
  PEAppTransaction Logging Framework.
